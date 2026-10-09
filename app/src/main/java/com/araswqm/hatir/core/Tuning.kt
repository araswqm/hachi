package com.araswqm.hatir.core

/**
 * Uygulamadaki ayarlanabilir tum sayilar burada.
 *
 * Kural: Sihirli sayi baska hicbir dosyada bulunmaz. Bir esik, agirlik, sure veya
 * limit degisecekse SADECE bu dosya duzenlenir. Amac, davranisi tek yerden
 * ayarlayabilmek ve ileride "acaba nerede 0.8 yaziyordu?" aramasini bitirmek.
 *
 * Not: Bu nesne saf veri; hicbir sey hesaplamaz.
 */
object Tuning {

    // ---------------------------------------------------------------------
    // Sohbet + istem (prompt) kurulumu
    // ---------------------------------------------------------------------

    /** Prompt'a "son N mesaj" olarak girecek mesaj sayisi. */
    const val RECENT_MESSAGES_IN_PROMPT = 20

    /**
     * Son mesajdan bu kadar saat gectiyse, kullanicinin son mesajina
     * "[son mesajdan X saat sonra]" notu eklenir. Veritabani'na YAZILMAZ.
     */
    const val TIME_GAP_NOTICE_HOURS = 1

    /** Bir gundeki saat sayisi (gun<->saat cevrimleri icin). */
    const val HOURS_PER_DAY = 24L

    /** Varsayilan yaratıcılık (temperature). Karakter bazinda degistirilebilir. */
    const val DEFAULT_TEMPERATURE = 1.0f

    /** Karakter bazli yaratıcılık icin izin verilen aralik. */
    const val MIN_TEMPERATURE = 0.0f
    const val MAX_TEMPERATURE = 2.0f

    /**
     * Varsayilan en fazla yanit uzunlugu (token). Dusunme (thinking) modu kapali
     * oldugu icin bu tamamen gorunur yanita gider.
     */
    const val DEFAULT_MAX_TOKENS = 1024

    /** Düzenleyicide en fazla yanıt uzunluğu alanı için izin verilen aralık. */
    const val MIN_MAX_TOKENS = 64
    const val MAX_MAX_TOKENS = 8192

    /** Ozetleyici ve karar cagrilari icin daha kisa yanit yeterli. */
    const val SUMMARY_MAX_TOKENS = 2048
    const val DECISION_MAX_TOKENS = 512

    /**
     * Ozetleyici ve karar cagrilari daha tutarli cikti versin diye
     * yaraticilik dusuk tutulur (sohbet sicakligi karakter bazinda).
     */
    const val SUMMARY_TEMPERATURE = 0.4f
    const val DECISION_TEMPERATURE = 0.6f

    /** Dusunme modu kapali oldugu icin top_p sabittir (1.0); istemde gonderilmez. */
    const val TOP_P_FIXED = 1.0

    // ---------------------------------------------------------------------
    // Varsayilan model / sunucu
    // ---------------------------------------------------------------------

    const val DEFAULT_BASE_URL = "https://api.deepseek.com"

    /**
     * Not: "deepseek-chat" ve "deepseek-reasoner" 24 Temmuz 2026'da kapatildi.
     * Aktif modeller: deepseek-flash (V4.1 Flash, 1M baglam) ve deepseek-v4-pro.
     * Uc kullanim da varsayilan olarak flash kullanir.
     */
    const val DEFAULT_MODEL_CHAT = "deepseek-flash"
    const val DEFAULT_MODEL_SUMMARY = "deepseek-flash"
    const val DEFAULT_MODEL_DECISION = "deepseek-flash"

    val KNOWN_MODELS = listOf("deepseek-flash", "deepseek-v4-pro")

    // ---------------------------------------------------------------------
    // Hafiza (.md) sistemi
    // ---------------------------------------------------------------------

    /**
     * Ozetlemenin tetiklenmesi icin gereken mesaj sayisi. "Mesaj" = kullanici +
     * karakter mesajlari toplami; yeniden uretme / kaydirma SAYILMAZ.
     */
    const val SUMMARY_EVERY_MESSAGES = 10

