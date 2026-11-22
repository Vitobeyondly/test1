package app.zapretvtb;

import android.os.ParcelFileDescriptor;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Userspace TCP/UDP relay for a TUN fd. Splits the first TLS/HTTP request of listed hosts. */
final class Engine {
    interface Prot { void protect(Socket s); void protect(DatagramSocket s); }

    static final int FIN = 1, SYN = 2, RST = 4, PSH = 8, ACK = 16;
    static final long M = 0xffffffffL;

    static final class Conn {
        byte[] cip, dip; int sp, dp; long key;
        volatile Socket s; ParcelFileDescriptor pfd; int ttl0 = 64;
        volatile long mySeq, next, lastAck, last = System.currentTimeMillis();
        volatile int wnd = 65535;
        volatile boolean closed, finSent, finRecv;
        boolean first;
        final LinkedBlockingQueue<byte[]> up = new LinkedBlockingQueue<>();
    }

    static final class Udp {
        byte[] cip, dip; int sp, dp; long key; DatagramSocket s; ParcelFileDescriptor pfd; int ttl0 = 64, n; Strategy.Profile p;
        volatile long last = System.currentTimeMillis();
    }

    final OutputStream tun; final Prot prot; final List<Strategy.Profile> profiles; final Strategy.Res res; final int gttl; final boolean noQuic; final File tmp;
    final Map<Long, Conn> tcp = new ConcurrentHashMap<>();
    final Map<Long, Udp> udp = new ConcurrentHashMap<>();
    final Random rnd = new Random();
    final ExecutorService ex = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
    final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
    volatile boolean run = true;

