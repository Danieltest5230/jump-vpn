package com.jump.lite.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jump.lite.R
import com.jump.lite.core.payload.PayloadGenerator

class PayloadActivity : AppCompatActivity() {

    private lateinit var etBugHost: EditText
    private lateinit var spMethod: Spinner
    private lateinit var spInjectionType: Spinner
    private lateinit var cbOnlineHost: CheckBox
    private lateinit var cbKeepAlive: CheckBox
    private lateinit var cbForwardHost: CheckBox
    private lateinit var cbUserAgent: CheckBox
    private lateinit var etPayloadResult: EditText
    private lateinit var btnGeneratePayload: Button
    private lateinit var btnGenerateWs: Button
    private lateinit var btnApplyPayload: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payload)

        initViews()
        setupSpinners()
        setupListeners()

        val currentPayload = intent.getStringExtra("current_payload") ?: ""
        if (currentPayload.isNotEmpty()) {
            etPayloadResult.setText(currentPayload)
        }
    }

    private fun initViews() {
        etBugHost = findViewById(R.id.etBugHost)
        spMethod = findViewById(R.id.spMethod)
        spInjectionType = findViewById(R.id.spInjectionType)
        cbOnlineHost = findViewById(R.id.cbOnlineHost)
        cbKeepAlive = findViewById(R.id.cbKeepAlive)
        cbForwardHost = findViewById(R.id.cbForwardHost)
        cbUserAgent = findViewById(R.id.cbUserAgent)
        etPayloadResult = findViewById(R.id.etPayloadResult)
        btnGeneratePayload = findViewById(R.id.btnGeneratePayload)
        btnGenerateWs = findViewById(R.id.btnGenerateWs)
        btnApplyPayload = findViewById(R.id.btnApplyPayload)
    }

    private fun setupSpinners() {
        val methods = PayloadGenerator.RequestMethod.values().map { it.name }
        spMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, methods)

        val injectionTypes = listOf("Normal", "Front Inject", "Back Inject")
        spInjectionType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, injectionTypes)
    }

    private fun setupListeners() {
        btnGeneratePayload.setOnClickListener {
            val bug = etBugHost.text.toString().trim()
            val method = PayloadGenerator.RequestMethod.values()[spMethod.selectedItemPosition]
            val injectionType = when (spInjectionType.selectedItemPosition) {
                1 -> PayloadGenerator.InjectionType.FRONT_INJECT
                2 -> PayloadGenerator.InjectionType.BACK_INJECT
                else -> PayloadGenerator.InjectionType.NORMAL
            }

            val options = PayloadGenerator.GeneratorOptions(
                bugHost = bug,
                method = method,
                injectionType = injectionType,
                keepAlive = cbKeepAlive.isChecked,
                onlineHost = cbOnlineHost.isChecked,
                forwardHost = cbForwardHost.isChecked,
                userAgent = cbUserAgent.isChecked
            )

            val generated = PayloadGenerator.generate(options)
            etPayloadResult.setText(generated)
            Toast.makeText(this, "Payload HTTP generado", Toast.LENGTH_SHORT).show()
        }

        btnGenerateWs.setOnClickListener {
            val bug = etBugHost.text.toString().trim()
            val generated = PayloadGenerator.generateWebSocket(bug)
            etPayloadResult.setText(generated)
            Toast.makeText(this, "Payload WebSocket generado", Toast.LENGTH_SHORT).show()
        }

        btnApplyPayload.setOnClickListener {
            val payload = etPayloadResult.text.toString()
            val resultIntent = Intent().apply {
                putExtra("payload", payload)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}
