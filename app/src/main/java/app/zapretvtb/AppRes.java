package app.zapretvtb;

import android.content.Context;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

final class AppRes implements Strategy.Res {
    final Context c;
    final Map<String, HostList> hc = new HashMap<>();
    final Map<String, IpSet> ic = new HashMap<>();
    final Map<String, byte[]> bc = new HashMap<>();

    AppRes(Context c) { this.c = c; }

    static File userDir(Context c) { File d = new File(c.getFilesDir(), "user"); d.mkdirs(); return d; }

    InputStream open(String n) throws IOException {
        File f = new File(userDir(c), n);
        if (f.exists()) return new FileInputStream(f);
        for (String d : new String[]{"strategies/", "lists/", "files/"}) {
            try { return c.getAssets().open(d + n); } catch (IOException ignored) { }
        }
        throw new FileNotFoundException(n);
    }

    public byte[] bytes(String n) {
        byte[] r = bc.get(n);
        if (r != null) return r;
        try (InputStream in = open(n)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int k;
            while ((k = in.read(b)) > 0) bo.write(b, 0, k);
            r = bo.toByteArray(); bc.put(n, r); return r;
        } catch (IOException e) { return null; }
    }

    String text(String n) throws IOException { return new String(bytes(n), "UTF-8"); }

    public HostList hosts(String n) {
        HostList h = hc.get(n);
        if (h == null) { h = new HostList(); try (InputStream in = open(n)) { h.load(in); } catch (IOException ignored) { } hc.put(n, h); }
        return h;
    }

    public IpSet ips(String n) {
        IpSet s = ic.get(n);
        if (s == null) { s = new IpSet(); try (InputStream in = open(n)) { s.load(in); } catch (IOException ignored) { } ic.put(n, s); }
        return s;
    }
}
