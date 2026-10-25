package com.mk.tvhome;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
        Intent launch;
        ResolveInfo ri;
    }

    private static final long WALL_INTERVAL_MS = 120000L;

    private FrameLayout root;
    private ImageView bgA, bgB;
    private boolean showingA = true;
    private TextView clock, dateView;
    private LinearLayout row;
    private HorizontalScrollView scroll;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<File> walls = new ArrayList<File>();
    private int wallIdx = 0;
    private SharedPreferences prefs;
    private float density;
    private boolean receiverOn = false;
    private static final String NAME = "MK";
    private Typeface fLight, fReg, fMed, fSemi;
    private TextView greeting;
    private GradientDrawable shadeDrawable;
    private final ObjectAnimator[] zooms = new ObjectAnimator[2];
    private boolean animateNext = true;

    private Typeface font(String file, Typeface fallback) {
        try { return Typeface.createFromAsset(getAssets(), "fonts/" + file); }
        catch (Throwable t) { return fallback; }
    }

    private String greetText() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String g = h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : h < 21 ? "Good evening" : "Good night";
        return g + ", " + NAME;
    }

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { buildRow(); }
    };

    private final Runnable clockRun = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            clock.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now));
            dateView.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now));
            handler.postDelayed(this, 20000L);
        }
    };

    private final Runnable wallRun = new Runnable() {
        @Override public void run() {
            if (walls.size() > 1) {
                wallIdx = (wallIdx + 1) % walls.size();
                showWall();
                handler.postDelayed(this, WALL_INTERVAL_MS);
            }
        }
    };

    private int dp(int v) { return (int) (v * density + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
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

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.END);
        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 54);
        clock.setTypeface(fLight);
        clock.setShadowLayer(8f, 0f, 2f, 0x99000000);
        dateView = new TextView(this);
        dateView.setTextColor(0xDDFFFFFF);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        dateView.setTypeface(fReg);
        dateView.setGravity(Gravity.END);
        dateView.setShadowLayer(6f, 0f, 2f, 0x99000000);
        top.addView(clock);
        top.addView(dateView);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        tp.topMargin = dp(36);
        tp.rightMargin = dp(60);
        root.addView(top, tp);

        greeting = new TextView(this);
        greeting.setTextColor(Color.WHITE);
        greeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        greeting.setTypeface(fMed);
        greeting.setShadowLayer(8f, 0f, 2f, 0x99000000);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        gp.topMargin = dp(52);
        gp.leftMargin = dp(60);
        root.addView(greeting, gp);

        View panel = new View(this);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(30));
        pg.setColor(0x38000000);
        pg.setStroke(dp(1), 0x22FFFFFF);
        panel.setBackground(pg);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(250), Gravity.BOTTOM);
        pp.leftMargin = dp(30);
        pp.rightMargin = dp(30);
        pp.bottomMargin = dp(24);
        root.addView(panel, pp);

        scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(dp(60), dp(30), dp(60), dp(30));
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        sp.bottomMargin = dp(30);
        root.addView(scroll, sp);

        setContentView(root);
        hideBars();
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
        buildRow();
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
        scroll.smoothScrollTo(0, 0);
        if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
    }

    @Override
    public void onBackPressed() { /* Home par back kuch nahi karega */ }

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
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) {
                    walls.add(f);
                }
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
        if (walls.size() > 1) handler.postDelayed(wallRun, WALL_INTERVAL_MS);
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
        zooms[zi] = ObjectAnimator.ofPropertyValuesHolder(in,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f));
        zooms[zi].setDuration(WALL_INTERVAL_MS + 2000L);
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

    // ---------- Apps row ----------

    private List<AppItem> loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, AppItem> map = new LinkedHashMap<String, AppItem>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || map.containsKey(pkg)) continue;
                AppItem a = new AppItem();
                a.pkg = pkg;
                a.ri = ri;
                a.label = String.valueOf(ri.loadLabel(pm));
                a.launch = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                map.put(pkg, a);
            }
        }
        final Set<String> pins = new HashSet<String>(prefs.getStringSet("pins", new HashSet<String>()));
        List<AppItem> items = new ArrayList<AppItem>(map.values());
        Collections.sort(items, new Comparator<AppItem>() {
            @Override public int compare(AppItem x, AppItem y) {
                boolean px = pins.contains(x.pkg), py = pins.contains(y.pkg);
                if (px != py) return px ? -1 : 1;
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        return items;
    }

    private void buildRow() {
        String focusPkg = null;
        View fc = row.getFocusedChild();
        if (fc != null && fc.getTag() != null) focusPkg = String.valueOf(fc.getTag());

        row.removeAllViews();
        Set<String> pins = prefs.getStringSet("pins", new HashSet<String>());
        View toFocus = null;
        for (AppItem a : loadApps()) {
            View t = makeTile(a, pins.contains(a.pkg));
            row.addView(t, tileParams());
            if (a.pkg.equals(focusPkg)) toFocus = t;
        }
        row.addView(makeSettingsTile(), tileParams());
        if (animateNext) {
            animateNext = false;
            for (int k = 0; k < row.getChildCount(); k++) {
                View c = row.getChildAt(k);
                c.setAlpha(0f);
                c.setTranslationY(dp(36));
                c.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(k, 8) * 45L)
                        .setDuration(380).setInterpolator(new DecelerateInterpolator());
            }
        }
        if (toFocus == null && row.getChildCount() > 0) toFocus = row.getChildAt(0);
        final View tf = toFocus;
        if (tf != null) {
            tf.post(new Runnable() { @Override public void run() { tf.requestFocus(); } });
        }
    }

    private LinearLayout.LayoutParams tileParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(240),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(24);
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
        g.setStroke(dp(focused ? 3 : 1), focused ? 0xFFFFFFFF : 0x44FFFFFF);
        return g;
    }

    private View buildTile(String tag, FrameLayout card, final TextView label) {
        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setClipChildren(false);
        tile.setTag(tag);
        tile.addView(card, new LinearLayout.LayoutParams(dp(240), dp(135)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTypeface(fMed);
        label.setTextColor(0xAAFFFFFF);
        label.setShadowLayer(6f, 0f, 2f, 0x99000000);
        tile.addView(label, lp);
        final FrameLayout c = card;
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                c.setForeground(ring(has));
                label.setTextColor(has ? Color.WHITE : 0xAAFFFFFF);
                v.animate().scaleX(has ? 1.14f : 1f).scaleY(has ? 1.14f : 1f)
                        .translationZ(has ? dp(12) : 0f).setDuration(150);
            }
        });
        return tile;
    }

    private FrameLayout newCard() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(fill());
        card.setForeground(ring(false));
        card.setClipToOutline(true);
        return card;
    }

    private View makeTile(final AppItem a, final boolean pinned) {
        PackageManager pm = getPackageManager();
        FrameLayout card = newCard();
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
            card.addView(iv, new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER));
        }
        TextView label = new TextView(this);
        label.setText((pinned ? "\u2605 " : "") + a.label);
        View tile = buildTile(a.pkg, card, label);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(a.launch); }
                catch (Exception e) { Toast.makeText(HomeActivity.this, "Open nahi hua", Toast.LENGTH_SHORT).show(); }
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                Set<String> p = new HashSet<String>(prefs.getStringSet("pins", new HashSet<String>()));
                if (p.contains(a.pkg)) p.remove(a.pkg); else p.add(a.pkg);
                prefs.edit().putStringSet("pins", p).apply();
                Toast.makeText(HomeActivity.this, p.contains(a.pkg) ? "Pinned" : "Unpinned", Toast.LENGTH_SHORT).show();
                buildRow();
                return true;
            }
        });
        return tile;
    }

    private View makeSettingsTile() {
        FrameLayout card = newCard();
        TextView t = new TextView(this);
        t.setText("Settings");
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        t.setTypeface(fLight);
        card.addView(t, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        TextView label = new TextView(this);
        label.setText("Settings");
        View tile = buildTile("__settings", card, label);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) { }
            }
        });
        return tile;
    }
}
