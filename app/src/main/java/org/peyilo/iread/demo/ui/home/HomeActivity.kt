package org.peyilo.iread.demo.ui.home

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.graphics.Color
import android.widget.Button
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import org.peyilo.iread.demo.R
import org.peyilo.iread.demo.ui.reader.ReaderActivity
import org.peyilo.iread.demo.ui.examples.*
import org.peyilo.iread.demo.ui.effects.CurlActivity
import org.peyilo.iread.demo.ui.container.ContainerActivity

/** Launcher menu. Each entry explicitly chooses the demonstration to open. */
class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBarStyle()
        setContentView(R.layout.activity_home)
        listOf(R.id.open_builtin, R.id.open_local_txt, R.id.open_epub, R.id.open_network, R.id.more_examples).forEach { id ->
            findViewById<View>(id).accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Button::class.java.name
                }
            }
        }
        findViewById<View>(R.id.open_builtin).setOnClickListener {
            startActivity(Intent(this, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_ENTRY, ReaderActivity.ENTRY_BUILTIN))
        }
        findViewById<View>(R.id.open_local_txt).setOnClickListener { chooseTextFile() }
        findViewById<View>(R.id.open_epub).setOnClickListener {
            startActivity(Intent(this, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_ENTRY, ReaderActivity.ENTRY_EPUB))
        }
        findViewById<View>(R.id.open_network).setOnClickListener {
            startActivity(Intent(this, NetworkActivity::class.java))
        }
        findViewById<View>(R.id.more_examples).setOnClickListener {
            AlertDialog.Builder(this).setTitle("更多 Widget 示例")
                .setItems(arrayOf("翻页与页眉页脚", "卷页翻页", "非书籍容器", "自定义正文块", "TXT 来源配置", "Java 宿主")) { _, index ->
                    val target = when (index) {
                        0, 1 -> CurlActivity::class.java
                        2 -> ContainerActivity::class.java
                        3 -> CustomContentActivity::class.java
                        4 -> TextSourceActivity::class.java
                        else -> JavaReaderActivity::class.java
                    }
                    startActivity(Intent(this, target).putExtra("pageCurl", index == 1))
                }.setNegativeButton("取消", null).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun applySystemBarStyle() {
        val paper = Color.rgb(246, 244, 237)
        val ink = Color.rgb(36, 77, 68)
        window.statusBarColor = if (Build.VERSION.SDK_INT >= 23) paper else ink
        window.navigationBarColor = if (Build.VERSION.SDK_INT >= 26) paper else ink
        var flags = window.decorView.systemUiVisibility
        if (Build.VERSION.SDK_INT >= 23) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = flags
    }

    @Suppress("DEPRECATION")
    private fun chooseTextFile() {
        val request = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/*", "application/octet-stream"))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try { startActivityForResult(request, SELECT_TXT) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "系统没有可用的文件选择器", Toast.LENGTH_LONG).show() }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SELECT_TXT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        startActivity(Intent(this, ReaderActivity::class.java).setData(uri)
            .addFlags(data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
            .putExtra(ReaderActivity.EXTRA_ENTRY, ReaderActivity.ENTRY_LOCAL_TXT))
    }

    private companion object { const val SELECT_TXT = 10 }
}
