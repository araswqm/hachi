package com.araswqm.hatir.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Markdown'i Compose metnine cevirir ([MarkdownParser] cikisini cizer).
 *
 * Ayristirma isi saf Kotlin'de yapilir ([MarkdownParser], JVM testli); buradaki
 * is yalnizca cizim. Baglantilar **tiklanabilir degildir** — yalnizca renk +
 * alt cizgi ile vurgulanir; boylece LinkAnnotation/withLink API riski alinmaz.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        blocks.forEach { block -> MarkdownBlock(block, color, style) }
    }
}

// ----------------------------------------------------------------------
// Bloklar
// ----------------------------------------------------------------------

@Composable
private fun MarkdownBlock(block: MdBlock, color: Color, base: TextStyle) {
    when (block) {
        is MdBlock.Paragraph -> MarkdownLine(block.spans, color, base)

        is MdBlock.Heading -> MarkdownLine(block.spans, color, headingStyle(block.level))

        is MdBlock.Bullet -> MarkdownList(block.items, color, base, start = null)

        is MdBlock.Ordered -> MarkdownList(block.items, color, base, start = block.start)

        is MdBlock.Quote -> MarkdownQuote(block.spans, color, base)

        is MdBlock.Code -> MarkdownCode(block.code, color, base)

        MdBlock.Rule -> HorizontalDivider()
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.titleLarge
    2 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
}

@Composable
private fun MarkdownList(
    items: List<List<MdSpan>>,
    color: Color,
    base: TextStyle,
    start: Int?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, spans ->
            Row {
                Text(
                    text = if (start == null) "•" else "${start + index}.",
                    style = base,
                    color = color,
                )
                Spacer(Modifier.width(6.dp))
                MarkdownLine(
                    spans = spans,
                    color = color,
                    style = base,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MarkdownQuote(spans: List<MdSpan>, color: Color, base: TextStyle) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        MarkdownLine(
            spans = spans,
            color = color,
            style = base.copy(color = color.copy(alpha = 0.85f)),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MarkdownCode(code: String, color: Color, base: TextStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = code,
            style = base.copy(fontFamily = FontFamily.Monospace),
            color = color,
        )
    }
}

// ----------------------------------------------------------------------
// Satir ici
// ----------------------------------------------------------------------

@Composable
private fun MarkdownLine(
    spans: List<MdSpan>,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(spans, linkColor) { annotate(spans, linkColor) }
    Text(text = annotated, style = style, color = color, modifier = modifier)
}

/** Parcalari tek bir [AnnotatedString]'e cevirir; renk/harf bicimi stil katmanidir. */
private fun annotate(spans: List<MdSpan>, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        spans.forEach { span ->
            val spanStyle = SpanStyle(
                fontWeight = if (MdStyle.BOLD in span.styles) FontWeight.Bold else null,
                fontStyle = if (MdStyle.ITALIC in span.styles) FontStyle.Italic else null,
                textDecoration = when {
                    MdStyle.STRIKE in span.styles -> TextDecoration.LineThrough
                    span.link != null -> TextDecoration.Underline
                    else -> null
                },
                fontFamily = if (MdStyle.CODE in span.styles) FontFamily.Monospace else null,
                color = if (span.link != null) linkColor else Color.Unspecified,
            )
            withStyle(spanStyle) { append(span.text) }
        }
    }
