package app.zapretvtb;

import java.io.*;
import java.util.*;

final class IpSet {
    long[] a = new long[0], b = new long[0];

    void load(InputStream in) throws IOException {
        List<long[]> l = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"), 65536)) {
            String s;
            while ((s = r.readLine()) != null) {
                s = s.replace("\uFEFF", "").trim();
                if (s.isEmpty() || s.startsWith("#") || s.indexOf(':') >= 0) continue;
                try {
                    int sl = s.indexOf('/'), bits = 32;
                    if (sl >= 0) { bits = Integer.parseInt(s.substring(sl + 1)); s = s.substring(0, sl); }
                    String[] q = s.split("\\.");
                    long ip = 0;
                    for (int i = 0; i < 4; i++) ip = (ip << 8) | Integer.parseInt(q[i]);
                    long mask = bits == 0 ? 0 : (0xffffffffL << (32 - bits)) & 0xffffffffL;
                    long st = ip & mask;
                    l.add(new long[]{st, st | (~mask & 0xffffffffL)});
                } catch (Exception ignored) { }
            }
        }
        l.sort((x, y) -> Long.compare(x[0], y[0]));
        List<long[]> m = new ArrayList<>();
        for (long[] x : l) {
            if (!m.isEmpty() && x[0] <= m.get(m.size() - 1)[1] + 1) { long[] t = m.get(m.size() - 1); if (x[1] > t[1]) t[1] = x[1]; }
            else m.add(new long[]{x[0], x[1]});
        }
        a = new long[m.size()]; b = new long[m.size()];
        for (int i = 0; i < a.length; i++) { a[i] = m.get(i)[0]; b[i] = m.get(i)[1]; }
    }

    boolean contains(long ip) {
        int i = Arrays.binarySearch(a, ip);
        if (i < 0) i = -i - 2;
        return i >= 0 && ip <= b[i];
    }
}
