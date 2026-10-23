package org.peyilo.iread.host;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.peyilo.ireadview.api.event.ReaderListenerAdapter;
import org.peyilo.ireadview.api.event.ReaderAction;
import org.peyilo.ireadview.api.effect.CurlOptions;
import org.peyilo.ireadview.api.effect.PageTurnEffects;
import org.peyilo.ireadview.api.effect.TurnPoint;
import org.peyilo.ireadview.spi.page.ReaderPageContext;
import org.peyilo.ireadview.spi.page.ReaderPageDecorationAdapter;
import org.peyilo.ireadview.spi.page.ReaderPageFactories;
import org.peyilo.ireadview.api.model.ReaderSnapshot;
import org.peyilo.ireadview.api.state.*;
import org.peyilo.ireadview.source.MemoryBookSource;
import org.peyilo.ireadview.widget.BookReadView;
import org.peyilo.ireadview.widget.ReaderView;

/** Programmatic Java host; no Activity or controller is supplied by the Widget library. */
public final class JavaReaderActivity extends Activity {
    private ReaderView reader;
    private ReaderRequest request;
    private ReaderSnapshot snapshot;
    private boolean started;
    private long serial;
    private boolean appleCurl;
    private boolean acceleratedCurl;
    private void applyCurl() {
        final boolean accelerated = acceleratedCurl;
        CurlOptions options = new CurlOptions(.10f, 18f, .20f, 0xffeee9df, .25f, 64, 48, control -> {
            float p = control.getSheetProgress();
            if (accelerated) p = Math.min(1f, p / .75f);
            float q = p * p * (3f - 2f * p);
            return new TurnPoint(control.getViewport().getWidthPx() * (1f - 2f * q),
                    control.getDefaultControlPoint().getYPx());
        });
        reader.setPageTurnEffect(appleCurl ? PageTurnEffects.iBookCurl(options) : PageTurnEffects.googleCurl(options));
    }
    private final Runnable resume = () -> {
        if (!started) return;
        long current = ++serial;
        ReaderCallback<OpenResult> callback = completion -> { if (serial == current) request = null; };
        MemoryBookSource source = SampleBooks.create();
        request = snapshot == null ? reader.openBook(source, callback) : reader.restoreSnapshot(source, snapshot, callback);
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        snapshot = SnapshotBundles.read(state == null ? null : state.getBundle("readerSnapshot"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        TextView status = new TextView(this);
        status.setMinLines(2);
        status.setMaxLines(2);
        reader = new ReaderView(this);
        HostContentStrategies.install(reader);
        reader.setId(R.id.programmatic_reader);
        reader.setPageFactory(ReaderPageFactories.withDecorations(context -> {
            LinearLayout group = new LinearLayout(context);
            group.setGravity(android.view.Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(context);
            icon.setImageResource(android.R.drawable.ic_menu_compass);
            icon.setContentDescription("章节标识");
            int iconSize = Math.round(28f * context.getResources().getDisplayMetrics().density);
            TextView header = new TextView(context);
            header.setSingleLine(true);
            group.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
            group.addView(header, new LinearLayout.LayoutParams(0, -2, 1f));
            return new ReaderPageDecorationAdapter() {
                @Override public View getView() { return group; }
                @Override public void bind(ReaderPageContext page) {
                    header.setText("Java 页眉 · 第 " + (page.getPosition() == null ? "…" : page.getPosition().getPageNumberHint()) + " 页");
                }
                @Override public void unbind() { header.setText(""); }
            };
        }, context -> {
            LinearLayout group = new LinearLayout(context);
            group.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView label = new TextView(context);
            Button footer = new Button(context);
            footer.setText("页脚设置");
            group.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            group.addView(footer, new LinearLayout.LayoutParams(-2, -2));
            return new ReaderPageDecorationAdapter() {
                @Override public View getView() { return group; }
                @Override public void bind(ReaderPageContext page) {
                    label.setText("Java 页脚 · 第 " + (page.getPosition() == null ? "…" : page.getPosition().getPageNumberHint()) + " 页");
                    footer.setEnabled(page.getPosition() != null);
                    footer.setOnClickListener(view -> page.getRequestReaderAction().invoke(ReaderAction.ShowSettings));
                }
                @Override public void unbind() { label.setText(""); footer.setOnClickListener(null); }
            };
        }));
        reader.setReaderActionHandler((view, action) -> {
            if (action != ReaderAction.ShowSettings) return false;
            status.setText("页脚设置已响应");
            return true;
        });
        applyCurl();
        LinearLayout effects = new LinearLayout(this);
        Button apple = new Button(this);
        apple.setText("Apple");
        apple.setOnClickListener(view -> { appleCurl = true; applyCurl(); });
        Button google = new Button(this);
        google.setText("Google");
        google.setOnClickListener(view -> { appleCurl = false; applyCurl(); });
        Button travel = new Button(this);
        travel.setText("标准行程");
        travel.setOnClickListener(view -> {
            acceleratedCurl = !acceleratedCurl;
            travel.setText(acceleratedCurl ? "加速行程" : "标准行程");
            applyCurl();
        });
        Button direction = new Button(this);
        direction.setText("LTR");
        direction.setOnClickListener(view -> {
            boolean rtl = reader.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL;
            reader.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
            direction.setText(rtl ? "RTL" : "LTR");
        });
        effects.addView(apple, new LinearLayout.LayoutParams(0, -2, 1f));
        effects.addView(google, new LinearLayout.LayoutParams(0, -2, 1f));
        effects.addView(direction, new LinearLayout.LayoutParams(0, -2, 1f));
        effects.addView(travel, new LinearLayout.LayoutParams(0, -2, 1f));
        Button previous = new Button(this);
        previous.setText("上一页");
        previous.setEnabled(false);
        previous.setOnClickListener(view -> { if (started) reader.previousPage(new NavigationOptions(), completion -> {}); });
        Button next = new Button(this);
        next.setText("下一页");
        next.setEnabled(false);
        next.setOnClickListener(view -> { if (started) reader.nextPage(new NavigationOptions(), completion -> {}); });
        reader.addReaderListener(new ReaderListenerAdapter() {
            @Override public void onStateChanged(BookReadView view, ReaderState state) {
                if (!started) return;
                status.setText(state.getError() != null ? state.getError().getMessage() : state.getPosition() != null
                    ? "第" + state.getPosition().getPageNumberHint() + "页" : "正在打开…");
                next.setEnabled(state.getNavigationCapabilities().getNext() == NavigationAvailability.Available);
                previous.setEnabled(state.getNavigationCapabilities().getPrevious() == NavigationAvailability.Available);
            }
        });
        root.addView(status);
        root.addView(effects);
        root.addView(reader, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout navigation = new LinearLayout(this);
        navigation.addView(previous, new LinearLayout.LayoutParams(0, -2, 1f));
        navigation.addView(next, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(navigation);
        setContentView(root);
    }
    @Override protected void onStart() { super.onStart(); started = true; reader.post(resume); }
    @Override protected void onSaveInstanceState(Bundle outState) {
        ReaderSnapshot current = reader.exportSnapshot();
        if (current == null) current = snapshot;
        if (current != null) outState.putBundle("readerSnapshot", SnapshotBundles.write(current));
        super.onSaveInstanceState(outState);
    }
    @Override protected void onStop() {
        started = false; reader.removeCallbacks(resume);
        ReaderSnapshot current = reader.exportSnapshot();
        if (current != null) snapshot = current;
        serial++;
        ReaderRequest old = request; request = null;
        try { if (old != null) old.cancel(); } finally { reader.closeBook(); super.onStop(); }
    }
    @Override protected void onDestroy() { try { reader.release(); } finally { super.onDestroy(); } }
}