    Engine(OutputStream tun, Prot prot, List<Strategy.Profile> profiles, Strategy.Res res, int gttl, boolean noQuic, File tmp) {
        this.tun = tun; this.prot = prot; this.profiles = profiles; this.res = res; this.gttl = gttl; this.noQuic = noQuic; this.tmp = tmp;
        sweeper.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            for (Conn c : tcp.values()) if (now - c.last > 180000) close(c, true);
        }, 30, 30, TimeUnit.SECONDS);
    }

    void out(byte[] p) { synchronized (tun) { try { tun.write(p); } catch (IOException ignored) { } } }

    void send(Conn c, int fl, byte[] pl, int n, boolean mss) {
        out(Pkt.tcp(c.dip, c.cip, c.dp, c.sp, c.mySeq, c.next, fl, pl, 0, n, mss));
    }

    void input(byte[] b, int n) {
        if (n < 20 || (b[0] >> 4 & 15) != 4) return; // IPv6 dropped -> apps fall back to IPv4
        int ihl = (b[0] & 15) * 4, proto = b[9] & 255, tot = Math.min(Pkt.u16(b, 2), n);
        if ((Pkt.u16(b, 6) & 0x3fff) != 0) return;
        byte[] src = Arrays.copyOfRange(b, 12, 16), dst = Arrays.copyOfRange(b, 16, 20);
        if (proto == 6) tcpIn(b, ihl, tot, src, dst);
        else if (proto == 17) udpIn(b, ihl, tot, src, dst);
    }

    void tcpIn(byte[] b, int o, int tot, byte[] src, byte[] dst) {
        if (tot - o < 20) return;
        int sp = Pkt.u16(b, o), dp = Pkt.u16(b, o + 2);
        long seq = Pkt.u32(b, o + 4), ack = Pkt.u32(b, o + 8);
        int off = (b[o + 12] >> 4 & 15) * 4, fl = b[o + 13] & 0x3f, win = Pkt.u16(b, o + 14);
        int po = o + off, pn = tot - po;
        if (pn < 0) return;
        long key = ((long) sp << 48) | (Pkt.u32(dst, 0) << 16) | dp;
        Conn c = tcp.get(key);
        if ((fl & SYN) != 0 && (fl & ACK) == 0) {
            if (c == null) {
                final Conn nc = new Conn();
                nc.cip = src; nc.dip = dst; nc.sp = sp; nc.dp = dp; nc.key = key; nc.next = (seq + 1) & M;
                tcp.put(key, nc);
                ex.execute(() -> connect(nc));
            }
            return;
        }
        if (c == null) {
            if ((fl & RST) == 0) {
                boolean a = (fl & ACK) != 0;
                out(Pkt.tcp(dst, src, dp, sp, a ? ack : 0, (seq + pn + ((fl & (SYN | FIN)) != 0 ? 1 : 0)) & M, a ? RST : (RST | ACK), null, 0, 0, false));
            }
            return;
        }
        c.last = System.currentTimeMillis();
        if ((fl & RST) != 0) { close(c, false); return; }
        if (c.s == null) return;
        if ((fl & ACK) != 0) {
            synchronized (c) {
                if (((ack - c.lastAck) & M) < 0x80000000L) c.lastAck = ack;
                c.wnd = win; c.notifyAll();
            }
            if (c.finSent && c.finRecv && ack == c.mySeq) { close(c, false); return; }
        }
        if (pn > 0) {
            if (seq == c.next) { c.next = (c.next + pn) & M; c.up.add(Arrays.copyOfRange(b, po, po + pn)); }
            send(c, ACK, null, 0, false);
        }
        if ((fl & FIN) != 0 && !c.finRecv && ((seq + pn) & M) == c.next) {
            c.finRecv = true; c.next = (c.next + 1) & M;
            send(c, ACK, null, 0, false);
            c.up.add(new byte[0]);
        }
    }

    void connect(Conn c) {
        Socket s = new Socket();
        try {
            prot.protect(s);
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(InetAddress.getByAddress(c.dip), c.dp), 8000);
        } catch (Exception e) {
            try { s.close(); } catch (Exception ignored) { }
            out(Pkt.tcp(c.dip, c.cip, c.dp, c.sp, 0, c.next, RST | ACK, null, 0, 0, false));
            tcp.remove(c.key);
            return;
        }
        c.pfd = Raw.dup(s);
        if (c.pfd != null) c.ttl0 = Raw.ttl(c.pfd.getFileDescriptor());
        long iss = rnd.nextInt() & M;
        c.mySeq = iss;
        send(c, SYN | ACK, null, 0, true);
        c.mySeq = (iss + 1) & M; c.lastAck = c.mySeq;
        c.s = s;
        ex.execute(() -> writer(c));
        ex.execute(() -> reader(c));
    }

    void reader(Conn c) {
        byte[] buf = new byte[1360];
        boolean err = false;
        try {
            InputStream is = c.s.getInputStream();
            while (!c.closed) {
                int n = is.read(buf);
                if (n < 0) break;
                synchronized (c) {
                    while (!c.closed && ((c.mySeq - c.lastAck) & M) >= Math.max(c.wnd, 1)) c.wait(500);
                }
                if (c.closed) return;
                c.last = System.currentTimeMillis();
                send(c, PSH | ACK, buf, n, false);
                c.mySeq = (c.mySeq + n) & M;
            }
        } catch (Exception e) { err = true; }
        if (c.closed) return;
        if (err) { close(c, true); return; }
        byte[] p = Pkt.tcp(c.dip, c.cip, c.dp, c.sp, c.mySeq, c.next, FIN | ACK, null, 0, 0, false);
        c.mySeq = (c.mySeq + 1) & M; c.finSent = true;
        out(p);
    }

    void writer(Conn c) {
        try {
            OutputStream os = c.s.getOutputStream();
            while (!c.closed) {
                byte[] d = c.up.poll(1, TimeUnit.SECONDS);
                if (d == null) continue;
                if (d.length == 0) { c.s.shutdownOutput(); return; }
                if (!c.first) { c.first = true; desync(c, os, gather(c, d)); }
                else os.write(d);
            }
        } catch (Exception e) { close(c, true); }
    }

    /** Collect the full first TLS record (ClientHello may span several TUN packets). */
    byte[] gather(Conn c, byte[] d) throws InterruptedException {
        if (d.length < 5 || d[0] != 0x16) return d;
        int need = 5 + Pkt.u16(d, 3);
        if (d.length >= need) return d;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(d, 0, d.length);
        while (bo.size() < need) {
            byte[] x = c.up.poll(300, TimeUnit.MILLISECONDS);
            if (x == null) break;
            if (x.length == 0) { c.up.add(x); break; }
            bo.write(x, 0, x.length);
        }
        return bo.toByteArray();
    }

    Strategy.Profile pick(Conn c, String host) {
        long ip = Pkt.u32(c.dip, 0);
        for (Strategy.Profile p : profiles) {
            if (p.tcp == null && p.udp != null) continue;
            if (p.tcp != null && !Strategy.inRanges(p.tcp, c.dp)) continue;
            if (p.hosts != null && (host == null || !p.hosts.match(host))) continue;
            if (p.ips != null && !p.ips.contains(ip)) continue;
            return p;
        }
        return null;
    }

    static int resolve(String t, byte[] d, int[] h, boolean tls) {
        try {
            switch (t) {
                case "midsld": return h == null ? -1 : (tls ? Tls.mid(d, h) : h[0] + h[1] / 2);
                case "midhost": return h == null ? -1 : h[0] + h[1] / 2;
                case "host": case "sniext": return h == null ? -1 : h[0];
                case "endhost": return h == null ? -1 : h[0] + h[1];
                default: return Integer.parseInt(t);
            }
        } catch (Exception e) { return -1; }
    }

    int ttlOf(Strategy.Profile p, Strategy.Step s) { return s.ttl > 0 ? s.ttl : p.ttl > 0 ? p.ttl : gttl; }

    void desync(Conn c, OutputStream os, byte[] d) throws Exception {
        boolean tls = d.length > 5 && d[0] == 0x16 && d[5] == 1;
        int[] h = tls ? Tls.sni(d) : Tls.httpHost(d);
        String host = h == null ? null : new String(d, h[0], h[1], StandardCharsets.ISO_8859_1);
        Strategy.Profile p = pick(c, host);
        if (p == null) { os.write(d); return; }
        Strategy.Step fake = null, split = null, dis = null;
        for (Strategy.Step s : p.steps) {
            switch (s.type) {
                case "fake": fake = s; break;
                case "multisplit": case "split": case "split2": case "fakedsplit": case "hostfakesplit": split = s; break;
                case "multidisorder": case "disorder": case "disorder2": case "fakeddisorder": dis = s; break;
                default: break;
            }
        }
        Strategy.Step ps = dis != null ? dis : split;
        List<Integer> cuts = new ArrayList<>();
        if (ps != null) {
            if (ps.type.equals("hostfakesplit") && h != null) { cuts.add(h[0]); cuts.add(h[0] + h[1]); }
            else for (String t : (ps.pos != null ? ps.pos : p.splitPos).split(",")) {
                int v = resolve(t.trim(), d, h, tls);
                if (v > 0 && v < d.length && !cuts.contains(v)) cuts.add(v);
            }
            Collections.sort(cuts);
        }
        FileDescriptor fd = c.pfd != null ? c.pfd.getFileDescriptor() : null;
        int start = 0;
        if (fake != null && fd != null) {
            String fn = fake.file != null ? fake.file : tls ? p.fakeTls : (h != null ? p.fakeHttp : null);
            byte[] fb = res.bytes(fn != null ? fn : "tls_clienthello_www_google_com.bin");
            if (fb != null && fb.length > 0) start = Raw.fakeSend(fd, tmp, fb, d, Math.min(fb.length, d.length), ttlOf(p, fake), c.ttl0);
        }
        int prev = start;
        boolean first = true;
        for (int i = 0; i <= cuts.size(); i++) {
            int e = i < cuts.size() ? cuts.get(i) : d.length;
            if (e <= prev) continue;
            boolean lost = dis != null && fake == null && first && fd != null && e < d.length;
            if (lost) Raw.ttl(fd, 1);
            os.write(d, prev, e - prev); os.flush();
            if (lost) { Thread.sleep(3); Raw.ttl(fd, c.ttl0); }
            first = false; prev = e;
            if (e < d.length) Thread.sleep(2);
        }
    }

    void close(Conn c, boolean rst) {
        if (c.closed) return;
        c.closed = true;
        tcp.remove(c.key);
        if (rst && c.s != null) out(Pkt.tcp(c.dip, c.cip, c.dp, c.sp, c.mySeq, c.next, RST | ACK, null, 0, 0, false));
        try { if (c.s != null) c.s.close(); if (c.pfd != null) c.pfd.close(); } catch (Exception ignored) { }
        synchronized (c) { c.notifyAll(); }
    }

    Strategy.Profile udpProfile(byte[] dst, int dp) {
        long ip = Pkt.u32(dst, 0);
        for (Strategy.Profile p : profiles) {
            if (p.udp == null || !Strategy.inRanges(p.udp, dp)) continue;
            if (p.ips != null && !p.ips.contains(ip)) continue;
            for (Strategy.Step s : p.steps) if (s.type.equals("fake")) return p;
        }
        return null;
    }

    void fakeUdp(Udp u) throws Exception {
        Strategy.Step st = null;
        for (Strategy.Step s : u.p.steps) if (s.type.equals("fake")) { st = s; break; }
        if (st == null || u.pfd == null) return;
        String fn = st.file != null ? st.file : u.dp == 443 ? u.p.fakeQuic : u.p.fakeUdp;
        byte[] fb = res.bytes(fn != null ? fn : "quic_initial_www_google_com.bin");
        if (fb == null || fb.length == 0) return;
        int reps = Math.min(8, st.z1 ? u.p.repeats : st.repeats);
        Raw.ttl(u.pfd.getFileDescriptor(), ttlOf(u.p, st));
        InetAddress a = InetAddress.getByAddress(u.dip);
        for (int i = 0; i < reps; i++) u.s.send(new DatagramPacket(fb, fb.length, a, u.dp));
        Raw.ttl(u.pfd.getFileDescriptor(), u.ttl0);
    }

    void udpIn(byte[] b, int o, int tot, byte[] src, byte[] dst) {
        if (tot - o < 8) return;
        int sp = Pkt.u16(b, o), dp = Pkt.u16(b, o + 2), n = tot - o - 8;
        if (n < 0) return;
        long key = ((long) sp << 48) | (Pkt.u32(dst, 0) << 16) | dp;
        Udp u = udp.get(key);
        try {
            if (u == null) {
                Strategy.Profile p = udpProfile(dst, dp);
                if (p == null && dp == 443 && noQuic) return; // no QUIC strategy -> force TCP fallback
                u = new Udp();
                u.cip = src; u.dip = dst; u.sp = sp; u.dp = dp; u.key = key; u.p = p;
                u.s = new DatagramSocket();
                prot.protect(u.s);
                u.pfd = Raw.dup(u.s);
                if (u.pfd != null) u.ttl0 = Raw.ttl(u.pfd.getFileDescriptor());
                udp.put(key, u);
                final Udp nu = u;
                ex.execute(() -> udpRead(nu));
            }
            u.last = System.currentTimeMillis();
            if (u.p != null && u.n++ < u.p.cutoff) fakeUdp(u);
            u.s.send(new DatagramPacket(b, o + 8, n, InetAddress.getByAddress(dst), dp));
        } catch (Exception ignored) { }
    }

    void udpRead(Udp u) {
        byte[] buf = new byte[2048];
        DatagramPacket pk = new DatagramPacket(buf, buf.length);
        try {
            u.s.setSoTimeout(3000);
            while (run) {
                try {
                    pk.setLength(buf.length);
                    u.s.receive(pk);
                    u.last = System.currentTimeMillis();
                    out(Pkt.udp(u.dip, u.cip, u.dp, u.sp, buf, 0, pk.getLength()));
                } catch (SocketTimeoutException e) {
                    if (System.currentTimeMillis() - u.last > (u.dp == 53 ? 15000 : 90000)) break;
                }
            }
        } catch (Exception ignored) { }
        udp.remove(u.key);
        u.s.close();
        try { if (u.pfd != null) u.pfd.close(); } catch (Exception ignored) { }
    }

    void stop() {
        run = false;
        for (Conn c : tcp.values()) close(c, false);
        for (Udp u : udp.values()) u.s.close();
        sweeper.shutdownNow();
        ex.shutdownNow();
    }
}
