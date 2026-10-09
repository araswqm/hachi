package com.araswqm.hatir.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    // ------------------------------------------------------------------
    // Bloklar
    // ------------------------------------------------------------------

    @Test
    fun `bos metin bos blok listesi verir`() {
        assertTrue(MarkdownParser.parse("").isEmpty())
        assertTrue(MarkdownParser.parse("   \n  ").isEmpty())
    }

    @Test
    fun `duz metin tek paragraf olur`() {
        val blocks = MarkdownParser.parse("Merhaba dünya")
        assertEquals(1, blocks.size)
        val p = blocks[0] as MdBlock.Paragraph
        assertEquals("Merhaba dünya", p.spans.single().text)
        assertTrue(p.spans.single().styles.isEmpty())
    }

    @Test
    fun `bos satir paragrafi boler`() {
        val blocks = MarkdownParser.parse("bir\n\niki")
        assertEquals(2, blocks.size)
        assertEquals("bir", (blocks[0] as MdBlock.Paragraph).spans.single().text)
        assertEquals("iki", (blocks[1] as MdBlock.Paragraph).spans.single().text)
    }

    @Test
    fun `baslik seviyesi ve metni dogru okunur`() {
        val blocks = MarkdownParser.parse("# Başlık")
        val h = blocks.single() as MdBlock.Heading
        assertEquals(1, h.level)
        assertEquals("Başlık", h.spans.single().text)

        val h3 = MarkdownParser.parse("### Üç").single() as MdBlock.Heading
        assertEquals(3, h3.level)
    }

    @Test
    fun `yatay cizgi kurali taninir`() {
        assertEquals(MdBlock.Rule, MarkdownParser.parse("---").single())
        assertEquals(MdBlock.Rule, MarkdownParser.parse("***").single())
    }

    @Test
    fun `madde isaretli liste maddeleri toplanir`() {
        val blocks = MarkdownParser.parse("- bir\n- iki\n- üç")
        val b = blocks.single() as MdBlock.Bullet
        assertEquals(3, b.items.size)
        assertEquals("bir", b.items[0].single().text)
        assertEquals("üç", b.items[2].single().text)
    }

    @Test
    fun `numarali liste baslangic degerini korur`() {
        val blocks = MarkdownParser.parse("3. üç\n4. dört")
        val o = blocks.single() as MdBlock.Ordered
        assertEquals(3, o.start)
        assertEquals(2, o.items.size)
        assertEquals("dört", o.items[1].single().text)
    }

    @Test
    fun `alinti blogu toplanir`() {
        val blocks = MarkdownParser.parse("> ilk\n> ikinci")
        val q = blocks.single() as MdBlock.Quote
        assertEquals("ilk\nikinci", q.spans.joinToString("") { it.text })
    }

    @Test
    fun `kod bloku kapanisa kadar toplanir`() {
        val blocks = MarkdownParser.parse("```\nval x = 1\n```")
        val c = blocks.single() as MdBlock.Code
        assertEquals("", c.language)
        assertEquals("val x = 1", c.code)
    }

    @Test
    fun `kod bloku dil etiketini alir`() {
        val c = MarkdownParser.parse("```kotlin\n1\n```").single() as MdBlock.Code
        assertEquals("kotlin", c.language)
    }

    // ------------------------------------------------------------------
    // Satir ici
    // ------------------------------------------------------------------

    @Test
    fun `kalin metin isaretlenir`() {
        val spans = MarkdownParser.parseInline("**kalın**")
        val span = spans.single()
        assertEquals("kalın", span.text)
        assertEquals(setOf(MdStyle.BOLD), span.styles)
    }

    @Test
    fun `tek yildiz italik isaretler`() {
        val span = MarkdownParser.parseInline("*italik*").single()
        assertEquals("italik", span.text)
        assertEquals(setOf(MdStyle.ITALIC), span.styles)
    }

    @Test
    fun `ustu cizili metin isaretlenir`() {
        val span = MarkdownParser.parseInline("~~sil~~").single()
        assertEquals("sil", span.text)
        assertEquals(setOf(MdStyle.STRIKE), span.styles)
    }

    @Test
    fun `satir ici kod isaretlenir ve ici yorumlanmaz`() {
        val span = MarkdownParser.parseInline("`a*b`").single()
        assertEquals("a*b", span.text)
        assertEquals(setOf(MdStyle.CODE), span.styles)
    }

    @Test
    fun `baglanti adres bilgisini tasir`() {
        val span = MarkdownParser.parseInline("[Açık](https://example.com)").single()
        assertEquals("Açık", span.text)
        assertEquals("https://example.com", span.link)
    }

    @Test
    fun `eslesmeyen yildiz duz metin kalir`() {
        val spans = MarkdownParser.parseInline("3 * 4")
        assertEquals("3 * 4", spans.joinToString("") { it.text })
        assertTrue(spans.all { it.styles.isEmpty() })
    }

    @Test
    fun `kacis isareti bicimi etkisiz kilar`() {
        val span = MarkdownParser.parseInline("\\*düz\\*").single()
        assertEquals("*düz*", span.text)
        assertTrue(span.styles.isEmpty())
    }

    @Test
    fun `kelime ortasindaki alt cizgi italik baslatmaz`() {
        val spans = MarkdownParser.parseInline("snake_case")
        assertEquals("snake_case", spans.joinToString("") { it.text })
        assertTrue(spans.all { it.styles.isEmpty() })
    }

    @Test
    fun `karisik satir dogru parcalanir`() {
        val spans = MarkdownParser.parseInline("**kalın** ve normal")
        assertEquals("kalın", spans[0].text)
        assertEquals(setOf(MdStyle.BOLD), spans[0].styles)
        assertEquals(" ve normal", spans[1].text)
        assertTrue(spans[1].styles.isEmpty())
    }

    @Test
    fun `bos satir ici bos liste verir`() {
        assertTrue(MarkdownParser.parseInline("").isEmpty())
    }
}
