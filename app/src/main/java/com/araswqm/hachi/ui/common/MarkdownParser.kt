package com.araswqm.hachi.ui.common

/**
 * Hafif Markdown ayristirici.
 *
 * **Neden kutuphane yok:** Derleme yalnizca CI'da yapiliyor ve AGP 9 /
 * compileSdk 37 hattinda yeni bir AAR bagimliligi (AAR metadata) riski
 * tasiyor. Bu yuzden karakter kartlarinda ve sohbet balonlarinda gecen
 * Markdown'in kucuk bir alt kumesi kendimiz ayristirilir.
 *
 * Bu dosya **saf Kotlin**'dir (Android/Compose bagimliligi yok) — bu sayede
 * JVM birim testleriyle dogrulanabilir. Cizim isi [Markdown.kt] icindedir.
 *
 * Desteklenenler:
 *  - Basliklar `#` … `######`
 *  - Yatay cizgi `---`, `***`, `___`
 *  - Madde isaretli liste `-`, `*`, `+`
 *  - Numarali liste `1.`, `2)`
 *  - Alinti `>`
 *  - Kod bloklari ``` ```
 *  - Satir ici: `kod`, **kalin**, __kalin__, *italik*, _italik_, ~~ustu cizili~~,
 *    [metin](adres), `\` ile kacis
 */

/** Satir ici bicim. */
enum class MdStyle { BOLD, ITALIC, STRIKE, CODE }

/** Bicimlendirilmis metin parcasi. [link] doluysa [text] tiklanabilir etikettir. */
data class MdSpan(
    val text: String,
    val styles: Set<MdStyle> = emptySet(),
    val link: String? = null,
)

