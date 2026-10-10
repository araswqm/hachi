package com.araswqm.hachi.data.memory

import com.araswqm.hachi.core.Tuning

/**
 * `MemoryStateEntity.chatState` alaninin kodlamasi.
 *
 * Sema degistirmeden (DB surumu 1 kalir) "sohbet kapandi mi?" bilgisini ve
 * konusmanin nerede kaldigina dair kisa aciklamayi birlikte tasir.
 *
 * Bicim: `"closed|aciklama"` veya `"open|aciklama"`; aciklama bos olabilir.
 * Kodlar Ingilizcedir (§2.2: kalici hafiza verisi daima Ingilizce).
 */
data class ChatState(
    val closed: Boolean,
    val description: String,
) {

    fun encode(): String {
        val flag = if (closed) CLOSED else OPEN
        val desc = description.trim().replace('|', ' ').take(Tuning.CHAT_STATE_MAX_DESCRIPTION_CHARS)
        return if (desc.isEmpty()) flag else "$flag|$desc"
    }

    companion object {
        const val CLOSED = "closed"
        const val OPEN = "open"

        /** Toleransli cozumleme: taninmayan bicimde "open" varsayilir. */
        fun decode(raw: String): ChatState {
            if (raw.isBlank()) return ChatState(closed = false, description = "")
            val idx = raw.indexOf('|')
            val flag = if (idx >= 0) raw.substring(0, idx).trim() else raw.trim()
            val desc = if (idx >= 0) raw.substring(idx + 1).trim() else ""
            return ChatState(closed = flag == CLOSED, description = desc)
        }
    }
}
