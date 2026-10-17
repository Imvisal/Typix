package app.typix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller

/**
 * Emoji grid drawn on a single Canvas with its own scrolling/fling: only the visible rows are drawn,
 * so even the 250-flag page opens instantly and scrolls smoothly (no hundreds of TextViews).
 */
class EmojiGridView(ctx: Context, th: Theme, private val onPick: (String) -> Unit) : View(ctx) {
    private var items: List<String> = emptyList()
    private var emptyText = ""
    private val cols = 7
    private val d = ctx.resources.displayMetrics.density
    private val cellH = 48 * d
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 26 * d }
    private val msg = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 14 * d; color = mixColor(th.text, th.bg, 0.45f); typeface = Fonts.get(ctx, 400) }
    private var scrollPos = 0f
    private val scroller = OverScroller(ctx)
    private var tracker: VelocityTracker? = null
    private var downX = 0f; private var downY = 0f; private var lastY = 0f; private var moved = false
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val minFling = ViewConfiguration.get(ctx).scaledMinimumFlingVelocity
    private val maxFling = ViewConfiguration.get(ctx).scaledMaximumFlingVelocity

    fun setItems(l: List<String>, empty: String = "") {
        items = l; emptyText = empty; scrollPos = 0f; scroller.forceFinished(true); invalidate()
    }

    private fun maxScroll(): Float {
        val rows = (items.size + cols - 1) / cols
        return maxOf(0f, rows * cellH - height)
    }

    override fun onDraw(c: Canvas) {
        if (items.isEmpty()) {
            var y = height * 0.35f
            for (line in emptyText.split('\n')) { c.drawText(line, width / 2f, y, msg); y += msg.textSize * 1.4f }
            return
        }
        val cw = width / cols.toFloat()
        val first = (scrollPos / cellH).toInt().coerceAtLeast(0)
        val last = ((scrollPos + height) / cellH).toInt() + 1
        val base = cellH / 2 + p.textSize * 0.35f
        for (r in first..last) for (col in 0 until cols) {
            val i = r * cols + col
            if (i >= items.size) return
            c.drawText(items[i], col * cw + cw / 2, r * cellH - scrollPos + base, p)
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) { scrollPos = scroller.currY.toFloat().coerceIn(0f, maxScroll()); postInvalidateOnAnimation() }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (tracker == null) tracker = VelocityTracker.obtain()
        tracker?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true); downX = e.x; downY = e.y; lastY = e.y; moved = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && Math.abs(e.y - downY) > slop) moved = true
                if (moved) { scrollPos = (scrollPos + (lastY - e.y)).coerceIn(0f, maxScroll()); invalidate() }
                lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    val col = (e.x / (width / cols.toFloat())).toInt().coerceIn(0, cols - 1)
                    val row = ((scrollPos + e.y) / cellH).toInt()
                    val i = row * cols + col
                    if (i in items.indices) { if (Prefs.bool(context, "vibrate", false)) performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); onPick(items[i]) }
                } else {
                    tracker?.computeCurrentVelocity(1000, maxFling.toFloat())
                    val vy = tracker?.yVelocity ?: 0f
                    if (Math.abs(vy) > minFling) { scroller.fling(0, scrollPos.toInt(), 0, (-vy).toInt(), 0, 0, 0, maxScroll().toInt()); postInvalidateOnAnimation() }
                }
                tracker?.recycle(); tracker = null
            }
            MotionEvent.ACTION_CANCEL -> { tracker?.recycle(); tracker = null }
        }
        return true
    }
}