    /**
     * Hafiza dosyasi bu token sayisini astiginda, eski bolumler tek bir
     * "uzun vadeli ozet" bloguna sikistirilir; yeni bolumler oldugu gibi kalir.
     * Token tahmini: karakter sayisi / CHARS_PER_TOKEN.
     */
    const val MEMORY_COMPRESS_THRESHOLD_TOKENS = 4500
    const val MEMORY_COMPRESS_KEEP_RECENT_TOKENS = 2500

    /** Kaba token tahmini icin karakter/token orani (Turkce icin muhafazakar). */
    const val CHARS_PER_TOKEN = 3.0

    /** Hafiza .md dosyasinda kullanici tarafindan kilitlenen satirin isareti. */
    const val MEMORY_LOCK_MARKER = "🔒" // 🔒

    /** Bir ozetleme cagrisina girecek en fazla bekleyen mesaj sayisi. */
    const val MEMORY_PENDING_MESSAGES_LIMIT = 60

    /** Ozetleyicinin urettigi alanlarin kirpilma uzunluklari (karakter). */
    const val MEMORY_CHARACTER_FEELING_MAX_CHARS = 500
    const val MEMORY_FOLLOWUP_TEXT_MAX_CHARS = 300

    /**
     * Ham yakinlik gecmisinin saklanan uzunlugu, hareketli ortalama
     * penceresinin kac kati olacak.
     */
    const val MEMORY_RAW_HISTORY_RETENTION_MULTIPLIER = 3

    /**
     * `MemoryStateEntity.chatState` icinde tasinan "sohbet nerede kaldi"
     * aciklamasinin ust siniri (karakter).
     */
    const val CHAT_STATE_MAX_DESCRIPTION_CHARS = 500

    // ---------------------------------------------------------------------
    // Gizli yakinlik (intimacy) motoru
    // ---------------------------------------------------------------------

    /** Nihai puan = OZET_AGIRLIGI * ozetOrtalamasi + SES_AGIRLIGI * hacimBileseni. */
    const val INTIMACY_SUMMARY_WEIGHT = 0.80
    const val INTIMACY_VOLUME_WEIGHT = 0.20

    /** Ozetleyicinin urettigi ham yakinlik puaninin aldigi aralik. */
    const val INTIMACY_RAW_MIN = 0
    const val INTIMACY_RAW_MAX = 100

    /** Hareketli ortalama: son kac ozet bolumunun ortalamasi alinir. */
    const val INTIMACY_MOVING_AVERAGE_WINDOW = 5

    /** Konusma olmayan her gun icin puan bu kadar duser. */
    const val INTIMACY_DAILY_DECAY = 2.0

    /** Seviye esikleri (alt sinir dahil, ust sinir haric). */
    const val LEVEL_FAMILIAR_MIN = 25.0   // Tanıdık
    const val LEVEL_FRIEND_MIN = 50.0     // Arkadaş
    const val LEVEL_CLOSE_MIN = 75.0      // Yakın

    /**
     * Hacim bileseni: son pencerede kac mesaj "dolu" sayilir. Uzun ama mesafeli
     * sohbetlerin yuksek puan almamasi icin hacim yalnizca %20 etkiler; asil
     * belirleyici ozetleyicinin ham puanidir.
     */
    const val INTIMACY_VOLUME_FULL_MESSAGES = 200
    const val INTIMACY_STREAK_FULL_DAYS = 14

    // ---------------------------------------------------------------------
    // Kendiliğinden mesajlar (proactive)
    // ---------------------------------------------------------------------

    /** Periyodik kapı (gate) isinin araligi. WorkManager min 15 dk; Doze kaydirir. */
    const val PROACTIVE_CHECK_INTERVAL_MINUTES = 60L

    /** WorkManager periyodik is icin zorunlu alt sinir. */
    const val WORK_MIN_PERIOD_MINUTES = 15L

    /** Varsayilan rahatsız etme (DND) penceresi, cihaz saat diliminde. */
    const val DND_START_HOUR = 23
    const val DND_END_HOUR = 9

