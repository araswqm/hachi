package com.araswqm.hatir.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceholdersTest {

    @Test
    fun `char yer tutucusu karakter adiyla degisir`() {
        assertEquals(
            "Merhaba Ayşe.",
            Placeholders.resolve("Merhaba {{char}}.", "Ayşe", null),
        )
    }

    @Test
    fun `user yer tutucusu persona adiyla degisir`() {
        assertEquals(
            "Nasılsın Mehmet?",
            Placeholders.resolve("Nasılsın {{user}}?", "Ayşe", "Mehmet"),
        )
    }

    @Test
    fun `persona da user gibi cozulur`() {
        assertEquals("Selam Mehmet", Placeholders.resolve("Selam {{persona}}", "Ayşe", "Mehmet"))
    }

    @Test
    fun `persona adi bos ise varsayilan ad kullanilir`() {
        assertEquals(
            "Selam ${Placeholders.DEFAULT_USER_NAME}",
            Placeholders.resolve("Selam {{user}}", "Ayşe", null),
        )
        assertEquals(
            "Selam ${Placeholders.DEFAULT_USER_NAME}",
            Placeholders.resolve("Selam {{user}}", "Ayşe", "   "),
        )
    }

    @Test
    fun `bosluklu ve buyuk harfli yazim da yakalanir`() {
        assertEquals("A — B", Placeholders.resolve("{{ CHAR }} — {{User}}", "A", "B"))
    }

    @Test
    fun `ayni metinde birden fazla yer tutucu cozulur`() {
        assertEquals(
            "Ayşe Mehmet'e, Mehmet Ayşe'ye",
            Placeholders.resolve("{{char}} {{user}}'e, {{user}} {{char}}'ye", "Ayşe", "Mehmet"),
        )
    }

    @Test
    fun `yer tutucu yoksa metin aynen doner`() {
        val text = "Düz bir cümle, hiç yer tutucu yok."
        assertEquals(text, Placeholders.resolve(text, "Ayşe", "Mehmet"))
    }

    @Test
    fun `bilinmeyen yer tutucu degismeden kalir`() {
        assertEquals("{{foo}} bar", Placeholders.resolve("{{foo}} bar", "Ayşe", "Mehmet"))
    }

    @Test
    fun `cozulmus metni tekrar cozmek zararsizdir`() {
        val once = Placeholders.resolve("{{char}} ve {{user}}", "Ayşe", "Mehmet")
        assertEquals(once, Placeholders.resolve(once, "Ayşe", "Mehmet"))
    }

    @Test
    fun `bos metin bos doner`() {
        assertEquals("", Placeholders.resolve("", "Ayşe", "Mehmet"))
    }
}
