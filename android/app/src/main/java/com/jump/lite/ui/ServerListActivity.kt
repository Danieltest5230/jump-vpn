package com.jump.lite.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jump.lite.R
import com.jump.lite.model.VpnProfile

class ServerListActivity : AppCompatActivity() {

    private lateinit var etServerHost: EditText
    private lateinit var etServerPort: EditText
    private lateinit var etSshUser: EditText
    private lateinit var etSshPass: EditText
    private lateinit var etSniHost: EditText
    private lateinit var btnSaveProfile: Button

    private lateinit var currentProfile: VpnProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_list)

        initViews()

        val json = intent.getStringExtra("profile_json")
        currentProfile = if (json != null) VpnProfile.fromJson(json) else VpnProfile.createDefault()

        populateFields()

        btnSaveProfile.setOnClickListener {
            saveChanges()
        }
    }

    private fun initViews() {
        etServerHost = findViewById(R.id.etServerHost)
        etServerPort = findViewById(R.id.etServerPort)
        etSshUser = findViewById(R.id.etSshUser)
        etSshPass = findViewById(R.id.etSshPass)
        etSniHost = findViewById(R.id.etSniHost)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
    }

    private fun populateFields() {
        etServerHost.setText(currentProfile.serverHost)
        etServerPort.setText(currentProfile.serverPort.toString())
        etSshUser.setText(currentProfile.sshUser)
        etSshPass.setText(currentProfile.sshPass)
        etSniHost.setText(currentProfile.sniHost)
    }

    private fun saveChanges() {
        currentProfile.serverHost = etServerHost.text.toString().trim()
        currentProfile.serverPort = etServerPort.text.toString().toIntOrNull() ?: 22
        currentProfile.sshUser = etSshUser.text.toString().trim()
        currentProfile.sshPass = etSshPass.text.toString().trim()
        currentProfile.sniHost = etSniHost.text.toString().trim()

        val resultIntent = Intent().apply {
            putExtra("profile_json", currentProfile.toJson())
        }
        setResult(Activity.RESULT_OK, resultIntent)
        Toast.makeText(this, "Ajustes de servidor guardados", Toast.LENGTH_SHORT).show()
        finish()
    }
}
