package com.linguabridge.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TtsLanguage(val locale: Locale) {
    ENGLISH(Locale.US),
    CHINESE(Locale.SIMPLIFIED_CHINESE),
}

/** Availability of each voice after engine init. MISSING_DATA means the user
 *  must download the offline voice in system TTS settings (one-time, the app
 *  itself stays offline). */
enum class VoiceStatus { CHECKING, READY, MISSING_DATA, UNSUPPORTED }

data class TtsState(
    val engineReady: Boolean = false,
    val english: VoiceStatus = VoiceStatus.CHECKING,
    val chinese: VoiceStatus = VoiceStatus.CHECKING,
    val speaking: Boolean = false,
)

/**
 * Thin wrapper over the platform [TextToSpeech]. One engine instance,
 * language switched per utterance. All methods are main-thread safe.
 */
class TtsManager(context: Context) {

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state

    private val utteranceSeq = AtomicInteger()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            _state.value = TtsState(
                engineReady = true,
                english = checkVoice(TtsLanguage.ENGLISH),
                chinese = checkVoice(TtsLanguage.CHINESE),
            )
        } else {
            _state.value = TtsState(
                engineReady = false,
                english = VoiceStatus.UNSUPPORTED,
                chinese = VoiceStatus.UNSUPPORTED,
            )
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = _state.value.copy(speaking = true)
            }

            override fun onDone(utteranceId: String?) {
                _state.value = _state.value.copy(speaking = false)
            }

            @Deprecated("Deprecated in API 21, still required to override")
            override fun onError(utteranceId: String?) {
                _state.value = _state.value.copy(speaking = false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _state.value = _state.value.copy(speaking = false)
            }
        })
    }

    private fun checkVoice(language: TtsLanguage): VoiceStatus =
        when (tts.isLanguageAvailable(language.locale)) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> VoiceStatus.READY
            TextToSpeech.LANG_MISSING_DATA -> VoiceStatus.MISSING_DATA
            else -> VoiceStatus.UNSUPPORTED
        }

    /** Speaks [text], replacing anything currently being spoken. */
    fun speak(text: String, language: TtsLanguage, rate: Float) {
        if (!_state.value.engineReady) return
        tts.language = language.locale
        tts.setSpeechRate(rate.coerceIn(0.7f, 1.2f))
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lb-${utteranceSeq.incrementAndGet()}")
    }

    fun stop() {
        tts.stop()
        _state.value = _state.value.copy(speaking = false)
    }

    fun shutdown() {
        tts.shutdown()
    }

    /** Re-runs voice availability checks (e.g. after the user installed voice data). */
    fun refreshVoices() {
        if (!_state.value.engineReady) return
        _state.value = _state.value.copy(
            english = checkVoice(TtsLanguage.ENGLISH),
            chinese = checkVoice(TtsLanguage.CHINESE),
        )
    }
}
