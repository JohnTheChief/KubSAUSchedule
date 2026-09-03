package me.heyjohn.kubsauschedule.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Срабатывает по будильнику и показывает напоминание о паре. */
class LessonAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
        Notifications.showLesson(context, id, title, text)
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
    }
}
