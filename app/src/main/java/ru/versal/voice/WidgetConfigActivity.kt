package ru.versal.voice

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class WidgetConfigActivity : Activity() {
    private var iconColor = Color.WHITE
    private var iconAlpha = 255
    private var bgColor = Color.rgb(18, 29, 49)
    private var bgAlpha = 210
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val prefs = getSharedPreferences(AssistantWidget.PREFS, MODE_PRIVATE)
        iconColor = prefs.getInt(AssistantWidget.KEY_ICON_COLOR, Color.WHITE)
        iconAlpha = prefs.getInt(AssistantWidget.KEY_ICON_ALPHA, 255)
        bgColor = prefs.getInt(AssistantWidget.KEY_BG_COLOR, Color.rgb(18, 29, 49))
        bgAlpha = prefs.getInt(AssistantWidget.KEY_BG_ALPHA, 210)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            setBackgroundColor(Color.rgb(18, 29, 49))
        }
        fun heading(value: String) {
            root.addView(TextView(this).apply {
                text = value; textSize = 18f; setTextColor(Color.WHITE)
                setPadding(0, 12, 0, 8)
            })
        }
        fun choices(title: String, labels: Array<String>, colors: IntArray, selected: Int, save: (Int) -> Unit) {
            heading(title)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            labels.forEachIndexed { index, label ->
                row.addView(Button(this).apply {
                    text = label
                    isAllCaps = false
                    setOnClickListener { save(colors[index]) }
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
            root.addView(row)
        }
        heading("Цвет значка")
        choices(" ", arrayOf("Чёрный", "Белый", "Жёлтый", "Красный", "Зелёный"),
            intArrayOf(Color.BLACK, Color.WHITE, Color.YELLOW, Color.RED, Color.GREEN), iconColor) { iconColor = it }
        heading("Прозрачность значка: ${iconAlpha * 100 / 255}%")
        root.addView(SeekBar(this).apply {
            max = 255; progress = iconAlpha
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) { iconAlpha = value }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })
        heading("Цвет фона")
        choices(" ", arrayOf("Тёмно-синий", "Чёрный", "Белый", "Жёлтый", "Красный", "Зелёный", "Прозрачный"),
            intArrayOf(Color.rgb(18,29,49), Color.BLACK, Color.WHITE, Color.YELLOW, Color.RED, Color.GREEN, Color.TRANSPARENT), bgColor) { bgColor = it }
        heading("Прозрачность фона: ${bgAlpha * 100 / 255}%")
        root.addView(SeekBar(this).apply {
            max = 255; progress = bgAlpha
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) { bgAlpha = value }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })
        root.addView(Button(this).apply {
            text = "Сохранить"; isAllCaps = false
            setOnClickListener {
                prefs.edit()
                    .putInt(AssistantWidget.KEY_ICON_COLOR, iconColor)
                    .putInt(AssistantWidget.KEY_ICON_ALPHA, iconAlpha)
                    .putInt(AssistantWidget.KEY_BG_COLOR, bgColor)
                    .putInt(AssistantWidget.KEY_BG_ALPHA, bgAlpha)
                    .apply()
                val manager = AppWidgetManager.getInstance(this@WidgetConfigActivity)
                AssistantWidget.render(this@WidgetConfigActivity, manager, widgetId)
                val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                setResult(RESULT_OK, result)
                finish()
            }
        })
        setContentView(root)
    }
}
