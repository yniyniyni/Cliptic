package art.yniyniyni.cliptic.xposed.hooks

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * Stock-style "content copy" glyph drawn programmatically (two overlapping rounded sheets) so the
 * module needs no bundled drawable resource. Honours tint applied by the host
 * ([setTint] / [setTintList] / [setColorFilter]) so it matches whatever icon colour the surrounding
 * UI uses — the SystemUI screenshot shelf and the Markup editor toolbar both tint it to their theme.
 *
 * Shared by [CopyButtonInjector] (SystemUI shelf) and [MarkupCopyInjector] (Markup editor).
 */
internal class CopyIconDrawable(defaultColor: Int, private val sizePx: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = defaultColor
    }

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        if (s <= 0f) return
        val left = b.left + (b.width() - s) / 2f
        val top = b.top + (b.height() - s) / 2f
        paint.strokeWidth = s * 0.085f
        val r = s * 0.14f
        val fl = left + s * 0.30f; val ft = top + s * 0.30f
        val fr = left + s * 0.92f; val fb = top + s * 0.92f
        val gap = s * 0.20f
        canvas.drawRoundRect(left + s * 0.10f, top + s * 0.10f, fr - gap, fb - gap, r, r, paint)
        canvas.drawRoundRect(fl, ft, fr, fb, r, r, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }
    override fun setTint(tintColor: Int) { paint.color = tintColor; invalidateSelf() }
    override fun setTintList(tint: ColorStateList?) {
        tint?.defaultColor?.let { paint.color = it; invalidateSelf() }
    }
    @Deprecated("deprecated in Drawable", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
