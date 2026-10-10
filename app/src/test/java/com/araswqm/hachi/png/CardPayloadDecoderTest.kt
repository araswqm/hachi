package com.araswqm.hachi.png

import com.araswqm.hachi.core.png.CardPayloadDecoder
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardPayloadDecoderTest {

    private val json = "{\"name\":\"Elif\",\"desc\":\"cok uzun\"}"

    @Test
    fun `standart base64 cozulur`() {
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray())

        assertEquals(json, CardPayloadDecoder.decode(encoded))
    }

    @Test
    fun `dolgusuz base64 cozulur`() {
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray()).trimEnd('=')

        assertEquals(json, CardPayloadDecoder.decode(encoded))
    }

    @Test
    fun `url-safe base64 cozulur`() {
        val tricky = "{\"x\":\"??>>??\"}"
        val encoded = Base64.getUrlEncoder().encodeToString(tricky.toByteArray())

        assertEquals(tricky, CardPayloadDecoder.decode(encoded))
    }

    @Test
    fun `satir sonlari olan base64 cozulur`() {
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray())
        val wrapped = encoded.chunked(16).joinToString("\n")

        assertEquals(json, CardPayloadDecoder.decode(wrapped))
    }

    @Test
    fun `dogrudan JSON oldugu gibi doner`() {
        assertEquals(json, CardPayloadDecoder.decode("  $json  "))
    }

    @Test
    fun `bos girdi null doner`() {
        assertNull(CardPayloadDecoder.decode(""))
        assertNull(CardPayloadDecoder.decode("   \n "))
    }

    @Test
    fun `cozulemeyen girdi null doner`() {
        assertNull(CardPayloadDecoder.decode("!!!bu base64 degil!!!"))
    }

    @Test
    fun `base64 ama JSON olmayan icerik null doner`() {
        val encoded = Base64.getEncoder().encodeToString("sadece duz metin".toByteArray())

        assertNull(CardPayloadDecoder.decode(encoded))
    }
}
