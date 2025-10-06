package com.cuibluetooth.bleeconomy.mqtt

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cuibluetooth.bleeconomy.R
import com.cuibluetooth.bleeconomy.databinding.ActivityStreamingSettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StreamingSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStreamingSettingsBinding
    private lateinit var settingsStore: StreamingSettingsStore
    private var isUpdatingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStreamingSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settingsStore = StreamingSettingsStore.getInstance(applicationContext)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.switchEnableStreaming.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                lifecycleScope.launch { settingsStore.setEnabled(isChecked) }
            }
        }

        binding.switchUseTls.setOnCheckedChangeListener { _, _ ->
            // handled on save
        }

        binding.buttonSave.setOnClickListener {
            saveSettings()
        }

        binding.inputHost.doAfterTextChanged { binding.layoutHost.error = null }
        binding.inputPort.doAfterTextChanged { binding.layoutPort.error = null }
        binding.inputTopic.doAfterTextChanged { binding.layoutTopic.error = null }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsStore.settingsFlow.collectLatest { settings ->
                    isUpdatingUi = true
                    binding.switchEnableStreaming.isChecked = settings.enabled
                    binding.switchUseTls.isChecked = settings.useTls
                    binding.inputHost.setText(settings.host)
                    binding.inputPort.setText(settings.port.toString())
                    binding.inputTopic.setText(settings.topic)
                    binding.inputUsername.setText(settings.username ?: "")
                    binding.inputPassword.setText(settings.password ?: "")
                    isUpdatingUi = false
                }
            }
        }
    }
    private fun saveSettings() {
        val host = binding.inputHost.text?.toString()?.trim().orEmpty()
        val portText = binding.inputPort.text?.toString()?.trim().orEmpty()
        val topic = binding.inputTopic.text?.toString()?.trim().orEmpty()
        val username = binding.inputUsername.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString()?.trim().orEmpty()
        var hasError = false
        if (host.isEmpty()) {
            binding.layoutHost.error = getString(R.string.imu_settings_host_error)
            hasError = true
        }
        val port = portText.toIntOrNull()
        if (port == null || port <= 0 || port > 65535) {
            binding.layoutPort.error = getString(R.string.imu_settings_port_error)
            hasError = true
        }
        if (topic.isEmpty()) {
            binding.layoutTopic.error = getString(R.string.imu_settings_topic_error)
            hasError = true
        }
        if (hasError) return
        val resolvedPort = port ?: return

        lifecycleScope.launch {
            settingsStore.update {
                copy(
                    host = host,
                    port = resolvedPort,
                    useTls = binding.switchUseTls.isChecked,
                    topic = topic,
                    username = username.ifBlank { null },
                    password = password.ifBlank { null }
                )
            }
            Toast.makeText(this@StreamingSettingsActivity, R.string.imu_settings_saved, Toast.LENGTH_SHORT).show()
        }
    }
}