    /** Bu kadar gun konusulmadiysa, karakter artik kendiliğinden yazmaz. */
    const val PROACTIVE_MAX_SILENCE_DAYS = 7

    /**
     * Konusma "dogal kapandi" olarak isaretlendiyse, kapanisin hemen ardindan
     * yazmamak icin beklenen en az sure. (Vadesi gelmis bir takip konusu bu
     * bekleyisi gecersiz kilar; diger kurallar yine gecerli.)
     */
    const val PROACTIVE_CLOSED_CHAT_WAIT_HOURS = 6

    /** Gunluk toplam ve karakter-basi gonderim limitleri. */
    const val PROACTIVE_DAILY_LIMIT_TOTAL = 6
    const val PROACTIVE_DAILY_LIMIT_PER_CHARACTER = 2

    /**
     * Karakterin yazdigi son kendiliğinden mesaj yanitlanmadiysa, bir sonraki
     * kontrol araligi katlanarak buyur (bkz. PROACTIVE_BACKOFF_*).
     */
    const val PROACTIVE_BACKOFF_BASE_HOURS = 6.0
    const val PROACTIVE_BACKOFF_MULTIPLIER = 2.0
    const val PROACTIVE_BACKOFF_MAX_HOURS = 168.0 // 7 gun

    /** LLM'in next_check_hours icin izin verilen sinirlar. */
    const val PROACTIVE_NEXT_CHECK_MIN_HOURS = 0.5
    const val PROACTIVE_NEXT_CHECK_MAX_HOURS = 72.0

    /** Sadece bu seviye ve uzeri karakterler kendiliğinden yazabilir. */
    const val PROACTIVE_MIN_LEVEL = "Arkadaş"

    /** Hata ayiklama kaydindaki `reason` alaninin kirpilma uzunlugu. */
    const val PROACTIVE_LOG_REASON_MAX_CHARS = 500

    // ---------------------------------------------------------------------
    // Karakter karti (PNG) okuyucu
    // ---------------------------------------------------------------------

    /** PNG metin yigini icin makul ust sinir; kotu niyetli/bozuk dosyaya karsi. */
    const val PNG_CHUNK_SIZE_LIMIT_BYTES = 16 * 1024 * 1024
    const val PNG_TEXT_DECODE_LIMIT_BYTES = 8 * 1024 * 1024

    // ---------------------------------------------------------------------
    // Ag / istek davranisi
    // ---------------------------------------------------------------------

    const val HTTP_CONNECT_TIMEOUT_SECONDS = 30L
    const val HTTP_READ_TIMEOUT_SECONDS = 120L
    const val HTTP_WRITE_TIMEOUT_SECONDS = 30L

    /** Bos veya cozulemeyen JSON yaniti icin kac kez daha denenecek. */
    const val JSON_RETRY_COUNT = 1

    /** SSE akisinda ust sinir; takilan baglantiyi kesmek icin. */
    const val STREAM_MAX_DURATION_SECONDS = 300L

    /** Hata mesajlarina gomulen sunucu detayi / ham yanitin kirpilma uzunlugu. */
    const val AI_ERROR_DETAIL_MAX_CHARS = 300
    const val AI_ERROR_RAW_MAX_CHARS = 200

    // ---------------------------------------------------------------------
    // Bildirim ve arka plan
    // ---------------------------------------------------------------------

    const val NOTIFICATION_ID_BASE = 1000

    /** Hata ayiklama ekraninda listelenen kayit sayisi. */
    const val DEBUG_LOG_LIMIT = 50

    /** Gizli hata ayiklama ekranini acmak icin surum satirina dokunma sayisi. */
    const val DEBUG_UNLOCK_TAPS = 5

    /** onStop ozetlemesi icin OneTimeWorkRequest backoff ayarlari. */
    const val ONSTOP_SUMMARY_BACKOFF_SECONDS = 30L
    const val ONSTOP_SUMMARY_MAX_ATTEMPTS = 5
}
