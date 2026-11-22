package app.zapretvtb;

import java.util.*;
import java.util.regex.*;

/** Parses winws (zapret1) and winws2 (zapret2, --lua-desync subset) command lines from .bat files. */
final class Strategy {
    interface Res { byte[] bytes(String name); HostList hosts(String name); IpSet ips(String name); }

    static final class Step { String type, pos, file; int ttl, repeats = 1; boolean z1; }

    static final class Profile {
        int[][] tcp, udp; HostList hosts; IpSet ips;
        final List<Step> steps = new ArrayList<>();
        String splitPos = "2", fakeTls, fakeHttp, fakeQuic, fakeUdp;
        int ttl, cutoff = 1, repeats = 1;
        boolean l7udp;
    }

    static String base(String v) {
        v = v.replace("\"", "");
        int i = Math.max(v.lastIndexOf('\\'), v.lastIndexOf('/'));
        return v.substring(i + 1);
    }

    static int num(String v, int def) { try { return Integer.parseInt(v.replaceAll("\\D", "")); } catch (Exception e) { return def; } }

    static int[][] ports(String v) {
        List<int[]> l = new ArrayList<>();
        for (String t : v.replace("\"", "").split(",")) {
            try {
                String[] r = t.trim().split("-");
                int a = Integer.parseInt(r[0]), b = r.length > 1 ? Integer.parseInt(r[1]) : a;
                l.add(new int[]{a, b});
            } catch (Exception ignored) { }
        }
        return l.toArray(new int[0][]);
    }

    static boolean inRanges(int[][] r, int p) {
        for (int[] x : r) if (p >= x[0] && p <= x[1]) return true;
        return false;
    }

    static void add(List<Profile> out, Profile p) {
        if (p.steps.isEmpty()) return;
        if (p.tcp == null && p.udp == null && p.l7udp) p.udp = new int[][]{{1, 65535}};
        out.add(p);
    }

    static List<Profile> parse(String txt, Res r) {
        txt = txt.replace("\r", "").replace("^\n", " ");
        Map<String, String> vars = new HashMap<>();
        Matcher sm = Pattern.compile("set \"(\\w+)=([^\"]*)\"").matcher(txt);
        while (sm.find()) vars.putIfAbsent(sm.group(1), sm.group(2));
        for (Map.Entry<String, String> e : vars.entrySet()) txt = txt.replace("%" + e.getKey() + "%", e.getValue());
        Map<String, String> blobs = new HashMap<>();
        List<Profile> out = new ArrayList<>();
        Profile cur = new Profile();
        Matcher m = Pattern.compile("--([\\w-]+)(?:=((?:[^\\s\"]|\"[^\"]*\")+))?").matcher(txt);
        while (m.find()) {
            String k = m.group(1), raw = m.group(2) == null ? "" : m.group(2);
            String v = raw.replace("\"", "");
            if (v.startsWith("@")) v = v.substring(1);
            switch (k) {
                case "new": add(out, cur); cur = new Profile(); break;
                case "filter-tcp": cur.tcp = ports(v); break;
                case "filter-udp": cur.udp = ports(v); break;
                case "filter-l7": if (v.matches(".*(stun|discord|quic|wireguard|dht).*")) cur.l7udp = true; break;
                case "hostlist": cur.hosts = r.hosts(base(v)); break;
                case "ipset": cur.ips = r.ips(base(v)); break;
                case "dpi-desync":
                    for (String t : v.split(",")) { Step s = new Step(); s.type = t.trim(); s.z1 = true; cur.steps.add(s); }
                    break;
                case "dpi-desync-split-pos": cur.splitPos = v; break;
                case "dpi-desync-ttl": cur.ttl = num(v, 0); break;
                case "dpi-desync-repeats": cur.repeats = Math.max(1, num(v, 1)); break;
                case "dpi-desync-cutoff": cur.cutoff = Math.max(1, num(v, 1)); break;
                case "dpi-desync-fake-tls": cur.fakeTls = base(v); break;
                case "dpi-desync-fake-http": cur.fakeHttp = base(v); break;
                case "dpi-desync-fake-quic": cur.fakeQuic = base(v); break;
                case "dpi-desync-fake-unknown-udp": case "dpi-desync-fake-discord": case "dpi-desync-fake-stun":
                    if (cur.fakeUdp == null) cur.fakeUdp = base(v); break;
                case "blob": {
                    int i = v.indexOf(":@");
                    if (i < 0) i = v.indexOf(':');
                    if (i > 0) blobs.put(v.substring(0, i), base(v.substring(i + 1)));
                    break;
                }
                case "lua-desync": {
                    String[] a = v.split(":");
                    Step s = new Step(); s.type = a[0];
                    for (int i = 1; i < a.length; i++) {
                        String[] kv = a[i].split("=", 2);
                        String val = kv.length > 1 ? kv[1] : "";
                        switch (kv[0]) {
                            case "blob": s.file = blobs.containsKey(val) ? blobs.get(val)
                                    : val.contains("quic") ? "quic_initial_www_google_com.bin" : "tls_clienthello_www_google_com.bin"; break;
                            case "pos": s.pos = val; break;
                            case "ttl": s.ttl = num(val, 0); break;
                            case "repeats": s.repeats = Math.max(1, num(val, 1)); break;
                        }
                    }
                    cur.steps.add(s);
                    break;
                }
                default: break;
            }
        }
        add(out, cur);
        return out;
    }
}
