package com.irpro.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Icônes dessinées (traits fins, style console). */
enum class IrIcon { LIVE, MOVIE, SERIES, GUIDE, FAVORITE, SEARCH, SETTINGS, PLAYLIST }

@Composable
fun IrIconView(icon: IrIcon, color: Color, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val st = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
        when (icon) {
            IrIcon.LIVE -> {
                drawRoundRect(color, Offset(w * .08f, w * .14f), Size(w * .84f, w * .58f), CornerRadius(w * .1f), style = st)
                drawLine(color, Offset(w * .32f, w * .88f), Offset(w * .68f, w * .88f), st.width, StrokeCap.Round)
                tri(color, w * .42f, w * .3f, w * .26f)
            }
            IrIcon.MOVIE -> {
                drawRoundRect(color, Offset(w * .1f, w * .32f), Size(w * .8f, w * .56f), CornerRadius(w * .08f), style = st)
                val p = Path().apply {
                    moveTo(w * .1f, w * .3f); lineTo(w * .84f, w * .1f)
                }
                drawPath(p, color, style = st)
                for (i in 0..2) drawLine(color, Offset(w * (.26f + i * .2f), w * .26f), Offset(w * (.32f + i * .2f), w * .16f), st.width * .8f, StrokeCap.Round)
            }
            IrIcon.SERIES -> {
                drawRoundRect(color, Offset(w * .2f, w * .1f), Size(w * .7f, w * .5f), CornerRadius(w * .08f), style = Stroke(st.width * .7f))
                drawRoundRect(color, Offset(w * .1f, w * .3f), Size(w * .7f, w * .56f), CornerRadius(w * .08f), style = st)
                tri(color, w * .36f, w * .46f, w * .22f)
            }
            IrIcon.GUIDE -> {
                drawRoundRect(color, Offset(w * .1f, w * .18f), Size(w * .8f, w * .7f), CornerRadius(w * .1f), style = st)
                drawLine(color, Offset(w * .1f, w * .38f), Offset(w * .9f, w * .38f), st.width)
                drawLine(color, Offset(w * .32f, w * .08f), Offset(w * .32f, w * .26f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * .68f, w * .08f), Offset(w * .68f, w * .26f), st.width, StrokeCap.Round)
                for (r in 0..1) for (c in 0..2) drawCircle(color, w * .045f, Offset(w * (.3f + c * .2f), w * (.56f + r * .17f)))
            }
            IrIcon.FAVORITE -> {
                val p = Path().apply {
                    moveTo(w * .5f, w * .86f)
                    cubicTo(w * .05f, w * .55f, w * .02f, w * .2f, w * .3f, w * .16f)
                    cubicTo(w * .42f, w * .14f, w * .48f, w * .22f, w * .5f, w * .3f)
                    cubicTo(w * .52f, w * .22f, w * .58f, w * .14f, w * .7f, w * .16f)
                    cubicTo(w * .98f, w * .2f, w * .95f, w * .55f, w * .5f, w * .86f)
                    close()
                }
                drawPath(p, color, style = st)
            }
            IrIcon.SEARCH -> {
                drawCircle(color, w * .3f, Offset(w * .42f, w * .42f), style = st)
                drawLine(color, Offset(w * .64f, w * .64f), Offset(w * .88f, w * .88f), st.width * 1.2f, StrokeCap.Round)
            }
            IrIcon.SETTINGS -> {
                drawCircle(color, w * .16f, Offset(w * .5f, w * .5f), style = st)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val c = Offset(w * .5f, w * .5f)
                    val a1 = Offset(c.x + (w * .3f * Math.cos(a)).toFloat(), c.y + (w * .3f * Math.sin(a)).toFloat())
                    val a2 = Offset(c.x + (w * .42f * Math.cos(a)).toFloat(), c.y + (w * .42f * Math.sin(a)).toFloat())
                    drawLine(color, a1, a2, st.width * 1.3f, StrokeCap.Round)
                }
                drawCircle(color, w * .3f, Offset(w * .5f, w * .5f), style = Stroke(st.width * .8f))
            }
            IrIcon.PLAYLIST -> {
                for (i in 0..2) drawLine(color, Offset(w * .12f, w * (.25f + i * .22f)), Offset(w * .62f, w * (.25f + i * .22f)), st.width, StrokeCap.Round)
                tri(color, w * .68f, w * .52f, w * .24f)
            }
        }
    }
}

private fun DrawScope.tri(color: Color, x: Float, y: Float, s: Float) {
    val p = Path().apply { moveTo(x, y); lineTo(x + s, y + s / 2f); lineTo(x, y + s); close() }
    drawPath(p, color)
}
