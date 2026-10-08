package com.eelan.musclediary.reminder

import android.content.Context
import android.speech.tts.TextToSpeech
import android.media.AudioAttributes
import android.media.SoundPool
import java.util.Locale

/**
 * 训练语音播放器。
 * 优先播放内置音色包 assets/voices/<voiceId>/ 下的 mp3；
 * 缺文件或没有音色包时逐句降级为系统 TTS（离线），保证提示永不缺席。
 * 拼接式播放：如「还有」+ n_10 + 「个」+「加油」按顺序连播。
 */
class VoicePlayer(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceDir: String? = null // assets 内音色目录，null = 无包

    // key -> soundId
    private val loaded = HashMap<String, Int>()
    private val queue = ArrayDeque<MutableList<String>>() // 待播句（每句由若干 key 组成）
    private var speaking = false
    private val playedCache = HashSet<String>()

    init {
        tts = TextToSpeech(context) { st ->
            ttsReady = st == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.SIMPLIFIED_CHINESE
        }
        scanVoice("default")
    }

    /** 扫描 assets 下音色目录；文件清单存在才认为有效 */
    fun scanVoice(voiceId: String): Boolean {
        return try {
            val files: List<String> = context.assets.list("voices/$voiceId")?.filterNotNull()
                ?: return false
            if (files.isEmpty()) return false
            voiceDir = "voices/$voiceId"
            preload(files)
            true
        } catch (_: Exception) {
            voiceDir = null
            false
        }
    }

    val hasVoicePack: Boolean get() = voiceDir != null

    private fun preload(files: List<String>) {
        val sp = soundPool ?: SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            .build()
            .also { soundPool = it }
        files.filter { it != null && it.endsWith(".mp3") }.forEach { f ->
            val name = f ?: return@forEach
            val key = name.removeSuffix(".mp3")
            if (key !in playedCache) {
                try {
                    context.assets.openFd("$voiceDir/$name").use { afd ->
                        loaded[key] = sp.load(afd, 1)
                        playedCache.add(key)
                    }
                } catch (_: Exception) { /* 单文件缺失忽略，走 TTS */ }
            }
        }
    }

    /** 播放一句：keys 按序连播（如 listOf("hai_you","n_10","rep_word","jiayou")） */
    fun speak(vararg keys: String) {
        queue.addLast(MutableList(keys.size) { keys[it] })
        if (!speaking) playNext()
    }

    /** 直接播放任意文本（TTS），用于动作名等没有录音的场景 */
    fun speakText(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text)
        else ding()
    }

    private fun playNext() {
        val sentence = queue.removeFirstOrNull() ?: run { speaking = false; return }
        speaking = true
        playKeys(sentence.toMutableList())
    }

    private fun playKeys(keys: MutableList<String>) {
        val key = keys.removeFirstOrNull() ?: run {
            // 这句播完，接下一句（留 200ms 间隔）
            android.os.Handler(context.mainLooper).postDelayed({
                speaking = false; playNext()
            }, 200)
            return
        }
        val sp = soundPool
        val sid = loaded[key]
        if (sp != null && sid != null) {
            val ok = sp.play(sid, 1f, 1f, 1, 0, 1f)
            // 播放调度失败则整句走 TTS
            if (ok == 0) {
                speakTextFallback(key)
                android.os.Handler(context.mainLooper).postDelayed({ playKeys(keys) }, 350)
                return
            }
            // 估算音频时长后接续（32000Hz 128kbps ≈ 16KB/s）
            val size = runCatching {
                context.assets.openFd("$voiceDir/$key.mp3").use { it.length }
            }.getOrDefault(16000L)
            val delayMs = (size / 16).coerceIn(150L, 5000L) + 60
            android.os.Handler(context.mainLooper).postDelayed({ playKeys(keys) }, delayMs)
        } else if (key == "ding") {
            ding()
            android.os.Handler(context.mainLooper).postDelayed({ playKeys(keys) }, 300)
        } else {
            // 缺录音 → TTS 直接念近似文本
            speakTextFallback(textForKey(key))
            android.os.Handler(context.mainLooper).postDelayed({ playKeys(keys) }, 350)
        }
    }

    private fun speakTextFallback(key: String) {
        val t = textForKey(key)
        if (ttsReady) tts?.speak(t, TextToSpeech.QUEUE_ADD, null, key) else ding()
    }

    /** key → 近似中文（TTS 降级用） */
    private fun textForKey(key: String): String = when {
        key == "di" -> "第"
        key == "set_word" -> "组"
        key == "hai_you" -> "还有"
        key == "rep_word" -> "个"
        key == "sec_word" -> "秒"
        key == "jiayou" -> "加油"
        key == "hold_on" -> "再坚持一下"
        key.startsWith("n_") -> key.removePrefix("n_").toIntOrNull()?.let { numToCn(it) } ?: key
        key.startsWith("ex_") -> "" // 动作名缺录音时由调用方补充文字
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

    /** 短提示音（无音源时用 TTS 念「叮」近似，或静默） */
    private fun ding() {
        if (ttsReady) {
            // TTS 播放短促「叮」不可行，静默处理（保持节奏即可）
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        tts?.shutdown()
        tts = null
    }
}
