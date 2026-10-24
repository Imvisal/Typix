package app.typix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** The Typix toolbar logo: lime "tpx" with a white underline on the Ink tile (120x120 grid). Always the same, whatever the theme. */
class LogoView(ctx: Context, @Suppress("UNUSED_PARAMETER") accent: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Fonts.get(ctx, 700); color = 0xFFC8F135.toInt() }
    override fun onDraw(c: Canvas) {
        val s = width / 120f
        c.save(); c.scale(s, s)
        p.style = Paint.Style.FILL
        p.color = 0xFF0E1224.toInt(); c.drawRoundRect(0f, 0f, 120f, 120f, 26f, 26f, p)
        t.textSize = 54f
        c.drawText("tpx", 60f, 66f, t)
        p.color = 0xFFEEF1FF.toInt(); c.drawRoundRect(30f, 92f, 90f, 99f, 3.5f, 3.5f, p)
        c.restore()
    }
}
