package org.peyilo.iread.demo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import org.peyilo.ireadview.api.event.ReaderListenerAdapter;
import org.peyilo.ireadview.api.event.ReaderAction;
import org.peyilo.ireadview.api.effect.CurlOptions;
import org.peyilo.ireadview.api.effect.PageTurnEffects;
import org.peyilo.ireadview.api.effect.TurnPoint;
import org.peyilo.ireadview.api.model.BookDescriptor;
import org.peyilo.ireadview.api.model.ChapterContent;
import org.peyilo.ireadview.api.model.ChapterDescriptor;
import org.peyilo.ireadview.api.model.ContentBlock;
import org.peyilo.ireadview.api.model.HeadingBlock;
import org.peyilo.ireadview.api.model.ReaderSnapshot;
import org.peyilo.ireadview.api.model.ReadingPosition;
import org.peyilo.ireadview.api.model.TextBlock;
import org.peyilo.ireadview.api.model.TocEntry;
import org.peyilo.ireadview.api.state.NavigationAvailability;
import org.peyilo.ireadview.api.state.NavigationOptions;
import org.peyilo.ireadview.api.state.NavigationResult;
import org.peyilo.ireadview.api.state.OpenResult;
import org.peyilo.ireadview.api.state.ReaderCallback;
import org.peyilo.ireadview.api.state.ReaderCompletion;
import org.peyilo.ireadview.api.state.ReaderRequest;
import org.peyilo.ireadview.api.state.ReaderState;
import org.peyilo.ireadview.source.MemoryBookSource;
import org.peyilo.ireadview.source.TextBookSource;
import org.peyilo.ireadview.source.TextBookOptions;
import org.peyilo.ireadview.api.source.TextBlockIdentity;
import org.peyilo.ireadview.api.source.TextContentIdProviderAdapter;
import org.peyilo.ireadview.api.source.TextNormalization;
import org.peyilo.ireadview.api.source.BookSource;
import org.peyilo.ireadview.api.source.ReaderInput;
import org.peyilo.ireadview.widget.BookReadView;
import org.peyilo.ireadview.widget.ReaderView;

/** Java consumer of the public Widget API; the Bundle codec belongs to this demo host. */
public final class DemoJavaActivity extends Activity {
    private ReaderView reader;
    private TextView status;
    private Button previous;
    private Button next;
    private ReaderRequest request;
    private ReaderSnapshot snapshot;
    private long sequence;
    private boolean started;
    private boolean textMode;
    private TextBookSource textSource;
    private final Runnable resume = () -> { if (started) open(true); };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        snapshot = DemoSnapshotState.INSTANCE.read(savedInstanceState == null ? null : savedInstanceState.getBundle("javaSnapshot"));
        textMode = savedInstanceState != null && savedInstanceState.getBoolean("javaTextSource");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setFitsSystemWindows(true);
        status = new TextView(this);
        status.setMinLines(2);
        status.setMaxLines(2);
        status.setText("Java 回调、取消与轻量位置恢复示例");
        reader = new ReaderView(this);
        reader.setId(R.id.java_reader);
        reader.setPageFactory(DemoJavaDecorations.create());
        reader.setReaderActionHandler((view, action) -> {
            if (action != ReaderAction.ShowSettings) return false;
            status.setText("Java 页脚按钮已响应");
            return true;
        });
        reader.setPageTurnEffect(PageTurnEffects.googleCurl(new CurlOptions(
                .10f, 18f, .20f, 0xffeee9df, .25f, 64, 48, control -> {
                    float p = control.getSheetProgress();
                    float eased = p * p * (3f - 2f * p);
                    return new TurnPoint(control.getViewport().getWidthPx() * (1f - 2f * eased),
                            control.getDefaultControlPoint().getYPx());
                })));
        LinearLayout controls = new LinearLayout(this);
        add(controls, button("从头打开", () -> open(false)));
        add(controls, button("保存并恢复", () -> open(true)));
        add(controls, button("取消请求", () -> {
            cancelRequest();
            status.setText("已取消当前请求；阅读位置以组件提交结果为准");
        }));
        layout.addView(controls);
        layout.addView(button("切换内存模型 / TXT 来源", () -> { textMode = !textMode; open(true); }));
        layout.addView(status);
        layout.addView(reader, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout navigation = new LinearLayout(this);
        previous = button("上一页", () -> navigate(false));
        next = button("下一页", () -> navigate(true));
        add(navigation, previous);
        add(navigation, next);
        add(navigation, button("退出", this::finish));
        layout.addView(navigation);
        setContentView(layout);
        reader.addReaderListener(new ReaderListenerAdapter() {
            @Override public void onStateChanged(BookReadView view, ReaderState state) {
                previous.setEnabled(available(state.getNavigationCapabilities().getPrevious()));
                next.setEnabled(available(state.getNavigationCapabilities().getNext()));
            }
        });
        previous.setEnabled(false);
        next.setEnabled(false);
    }

