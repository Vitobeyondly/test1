package app.zapretvtb;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.*;
import java.net.DatagramSocket;
import java.net.Socket;

public class ZapretVpnService extends VpnService {
    static volatile boolean running;
    private ParcelFileDescriptor tun;
    private Engine engine;
    private Thread loop;

    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (i != null && "stop".equals(i.getAction())) { stop(); stopSelf(); return START_NOT_STICKY; }
        if (!running) start();
        return START_STICKY;
    }

    private void start() {
        SharedPreferences p = getSharedPreferences("s", 0);
        AppRes res = new AppRes(this);
        java.util.List<Strategy.Profile> ps = new java.util.ArrayList<>();
        try { ps = Strategy.parse(res.text(p.getString("strat", "Test_Beta.bat")), res); } catch (Exception ignored) { }
        try {
            tun = new Builder().setSession("Zapret VTB").setMtu(1400)
                    .addAddress("10.0.0.2", 32).addRoute("0.0.0.0", 0)
                    .addAddress("fd00::2", 128).addRoute("::", 0).establish();
        } catch (Exception e) { tun = null; }
        if (tun == null) { stopSelf(); return; }
        final FileInputStream in = new FileInputStream(tun.getFileDescriptor());
        final FileOutputStream out = new FileOutputStream(tun.getFileDescriptor());
        engine = new Engine(out, new Engine.Prot() {
            public void protect(Socket s) { ZapretVpnService.this.protect(s); }
            public void protect(DatagramSocket s) { ZapretVpnService.this.protect(s); }
        }, ps, res, p.getInt("ttl", 4), p.getBoolean("quic", true), getCacheDir());
        running = true;
        loop = new Thread(() -> {
            byte[] buf = new byte[32767];
            try { int n; while (running && (n = in.read(buf)) >= 0) if (n > 0) engine.input(buf, n); }
            catch (IOException ignored) { }
        });
        loop.setDaemon(true);
        loop.start();
    }

    private void stop() {
        running = false;
        if (engine != null) engine.stop();
        try { if (tun != null) tun.close(); } catch (IOException ignored) { }
        tun = null;
    }

    @Override public void onDestroy() { stop(); super.onDestroy(); }
    @Override public void onRevoke() { stop(); super.onRevoke(); }
}
