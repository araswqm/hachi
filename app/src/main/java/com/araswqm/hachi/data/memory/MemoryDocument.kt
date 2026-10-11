package com.araswqm.hachi.data.memory

import com.araswqm.hachi.core.Tuning

/** Tek bir hafiza bolumu: baslik + govde. */
data class MemorySection(val title: String, val body: String) {
    fun estimatedTokens(): Double = (title.length + body.length) / Tuning.CHARS_PER_TOKEN
}

/**
 * `memory.md` dosyasinin yapisal modeli.
 *
 * Dosya uc bolgeden olusur:
 *  1. **Iliski durumu** — `<!-- HACHI:BASLANGIC -->` ve `<!-- HACHI:SON -->`
 *     isaretleri arasinda; her ozetlemede bastan uretilir.
 *  2. **Bolumler** — `## Baslik` ile baslayan, en yenisi en altta biriken bloklar.
 *  3. **Kullanici notlari** — `🔒` ile isaretli, kullanici tarafindan kilitlenmis
 *     satirlar; ozetleyici bunlara dokunmaz.
 *
 * Kullanici dosyayi elle duzenleyebilir; bu yuzden okuyucu toleransli, yazici ise
 * kullanicinin kilitli satirlarini aynen korur.
 */
data class MemoryDocument(
    val relationship: String,
    val sections: List<MemorySection>,
    val lockedNotes: List<String>,
) {

    fun toText(): String = buildString {
        // §2.1/§8.1: iliski blogu yalnizca doluyken yazilir. Bos relation'da iki
        // isaret satiri ust uste gelmesin (bos iskelet dosya olusmasin).
        if (relationship.isNotBlank()) {
            appendLine(START)
            appendLine(relationship.trim())
            appendLine(END)
            appendLine()
        }
        sections.forEach { section ->
            appendLine("## ${section.title}")
            appendLine(section.body.trim())
            appendLine()
        }
        if (lockedNotes.isNotEmpty()) {
            appendLine("## $NOTES_TITLE")
            lockedNotes.forEach { appendLine(it.trimEnd()) }
            appendLine()
        }
    }.trimEnd() + "\n"

    fun estimatedTokens(): Double = toText().length / Tuning.CHARS_PER_TOKEN

    /** Baslik + govdeyi tek bir [MemorySection] olarak sona ekler. */
    fun appending(title: String, body: String): MemoryDocument =
        copy(sections = sections + MemorySection(title, body))

    /**
     * §2.1: bozuk/silinebilir bir hafizayi bastan kurar. Kilitli kullanici
     * notlari ([lockedNotes]) varsayilan olarak korunur; [notes] verilirse onlar
     * kullanilir.
     */
    fun rebuild(
        sections: List<MemorySection>,
        relationship: String,
        notes: List<String> = lockedNotes,
    ): MemoryDocument = MemoryDocument(relationship = relationship, sections = sections, lockedNotes = notes)

    /** Verilen basliktaki bolumu yenisiyle degistirir; yoksa sona ekler. */
    fun withSection(title: String, body: String): MemoryDocument {
        val idx = sections.indexOfFirst { it.title == title }
        if (idx < 0) return appending(title, body)
        val updated = sections.toMutableList()
        updated[idx] = MemorySection(title, body)
        return copy(sections = updated)
    }

    /**
     * Token ust siniri asildiysa eski bolumleri tek bir "Uzun Vadeli Özet"
     * bloguna sikistirir; en yeni bolumler oldugu gibi kalir.
     */
    fun compressed(): MemoryDocument {
        if (estimatedTokens() <= Tuning.MEMORY_COMPRESS_THRESHOLD_TOKENS) return this

        val remaining = sections.toMutableList()
        val longTerm = StringBuilder()

        val idx = remaining.indexOfFirst { it.title == LONG_TERM_TITLE }
        if (idx >= 0) {
            longTerm.append(remaining.removeAt(idx).body.trim())
        }

        fun remainingTokens(): Double =
            remaining.sumOf { it.estimatedTokens() } + longTerm.length / Tuning.CHARS_PER_TOKEN

        while (remaining.size > 1 && remainingTokens() > Tuning.MEMORY_COMPRESS_KEEP_RECENT_TOKENS) {
            val oldest = remaining.removeAt(0)
            if (oldest.title == LONG_TERM_TITLE) continue
            if (longTerm.isNotEmpty()) longTerm.append('\n')
            longTerm.append(oldest.body.trim())
        }

        val newSections = if (longTerm.isNotEmpty()) {
            listOf(MemorySection(LONG_TERM_TITLE, longTerm.toString().trim())) + remaining
        } else {
            remaining
        }
        return copy(sections = newSections)
    }

    companion object {
        const val START = "<!-- HACHI:BASLANGIC -->"
        const val END = "<!-- HACHI:SON -->"

        // Basliklar Ingilizcedir: hafiza dosyasi prompt'a oldugu gibi girer ve
        // §2.2 geregi daima Ingilizce tutulur (arayuz dili ne olursa olsun).
        const val NOTES_TITLE = "User Notes"
        const val LONG_TERM_TITLE = "Long-Term Summary"

        fun empty(): MemoryDocument = MemoryDocument("", emptyList(), emptyList())

        /** Toleransli ayristirma: beklenen isaretler yoksa tumunu iliski blogu sayar. */
        fun parse(text: String): MemoryDocument {
            if (text.isBlank()) return empty()

            val lines = text.lines()
            val startIdx = lines.indexOfFirst { it.trim() == START }
            val endIdx = lines.indexOfFirst { it.trim() == END }

            val relationship: String
            val tail: List<String>
            if (startIdx >= 0 && endIdx > startIdx) {
                relationship = lines.subList(startIdx + 1, endIdx).joinToString("\n").trim()
                tail = lines.subList(endIdx + 1, lines.size)
            } else {
                relationship = ""
                tail = lines
            }

            val sections = mutableListOf<MemorySection>()
            val notes = mutableListOf<String>()
            var title: String? = null
            val body = StringBuilder()

            fun flush() {
                val t = title ?: return
                val b = body.toString().trim()
                if (t == NOTES_TITLE) {
                    b.lines().filter { it.isNotBlank() }.forEach { notes.add(it) }
                } else if (b.isNotEmpty()) {
                    sections.add(MemorySection(t, b))
                }
                body.clear()
            }

            tail.forEach { line ->
                val header = line.trim()
                if (header.startsWith("## ")) {
                    flush()
                    title = header.removePrefix("## ").trim()
                } else if (title != null) {
                    body.appendLine(line)
                }
            }
            flush()

            return MemoryDocument(relationship, sections, notes)
        }
    }
}