    private boolean current(long serial) { return serial == sequence && started && !isFinishing() && !isDestroyed(); }

    private void open(boolean restore) {
        ReaderSnapshot visible = reader.exportSnapshot();
        if (visible != null) snapshot = visible;
        cancelRequest();
        try { reader.closeBook(); } finally { retireTextSource(); }
        BookSource source;
        if (textMode) {
            StringBuilder text = new StringBuilder("Chapter 1 Java TXT 来源\n");
            for (int index = 0; index < 36; index++) text.append("第").append(index + 1).append("段：Java 静态输入工厂每次提供新流，文本来源负责解析与关闭读取流，来源对象由宿主管理。\n");
            byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
            // Direct ReaderInput SAM implementations may also declare IOException, e.g. file I/O.
            TextBookOptions defaults = new TextBookOptions();
            TextContentIdProviderAdapter identities = new TextContentIdProviderAdapter() {
                @Override public String getPersistentIndexKey() { return "java-text-ids:v1"; }
                @Override public String blockId(TextBlockIdentity context) { return "java-line-" + context.getBlockNumber(); }
            };
            TextBookOptions options = new TextBookOptions("Java TXT", StandardCharsets.UTF_8,
                defaults.getChapterHeading(), defaults.getPreambleTitle(), defaults.getMaxInputBytes(),
                new File(getCacheDir(), "java-text"), defaults.getMaxIndexBytes(), identities,
                TextNormalization.PRESERVE, true, defaults.getPersistentIndexCacheBytes());
            textSource = new TextBookSource("demo-java-text", ReaderInput.fromStream(() -> new ByteArrayInputStream(bytes)), options);
            source = textSource;
        } else source = sample();
        if (snapshot != null && !source.getBookId().equals(snapshot.getBookId())) snapshot = null;
        long serial = sequence;
        int[] completions = {0};
        ReaderCallback<OpenResult> callback = outcome -> {
            int count = ++completions[0];
            if (!current(serial)) return;
            request = null;
            if (outcome instanceof ReaderCompletion.Completed<?>) {
                Object result = ((ReaderCompletion.Completed<?>) outcome).getValue();
                if (result instanceof OpenResult.Failed) status.setText(((OpenResult.Failed) result).getError().getMessage());
                else if (result instanceof OpenResult.Empty) status.setText("书籍没有章节");
                else if (result instanceof OpenResult.Superseded) status.setText("打开请求已被较新操作替代");
                else if (result instanceof OpenResult.Opened) status.setText("已打开 · 本请求回调 " + count + " 次 · " + positionLabel());
            } else status.setText("打开请求已取消");
        };
        ReaderRequest submitted = restore && snapshot != null
                ? reader.restoreSnapshot(source, snapshot, callback)
                : reader.openBook(source, callback);
        // Immediate completion may precede return of the handle; never register an obsolete handle.
        if (current(serial) && !submitted.isCompleted()) request = submitted;
    }

