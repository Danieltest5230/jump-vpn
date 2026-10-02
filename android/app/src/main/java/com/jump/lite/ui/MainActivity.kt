package com.jump.lite.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jump.lite.R
import com.jump.lite.core.JumpVpnService
import com.jump.lite.model.ConnectionState
import com.jump.lite.model.TunnelMode
import com.jump.lite.model.VpnProfile
import com.jump.lite.utils.BatteryOptimizationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val REQUEST_VPN_PERMISSION = 1001
    private val REQUEST_PAYLOAD = 1002
    private val REQUEST_SERVER = 1003

    private lateinit var tvStatusBadge: TextView
    private lateinit var spTunnelMode: Spinner
    private lateinit var tvServerName: TextView
    private lateinit var tvServerDetails: TextView
    private lateinit var tvPing: TextView
    private lateinit var btnConnect: Button
    private lateinit var tvDownloadSpeed: TextView
    private lateinit var tvUploadSpeed: TextView
    private lateinit var tvLogConsole: TextView
    private lateinit var scrollLogs: ScrollView
    private lateinit var btnOpenPayload: Button
    private lateinit var btnServers: Button
    private lateinit var btnClearLogs: Button
    private lateinit var cardServerSelect: View

    private var activeProfile = VpnProfile.createDefault()
    private var isConnected = false

    private val vpnBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return

            // Logs recibidos
            val log = intent.getStringExtra(JumpVpnService.EXTRA_LOG)
            if (log != null) {
                appendLog(log)
            }

            // Estado de conexión
            val stateName = intent.getStringExtra(JumpVpnService.EXTRA_STATE)
            if (stateName != null) {
                try {
                    val state = ConnectionState.valueOf(stateName)
                    updateConnectionUi(state)
                } catch (e: Exception) {
                    // Ignorar
                }
            }

            // Estadísticas de tráfico
            val rx = intent.getLongExtra(JumpVpnService.EXTRA_BYTES_IN, -1)
            val tx = intent.getLongExtra(JumpVpnService.EXTRA_BYTES_OUT, -1)
            if (rx >= 0 && tx >= 0) {
                tvDownloadSpeed.text = formatSpeed(rx)
                tvUploadSpeed.text = formatSpeed(tx)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupTunnelModeSpinner()
        setupListeners()
        updateProfileUi()

        // Solicitar optimización de batería recomendada
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)

        appendLog("Jump VPN v4.6.2 iniciado. Motor SSH T listo.")
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(JumpVpnService.BROADCAST_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vpnBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(vpnBroadcastReceiver, filter)
        }

        if (JumpVpnService.isServiceRunning) {
            updateConnectionUi(ConnectionState.CONNECTED)
        } else {
            updateConnectionUi(ConnectionState.DISCONNECTED)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(vpnBroadcastReceiver)
        } catch (e: Exception) {
            // Ya desregistrado
        }
    }

    private fun initViews() {
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        spTunnelMode = findViewById(R.id.spTunnelMode)
        tvServerName = findViewById(R.id.tvServerName)
        tvServerDetails = findViewById(R.id.tvServerDetails)
        tvPing = findViewById(R.id.tvPing)
        btnConnect = findViewById(R.id.btnConnect)
        tvDownloadSpeed = findViewById(R.id.tvDownloadSpeed)
        tvUploadSpeed = findViewById(R.id.tvUploadSpeed)
        tvLogConsole = findViewById(R.id.tvLogConsole)
        scrollLogs = findViewById(R.id.scrollLogs)
        btnOpenPayload = findViewById(R.id.btnOpenPayload)
        btnServers = findViewById(R.id.btnServers)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        cardServerSelect = findViewById(R.id.cardServerSelect)
    }

    private fun setupTunnelModeSpinner() {
        val modes = TunnelMode.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)
        spTunnelMode.adapter = adapter
        spTunnelMode.setSelection(activeProfile.tunnelMode.ordinal)

        spTunnelMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                activeProfile.tunnelMode = TunnelMode.fromOrdinal(position)
                updateProfileUi()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener {
            if (isConnected || JumpVpnService.isServiceRunning) {
                triggerDisconnect()
            } else {
                prepareAndConnectVpn()
            }
        }

        btnOpenPayload.setOnClickListener {
            val intent = Intent(this, PayloadActivity::class.java).apply {
                putExtra("current_payload", activeProfile.payload)
            }
            startActivityForResult(intent, REQUEST_PAYLOAD)
        }

        btnServers.setOnClickListener {
            val intent = Intent(this, ServerListActivity::class.java).apply {
                putExtra("profile_json", activeProfile.toJson())
            }
            startActivityForResult(intent, REQUEST_SERVER)
        }

        cardServerSelect.setOnClickListener {
            btnServers.performClick()
        }

        btnClearLogs.setOnClickListener {
            tvLogConsole.text = ""
            appendLog("Logs limpiados.")
        }
    }

    private fun prepareAndConnectVpn() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, REQUEST_VPN_PERMISSION)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, JumpVpnService::class.java).apply {
            action = JumpVpnService.ACTION_CONNECT
            putExtra(JumpVpnService.EXTRA_PROFILE, activeProfile)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun triggerDisconnect() {
        val intent = Intent(this, JumpVpnService::class.java).apply {
            action = JumpVpnService.ACTION_DISCONNECT
        }
        startService(intent)
    }

    private fun updateConnectionUi(state: ConnectionState) {
        when (state) {
            ConnectionState.DISCONNECTED -> {
                isConnected = false
                tvStatusBadge.text = "DESCONECTADO"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_red))
                tvStatusBadge.setBackgroundResource(R.drawable.badge_disconnected)
                btnConnect.text = "CONECTAR"
                btnConnect.setBackgroundResource(R.drawable.btn_connect_glow)
            }
            ConnectionState.CONNECTING -> {
                tvStatusBadge.text = "CONECTANDO..."
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_yellow))
                btnConnect.text = "CANCELAR"
            }
            ConnectionState.INJECTING_PAYLOAD -> {
                tvStatusBadge.text = "INYECTANDO"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.primary_electric))
            }
            ConnectionState.AUTHENTICATING -> {
                tvStatusBadge.text = "SSH AUTH"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_neon))
            }
            ConnectionState.CONNECTED -> {
                isConnected = true
                tvStatusBadge.text = "CONECTADO"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_green))
                tvStatusBadge.setBackgroundResource(R.drawable.badge_connected)
                btnConnect.text = "DESCONECTAR"
                btnConnect.setBackgroundResource(R.drawable.btn_disconnect_glow)
            }
            ConnectionState.RECONNECTING -> {
                tvStatusBadge.text = "RECONECTANDO"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_yellow))
            }
            ConnectionState.FAILED -> {
                isConnected = false
                tvStatusBadge.text = "FALLÓ"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_red))
                btnConnect.text = "REINTENTAR"
            }
        }
    }

    private fun updateProfileUi() {
        tvServerName.text = activeProfile.name
        tvServerDetails.text = "${activeProfile.serverHost}:${activeProfile.serverPort} • ${activeProfile.tunnelMode.displayName}"
    }

    private fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formatted = "[$time] $msg\n"
        tvLogConsole.append(formatted)
        scrollLogs.post { scrollLogs.fullScroll(View.FOCUS_DOWN) }
    }

    private fun formatSpeed(bytes: Long): String {
        return if (bytes < 1024) {
            "$bytes B/s"
        } else if (bytes < 1024 * 1024) {
            String.format(Locale.US, "%.1f KB/s", bytes / 1024.0)
        } else {
            String.format(Locale.US, "%.2f MB/s", bytes / (1024.0 * 1024.0))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN_PERMISSION && resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else if (requestCode == REQUEST_PAYLOAD && resultCode == Activity.RESULT_OK) {
            val newPayload = data?.getStringExtra("payload")
            if (newPayload != null) {
                activeProfile.payload = newPayload
                appendLog("Payload actualizado: ${newPayload.take(30)}...")
            }
        } else if (requestCode == REQUEST_SERVER && resultCode == Activity.RESULT_OK) {
            val json = data?.getStringExtra("profile_json")
            if (json != null) {
                activeProfile = VpnProfile.fromJson(json)
                updateProfileUi()
                appendLog("Perfil de servidor actualizado: ${activeProfile.serverHost}")
            }
        }
    }
}
