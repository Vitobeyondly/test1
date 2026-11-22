package app.zapretvtb;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.widget.*;

import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private Button btn;
    private Spinner sp;
    private SharedPreferences p;
    private List<String> names = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("s", 0);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(48, 96, 48, 48);
        TextView t = new TextView(this);
        t.setText("Zapret VTB (без root)\nВыберите стратегию (.bat из вашей сборки). Применяется при запуске.");
        l.addView(t);
        sp = new Spinner(this);
        l.addView(sp);
        Button imp = new Button(this);
        imp.setText("Импорт стратегий / списков / .bin");
        imp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, 2);
        });
        l.addView(imp);
        TextView tt = new TextView(this);
        tt.setText("TTL для fake (fake не должен доходить до сервера, но должен дойти до DPI):");
        l.addView(tt);
        final EditText ttl = new EditText(this);
        ttl.setInputType(InputType.TYPE_CLASS_NUMBER);
        ttl.setText(String.valueOf(p.getInt("ttl", 4)));
        ttl.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(android.text.Editable e) {
                try { p.edit().putInt("ttl", Math.max(1, Math.min(64, Integer.parseInt(e.toString())))).apply(); } catch (Exception ignored) { }
            }
        });
        l.addView(ttl);
        CheckBox q = new CheckBox(this);
        q.setText("Блокировать QUIC, если нет UDP-стратегии");
        q.setChecked(p.getBoolean("quic", true));
        q.setOnCheckedChangeListener((v, on) -> p.edit().putBoolean("quic", on).apply());
        l.addView(q);
        btn = new Button(this);
        btn.setOnClickListener(v -> toggle());
        l.addView(btn);
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
        fill();
    }

    private void fill() {
        TreeSet<String> s = new TreeSet<>();
        try { s.addAll(Arrays.asList(getAssets().list("strategies"))); } catch (IOException ignored) { }
        File[] u = AppRes.userDir(this).listFiles();
        if (u != null) for (File f : u) { String n = f.getName().toLowerCase(); if (n.endsWith(".bat") || n.endsWith(".cmd")) s.add(f.getName()); }
        names = new ArrayList<>(s);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        int i = names.indexOf(p.getString("strat", "Test_Beta.bat"));
        if (i >= 0) sp.setSelection(i);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) { p.edit().putString("strat", names.get(pos)).apply(); }
            public void onNothingSelected(AdapterView<?> a) { }
        });
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() { btn.setText(ZapretVpnService.running ? "Остановить" : "Запустить"); }

    private void toggle() {
        if (ZapretVpnService.running) {
            startService(new Intent(this, ZapretVpnService.class).setAction("stop"));
            btn.postDelayed(this::refresh, 400);
            return;
        }
        Intent i = VpnService.prepare(this);
        if (i != null) startActivityForResult(i, 1); else go();
    }

    @Override protected void onActivityResult(int rq, int rs, Intent d) {
        if (rs != RESULT_OK) return;
        if (rq == 1) go();
        else if (rq == 2 && d != null) {
            List<Uri> us = new ArrayList<>();
            ClipData cd = d.getClipData();
            if (cd != null) for (int i = 0; i < cd.getItemCount(); i++) us.add(cd.getItemAt(i).getUri());
            else if (d.getData() != null) us.add(d.getData());
            for (Uri u : us) copy(u);
            fill();
        }
    }

    private void copy(Uri u) {
        String n = "import.bin";
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) n = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        } catch (Exception ignored) { }
        try (InputStream in = getContentResolver().openInputStream(u); OutputStream o = new FileOutputStream(new File(AppRes.userDir(this), new File(n).getName()))) {
            byte[] b = new byte[8192]; int k;
            while ((k = in.read(b)) > 0) o.write(b, 0, k);
        } catch (Exception e) { Toast.makeText(this, "Ошибка импорта: " + n, Toast.LENGTH_SHORT).show(); }
    }

    private void go() {
        startService(new Intent(this, ZapretVpnService.class));
        btn.postDelayed(this::refresh, 400);
    }
}
