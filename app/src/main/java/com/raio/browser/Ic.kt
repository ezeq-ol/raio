package com.raio.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Icone desenhado em codigo (sem imagens, sem fontes): leve e nitido em qualquer tela. */
class Ic(c: Context, val kind: Int, var col: Int) : View(c) {
    companion object {
        const val BACK = 0
        const val FWD = 1
        const val HOME = 2
        const val TABS = 3
        const val MENU = 4
        const val RELOAD = 5
        const val STOP = 6
    }

    var badge = ""
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        invalidate()
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = minOf(width, height) * 0.24f
        p.color = if (isEnabled) col else (col and 0x00FFFFFF) or 0x4D000000
        p.strokeWidth = s * 0.18f * 2f
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        path.reset()
        when (kind) {
            BACK -> {
                path.moveTo(cx + s * .35f, cy - s)
                path.lineTo(cx - s * .65f, cy)
                path.lineTo(cx + s * .35f, cy + s)
                cv.drawPath(path, p)
            }
            FWD -> {
                path.moveTo(cx - s * .35f, cy - s)
                path.lineTo(cx + s * .65f, cy)
                path.lineTo(cx - s * .35f, cy + s)
                cv.drawPath(path, p)
            }
            HOME -> {
                path.moveTo(cx - s * 1.05f, cy - s * .05f)
                path.lineTo(cx, cy - s * .95f)
                path.lineTo(cx + s * 1.05f, cy - s * .05f)
                path.moveTo(cx - s * .72f, cy - s * .25f)
                path.lineTo(cx - s * .72f, cy + s * .9f)
                path.lineTo(cx + s * .72f, cy + s * .9f)
                path.lineTo(cx + s * .72f, cy - s * .25f)
                cv.drawPath(path, p)
            }
            TABS -> {
                rect.set(cx - s, cy - s * .9f, cx + s, cy + s * .9f)
                cv.drawRoundRect(rect, s * .3f, s * .3f, p)
                p.style = Paint.Style.FILL
                p.textSize = s * 1.05f
                p.textAlign = Paint.Align.CENTER
                p.isFakeBoldText = true
                cv.drawText(badge, cx, cy + s * .36f, p)
                p.isFakeBoldText = false
            }
            MENU -> {
                p.style = Paint.Style.FILL
                val r = s * .17f * 1.25f
                cv.drawCircle(cx, cy - s * .8f, r, p)
                cv.drawCircle(cx, cy, r, p)
                cv.drawCircle(cx, cy + s * .8f, r, p)
            }
            RELOAD -> {
                val r = s * .85f
                rect.set(cx - r, cy - r, cx + r, cy + r)
                cv.drawArc(rect, -60f, 290f, false, p)
                // ponta da seta no fim do arco (angulo 230 graus)
                val rad = Math.toRadians(230.0)
                val tx = cx + (r * Math.cos(rad)).toFloat()
                val ty = cy + (r * Math.sin(rad)).toFloat()
                p.style = Paint.Style.FILL
                cv.save()
                cv.translate(tx, ty)
                cv.rotate(-40f)
                path.reset()
                path.moveTo(s * .7f, 0f)
                path.lineTo(-s * .35f, -s * .65f)
                path.lineTo(-s * .35f, s * .65f)
                path.close()
                cv.drawPath(path, p)
                cv.restore()
            }
            STOP -> {
                cv.drawLine(cx - s * .8f, cy - s * .8f, cx + s * .8f, cy + s * .8f, p)
                cv.drawLine(cx + s * .8f, cy - s * .8f, cx - s * .8f, cy + s * .8f, p)
            }
        }
    }
}
