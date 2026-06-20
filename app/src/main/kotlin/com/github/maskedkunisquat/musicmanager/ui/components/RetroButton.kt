package com.github.maskedkunisquat.musicmanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.maskedkunisquat.musicmanager.ui.theme.RetroTheme

private val PIXEL_SIZE: Dp = 4.dp
private const val CORNER_SIZE: Int = 2

private data class Segment(val topLeft: Offset, val size: Size)

private fun DrawScope.drawSegments(segments: List<Segment>, color: Color) {
    segments.forEach { drawRect(color = color, topLeft = it.topLeft, size = it.size) }
}

private fun buildBorderSegments(size: Size, px: Float, cornerSize: Int): List<Segment> {
    val c = cornerSize * px
    val segments = mutableListOf<Segment>()

    segments += Segment(Offset(c, 0f), Size(size.width - c * 2, px))
    segments += Segment(Offset(c, size.height - px), Size(size.width - c * 2, px))
    segments += Segment(Offset(0f, c), Size(px, size.height - c * 2))
    segments += Segment(Offset(size.width - px, c), Size(px, size.height - c * 2))

    for (i in 1..cornerSize) {
        for (j in 1..cornerSize) {
            if (i + j == cornerSize + 1) {
                val s = Size(px, px)
                segments += Segment(Offset(i * px, j * px), s)
                segments += Segment(Offset(size.width - (i + 1) * px, j * px), s)
                segments += Segment(Offset(size.width - (i + 1) * px, size.height - (j + 1) * px), s)
                segments += Segment(Offset(i * px, size.height - (j + 1) * px), s)
            }
        }
    }

    return segments
}

// Octagonal path so the fill is clipped at corners, not just overlaid.
private fun DrawScope.drawPixelFill(color: Color, px: Float, cornerSize: Int) {
    val c = cornerSize * px
    val path = Path().apply {
        moveTo(c, 0f)
        lineTo(size.width - c, 0f)
        lineTo(size.width, c)
        lineTo(size.width, size.height - c)
        lineTo(size.width - c, size.height)
        lineTo(c, size.height)
        lineTo(0f, size.height - c)
        lineTo(0f, c)
        close()
    }
    drawPath(path, color)
}

@Composable
fun RetroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val disabled = MaterialTheme.colorScheme.onSurfaceVariant

    val borderColor = if (enabled) primary else disabled
    val contentColor = when {
        !enabled -> disabled
        filled   -> onPrimary
        else     -> primary
    }

    val segmentCache = remember { object { var key = Size.Zero; var segments = emptyList<Segment>() } }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .drawBehind {
                    val px = PIXEL_SIZE.toPx()
                    if (filled && enabled) drawPixelFill(primary, px, CORNER_SIZE)
                    val segments = if (segmentCache.key == size) {
                        segmentCache.segments
                    } else {
                        buildBorderSegments(size, px, CORNER_SIZE).also {
                            segmentCache.key = size
                            segmentCache.segments = it
                        }
                    }
                    drawSegments(segments, borderColor)
                }
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun RetroButtonFilledPreview() {
    RetroTheme {
        RetroButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("OPEN INBOX")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun RetroButtonOutlinePreview() {
    RetroTheme {
        RetroButton(onClick = {}, filled = false, modifier = Modifier.fillMaxWidth()) {
            Text("SECONDARY ACTION")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun RetroButtonDisabledPreview() {
    RetroTheme {
        RetroButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("DISABLED")
        }
    }
}
