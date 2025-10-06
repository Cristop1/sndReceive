package com.cuibluetooth.bleeconomy

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cuibluetooth.bleeconomy.databinding.ActivityLoginBinding
import com.cuibluetooth.bleeconomy.repository.AuthRepository
import com.cuibluetooth.bleeconomy.repository.PermitRepository
import com.cuibluetooth.bleeconomy.repository.SessionStore
import com.cuibluetooth.bleeconomy.viewmodel.LoginUiState
import com.cuibluetooth.bleeconomy.viewmodel.LoginViewModel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels {
        LoginViewModel.Factory(
            authRepository = AuthRepository(),
            permitRepository = PermitRepository(),
            sessionStore = SessionStore.getInstance(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.submitButton.setOnClickListener { attemptLogin() }
        binding.permitInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else {
                false
            }
        }

        listOf(binding.usernameInput, binding.passwordInput, binding.permitInput).forEach { editText ->
            editText.doAfterTextChanged {
                clearErrors()
                viewModel.clearError()
            }
        }

        observeState()
    }

    private fun attemptLogin() {
        val username = binding.usernameInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        val permit = binding.permitInput.text?.toString()?.trim().orEmpty()

        if (username.isBlank() || password.isBlank() || permit.isBlank()) {
            showError(getString(R.string.login_missing_fields))
            return
        }

        clearErrors()
        viewModel.submit(username, password, permit)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        LoginUiState.Idle -> showIdle()
                        LoginUiState.Loading -> showLoading()
                        is LoginUiState.Error -> showError(state.message)
                        is LoginUiState.Success -> handleSuccess(state)
                    }
                }
            }
        }
    }

    private fun showIdle() {
        binding.progress.isVisible = false
        binding.submitButton.isEnabled = true
    }

    private fun showLoading() {
        binding.errorText.isVisible = false
        binding.submitButton.isEnabled = false
        binding.progress.isVisible = true
    }

    private fun showError(message: String) {
        binding.progress.isVisible = false
        binding.submitButton.isEnabled = true
        binding.errorText.text = message
        binding.errorText.isVisible = true
    }

    private fun clearErrors() {
        binding.usernameInputLayout.error = null
        binding.passwordInputLayout.error = null
        binding.permitInputLayout.error = null
        binding.errorText.isVisible = false
    }

    private fun handleSuccess(state: LoginUiState.Success) {
        val session = state.session
        val data = Intent().apply {
            putExtra(EXTRA_USERNAME, session.username)
            putExtra(EXTRA_DISPLAY_NAME, session.displayName)
            putExtra(EXTRA_USER_UUID, session.userUuid)
            putExtra(EXTRA_ASSIGNED_UUID, session.assignedUuid)
            putExtra(EXTRA_PERMIT_CODE, session.permitCode)
            session.expiryEpochMillis?.let { putExtra(EXTRA_EXPIRY_EPOCH_MILLIS, it) }
        }
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val EXTRA_USER_UUID = "extra_user_uuid"
        const val EXTRA_ASSIGNED_UUID = "extra_assigned_uuid"
        const val EXTRA_PERMIT_CODE = "extra_permit_code"
        const val EXTRA_EXPIRY_EPOCH_MILLIS = "extra_expiry_epoch_millis"
    }

}