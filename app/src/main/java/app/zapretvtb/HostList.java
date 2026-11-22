package app.zapretvtb;

import java.io.*;
import java.util.HashSet;
import java.util.Locale;

final class HostList {
    final HashSet<String> set = new HashSet<>();

    void load(InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"), 65536)) {
            String l;
            while ((l = r.readLine()) != null) {
                l = l.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
                if (l.isEmpty() || l.startsWith("#")) continue;
                while (l.endsWith("*")) l = l.substring(0, l.length() - 1);
                if (!l.isEmpty()) set.add(l);
            }
        }
    }

    boolean match(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        while (true) {
            if (set.contains(h)) return true;
            int i = h.indexOf('.');
            if (i < 0) return false;
            h = h.substring(i + 1);
        }
    }
}
