package org.peyilo.iread.demo.reader

import org.peyilo.iread.demo.R
import org.peyilo.ireadview.api.style.*

/** Demo defaults traced to the reference ReadStyle, ReadActivity and page XML. */
internal object ReaderDefaults {
    // Bump when changing the baseline; older Demo styles must not hide a new requested default.
    const val TYPOGRAPHY_REVISION = 4
    fun font(role: TextRole): ReaderFont = when (role) {
        TextRole.TITLE -> ReaderFont.Resource(R.font.linja_waso_bold)
        TextRole.BODY -> ReaderFont.Resource(R.font.linja_waso)
        else -> ReaderFont.System("sans-serif")
    }
}
