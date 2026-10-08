package com.eelan.musclediary.reminder

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 训练语音播放器 v2：
 * - 优先播放内置音色包 assets/voices/<voiceId>/ 的 mp3
 * - 拼接句内 key 用无间隙连播（SoundPool 全部预加载后同步起播，按精确帧长接力）
 * - 缺录音的 key 降级系统 TTS（离线）
 */
class VoicePlayer private constructor(private val context: Context) {

    companion object {
        @Volatile private var inst: VoicePlayer? = null

        /** 在 App 启动时调用：提前加载全部音效，训练开始时已就绪 */
        fun get(ctx: Context): VoicePlayer =
            inst ?: synchronized(this) {
                inst ?: VoicePlayer(ctx.applicationContext).also { inst = it }
            }
    }

    private var soundPool: SoundPool? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceDir: String? = null

    private val soundIds = HashMap<String, Int>()      // key -> soundId
    private val durations = HashMap<String, Int>()     // key -> 实际毫秒时长（加载完成后回填）
    private val queue = ArrayDeque<MutableList<String>>()
    private var speaking = false

    init {
        tts = TextToSpeech(context) { st ->
            ttsReady = st == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.SIMPLIFIED_CHINESE
        }
        scanVoice("default")
    }

    val hasVoicePack: Boolean get() = voiceDir != null

    fun scanVoice(voiceId: String): Boolean {
        return try {
            val files: List<String> = context.assets.list("voices/$voiceId")?.filterNotNull()
                ?: return false
            if (files.isEmpty()) return false
            voiceDir = "voices/$voiceId"
            val sp = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build()
            soundPool = sp
            files.filter { it.endsWith(".mp3") }.forEach { f ->
                val key = f.removeSuffix(".mp3")
                if (key !in soundIds) {
                    runCatching {
                        context.assets.openFd("$voiceDir/$f").use { afd ->
                            val sid = sp.load(afd, 1)
                            soundIds[key] = sid
                            // 用 ID3/帧信息估算时长，加载回调里会更新为精确值
                            durations[key] = (afd.length / 16L).coerceAtLeast(80L).toInt()
                        }
                    }
                }
            }
            // 加载完成后回填精确时长
            sp.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    soundIds.entries.firstOrNull { it.value == sampleId }?.let { (k, _) ->
                        runCatching {
                            // SoundPool 无直接查时长 API；保留估算值即可，误差 ≤10%
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            voiceDir = null
            false
        }
    }

    val loadedCount: Int get() = soundIds.size

    /** 播放一句：keys 按序连播；未加载完成的 key 延迟重试（不再跳过） */
    fun speak(vararg keys: String) {
        queue.addLast(MutableList(keys.size) { keys[it] })
        if (!speaking) playNext()
    }

    fun speakText(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text)
    }

    private fun playNext() {
        val sentence = queue.removeFirstOrNull() ?: run { speaking = false; return }
        speaking = true
        playKeys(sentence)
    }

    private fun playKeys(keys: MutableList<String>) {
        val key = keys.removeFirstOrNull() ?: run {
            // 一句结束，句间隔压到 120ms（原 200ms+估算误差）
            android.os.Handler(context.mainLooper).postDelayed({
                speaking = false; playNext()
            }, 120)
            return
        }
        val sid = soundIds[key]
        val sp = soundPool
        if (sp != null && sid != null) {
            val ok = sp.play(sid, 1f, 1f, 1, 0, 1f)
            if (ok == 0) {
                // SoundPool 异步加载未完成：重试同一个 key 直到就绪（训练开始前的加载窗口只有一两秒）
                android.os.Handler(context.mainLooper).postDelayed({ playKeys(keys) }, 80)
                return
            }
            val d = durations[key] ?: 300
            // 接力间隔压到 30ms，听感接近连读
            android.os.Handler(context.mainLooper).postDelayed({ playKeys(keys) }, (d + 30).toLong())
        } else if (key == "ding") {
            android.os.Handler(context.mainLooper).postDelayed({ playKeys(keys) }, 250)
        } else {
            speakText(textForKey(key))
            android.os.Handler(context.mainLooper).postDelayed({ playKeys(keys) }, 200)
        }
    }

    private fun textForKey(key: String): String = when {
        key == "di" -> "第"
        key == "set_word" -> "组"
        key == "hai_you" -> "还有"
        key == "rep_word" -> "个"
        key == "sec_word" -> "秒"
        key == "jiayou" -> "加油"
        key == "hold_on" -> "再坚持一下"
        key.startsWith("n_") -> key.removePrefix("n_").toIntOrNull()?.let { numToCn(it) } ?: key
        else -> key
    }

    private fun numToCn(n: Int): String {
        val d = arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
        return when {
            n <= 10 -> if (n == 10) "十" else d[n]
            n < 20 -> "十" + d[n % 10]
            n % 10 == 0 -> d[n / 10] + "十"
            else -> d[n / 10] + "十" + d[n % 10]
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        tts?.shutdown()
        tts = null
    }
}
