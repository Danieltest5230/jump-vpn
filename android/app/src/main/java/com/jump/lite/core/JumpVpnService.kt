package com.jump.lite.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.jump.lite.R
import com.jump.lite.model.ConnectionState
import com.jump.lite.model.VpnProfile
import com.jump.lite.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.Socket

class JumpVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.jump.lite.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.jump.lite.ACTION_DISCONNECT"
        const val EXTRA_PROFILE = "com.jump.lite.EXTRA_PROFILE"

        const val BROADCAST_STATUS = "com.jump.lite.BROADCAST_STATUS"
        const val EXTRA_STATE = "state"
        const val EXTRA_LOG = "log_msg"
        const val EXTRA_BYTES_IN = "bytes_in"
        const val EXTRA_BYTES_OUT = "bytes_out"

        private const val NOTIFICATION_ID = 46201
        private const val CHANNEL_ID = "jump_vpn_channel"

        var isServiceRunning = false
            private set

        @Volatile
        private var instance: JumpVpnService? = null

        /**
         * Protege sockets de transporte para que no sean interceptados por la interfaz TUN virtual,
         * previniendo bucles infinitos de red y desconexiones repentinas.
         */
        fun protectSocket(socket: Socket): Boolean {
            return instance?.protect(socket) ?: true
        }

        fun protectSocket(socket: DatagramSocket): Boolean {
            return instance?.protect(socket) ?: true
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var sshClient: com.jump.lite.core.tunnel.SshTunnelClient? = null
    private var tun2socks: Tun2Socks? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_DISCONNECT) {
            disconnectVpn("Desconexión solicitada por el usuario.")
            return START_NOT_STICKY
        }

        if (action == ACTION_CONNECT && intent != null) {
            val profile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(EXTRA_PROFILE, VpnProfile::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(EXTRA_PROFILE) as? VpnProfile
            } ?: VpnProfile.createDefault()

            connectVpn(profile)
        }

        return START_STICKY
    }

    private fun connectVpn(profile: VpnProfile) {
        startForeground(NOTIFICATION_ID, buildNotification("Iniciando conexión Jump...", ConnectionState.CONNECTING))
        isServiceRunning = true
        broadcastState(ConnectionState.CONNECTING)
        sendLog("=== Iniciando Jump VPN v4.6.2 ===")

        serviceScope.launch {
            try {
                sendLog("Configurando interfaz de red virtual TUN con protección Anti-Leak...")
                val builder = Builder()
                    .setSession("Jump VPN")
                    .addAddress("10.0.0.2", 24)
                    .addRoute("0.0.0.0", 0)
                    // Mitigación de Fuga IPv6: Enrutar IPv6 hacia la interfaz virtual para que no escape a la red celular
                    .addAddress("fd00::2", 120)
                    .addRoute("::", 0)
                    .addDnsServer(profile.dnsPrimary.ifEmpty { "1.1.1.1" })
                    .addDnsServer(profile.dnsSecondary.ifEmpty { "1.0.0.1" })
                    .setMtu(1500)
                    .setBlocking(true)

                vpnInterface = builder.establish()

                if (vpnInterface == null) {
                    throw IllegalStateException("El sistema Android rechazó la interfaz TUN.")
                }

                sendLog("✓ Interfaz TUN creada exitosamente (10.0.0.2/24 - fd00::2/120)")
                broadcastState(ConnectionState.INJECTING_PAYLOAD)

                // Iniciar cliente de túnel SSH
                sshClient = com.jump.lite.core.tunnel.SshTunnelClient(
                    profile = profile,
                    localSocksPort = 1080,
                    onLog = { logMsg -> sendLog(logMsg) }
                )

                broadcastState(ConnectionState.AUTHENTICATING)
                sshClient?.start()

                // Iniciar motor de enrutamiento Tun2Socks para procesar paquetes reales y DNS
                tun2socks = Tun2Socks(
                    vpnInterface = vpnInterface!!,
                    localSocksPort = 1080,
                    dnsServerIp = profile.dnsPrimary.ifEmpty { "1.1.1.1" },
                    onLog = { msg -> sendLog(msg) },
                    onStats = { rx, tx -> broadcastStats(rx, tx) }
                )
                tun2socks?.start()

                broadcastState(ConnectionState.CONNECTED)
                updateNotification("Conectado | ${profile.name}", ConnectionState.CONNECTED)
                sendLog("✓ CONECTADO: Túnel seguro activo. Tráfico IP y DNS protegido contra fugas.")

            } catch (e: Exception) {
                sendLog("ERROR CRÍTICO: ${e.message}")
                disconnectVpn("Fallo en la conexión: ${e.message}")
            }
        }
    }

    private fun disconnectVpn(reason: String) {
        sendLog("Desconectando: $reason")
        isServiceRunning = false
        broadcastState(ConnectionState.DISCONNECTED)

        try {
            tun2socks?.stop()
            tun2socks = null
            sshClient?.stop()
            sshClient = null
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            // Ignorar
        }

        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        disconnectVpn("Servicio destruido.")
        serviceJob.cancel()
        instance = null
        super.onDestroy()
    }

    private fun sendLog(message: String) {
        val intent = Intent(BROADCAST_STATUS).apply {
            putExtra(EXTRA_LOG, message)
        }
        sendBroadcast(intent)
    }

    private fun broadcastState(state: ConnectionState) {
        val intent = Intent(BROADCAST_STATUS).apply {
            putExtra(EXTRA_STATE, state.name)
        }
        sendBroadcast(intent)
    }

    private fun broadcastStats(rx: Long, tx: Long) {
        val intent = Intent(BROADCAST_STATUS).apply {
            putExtra(EXTRA_BYTES_IN, rx)
            putExtra(EXTRA_BYTES_OUT, tx)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jump VPN Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el estado activo de Jump VPN"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, state: ConnectionState): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, JumpVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Jump VPN - 4.6.2")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_delete, "Desconectar", disconnectIntent)
            .setOngoing(state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String, state: ConnectionState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text, state))
    }
}
