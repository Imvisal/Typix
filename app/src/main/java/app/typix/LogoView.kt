package app.typix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** The Typix logo, drawn from the design (120x120 grid). */
class LogoView(ctx: Context, @Suppress("UNUSED_PARAMETER") accent: Int) : View(ctx) {
    private val accent = 0xFFC8F135.toInt()   // the logo always keeps its lime colour, whatever the theme
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val a = Path().apply { moveTo(54f, 33f); lineTo(54f, 68f); quadTo(54f, 78f, 64f, 78f); lineTo(68f, 78f) }
    private val b = Path().apply { moveTo(41f, 50f); lineTo(67f, 50f) }
    override fun onDraw(c: Canvas) {
        // only the keycap (no outer tile / border), cropped so it fills the view evenly
        val s = width / 88f
        c.save(); c.scale(s, s); c.translate(-16f, -16f)
        p.style = Paint.Style.FILL
        p.color = 0xFF070A16.toInt(); c.drawRoundRect(20f, 22f, 100f, 104f, 20f, 20f, p)
        p.color = 0xFF1B2140.toInt(); c.drawRoundRect(20f, 16f, 100f, 96f, 20f, 20f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 11f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.color = accent; c.drawPath(a, p); c.drawPath(b, p)
        p.style = Paint.Style.FILL; p.color = 0xFFEEF1FF.toInt(); c.drawRoundRect(78f, 46f, 85f, 78f, 3.5f, 3.5f, p)
        p.color = accent; c.drawRoundRect(44f, 85f, 76f, 90f, 2.5f, 2.5f, p)
        c.restore()
    }
}