/** Blok duzeyi oge. */
sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Bullet(val items: List<List<MdSpan>>) : MdBlock
    data class Ordered(val start: Int, val items: List<List<MdSpan>>) : MdBlock
    data class Quote(val spans: List<MdSpan>) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {

    private val HEADING = Regex("^#{1,6}\\s+.*$")
    private val HEADING_CAPTURE = Regex("^(#{1,6})\\s+(.*)$")
    private val RULE = Regex("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$")
    private val BULLET = Regex("^\\s*[-*+]\\s+(.*)$")
    private val ORDERED = Regex("^\\s*(\\d{1,9})[.)]\\s+(.*)$")
    private val FENCE = Regex("^\\s*```\\s*([^`]*)$")
    private val QUOTE = Regex("^\\s*>\\s?(.*)$")

    private val ESCAPABLE = setOf('\\', '`', '*', '_', '~', '[', ']', '(', ')')

    /** Metni bloklara ayirir; bos girdi bos liste doner. */
    fun parse(text: String): List<MdBlock> {
        if (text.isBlank()) return emptyList()

        val lines = text.lines()
        val blocks = ArrayList<MdBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            if (line.isBlank()) {
                i++
                continue
            }

            // Kod bloğu: ``` ile acilir, ``` ile kapanir (kapanmazsa dosya sonuna kadar).
            val fence = FENCE.matchEntire(line)
            if (fence != null) {
                val language = fence.groupValues[1].trim()
                val body = StringBuilder()
                i++
                while (i < lines.size && FENCE.matchEntire(lines[i]) == null) {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[i])
                    i++
                }
                if (i < lines.size) i++ // kapanis fence'i
                blocks.add(MdBlock.Code(language, body.toString()))
                continue
            }

            if (RULE.matches(line)) {
                blocks.add(MdBlock.Rule)
                i++
                continue
            }

            val heading = HEADING_CAPTURE.matchEntire(line)
            if (heading != null) {
                blocks.add(
                    MdBlock.Heading(
                        level = heading.groupValues[1].length,
                        spans = parseInline(heading.groupValues[2].trim()),
                    ),
                )
                i++
                continue
            }

            if (QUOTE.matches(line)) {
                val joined = StringBuilder()
                while (i < lines.size) {
                    val m = QUOTE.matchEntire(lines[i]) ?: break
                    if (joined.isNotEmpty()) joined.append('\n')
                    joined.append(m.groupValues[1])
                    i++
                }
                blocks.add(MdBlock.Quote(parseInline(joined.toString())))
                continue
            }

            if (BULLET.matches(line)) {
                val items = ArrayList<List<MdSpan>>()
                while (i < lines.size) {
                    val m = BULLET.matchEntire(lines[i]) ?: break
                    items.add(parseInline(m.groupValues[1].trim()))
                    i++
                }
                blocks.add(MdBlock.Bullet(items))
                continue
            }

            val orderedFirst = ORDERED.matchEntire(line)
            if (orderedFirst != null) {
                val start = orderedFirst.groupValues[1].toIntOrNull() ?: 1
                val items = ArrayList<List<MdSpan>>()
                while (i < lines.size) {
                    val m = ORDERED.matchEntire(lines[i]) ?: break
                    items.add(parseInline(m.groupValues[2].trim()))
                    i++
                }
                blocks.add(MdBlock.Ordered(start, items))
                continue
            }

            // Paragraf: bos satira ya da yeni bir blok baslangicina kadar surer.
            val para = StringBuilder()
            while (i < lines.size) {
                val l = lines[i]
                if (l.isBlank() || startsBlock(l)) break
                if (para.isNotEmpty()) para.append('\n')
                para.append(l.trim())
                i++
            }
            if (para.isNotEmpty()) blocks.add(MdBlock.Paragraph(parseInline(para.toString())))
        }

        return blocks
    }

    /** Bir satirin yeni bir blok baslatip baslatmadigi (paragrafi boler). */
    private fun startsBlock(line: String): Boolean =
        FENCE.matchEntire(line) != null ||
            RULE.matches(line) ||
            HEADING.matches(line) ||
            QUOTE.matches(line) ||
            BULLET.matches(line) ||
            ORDERED.matches(line)

    /**
     * Satir ici bicimleri cozer. Eslesmeyen bir isaret (or. tek `*`) duz metin
     * olarak kalir; boylece "3 * 4" gibi ifadeler bozulmaz.
     */
    fun parseInline(text: String): List<MdSpan> {
        if (text.isEmpty()) return emptyList()

        val spans = ArrayList<MdSpan>()
        val sb = StringBuilder()
        val styles = LinkedHashSet<MdStyle>()
        var i = 0

        fun flush() {
            if (sb.isNotEmpty()) {
                spans.add(MdSpan(sb.toString(), styles.toSet()))
                sb.setLength(0)
            }
        }

        while (i < text.length) {
            val c = text[i]

            // Kacis: `\*` duz yildiz olur.
            if (c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE) {
                sb.append(text[i + 1])
                i += 2
                continue
            }

            // Kod aralığı `...` — icindeki bicimler yorumlanmaz.
            if (c == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    flush()
                    spans.add(MdSpan(text.substring(i + 1, end), styles.toSet() + MdStyle.CODE))
                    i = end + 1
                    continue
                }
            }

            // Baglanti [metin](adres)
            if (c == '[') {
                val close = text.indexOf(']', i + 1)
                if (close > i && close + 1 < text.length && text[close + 1] == '(') {
                    val urlEnd = text.indexOf(')', close + 2)
                    if (urlEnd > close + 1) {
                        flush()
                        val url = text.substring(close + 2, urlEnd).trim()
                        parseInline(text.substring(i + 1, close))
                            .forEach { spans.add(it.copy(link = url)) }
                        i = urlEnd + 1
                        continue
                    }
                }
            }

            val two = i + 1 < text.length && text[i + 1] == c
            val style = when {
                two && (c == '*' || c == '_') -> MdStyle.BOLD
                two && c == '~' -> MdStyle.STRIKE
                c == '*' || c == '_' -> MdStyle.ITALIC
                else -> null
            }

            if (style != null) {
                val delim = if (two && style != MdStyle.ITALIC) 2 else 1
                val marker = text.substring(i, i + delim)
                val closing = style in styles
                // Kelime ortasindaki tek alt cizgi vurgu baslatmaz (snake_case bozulmasin).
                val intraword = !closing && c == '_' && delim == 1 &&
                    i > 0 && text[i - 1].isLetterOrDigit()
                val hasPartner = closing || text.substring(i + delim).contains(marker)

                if (!intraword && hasPartner) {
                    flush()
                    if (closing) styles.remove(style) else styles.add(style)
                    i += delim
                    continue
                }
            }

            sb.append(c)
            i++
        }

        flush()
        return spans
    }
}
