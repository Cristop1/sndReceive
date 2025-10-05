package com.cuibluetooth.bleeconomy

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cuibluetooth.bleeconomy.databinding.ActivityLoginBinding
import com.cuibluetooth.bleeconomy.viewmodel.LoginViewModel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.submitButton.setOnClickListener {
            val permit = binding.permitCodeInput.text?.toString()?.trim().orEmpty()
            val uuid = binding.uuidInput.text?.toString()?.trim().orEmpty()
            viewModel.verifyPermit(permit, uuid)
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val isLoading = state.isLoading

                        binding.permitCodeLayout.isEnabled = !isLoading
                        binding.uuidLayout.isEnabled = !isLoading
                        binding.submitButton.isEnabled = !isLoading
                        binding.loadingIndicator.isVisible = isLoading

                        val statusText = if (isLoading) "" else state.statusText.orEmpty()
                        binding.statusText.text = statusText
                        binding.statusText.isVisible = statusText.isNotEmpty()

                        binding.permitCodeLayout.error = if (isLoading) null else state.permitCodeError
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is LoginViewModel.Event.Success -> {
                                val resultIntent = Intent().apply {
                                    putExtra(EXTRA_PERMIT_CODE, event.permitCode)
                                    putExtra(EXTRA_ASSIGNED_UUID, event.assignedUuid)
                                    event.expiryEpochMillis?.let { expiry ->
                                        putExtra(EXTRA_EXPIRY_EPOCH_MILLIS, expiry)
                                    }
                                }
                                setResult(RESULT_OK, resultIntent)
                                finish()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PERMIT_CODE = "extra_permit_code"
        const val EXTRA_ASSIGNED_UUID = "extra_assigned_uuid"
        const val EXTRA_EXPIRY_EPOCH_MILLIS = "extra_expiry_epoch_millis"
    }
}