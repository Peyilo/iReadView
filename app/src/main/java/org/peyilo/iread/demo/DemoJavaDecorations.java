package org.peyilo.iread.demo;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import org.peyilo.ireadview.api.event.ReaderAction;
import org.peyilo.ireadview.spi.page.ReaderPageContext;
import org.peyilo.ireadview.spi.page.ReaderPageDecorationAdapter;
import org.peyilo.ireadview.spi.page.ReaderPageFactories;
import org.peyilo.ireadview.spi.page.ReaderPageFactory;

/** Each lambda creates a separate, unparented ordinary View for its page. */
final class DemoJavaDecorations {
    static ReaderPageFactory create() {
        return ReaderPageFactories.withDecorations(context -> {
            TextView title = new TextView(context);
            title.setTextSize(16f);
            title.setSingleLine(true);
            return new ReaderPageDecorationAdapter() {
                @Override public View getView() { return title; }
                @Override public void bind(ReaderPageContext page) {
                    Integer number = page.getPosition() == null ? null : page.getPosition().getPageNumberHint();
                    title.setText("Java 自定义页眉 · " + (number == null ? "准备中" : "第 " + number + " 页"));
                }
                @Override public void unbind() { title.setText(""); }
            };
        }, context -> {
            Button settings = new Button(context);
            settings.setText("Java 自定义页脚：设置");
            return new ReaderPageDecorationAdapter() {
                @Override public View getView() { return settings; }
                @Override public void bind(ReaderPageContext page) {
                    settings.setEnabled(page.getPosition() != null);
                    settings.setOnClickListener(view -> page.getRequestReaderAction().invoke(ReaderAction.ShowSettings));
                }
                @Override public void unbind() { settings.setOnClickListener(null); }
            };
        });
    }
    private DemoJavaDecorations() {}
}
