package com.github.tomasbjerre.wisp.location

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Thin wrapper around Android's on-device text-to-speech engine — see
 * specs/voice-feedback.md. Not unit tested directly, same as [LocationTracker]/
 * [StepCounterTracker]: it's a live platform API with nothing to assert against
 * outside a real device. [VoiceFeedbackAnnouncement] carries the testable logic
 * (what to say and when), this class only ever speaks the text it's handed.
 */
class VoiceFeedbackSpeaker(
    context: Context,
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts =
            TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS && setEnglishVoice()
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
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
