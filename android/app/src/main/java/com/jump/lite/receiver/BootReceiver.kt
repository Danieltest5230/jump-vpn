package com.jump.lite.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jump.lite.core.JumpVpnService
import com.jump.lite.model.VpnProfile

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences("jump_settings", Context.MODE_PRIVATE)
            val startOnBoot = prefs.getBoolean("start_on_boot", false)

            if (startOnBoot) {
                val profileJson = prefs.getString("active_profile_json", null)
                val profile = if (!profileJson.isNullOrEmpty()) {
                    VpnProfile.fromJson(profileJson)
                } else {
                    VpnProfile.createDefault()
                }

                val serviceIntent = Intent(context, JumpVpnService::class.java).apply {
                    this.action = JumpVpnService.ACTION_CONNECT
                    putExtra(JumpVpnService.EXTRA_PROFILE, profile)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
