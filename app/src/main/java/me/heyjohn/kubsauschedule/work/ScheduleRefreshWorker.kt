package me.heyjohn.kubsauschedule.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.heyjohn.kubsauschedule.App
import java.util.concurrent.TimeUnit

/**
 * Раз в час обновляет расписание последней группы в фоне.
 */
class ScheduleRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = App.repository(applicationContext)
        val group = repository.loadCached()?.schedule?.group() ?: return Result.success()
        return try {
            repository.refresh(group, background = true)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "schedule-refresh"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleRefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
