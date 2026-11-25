package ru.versal.voice

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class AssistantWidget : AppWidgetProvider() {
    companion object {
        const val PREFS = "assistant_widget"
        const val KEY_ACTIVE = "active"
        const val KEY_ICON_COLOR = "icon_color"
        const val KEY_ICON_ALPHA = "icon_alpha"
        const val KEY_BG_COLOR = "bg_color"
        const val KEY_BG_ALPHA = "bg_alpha"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AssistantWidget::class.java))
            if (ids.isNotEmpty()) AssistantWidget().onUpdate(context, manager, ids)
        }

        fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val active = prefs.getBoolean(KEY_ACTIVE, false)
            val iconColor = prefs.getInt(KEY_ICON_COLOR, Color.WHITE)
            val iconAlpha = prefs.getInt(KEY_ICON_ALPHA, 255)
            val bgColor = prefs.getInt(KEY_BG_COLOR, Color.rgb(18, 29, 49))
            val bgAlpha = prefs.getInt(KEY_BG_ALPHA, 210)
            val views = RemoteViews(context.packageName, R.layout.assistant_widget)
            views.setInt(R.id.widget_background, "setBackgroundColor",
                Color.argb(bgAlpha, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor)))
            views.setInt(R.id.widget_icon, "setColorFilter", iconColor)
            views.setInt(R.id.widget_icon, "setImageAlpha", iconAlpha)
            views.setTextViewText(R.id.widget_state, if (active) "●" else "○")
            val click = Intent(context, AssistantWidget::class.java).setAction("ru.versal.voice.WIDGET_TOGGLE")
            val pending = PendingIntent.getBroadcast(
                context, 7002, click, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "ru.versal.voice.WIDGET_TOGGLE") {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val active = prefs.getBoolean(KEY_ACTIVE, false)
            val serviceIntent = Intent(context, WakeWordService::class.java).setAction(
                if (active) WakeWordService.ACTION_STOP else WakeWordService.ACTION_START
            )
            if (active) context.startService(serviceIntent)
            else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
            prefs.edit().putBoolean(KEY_ACTIVE, !active).apply()
            refreshAll(context)
        }
    }
}
