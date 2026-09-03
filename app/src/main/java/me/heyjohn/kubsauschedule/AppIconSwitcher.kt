package me.heyjohn.kubsauschedule

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Иконка приложения: обычная (`LauncherStd`) или "расписание обновилось" (`LauncherUpdated`).
 * Реализовано включением одного из двух activity-alias из манифеста.
 */
object AppIconSwitcher {

    private const val STD = ".LauncherStd"
    private const val UPDATED = ".LauncherUpdated"

    fun isUpdated(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context, UPDATED)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setUpdated(context: Context, updated: Boolean) {
        if (isUpdated(context) == updated) return
        val pm = context.packageManager
        val enable = component(context, if (updated) UPDATED else STD)
        val disable = component(context, if (updated) STD else UPDATED)
        pm.setComponentEnabledSetting(
            enable, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            disable, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
        )
    }

    private fun component(context: Context, alias: String) =
        ComponentName(context, context.packageName + alias)
}
