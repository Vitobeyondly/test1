package app.zapretvtb;

import java.nio.charset.StandardCharsets;

final class Tls {
    /** {start,len} of SNI in a TLS ClientHello, or null. */
    static int[] sni(byte[] d) {
        try {
            int p = 43;
            p += 1 + (d[p] & 255);
            p += 2 + Pkt.u16(d, p);
            p += 1 + (d[p] & 255);
            int end = Math.min(p + 2 + Pkt.u16(d, p), d.length);
            p += 2;
            while (p + 4 <= end) {
                int t = Pkt.u16(d, p), l = Pkt.u16(d, p + 2);
                p += 4;
                if (t == 0 && l > 5) {
                    int nl = Pkt.u16(d, p + 3);
                    if (p + 5 + nl <= d.length) return new int[]{p + 5, nl};
                    return null;
                }
                p += l;
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** {start,len} of the Host header value in a plain HTTP request, or null. */
    static int[] httpHost(byte[] d) {
        if (d.length < 16) return null;
        String s = new String(d, 0, Math.min(d.length, 2048), StandardCharsets.ISO_8859_1);
        if (!(s.startsWith("GET ") || s.startsWith("POST ") || s.startsWith("HEAD ") || s.startsWith("PUT ") || s.startsWith("OPTIONS "))) return null;
        int i = s.toLowerCase().indexOf("\nhost:");
        if (i < 0) return null;
        int st = i + 6;
        while (st < s.length() && s.charAt(st) == ' ') st++;
        int e = st;
        while (e < s.length() && s.charAt(e) != '\r' && s.charAt(e) != '\n' && s.charAt(e) != ':') e++;
        return e > st ? new int[]{st, e - st} : null;
    }

    /** Split position in the middle of the second-level domain. */
    static int mid(byte[] d, int[] h) {
        String host = new String(d, h[0], h[1], StandardCharsets.ISO_8859_1);
        int last = host.lastIndexOf('.');
        if (last < 0) return h[0] + h[1] / 2;
        int sld = host.lastIndexOf('.', last - 1) + 1;
        return h[0] + sld + (last - sld) / 2;
    }
}
