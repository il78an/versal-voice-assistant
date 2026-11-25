package ru.versal.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.RecognitionListener
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

class WakeWordService : Service() {
    companion object {
        const val ACTION_START = "ru.versal.voice.START_WAKE"
        const val ACTION_STOP = "ru.versal.voice.STOP_WAKE"
        private const val CHANNEL_ID = "versal_wake_channel"
        private const val NOTIFICATION_ID = 7001
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
        private const val MODEL_DIR = "vosk-model-small-ru-0.22"
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var tts: TextToSpeech? = null
    @Volatile private var stopped = false
    @Volatile private var handlingCommand = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ru", "RU")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopped = true
                getSharedPreferences(AssistantWidget.PREFS, MODE_PRIVATE).edit().putBoolean(AssistantWidget.KEY_ACTIVE, false).apply()
                AssistantWidget.refreshAll(this)
                stopRecognition()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                stopped = false
                getSharedPreferences(AssistantWidget.PREFS, MODE_PRIVATE).edit().putBoolean(AssistantWidget.KEY_ACTIVE, true).apply()
                AssistantWidget.refreshAll(this)
                startForeground(NOTIFICATION_ID, notification("Подготовка офлайн-модели…"))
                worker.execute { prepareModelAndListen() }
            }
        }
        return START_STICKY
    }

    private fun prepareModelAndListen() {
        try {
            val modelDir = File(filesDir, MODEL_DIR)
            if (!File(modelDir, "am").exists() || !File(modelDir, "conf").exists()) {
                updateNotification("Загрузка русской модели распознавания…")
                val zipFile = File(cacheDir, "vosk-ru.zip")
                val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                }
                try {
                    connection.inputStream.use { input ->
                        FileOutputStream(zipFile).use { output -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }
                updateNotification("Распаковка модели…")
                val tempDir = File(filesDir, "vosk-model-temp")
                tempDir.deleteRecursively()
                tempDir.mkdirs()
                ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val target = File(tempDir, entry.name)
                        if (!target.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)) {
                            throw IllegalStateException("Некорректный путь в архиве модели")
                        }
                        if (entry.isDirectory) target.mkdirs() else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out -> zip.copyTo(out) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                val unpacked = tempDir.listFiles()?.firstOrNull { it.isDirectory && File(it, "am").exists() }
                    ?: throw IllegalStateException("В архиве не найдена модель Vosk")
                modelDir.deleteRecursively()
                if (!unpacked.renameTo(modelDir)) {
                    unpacked.copyRecursively(modelDir, overwrite = true)
                }
                tempDir.deleteRecursively()
                zipFile.delete()
            }
            if (stopped) return
            model = Model(modelDir.absolutePath)
            main.post {
                updateNotification("Ожидаю ключевое слово «Помощник»")
                startWakeRecognition()
            }
        } catch (e: Exception) {
            updateNotification("Ошибка подготовки модели. Откройте приложение и повторите запуск.")
            android.util.Log.e("VersalWake", "Model initialization failed", e)
        }
    }

    private fun startWakeRecognition() {
        if (stopped || handlingCommand) return
        try {
            stopRecognition()
            val loadedModel = model ?: return
            val recognizer = Recognizer(loadedModel, 16000.0f, """["помощник"]""")
            val service = SpeechService(recognizer, 16000.0f)
            speechService = service
            service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    if (!stopped && hypothesis.orEmpty().contains("помощник", ignoreCase = true)) {
                        beginCommand()
                    }
                }
                override fun onResult(hypothesis: String?) {
                    if (!stopped && hypothesis.orEmpty().contains("помощник", ignoreCase = true)) beginCommand()
                }
                override fun onFinalResult(hypothesis: String?) {
                    if (!stopped && hypothesis.orEmpty().contains("помощник", ignoreCase = true)) beginCommand()
                    else if (!stopped && !handlingCommand) main.post { startWakeRecognition() }
                }
                override fun onError(exception: Exception?) {
                    if (!stopped && !handlingCommand) main.postDelayed({ startWakeRecognition() }, 1200)
                }
                override fun onTimeout() {
                    if (!stopped && !handlingCommand) main.post { startWakeRecognition() }
                }
            })
        } catch (e: Exception) {
            updateNotification("Не удалось включить микрофон")
            android.util.Log.e("VersalWake", "Wake recognition failed", e)
        }
    }

    private fun beginCommand() {
        if (handlingCommand || stopped) return
        handlingCommand = true
        stopRecognition()
        updateNotification("Помощник активирован — слушаю команду")
        val params = android.os.Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "wake") }
        val startCommandListening = {
            if (!stopped) {
                try {
                    val loadedModel = model ?: throw IllegalStateException("Модель ещё не загружена")
                    val recognizer = Recognizer(loadedModel, 16000.0f)
                    val service = SpeechService(recognizer, 16000.0f)
                    speechService = service
                    var processed = false
                    val listener = object : RecognitionListener {
                        private fun process(hypothesis: String?) {
                            if (processed || stopped) return
                            val phrase = extractText(hypothesis.orEmpty())
                            if (phrase.isBlank()) return
                            processed = true
                            executeCommand(phrase)
                            main.postDelayed({
                                handlingCommand = false
                                startWakeRecognition()
                            }, 900)
                        }
                        override fun onPartialResult(hypothesis: String?) {}
                        override fun onResult(hypothesis: String?) = process(hypothesis)
                        override fun onFinalResult(hypothesis: String?) {
                            process(hypothesis)
                            if (!processed && !stopped) {
                                main.postDelayed({ handlingCommand = false; startWakeRecognition() }, 500)
                            }
                        }
                        override fun onError(exception: Exception?) {
                            if (!stopped) main.post { handlingCommand = false; startWakeRecognition() }
                        }
                        override fun onTimeout() {
                            if (!stopped) main.post { handlingCommand = false; startWakeRecognition() }
                        }
                    }
                    service.startListening(listener, 9000)
                } catch (e: Exception) {
                    android.util.Log.e("VersalWake", "Command recognition failed", e)
                    handlingCommand = false
                    startWakeRecognition()
                }
            }
        }
        if (tts != null) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == "wake") main.post { startCommandListening() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == "wake") main.post { startCommandListening() }
                }
            })
            tts?.speak("Слушаю вас", TextToSpeech.QUEUE_FLUSH, params, "wake")
        } else {
            main.postDelayed({ startCommandListening() }, 700)
        }
    }

    private fun extractText(json: String): String {
        return try { JSONObject(json).optString("text", "") } catch (_: Exception) { "" }
    }

    private fun executeCommand(raw: String) {
        val s = raw.lowercase(Locale.ROOT).replace("ё", "е")
        when {
            s.contains("открой") && (s.contains("яндекс") || s.contains("музык")) -> {
                packageManager.getLaunchIntentForPackage("ru.yandex.music")?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it) }
            }
            (s.contains("закрой") || s.contains("выключи приложение")) && (s.contains("яндекс") || s.contains("музык")) -> {
                media(KeyEvent.KEYCODE_MEDIA_PAUSE)
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(home)
            }
            s.contains("следующ") || s.contains("дальше") -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
            s.contains("предыдущ") || s.contains("прошлый трек") -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            s.contains("останов") || s.contains("пауза") -> media(KeyEvent.KEYCODE_MEDIA_PAUSE)
            s.contains("включи") || s.contains("запусти") || s.trim() == "играй" -> media(KeyEvent.KEYCODE_MEDIA_PLAY)
            s.contains("громк") && (s.contains("плюс") || s.contains("увелич")) -> changeVolume(if (s.contains("десять") || s.contains("10")) 10 else 5)
            s.contains("громк") && (s.contains("минус") || s.contains("уменьш")) -> changeVolume(if (s.contains("десять") || s.contains("10")) -10 else -5)
        }
        updateNotification("Команда: $raw · снова жду «Помощник»")
    }

    private fun media(code: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun changeVolume(percent: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val steps = kotlin.math.max(1, kotlin.math.round(max * kotlin.math.abs(percent) / 100f).toInt())
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (current + if (percent > 0) steps else -steps).coerceIn(0, max), 0)
    }

    private fun stopRecognition() {
        try { speechService?.cancel() } catch (_: Exception) {}
        try { speechService?.shutdown() } catch (_: Exception) {}
        speechService = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Фоновое голосовое управление", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Версаль — голосовой помощник")
            .setContentText(message)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Остановить", stop)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification(message))
    }

    override fun onDestroy() {
        stopped = true
        getSharedPreferences(AssistantWidget.PREFS, MODE_PRIVATE).edit().putBoolean(AssistantWidget.KEY_ACTIVE, false).apply()
        AssistantWidget.refreshAll(this)
        stopRecognition()
        model?.close()
        model = null
        tts?.stop()
        tts?.shutdown()
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
