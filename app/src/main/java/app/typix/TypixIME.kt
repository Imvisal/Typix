package app.typix

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class TypixIME : InputMethodService(), TypixKeyboardView.Listener {
    private var sinhala = true
    private var secure = false          // password-like field: no composing, no learning
    private val buf = StringBuilder()
    private var view: TypixKeyboardView? = null

    override fun onCreateInputView(): View =
        TypixKeyboardView(this, this).also { it.sinhala = sinhala; view = it }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        buf.setLength(0)
        val t = info?.inputType ?: 0
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        secure = (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    override fun onFinishInput() { buf.setLength(0); super.onFinishInput() }

    private fun flush() { if (buf.isNotEmpty()) { currentInputConnection?.finishComposingText(); buf.setLength(0) } }

    override fun onKey(c: Char) {
        val ic = currentInputConnection ?: return
        if (sinhala && !secure) { buf.append(c); ic.setComposingText(Transliterator.transliterate(buf.toString()), 1) }
        else ic.commitText(c.toString(), 1)
    }
    override fun onSpace() { flush(); currentInputConnection?.commitText(" ", 1) }
    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        if (buf.isNotEmpty()) {
            buf.deleteCharAt(buf.length - 1)
            if (buf.isEmpty()) ic.commitText("", 1) else ic.setComposingText(Transliterator.transliterate(buf.toString()), 1)
        } else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }
    override fun onEnter() { flush(); sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }
    override fun onToggleMode() { flush(); sinhala = !sinhala; view?.let { it.sinhala = sinhala; it.invalidate() } }
}
