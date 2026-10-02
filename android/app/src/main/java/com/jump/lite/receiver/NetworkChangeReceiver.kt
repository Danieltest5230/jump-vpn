package com.jump.lite.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.jump.lite.core.JumpVpnService

class NetworkChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val isConnected = checkNetworkAvailable(cm)

        if (isConnected && JumpVpnService.isServiceRunning) {
            // El servicio ya está corriendo, verificar si requiere reconexión de socket
            val pingIntent = Intent(JumpVpnService.BROADCAST_STATUS).apply {
                putExtra(JumpVpnService.EXTRA_LOG, "Cambio de red detectado (Wi-Fi/Móvil). Verificando ruta...")
            }
            context.sendBroadcast(pingIntent)
        }
    }

    private fun checkNetworkAvailable(cm: ConnectivityManager): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }
}
