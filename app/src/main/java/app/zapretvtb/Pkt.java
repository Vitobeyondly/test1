package app.zapretvtb;

final class Pkt {
    static int id;
    static int u16(byte[] b, int o) { return ((b[o] & 255) << 8) | (b[o + 1] & 255); }
    static long u32(byte[] b, int o) { return ((long) (b[o] & 255) << 24) | ((b[o + 1] & 255) << 16) | ((b[o + 2] & 255) << 8) | (b[o + 3] & 255); }
    static void p16(byte[] b, int o, int v) { b[o] = (byte) (v >> 8); b[o + 1] = (byte) v; }
    static void p32(byte[] b, int o, long v) { b[o] = (byte) (v >> 24); b[o + 1] = (byte) (v >> 16); b[o + 2] = (byte) (v >> 8); b[o + 3] = (byte) v; }

    static int sum(byte[] b, int off, int len, int init) {
        long s = init & 0xffffffffL; int i = off, e = off + len;
        for (; i + 1 < e; i += 2) s += ((b[i] & 255) << 8) | (b[i + 1] & 255);
        if (i < e) s += (b[i] & 255) << 8;
        while ((s >> 16) != 0) s = (s & 0xffff) + (s >> 16);
        return (int) s;
    }

    static int csum(byte[] p, int off, int len, byte[] s, byte[] d, int proto) {
        int init = sum(d, 0, 4, sum(s, 0, 4, 0)) + proto + len;
        return (~sum(p, off, len, init)) & 0xffff;
    }

    static void ip(byte[] p, int tot, int proto, byte[] s, byte[] d) {
        p[0] = 0x45; p16(p, 2, tot); p16(p, 4, id++ & 0xffff); p16(p, 6, 0x4000);
        p[8] = 64; p[9] = (byte) proto;
        System.arraycopy(s, 0, p, 12, 4); System.arraycopy(d, 0, p, 16, 4);
        p16(p, 10, (~sum(p, 0, 20, 0)) & 0xffff);
    }

    static byte[] tcp(byte[] src, byte[] dst, int sp, int dp, long seq, long ack, int fl, byte[] pl, int po, int pn, boolean mss) {
        int th = mss ? 24 : 20, tot = 20 + th + pn;
        byte[] p = new byte[tot];
        ip(p, tot, 6, src, dst);
        int t = 20;
        p16(p, t, sp); p16(p, t + 2, dp); p32(p, t + 4, seq); p32(p, t + 8, ack);
        p[t + 12] = (byte) ((th / 4) << 4); p[t + 13] = (byte) fl; p16(p, t + 14, 65535);
        if (mss) { p[t + 20] = 2; p[t + 21] = 4; p16(p, t + 22, 1360); }
        if (pn > 0) System.arraycopy(pl, po, p, t + th, pn);
        p16(p, t + 16, csum(p, t, th + pn, src, dst, 6));
        return p;
    }

    static byte[] udp(byte[] src, byte[] dst, int sp, int dp, byte[] pl, int po, int pn) {
        int tot = 28 + pn;
        byte[] p = new byte[tot];
        ip(p, tot, 17, src, dst);
        p16(p, 20, sp); p16(p, 22, dp); p16(p, 24, 8 + pn);
        System.arraycopy(pl, po, p, 28, pn);
        int c = csum(p, 20, 8 + pn, src, dst, 17);
        p16(p, 26, c == 0 ? 0xffff : c);
        return p;
    }
}
