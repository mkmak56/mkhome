package com.mk.tvhome;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends Activity {

    private static class AppItem {
        String pkg;
        String label;
        String cat;
        Intent launch;
        ResolveInfo ri;
    }

    private interface IntCb { void on(int i); }

    private static final String NAME = "MK";
    private static final String[] ROWS = {"Favorites", "Streaming", "Apps", "Games", "Tools"};
    private static final String[] MOVE_TO = {"Streaming", "Apps", "Games", "Tools"};
    private static final int[] ACCENTS = {0xFFFFFFFF, 0xFF5AC8FA, 0xFF34D399, 0xFFFBBF24, 0xFFFB7185, 0xFFA78BFA};
    private static final String[] ACCENT_NAMES = {"White", "Sky", "Mint", "Amber", "Rose", "Violet"};
    private static final String[] SIZE_NAMES = {"Small", "Medium", "Large"};
    private static final int[] SIZE_W = {170, 210, 260};
    private static final int[] SIZE_H = {96, 118, 146};
    private static final int[] WALL_MIN = {0, 1, 2, 5, 10};
    private static final String[] WALL_NAMES = {"Off", "1 min", "2 min", "5 min", "10 min"};
    private static final String[] STREAM_KEYS = {"netflix", "hotstar", "amazonvideo", "sonyliv", "youtube",
            "airtel", "jio", "discovery", "crunchyroll", "shemaroo", "hungama", "eros", "docubay", "zee5",
            "voot", "mxplayer", "plex", "anilab", "bookmyshow", "mobiotics", "netmirror", "hdhub", "tachiyomi",
            "playfy", "bluesun", "aha"};
    private static final String[] TOOL_KEYS = {"settings", "nextapp", "aptoide", "mouse", "sendfiles",
            "mediaexplorer", "gallery", "tvmanager", "handbook", "filemanager", "packageinstaller"};

    private FrameLayout root;
    private ImageView bgA, bgB;
    private boolean showingA = true;
    private TextView clock, dateView, greeting;
    private LinearLayout rowsHost, pills;
    private ScrollView vscroll;
    private View firstTile;
    private GradientDrawable shadeDrawable;
    private final ObjectAnimator[] zooms = new ObjectAnimator[2];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<File> walls = new ArrayList<File>();
    private int wallIdx = 0;
    private SharedPreferences prefs;
    private float density;
    private int screenH;
    private boolean receiverOn = false;
    private boolean animateNext = true;
    private Typeface fLight, fReg, fMed, fSemi;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { buildAll(); }
    };

    private final Runnable clockRun = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            boolean h24 = prefs.getBoolean("clock24", false);
            clock.setText(new SimpleDateFormat(h24 ? "HH:mm" : "hh:mm a", Locale.getDefault()).format(now));
            dateView.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now));
            handler.postDelayed(this, 20000L);
        }
    };

    private final Runnable wallRun = new Runnable() {
        @Override public void run() {
            if (walls.size() > 1 && wallMs() > 0) {
                wallIdx = (wallIdx + 1) % walls.size();
                showWall();
                handler.postDelayed(this, wallMs());
            }
        }
    };

    private int dp(int v) { return (int) (v * density + 0.5f); }
    private int accent() { return ACCENTS[prefs.getInt("accent", 0)]; }
    private long wallMs() { return WALL_MIN[prefs.getInt("wallmin", 2)] * 60000L; }

    private Set<String> getSet(String k) {
        return new HashSet<String>(prefs.getStringSet(k, new HashSet<String>()));
    }

    private Typeface font(String file, Typeface fallback) {
        try { return Typeface.createFromAsset(getAssets(), "fonts/" + file); }
        catch (Throwable t) { return fallback; }
    }

    private String greetText() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : h < 21 ? "Good evening" : "Good night";
        return g + ", " + NAME;
    }

    // ---------- Lifecycle ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenH = dm.heightPixels;
        prefs = getSharedPreferences("mk", MODE_PRIVATE);
        fLight = font("Poppins-Light.ttf", Typeface.create("sans-serif-light", Typeface.NORMAL));
        fReg = font("Poppins-Regular.ttf", Typeface.DEFAULT);
        fMed = font("Poppins-Medium.ttf", Typeface.DEFAULT_BOLD);
        fSemi = font("Poppins-SemiBold.ttf", Typeface.DEFAULT_BOLD);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0F1A);

        bgA = new ImageView(this);
        bgB = new ImageView(this);
        bgA.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgB.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgB.setAlpha(0f);
        root.addView(bgA, new FrameLayout.LayoutParams(-1, -1));
        root.addView(bgB, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shadeDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x00000000, 0xE6000000});
        shade.setBackground(shadeDrawable);
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        int hostTop = (int) (screenH * 0.36f);

        View panel = new View(this);
        GradientDrawable pg = new GradientDrawable();
        float r = dp(32);
        pg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        pg.setColor(0x990A0E1A);
        pg.setStroke(dp(1), 0x22FFFFFF);
        panel.setBackground(pg);
        FrameLayout.LayoutParams pnl = new FrameLayout.LayoutParams(-1, -1);
        pnl.topMargin = hostTop - dp(18);
        pnl.leftMargin = dp(16);
        pnl.rightMargin = dp(16);
        root.addView(panel, pnl);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        greeting = new TextView(this);
        greeting.setTextColor(0xCCFFFFFF);
        greeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        greeting.setTypeface(fMed);
        greeting.setShadowLayer(8f, 0f, 2f, 0x99000000);
        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        clock.setTypeface(fLight);
        clock.setShadowLayer(10f, 0f, 3f, 0x99000000);
        dateView = new TextView(this);
        dateView.setTextColor(0xDDFFFFFF);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        dateView.setTypeface(fReg);
        dateView.setShadowLayer(6f, 0f, 2f, 0x99000000);
        hero.addView(greeting);
        hero.addView(clock);
        hero.addView(dateView);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        hp.leftMargin = dp(56);
        hp.topMargin = dp(28);
        root.addView(hero, hp);

        pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        addPill("Customise", new Runnable() { @Override public void run() { showCustomise(); } });
        addPill("Wi-Fi", intentRunner(Settings.ACTION_WIFI_SETTINGS));
        addPill("Bluetooth", intentRunner(Settings.ACTION_BLUETOOTH_SETTINGS));
        addPill("Settings", intentRunner(Settings.ACTION_SETTINGS));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        pl.topMargin = dp(34);
        pl.rightMargin = dp(48);
        root.addView(pills, pl);

        vscroll = new ScrollView(this);
        vscroll.setVerticalScrollBarEnabled(false);
        vscroll.setClipChildren(false);
        vscroll.setClipToPadding(false);
        vscroll.setPadding(0, 0, 0, dp(40));
        rowsHost = new LinearLayout(this);
        rowsHost.setOrientation(LinearLayout.VERTICAL);
        rowsHost.setClipChildren(false);
        vscroll.addView(rowsHost, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(-1, -1);
        vp.topMargin = hostTop;
        root.addView(vscroll, vp);

        setContentView(root);
        hideBars();
    }

    private Runnable intentRunner(final String action) {
        return new Runnable() {
            @Override public void run() {
                try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Exception e) { Toast.makeText(HomeActivity.this, "Open nahi hua", Toast.LENGTH_SHORT).show(); }
            }
        };
    }

    private GradientDrawable pillBg(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(24));
        g.setColor(focused ? 0x55FFFFFF : 0x33000000);
        g.setStroke(dp(focused ? 2 : 1), focused ? accent() : 0x44FFFFFF);
        return g;
    }

    private void addPill(String text, final Runnable action) {
        final TextView p = new TextView(this);
        p.setText(text);
        p.setTextColor(Color.WHITE);
        p.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        p.setTypeface(fMed);
        p.setPadding(dp(20), dp(10), dp(20), dp(10));
        p.setBackground(pillBg(false));
        p.setFocusable(true);
        p.setClickable(true);
        p.setTag("__pill");
        p.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        p.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                v.setBackground(pillBg(has));
                v.animate().scaleX(has ? 1.07f : 1f).scaleY(has ? 1.07f : 1f).setDuration(120);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(12);
        pills.addView(p, lp);
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        if (!receiverOn) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REMOVED);
            f.addAction(Intent.ACTION_PACKAGE_CHANGED);
            f.addDataScheme("package");
            registerReceiver(pkgReceiver, f);
            receiverOn = true;
        }
        greeting.setText(greetText());
        animateNext = true;
        buildAll();
        loadWalls();
        handler.removeCallbacks(clockRun);
        handler.post(clockRun);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockRun);
        handler.removeCallbacks(wallRun);
        if (receiverOn) {
            try { unregisterReceiver(pkgReceiver); } catch (Exception e) { }
            receiverOn = false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        vscroll.smoothScrollTo(0, 0);
        if (firstTile != null) firstTile.requestFocus();
    }

    @Override
    public void onBackPressed() { }

    // ---------- Wallpapers ----------

    private void loadWalls() {
        walls.clear();
        File dir = new File(getExternalFilesDir(null), "wallpapers");
        dir.mkdirs();
        File[] fs = dir.listFiles();
        if (fs != null) {
            Arrays.sort(fs);
            for (File f : fs) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) walls.add(f);
            }
        }
        handler.removeCallbacks(wallRun);
        if (walls.isEmpty()) {
            bgA.setImageDrawable(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF1B2540, 0xFF0B0F1A}));
            bgA.setAlpha(1f);
            bgB.setAlpha(0f);
            return;
        }
        if (wallIdx >= walls.size()) wallIdx = 0;
        showWall();
        if (walls.size() > 1 && wallMs() > 0) handler.postDelayed(wallRun, wallMs());
    }

    private void showWall() {
        Bitmap bmp = decode(walls.get(wallIdx));
        if (bmp == null) return;
        final ImageView in = showingA ? bgB : bgA;
        final ImageView out = showingA ? bgA : bgB;
        in.setImageBitmap(bmp);
        in.setScaleX(1f);
        in.setScaleY(1f);
        int zi = (in == bgA) ? 0 : 1;
        if (zooms[zi] != null) zooms[zi].cancel();
        long zd = wallMs() > 0 ? wallMs() + 2000L : 120000L;
        zooms[zi] = ObjectAnimator.ofPropertyValuesHolder(in,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f));
        zooms[zi].setDuration(zd);
        zooms[zi].setInterpolator(new LinearInterpolator());
        zooms[zi].start();
        in.animate().alpha(1f).setDuration(1200);
        out.animate().alpha(0f).setDuration(1200);
        showingA = !showingA;
        try {
            Bitmap one = Bitmap.createScaledBitmap(bmp, 1, 1, true);
            int avg = one.getPixel(0, 0);
            int r = Color.red(avg) * 30 / 100, g2 = Color.green(avg) * 30 / 100, b2 = Color.blue(avg) * 30 / 100;
            shadeDrawable.setColors(new int[]{0x66000000, 0x00000000, Color.argb(0xE6, r, g2, b2)});
        } catch (Throwable t) { }
    }

    private Bitmap decode(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= 1280) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- Apps ----------

    private String autoCategory(String pkg, ResolveInfo ri) {
        String p = pkg.toLowerCase(Locale.ROOT);
        try {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            boolean game = (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
            if (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME) game = true;
            if (game) return "Games";
        } catch (Throwable t) { }
        for (String k : STREAM_KEYS) if (p.contains(k)) return "Streaming";
        for (String k : TOOL_KEYS) if (p.contains(k)) return "Tools";
        return "Apps";
    }

    private List<AppItem> loadApps() {
        PackageManager pm = getPackageManager();
        Set<String> hidden = getSet("hidden");
        Map<String, AppItem> map = new LinkedHashMap<String, AppItem>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || map.containsKey(pkg) || hidden.contains(pkg)) continue;
                AppItem a = new AppItem();
                a.pkg = pkg;
                a.ri = ri;
                a.label = String.valueOf(ri.loadLabel(pm));
                String over = prefs.getString("cat_" + pkg, null);
                a.cat = over != null ? over : autoCategory(pkg, ri);
                a.launch = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                map.put(pkg, a);
            }
        }
        List<AppItem> items = new ArrayList<AppItem>(map.values());
        Collections.sort(items, new Comparator<AppItem>() {
            @Override public int compare(AppItem x, AppItem y) { return x.label.compareToIgnoreCase(y.label); }
        });
        return items;
    }

    private void buildAll() {
        View cf = getCurrentFocus();
        String fp = (cf != null && cf.getTag() instanceof String) ? (String) cf.getTag() : null;
        rowsHost.removeAllViews();
        firstTile = null;
        Set<String> pins = getSet("pins");
        Map<String, List<AppItem>> g = new LinkedHashMap<String, List<AppItem>>();
        for (String r : ROWS) g.put(r, new ArrayList<AppItem>());
        for (AppItem a : loadApps()) {
            String key = pins.contains(a.pkg) ? "Favorites" : a.cat;
            List<AppItem> l = g.get(key);
            if (l == null) l = g.get("Apps");
            l.add(a);
        }
        View toFocus = null;
        int si = 0;
        for (String r : ROWS) {
            List<AppItem> l = g.get(r);
            if (l.isEmpty()) continue;
            LinearLayout sec = new LinearLayout(this);
            sec.setOrientation(LinearLayout.VERTICAL);
            sec.setClipChildren(false);
            TextView title = new TextView(this);
            title.setText(r);
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            title.setTypeface(fSemi);
            title.setPadding(dp(56), dp(10), 0, 0);
            sec.addView(title);
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipChildren(false);
            hs.setClipToPadding(false);
            LinearLayout tr = new LinearLayout(this);
            tr.setOrientation(LinearLayout.HORIZONTAL);
            tr.setClipChildren(false);
            tr.setPadding(dp(56), dp(16), dp(56), dp(16));
            for (AppItem a : l) {
                View t = makeTile(a);
                tr.addView(t, tileParams());
                if (a.pkg.equals(fp)) toFocus = t;
                if (firstTile == null) firstTile = t;
            }
            hs.addView(tr, new HorizontalScrollView.LayoutParams(
                    HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.WRAP_CONTENT));
            sec.addView(hs, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            rowsHost.addView(sec, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            if (animateNext) {
                sec.setAlpha(0f);
                sec.setTranslationY(dp(40));
                sec.animate().alpha(1f).translationY(0f).setStartDelay(si * 90L)
                        .setDuration(420).setInterpolator(new DecelerateInterpolator());
            }
            si++;
        }
        animateNext = false;
        if (toFocus == null) toFocus = firstTile;
        final View tf = toFocus;
        if (tf != null) tf.post(new Runnable() { @Override public void run() { tf.requestFocus(); } });
    }

    private LinearLayout.LayoutParams tileParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                dp(SIZE_W[prefs.getInt("size", 1)]), LinearLayout.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(22);
        return p;
    }

    private GradientDrawable fill() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(0x33FFFFFF);
        return g;
    }

    private GradientDrawable ring(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(focused ? 3 : 1), focused ? accent() : 0x44FFFFFF);
        return g;
    }

    private View makeTile(final AppItem a) {
        int sz = prefs.getInt("size", 1);
        PackageManager pm = getPackageManager();
        final FrameLayout card = new FrameLayout(this);
        card.setBackground(fill());
        card.setForeground(ring(false));
        card.setClipToOutline(true);
        Drawable banner = null;
        try {
            banner = a.ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = a.ri.activityInfo.applicationInfo.loadBanner(pm);
        } catch (Throwable t) { banner = null; }
        ImageView iv = new ImageView(this);
        if (banner != null) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(banner);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageDrawable(a.ri.loadIcon(pm));
            int is = dp(SIZE_H[sz] * 55 / 100);
            card.addView(iv, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        }

        final TextView label = new TextView(this);
        label.setText(a.label);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTypeface(fMed);
        label.setTextColor(0xAAFFFFFF);
        label.setShadowLayer(6f, 0f, 2f, 0x99000000);

        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setClipChildren(false);
        tile.setTag(a.pkg);
        tile.addView(card, new LinearLayout.LayoutParams(dp(SIZE_W[sz]), dp(SIZE_H[sz])));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        tile.addView(label, lp);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                card.setForeground(ring(has));
                label.setTextColor(has ? Color.WHITE : 0xAAFFFFFF);
                v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f)
                        .translationZ(has ? dp(12) : 0f).setDuration(150);
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(a.launch); }
                catch (Exception e) { Toast.makeText(HomeActivity.this, "Open nahi hua", Toast.LENGTH_SHORT).show(); }
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showAppMenu(a); return true; }
        });
        return tile;
    }

    // ---------- Dialogs ----------

    private AlertDialog.Builder dlg(String title) {
        return new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle(title);
    }

    private void menu(String title, String[] opts, final IntCb cb) {
        dlg(title).setItems(opts, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int w) { cb.on(w); }
        }).show();
    }

    private void choose(String title, String[] opts, int cur, final IntCb cb) {
        dlg(title).setSingleChoiceItems(opts, cur, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int w) { d.dismiss(); cb.on(w); }
        }).show();
    }

    private void showAppMenu(final AppItem a) {
        final boolean pinned = getSet("pins").contains(a.pkg);
        String[] opts = {pinned ? "Remove from Favorites" : "Add to Favorites",
                "Move to Streaming", "Move to Apps", "Move to Games", "Move to Tools",
                "Hide from home", "App info"};
        menu(a.label, opts, new IntCb() {
            @Override public void on(int i) {
                if (i == 0) {
                    Set<String> p = getSet("pins");
                    if (pinned) p.remove(a.pkg); else p.add(a.pkg);
                    prefs.edit().putStringSet("pins", p).apply();
                } else if (i >= 1 && i <= 4) {
                    prefs.edit().putString("cat_" + a.pkg, MOVE_TO[i - 1]).apply();
                    Set<String> p = getSet("pins");
                    if (p.remove(a.pkg)) prefs.edit().putStringSet("pins", p).apply();
                } else if (i == 5) {
                    Set<String> h = getSet("hidden");
                    h.add(a.pkg);
                    prefs.edit().putStringSet("hidden", h).apply();
                    Toast.makeText(HomeActivity.this, "Hidden. Customise se wapas laa sakte hain", Toast.LENGTH_LONG).show();
                } else if (i == 6) {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception e) { }
                    return;
                }
                buildAll();
            }
        });
    }

    private void showCustomise() {
        int hiddenN = getSet("hidden").size();
        String[] opts = {
                "Accent colour: " + ACCENT_NAMES[prefs.getInt("accent", 0)],
                "Tile size: " + SIZE_NAMES[prefs.getInt("size", 1)],
                "Clock: " + (prefs.getBoolean("clock24", false) ? "24-hour" : "12-hour"),
                "Wallpaper change: " + WALL_NAMES[prefs.getInt("wallmin", 2)],
                "Unhide all apps (" + hiddenN + " hidden)",
                "Reset layout and favorites"};
        menu("Customise", opts, new IntCb() {
            @Override public void on(int i) {
                if (i == 0) {
                    choose("Accent colour", ACCENT_NAMES, prefs.getInt("accent", 0), new IntCb() {
                        @Override public void on(int k) { prefs.edit().putInt("accent", k).apply(); applyAll(); }
                    });
                } else if (i == 1) {
                    choose("Tile size", SIZE_NAMES, prefs.getInt("size", 1), new IntCb() {
                        @Override public void on(int k) { prefs.edit().putInt("size", k).apply(); applyAll(); }
                    });
                } else if (i == 2) {
                    choose("Clock", new String[]{"12-hour", "24-hour"}, prefs.getBoolean("clock24", false) ? 1 : 0, new IntCb() {
                        @Override public void on(int k) { prefs.edit().putBoolean("clock24", k == 1).apply(); applyAll(); }
                    });
                } else if (i == 3) {
                    choose("Wallpaper change", WALL_NAMES, prefs.getInt("wallmin", 2), new IntCb() {
                        @Override public void on(int k) { prefs.edit().putInt("wallmin", k).apply(); applyAll(); }
                    });
                } else if (i == 4) {
                    prefs.edit().putStringSet("hidden", new HashSet<String>()).apply();
                    applyAll();
                } else if (i == 5) {
                    prefs.edit().clear().apply();
                    applyAll();
                }
            }
        });
    }

    private void applyAll() {
        for (int k = 0; k < pills.getChildCount(); k++) pills.getChildAt(k).setBackground(pillBg(false));
        handler.removeCallbacks(clockRun);
        handler.post(clockRun);
        loadWalls();
        buildAll();
    }
}
