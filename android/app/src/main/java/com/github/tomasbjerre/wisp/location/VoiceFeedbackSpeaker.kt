package com.github.tomasbjerre.wisp.location

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Thin wrapper around Android's on-device text-to-speech engine — see
 * specs/voice-feedback.md. Not unit tested directly, same as [LocationTracker]/
 * [StepCounterTracker]: it's a live platform API with nothing to assert against
 * outside a real device. [VoiceFeedbackAnnouncement] carries the testable logic
 * (what to say and when), this class only ever speaks the text it's handed — and,
 * per specs/voice-feedback.md#ducking-other-audio, asks for the audio output around
 * each one it actually speaks.
 */
class VoiceFeedbackSpeaker(
    context: Context,
) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private var tts: TextToSpeech? = null
    private var ready = false
    private var duckingRequest: AudioFocusRequest? = null

    init {
        tts =
            TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS && setEnglishVoice()
                if (ready) tts?.setOnUtteranceProgressListener(DuckingListener())
            }
    }

    /**
     * See specs/voice-feedback.md#language: the phrases [VoiceFeedbackAnnouncement] builds are
     * always English text (issue #185), so the engine's voice has to be pinned to English too,
     * regardless of the device's own language — leaving this as the device default (as it used
     * to be) meant a non-English device spoke English words through a voice built for a
     * different language's pronunciation and number-reading rules, which is exactly the "some
     * parts sound like my language, some don't" inconsistency reported there.
     *
     * False when no English voice is installed at all — the same "nothing usable to speak
     * through" case as no engine being installed. Any non-negative result from `setLanguage`
     * counts (`LANG_AVAILABLE`/`LANG_COUNTRY_AVAILABLE`/`LANG_COUNTRY_VAR_AVAILABLE`): the
     * engine only has to be able to say English words intelligibly, not match [Locale.US] down
     * to the variant.
     */
    private fun setEnglishVoice(): Boolean {
        val result = tts?.setLanguage(Locale.US) ?: return false
        return result >= TextToSpeech.LANG_AVAILABLE
    }

    /**
     * No-op with no usable engine (init still pending, failed, or none installed) —
     * see specs/voice-feedback.md: voice feedback is a best-effort enhancement, never
     * something that blocks or errors a recording. Queued (not interrupting whatever
     * is already being read), since a kilometer completes at most every few minutes —
     * nothing to ever queue up.
     */
    fun speak(text: String) {
        if (!ready) return
        requestDucking()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    /**
     * See specs/voice-feedback.md#ducking-other-audio (issue #175): a transient request
     * for the audio output, the same one a navigation app makes for a turn-by-turn
     * instruction — released by [DuckingListener] the moment this announcement finishes,
     * however it finishes. What the previous audio does in response (lower its volume,
     * or pause outright) is entirely up to it; Wisp never touches a volume directly and
     * needs no new permission for this. A clean no-op when nothing else is playing.
     */
    private fun requestDucking() {
        val manager = audioManager ?: return
        val request =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                ).build()
        duckingRequest = request
        manager.requestAudioFocus(request)
    }

    /** Idempotent — safe to call from both [shutdown] and [DuckingListener], whichever
     * notices first that there's nothing left to hold the output for. */
    private fun releaseDucking() {
        val request = duckingRequest ?: return
        duckingRequest = null
        audioManager?.abandonAudioFocusRequest(request)
    }

    fun shutdown() {
        tts?.stop()
        releaseDucking()
        tts?.shutdown()
        tts = null
    }

    /** Releases the transient audio request the moment the announcement it was taken
     * for actually finishes — on every path TTS can end an utterance, so it's never
     * held longer than the announcement itself. */
    private inner class DuckingListener : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            // Nothing to do — the request already went out in requestDucking(), before
            // this utterance was even queued.
        }

        override fun onDone(utteranceId: String?) = releaseDucking()

        // Deprecated in favor of onError(String, Int) below, but still abstract on
        // UtteranceProgressListener itself, so still has to be implemented.
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) = releaseDucking()

        override fun onError(
            utteranceId: String?,
            errorCode: Int,
        ) = releaseDucking()

        override fun onStop(
            utteranceId: String?,
            interrupted: Boolean,
        ) = releaseDucking()
    }
}