    private void navigate(boolean forward) {
        cancelRequest();
        long serial = sequence;
        int[] completions = {0};
        ReaderCallback<NavigationResult<ReadingPosition>> callback = outcome -> {
            int count = ++completions[0];
            if (!current(serial)) return;
            request = null;
            if (outcome instanceof ReaderCompletion.Completed<?>) {
                Object result = ((ReaderCompletion.Completed<?>) outcome).getValue();
                if (result instanceof NavigationResult.Failed) status.setText(((NavigationResult.Failed) result).getError().getMessage());
                else if (result instanceof NavigationResult.Rejected) status.setText("当前无法翻页：" + ((NavigationResult.Rejected) result).getReason());
                else if (result instanceof NavigationResult.Superseded) status.setText("翻页请求已被较新操作替代");
                else status.setText("导航结束 · 本请求回调 " + count + " 次 · " + positionLabel());
            } else status.setText("翻页请求已取消");
        };
        ReaderRequest submitted = forward ? reader.nextPage(new NavigationOptions(), callback) : reader.previousPage(new NavigationOptions(), callback);
        if (current(serial) && !submitted.isCompleted()) request = submitted;
    }

    private void cancelRequest() {
        sequence++;
        ReaderRequest old = request;
        request = null;
        if (old != null) old.cancel();
    }

    private void retireTextSource() {
        TextBookSource old = textSource;
        textSource = null;
        if (old != null) old.close();
    }

    private String positionLabel() {
        ReadingPosition position = reader.getCurrentPosition();
        return position == null ? "尚无已提交位置" : "第 " + position.getPageNumberHint() + " 页";
    }

    private static boolean available(NavigationAvailability value) {
        return value == NavigationAvailability.Available || value == NavigationAvailability.Unknown;
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> { if (started && !isFinishing() && !isDestroyed()) action.run(); });
        return button;
    }
    private static void add(LinearLayout row, Button button) { row.addView(button, new LinearLayout.LayoutParams(0, -2, 1)); }

    private static MemoryBookSource sample() {
        ChapterDescriptor chapter = new ChapterDescriptor("chapter-1", "Java 宿主接入", "1");
        BookDescriptor book = new BookDescriptor("demo-java", "Java 阅读示例", "1", Collections.singletonList(new TocEntry.Chapter(chapter)));
        List<ContentBlock> blocks = new ArrayList<>();
        blocks.add(new HeadingBlock("title", chapter.getTitle(), 1));
        for (int index = 0; index < 36; index++) blocks.add(new TextBlock("paragraph-" + index,
                "第" + (index + 1) + "段：Java 宿主使用不可变内容模型和公开回调接口。请求可以取消，页面位置只取组件已提交结果；宿主保存轻量快照，并在重建来源后恢复阅读。"));
        ChapterContent content = new ChapterContent(book.getBookId(), chapter.getChapterId(), "1", blocks);
        return new MemoryBookSource(book, Collections.singletonList(content));
    }

    @Override protected void onStart() { super.onStart(); started = true; reader.post(resume); }
    @Override protected void onStop() {
        started = false;
        reader.removeCallbacks(resume);
        ReaderSnapshot visible = reader.exportSnapshot();
        if (visible != null) snapshot = visible;
        cancelRequest();
        try { reader.closeBook(); } finally { retireTextSource(); }
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("javaTextSource", textMode);
        ReaderSnapshot visible = reader.exportSnapshot();
        ReaderSnapshot saved = visible == null ? snapshot : visible;
        if (saved != null) outState.putBundle("javaSnapshot", DemoSnapshotState.INSTANCE.write(saved));
        super.onSaveInstanceState(outState);
    }
    @Override protected void onDestroy() {
        if (reader != null) {
            reader.removeCallbacks(resume);
            cancelRequest();
            try { reader.release(); } finally { retireTextSource(); }
        }
        super.onDestroy();
    }
}
