package org.peyilo.iread.demo

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Temporary launch target for the project scaffold. Demo flows will be added after the public API is defined. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "iRead Demo scaffold" })
    }
}
