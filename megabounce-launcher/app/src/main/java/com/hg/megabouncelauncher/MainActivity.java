package com.hg.megabouncelauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int APPS_PER_PAGE = 6;
    private static final float ICON_PRESS_SCALE = 1.82f;
    private static final float LAUNCH_PEAK_SCALE = 2.95f;
    private static final float RETURN_START_SCALE = 0.16f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private HorizontalScrollView pager;
    private LinearLayout pageStrip;
    private TextView pageIndicator;
    private int pageWidth;
    private int pageCount = 1;
    private int currentPage = 0;
    private float touchDownX;
    private boolean firstResume = true;
    private boolean launching;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(24, 14, 42));
        window.setNavigationBarColor(Color.rgb(16, 7, 30));
        pageWidth = getResources().getDisplayMetrics().widthPixels;
        buildLauncherUi();
        loadInstalledApps();
        playReturnBounce(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (launching) {
            launching = false;
            playReturnBounce(false);
        }
    }

    private void buildLauncherUi() {
        root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setBackground(makeGradient(new int[]{
                Color.rgb(21, 10, 38), Color.rgb(52, 21, 87), Color.rgb(18, 8, 33)
        }, GradientDrawable.Orientation.TL_BR, 0));
        setContentView(root);

        GlowBackground glow = new GlowBackground(this);
        root.addView(glow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setGravity(Gravity.CENTER_HORIZONTAL);
        shell.setPadding(0, dp(20), 0, dp(16));
        shell.setClipChildren(false);
        shell.setClipToPadding(false);
        root.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("MEGA\nBOUNCE");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.12f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setShadowLayer(dp(18), 0, dp(5), 0xCC9B63FF);
        title.setLineSpacing(0, 0.82f);
        title.setOnClickListener(v -> pulseWholeLauncher(1.30f));
        shell.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100)));

        TextView subtitle = new TextView(this);
        subtitle.setText("아이콘도 크고, 움직임은 더 크게");
        subtitle.setTextColor(0xCCDFD2FF);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        shell.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        pager = new HorizontalScrollView(this);
        pager.setHorizontalScrollBarEnabled(false);
        pager.setFillViewport(true);
        pager.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        pager.setClipChildren(false);
        pager.setClipToPadding(false);
        pager.setOnTouchListener(new SwipeBounceListener());
        shell.addView(pager, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        pageStrip = new LinearLayout(this);
        pageStrip.setOrientation(LinearLayout.HORIZONTAL);
        pageStrip.setClipChildren(false);
        pageStrip.setClipToPadding(false);
        pager.addView(pageStrip, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pageIndicator = new TextView(this);
        pageIndicator.setTextColor(0xE6FFFFFF);
        pageIndicator.setTextSize(14);
        pageIndicator.setGravity(Gravity.CENTER);
        pageIndicator.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        pageIndicator.setBackground(makeGradient(
                new int[]{0x553D225F, 0x553D225F},
                GradientDrawable.Orientation.LEFT_RIGHT, dp(99)));
        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(dp(245), dp(42));
        indicatorParams.topMargin = dp(6);
        shell.addView(pageIndicator, indicatorParams);
        root.setOnClickListener(v -> pulseWholeLauncher(1.16f));
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> raw = pm.queryIntentActivities(query, 0);
        List<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo info : raw) {
            if (info.activityInfo == null) continue;
            if (getPackageName().equals(info.activityInfo.packageName)) continue;
            apps.add(info);
        }

        final Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo left, ResolveInfo right) {
                return collator.compare(String.valueOf(left.loadLabel(pm)),
                        String.valueOf(right.loadLabel(pm)));
            }
        });

        if (apps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("실행 가능한 앱을 찾지 못했어");
            empty.setTextColor(Color.WHITE);
            empty.setTextSize(20);
            empty.setGravity(Gravity.CENTER);
            pageStrip.addView(empty, new LinearLayout.LayoutParams(
                    pageWidth, ViewGroup.LayoutParams.MATCH_PARENT));
            pageCount = 1;
            updateIndicator();
            return;
        }

        pageCount = Math.max(1, (apps.size() + APPS_PER_PAGE - 1) / APPS_PER_PAGE);
        for (int page = 0; page < pageCount; page++) {
            GridLayout grid = makePageGrid();
            int start = page * APPS_PER_PAGE;
            int end = Math.min(apps.size(), start + APPS_PER_PAGE);
            for (int index = start; index < end; index++) {
                grid.addView(makeAppTile(apps.get(index), pm), makeCellParams(index - start));
            }
            pageStrip.addView(grid, new LinearLayout.LayoutParams(
                    pageWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        updateIndicator();
    }

    private GridLayout makePageGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setRowCount(3);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        grid.setPadding(dp(12), dp(7), dp(12), dp(7));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        return grid;
    }

    private GridLayout.LayoutParams makeCellParams(int slot) {
        GridLayout.Spec row = GridLayout.spec(slot / 2, 1, 1f);
        GridLayout.Spec col = GridLayout.spec(slot % 2, 1, 1f);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(row, col);
        params.width = 0;
        params.height = 0;
        params.setMargins(dp(8), dp(7), dp(8), dp(7));
        return params;
    }

    private View makeAppTile(final ResolveInfo info, final PackageManager pm) {
        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(8), dp(8), dp(7));
        tile.setClipChildren(false);
        tile.setClipToPadding(false);
        tile.setBackground(makeTileBackground());
        tile.setElevation(dp(7));
        tile.setClickable(true);
        tile.setFocusable(true);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(info.loadIcon(pm));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setAdjustViewBounds(true);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(130), dp(130)));

        TextView label = new TextView(this);
        label.setText(info.loadLabel(pm));
        label.setTextColor(Color.WHITE);
        label.setTextSize(18);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setShadowLayer(dp(4), 0, dp(2), 0xAA000000);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        labelParams.topMargin = dp(3);
        tile.addView(label, labelParams);

        tile.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                view.animate().cancel();
                view.animate().scaleX(ICON_PRESS_SCALE).scaleY(ICON_PRESS_SCALE)
                        .rotation(9f).translationY(-dp(13)).setDuration(190)
                        .setInterpolator(new OvershootInterpolator(5.5f)).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                view.animate().cancel();
                view.animate().scaleX(1f).scaleY(1f).rotation(0f).translationY(0f)
                        .setDuration(560).setInterpolator(new OvershootInterpolator(7.5f)).start();
            }
            return false;
        });
        tile.setOnClickListener(v -> launchWithHugeBounce(tile, info));
        return tile;
    }

    private void launchWithHugeBounce(final View tile, final ResolveInfo info) {
        if (launching || info.activityInfo == null) return;
        launching = true;
        tile.animate().cancel();
        root.animate().cancel();
        pageStrip.animate().cancel();

        AnimatorSet tileSet = new AnimatorSet();
        ObjectAnimator sx = ObjectAnimator.ofFloat(tile, View.SCALE_X, 1f, LAUNCH_PEAK_SCALE, 0.18f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(tile, View.SCALE_Y, 1f, LAUNCH_PEAK_SCALE, 0.18f);
        ObjectAnimator rot = ObjectAnimator.ofFloat(tile, View.ROTATION, 0f, 22f, -16f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(tile, View.ALPHA, 1f, 1f, 0.06f);
        ObjectAnimator up = ObjectAnimator.ofFloat(tile, View.TRANSLATION_Y, 0f, -dp(62), dp(30));
        tileSet.playTogether(sx, sy, rot, alpha, up);
        tileSet.setDuration(560);
        tileSet.setInterpolator(new OvershootInterpolator(3.2f));
        tileSet.start();

        root.animate().scaleX(1.34f).scaleY(1.34f).rotation(-4f).alpha(0.55f)
                .setDuration(430).setInterpolator(new DecelerateInterpolator()).start();

        handler.postDelayed(() -> {
            try {
                Intent launch = new Intent(Intent.ACTION_MAIN);
                launch.addCategory(Intent.CATEGORY_LAUNCHER);
                launch.setComponent(new ComponentName(
                        info.activityInfo.packageName, info.activityInfo.name));
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(launch);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            } catch (Exception error) {
                launching = false;
                Toast.makeText(MainActivity.this,
                        "앱을 실행하지 못했어: " + error.getClass().getSimpleName(),
                        Toast.LENGTH_SHORT).show();
                restoreAfterFailedLaunch(tile);
            }
        }, 370);
    }

    private void restoreAfterFailedLaunch(View tile) {
        tile.setAlpha(1f);
        tile.setScaleX(1f);
        tile.setScaleY(1f);
        tile.setRotation(0f);
        tile.setTranslationY(0f);
        root.setAlpha(1f);
        root.setScaleX(1f);
        root.setScaleY(1f);
        root.setRotation(0f);
        pulseWholeLauncher(1.22f);
    }

    private void playReturnBounce(boolean initial) {
        root.animate().cancel();
        pageStrip.animate().cancel();
        root.setAlpha(initial ? 0f : 0.14f);
        root.setScaleX(RETURN_START_SCALE);
        root.setScaleY(RETURN_START_SCALE);
        root.setRotation(initial ? 13f : -16f);
        root.setTranslationY(initial ? dp(90) : dp(135));
        pageStrip.setScaleX(1.85f);
        pageStrip.setScaleY(1.85f);
        pageStrip.setRotation(initial ? -8f : 11f);

        root.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f).translationY(0f)
                .setDuration(initial ? 1050 : 1250)
                .setInterpolator(new OvershootInterpolator(initial ? 5.2f : 7.2f)).start();
        pageStrip.animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(90)
                .setDuration(initial ? 900 : 1120)
                .setInterpolator(new OvershootInterpolator(8.2f)).start();
    }

    private void pulseWholeLauncher(float peak) {
        if (launching) return;
        AnimatorSet pulse = new AnimatorSet();
        ObjectAnimator sx = ObjectAnimator.ofFloat(root, View.SCALE_X, 1f, peak, 0.88f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(root, View.SCALE_Y, 1f, peak, 0.88f, 1f);
        ObjectAnimator rot = ObjectAnimator.ofFloat(root, View.ROTATION, 0f, 3.5f, -2f, 0f);
        pulse.playTogether(sx, sy, rot);
        pulse.setDuration(760);
        pulse.setInterpolator(new OvershootInterpolator(4.8f));
        pulse.start();
    }

    private void snapToPage(int target) {
        currentPage = Math.max(0, Math.min(pageCount - 1, target));
        int targetX = currentPage * pageWidth;
        ObjectAnimator scroll = ObjectAnimator.ofInt(pager, "scrollX", pager.getScrollX(), targetX);
        scroll.setDuration(780);
        scroll.setInterpolator(new OvershootInterpolator(4.6f));
        scroll.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                pager.scrollTo(currentPage * pageWidth, 0);
                updateIndicator();
            }
        });
        scroll.start();

        pageStrip.animate().cancel();
        pageStrip.setPivotX(targetX + pageWidth / 2f);
        pageStrip.setPivotY(pageStrip.getHeight() / 2f);
        pageStrip.animate().scaleX(1f).scaleY(1f).rotation(0f).translationY(0f)
                .setDuration(850).setInterpolator(new OvershootInterpolator(7.4f)).start();
    }

    private void updateIndicator() {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            if (i > 0) dots.append("  ");
            dots.append(i == currentPage ? "●" : "○");
        }
        pageIndicator.setText(dots + "   밀어서 튕기기");
    }

    private GradientDrawable makeTileBackground() {
        GradientDrawable bg = makeGradient(new int[]{0xB0502A78, 0x9B271541},
                GradientDrawable.Orientation.TL_BR, dp(27));
        bg.setStroke(dp(1), 0x66FFFFFF);
        return bg;
    }

    private GradientDrawable makeGradient(int[] colors,
                                           GradientDrawable.Orientation orientation,
                                           int radius) {
        GradientDrawable drawable = new GradientDrawable(orientation, colors);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class SwipeBounceListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownX = event.getX();
                    pageStrip.animate().cancel();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float delta = touchDownX - event.getX();
                    float amount = Math.min(1f,
                            Math.abs(delta) / Math.max(1f, pageWidth * 0.68f));
                    pageStrip.setPivotX(pager.getScrollX() + pageWidth / 2f);
                    pageStrip.setPivotY(pageStrip.getHeight() / 2f);
                    pageStrip.setScaleX(1f + 0.18f * amount);
                    pageStrip.setScaleY(1f + 0.42f * amount);
                    pageStrip.setRotation((delta > 0 ? -1f : 1f) * 8.5f * amount);
                    pageStrip.setTranslationY(-dp(18) * amount);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float distance = touchDownX - event.getX();
                    int target = Math.round((float) pager.getScrollX() /
                            Math.max(1, pageWidth));
                    if (Math.abs(distance) > pageWidth * 0.16f) {
                        target = currentPage + (distance > 0 ? 1 : -1);
                    }
                    final int snapTarget = target;
                    handler.postDelayed(() -> snapToPage(snapTarget), 24);
                    break;
                default:
                    break;
            }
            return false;
        }
    }

    private static final class GlowBackground extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        GlowBackground(Activity context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            paint.setShader(new RadialGradient(w * 0.16f, h * 0.30f, w * 0.72f,
                    new int[]{0x889C5EFF, 0x22653CA5, 0x00653CA5},
                    new float[]{0f, 0.46f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(w * 0.16f, h * 0.30f, w * 0.72f, paint);
            paint.setShader(new RadialGradient(w * 0.92f, h * 0.82f, w * 0.70f,
                    new int[]{0x66FF4CA8, 0x225B2C75, 0x005B2C75},
                    new float[]{0f, 0.50f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(w * 0.92f, h * 0.82f, w * 0.70f, paint);
            paint.setShader(null);
        }
    }
}
