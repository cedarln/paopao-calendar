package com.paopao.calendar.ui.training

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 计时页的语音提示。
 *
 * 只借用系统 TTS，不打包音频文件：小程序在 `assets/audio` 下放的是「开始/休息」两条
 * 预制音效，而中文播报要说清「下一个动作是什么、第几组」，预制音频做不到，
 * 靠几十个 wav 文件把包体积顶上去更不值。
 *
 * 引擎不是每台设备都有（裁剪过的 ROM 会缺，语言包也可能没有中文），所以：
 * 构造、设置语言、播报全部包在 runCatching 里，任何一步失败都只是「没有播报」——
 * 用户按了「开始」之后，没有任何理由因为一句提示音而看到崩溃或卡住的计时器。
 */
class WorkoutCue(context: Context) {

    private var ready = false
    private var engine: TextToSpeech? = null

    init {
        runCatching {
            engine = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val tts = engine
                    val available = runCatching { tts?.isLanguageAvailable(Locale.CHINA) }
                        .getOrNull() != TextToSpeech.LANG_NOT_SUPPORTED
                    if (available) {
                        runCatching { tts?.language = Locale.CHINA }
                        ready = true
                    }
                }
            }
        }
    }

    fun speak(text: String) {
        if (!ready) return
        runCatching { engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "paopao_cue") }
    }

    fun release() {
        runCatching {
            engine?.stop()
            engine?.shutdown()
        }
        engine = null
        ready = false
    }
}
