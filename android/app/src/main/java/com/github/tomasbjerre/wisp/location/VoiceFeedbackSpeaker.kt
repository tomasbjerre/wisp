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
                ready = status == TextToSpeech.SUCCESS
                if (ready) tts?.language = Locale.getDefault()
            }
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
