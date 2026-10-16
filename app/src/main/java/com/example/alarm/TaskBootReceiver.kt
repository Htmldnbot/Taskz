package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Device rebooted or package replaced ($action). Restoring scheduled task alarms...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val scheduler = TaskAlarmScheduler(context)
                    scheduler.restoreAlarmsAfterReboot()
                    Log.d(TAG, "Finished restoring alarms after reboot")
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring alarms: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
