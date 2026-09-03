package me.heyjohn.kubsauschedule

import android.app.Application
import android.content.Context
import me.heyjohn.kubsauschedule.data.ScheduleRepository
import me.heyjohn.kubsauschedule.notifications.Notifications
import me.heyjohn.kubsauschedule.work.ScheduleRefreshWorker

class App : Application() {

    lateinit var repository: ScheduleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ScheduleRepository(this)
        Notifications.createChannels(this)
        ScheduleRefreshWorker.enqueue(this)
    }

    companion object {
        fun repository(context: Context): ScheduleRepository =
            (context.applicationContext as App).repository
    }
}
