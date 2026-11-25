package ru.versal.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.os.Build

class MainActivity : Activity() {
    private var speech: SpeechRecognizer? = null
    private var continuous = false
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var listenButton: Button
    private val requestAudio = 41
    private val musicPackage = "ru.yandex.music"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio)
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 32, 28, 24)
            setBackgroundColor(0xFF0F1B31.toInt())
        }
        fun text(value: String, size: Float, color: Int) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(8, 14, 8, 14)
        }
        root.addView(text("В Е Р С А Л Ь", 25f, 0xFFE1C277.toInt()))
        root.addView(text("ГОЛОСОВОЕ УПРАВЛЕНИЕ ЯНДЕКС МУЗЫКОЙ", 13f, 0xFFD0D8E5.toInt()))
        status = text("Готов к работе", 18f, 0xFFFFFFFF.toInt())
        result = text("Здесь появится распознанная команда", 15f, 0xFFD0D8E5.toInt())
        root.addView(status)
        root.addView(result)
        listenButton = Button(this).apply {
            text = "🎙 Сказать команду"
            isAllCaps = false
            setOnClickListener { startRecognition(false) }
        }
        root.addView(listenButton)
        val continuousButton = Button(this).apply {
            text = "▶ Непрерывный режим (экран открыт)"
            isAllCaps = false
            setOnClickListener {
                if (continuous) {
                    continuous = false
                    speech?.cancel()
                    status.text = "Ожидание команды остановлено"
                    text = "▶ Непрерывный режим (экран открыт)"
                } else {
                    continuous = true
                    text = "■ Остановить непрерывный режим"
                    startRecognition(true)
                }
            }
        }
        root.addView(continuousButton)
        root.addView(Button(this).apply {
            text = "▶ Включить фоновое ожидание «Помощник»"
            isAllCaps = false
            setOnClickListener { startBackgroundAssistant() }
        })
        root.addView(Button(this).apply {
            text = "■ Остановить фоновое ожидание"
            isAllCaps = false
            setOnClickListener {
                startService(Intent(this@MainActivity, WakeWordService::class.java).setAction(WakeWordService.ACTION_STOP))
                status.text = "Фоновое ожидание остановлено"
            }
        })
        root.addView(Button(this).apply {
            text = "Настроить виджет 1×1"
            isAllCaps = false
            setOnClickListener { startActivity(Intent(this@MainActivity, WidgetConfigActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text = "Открыть Яндекс Музыку"
            isAllCaps = false
            setOnClickListener { openMusic() }
        })
        root.addView(text(
            "Команды: открой/закрой Яндекс Музыку; включи/останови музыку; " +
                "следующий/предыдущий трек; громкость плюс/минус пять или десять.\n\n" +
                "Фоновый режим: включите его кнопкой ниже. При первом запуске загрузится офлайн-модель (~45 МБ). " +
                "После запуска служба ожидает слово «Помощник» при выключенном экране.",
            14f, 0xFFD0D8E5.toInt()
        ))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun startBackgroundAssistant() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio)
            Toast.makeText(this, "Разрешите доступ к микрофону и нажмите кнопку ещё раз", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val serviceIntent = Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
            status.text = "Запускаю фоновое ожидание…"
        } catch (e: Exception) {
            status.text = "Не удалось запустить фоновый режим: " + e.message
        }
    }

    private fun startRecognition(loop: Boolean) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "На устройстве нет службы распознавания речи"
            return
        }
        continuous = loop
        listenButton.isEnabled = !loop
        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "Слушаю…" }
                override fun onBeginningOfSpeech() { status.text = "Распознаю речь…" }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "Обрабатываю…" }
                override fun onError(error: Int) { restartOrFinish(loop) }
                override fun onResults(bundle: Bundle?) {
                    val phrase = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    result.text = if (phrase.isBlank()) "Команда не распознана" else "Вы сказали: $phrase"
                    val done = executeCommand(phrase)
                    status.text = if (done) "Команда обработана" else "Не удалось определить команду"
                    restartOrFinish(loop)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try { speech?.startListening(intent) } catch (_: Exception) { restartOrFinish(false) }
    }

    private fun restartOrFinish(loop: Boolean) {
        if (loop && continuous) {
            status.postDelayed({ if (continuous) startRecognition(true) }, 800)
        } else {
            continuous = false
            listenButton.isEnabled = true
        }
    }

    private fun executeCommand(raw: String): Boolean {
        val s = raw.lowercase().replace("ё", "е").replace(Regex("[^а-яa-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        when {
            s.contains("открой") && (s.contains("яндекс") || s.contains("музык")) -> openMusic()
            (s.contains("закрой") || s.contains("выключи приложение")) &&
                (s.contains("яндекс") || s.contains("музык")) -> {
                media(KeyEvent.KEYCODE_MEDIA_PAUSE)
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            }
            s.contains("следующ") || s.contains("дальше") -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
            s.contains("предыдущ") || s.contains("прошлый трек") -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            s.contains("останов") || s.contains("пауза") -> media(KeyEvent.KEYCODE_MEDIA_PAUSE)
            s.contains("включи") || s.contains("запусти") || s == "играй" -> media(KeyEvent.KEYCODE_MEDIA_PLAY)
            s.contains("громк") && (s.contains("плюс") || s.contains("увелич")) ->
                changeVolume(if (s.contains("десять") || s.contains("10")) 10 else 5)
            s.contains("громк") && (s.contains("минус") || s.contains("уменьш")) ->
                changeVolume(if (s.contains("десять") || s.contains("10")) -10 else -5)
            s.contains("помощник") -> status.text = "Я слушаю. Назовите команду."
            else -> return false
        }
        return true
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
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,
            (current + if (percent > 0) steps else -steps).coerceIn(0, max), 0)
    }

    private fun openMusic() {
        val launch = packageManager.getLaunchIntentForPackage(musicPackage)
        if (launch != null) startActivity(launch)
        else Toast.makeText(this, "Яндекс Музыка не найдена", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        continuous = false
        speech?.destroy()
        speech = null
        super.onDestroy()
    }
}
