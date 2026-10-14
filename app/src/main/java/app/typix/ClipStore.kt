package app.typix

import android.content.ClipboardManager
import android.content.Context

/** Clipboard history. Recent items live in memory only; pins are saved in app-private storage. Sensitive clips are skipped. */
object ClipStore {
    private val recent = ArrayDeque<String>()
    private var ctx: Context? = null
    private var cm: ClipboardManager? = null
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { read() }

    fun start(c: Context) {
        stop(); ctx = c.applicationContext
        cm = ctx!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm!!.addPrimaryClipChangedListener(listener)
    }
    fun stop() { cm?.removePrimaryClipChangedListener(listener); cm = null }

    private fun read() {
        val c = ctx ?: return
        if (!Prefs.bool(c, "clipboard", true)) return
        val clip = cm?.primaryClip ?: return
        if (clip.itemCount == 0) return
        if (clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
        val t = clip.getItemAt(0)?.coerceToText(c)?.toString()?.trim() ?: return
        if (t.isEmpty() || t.length > 500) return
        recent.remove(t); recent.addFirst(t)
        while (recent.size > 20) recent.removeLast()
    }

    fun recent(c: Context): List<String> { val p = Prefs.pins(c); return recent.filter { it !in p } }
    fun clearRecent() = recent.clear()
    fun togglePin(c: Context, s: String) {
        val p = Prefs.pins(c).toMutableList()
        if (!p.remove(s)) p.add(0, s)
        Prefs.setPins(c, p)
    }
}
