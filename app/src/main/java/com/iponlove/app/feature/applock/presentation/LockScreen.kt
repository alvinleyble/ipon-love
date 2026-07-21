package com.iponlove.app.feature.applock.presentation

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.iponlove.app.R
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    isBiometricEnabled: Boolean,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // The PIN pad is the permanent fallback and is NEVER removed. When biometric is enabled the
    // lock opens in "biometric mode" — the pad is visually deferred behind the system biometric
    // prompt — but every non-success biometric outcome flips this true so the user is never locked
    // out of their own app (ADR-0023). rememberSaveable keeps the user on the pad across a rotation
    // once they've fallen back to it.
    var showPinPad by rememberSaveable { mutableStateOf(!isBiometricEnabled) }
    // Message shown under the pad for biometric outcomes that deserve an explanation: the OS's own
    // ERROR_LOCKOUT/ERROR_LOCKOUT_PERMANENT (item 8), and any unexpected hardware/system error —
    // silently swallowing those made a failed prompt indistinguishable from "the button does
    // nothing" (item 6).
    var biometricMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    var autoPromptAttempts by remember { mutableIntStateOf(0) }

    fun launchBiometric() {
        val activity = context.findActivity() ?: run {
            Log.w(TAG, "no FragmentActivity found in context chain, falling back to PIN")
            showPinPad = true
            return
        }
        clearStuckBiometricPromptFlag(activity)
        showBiometricPrompt(
            activity,
            onSuccess = viewModel::onBiometricSuccess,
            onSystemCanceled = {
                // ERROR_CANCELED is the *system* killing the prompt, not the user — seen live on
                // Alvin's Nothing Phone 2a as a ~0.1s flash right after keyguard dismissal, when
                // the (under-display) sensor is still held by the lock-screen transition. Falling
                // to PIN here would make a transient OS race look like a user choice; retry after
                // the transition settles instead, bounded by the shared attempt cap.
                if (autoPromptAttempts < MAX_AUTO_PROMPT_ATTEMPTS) {
                    autoPromptAttempts++
                    scope.launch {
                        delay(SYSTEM_CANCEL_RETRY_DELAY_MS)
                        if (!showPinPad) {
                            Log.d(TAG, "retrying after system cancel (attempt $autoPromptAttempts)")
                            launchBiometric()
                        }
                    }
                } else {
                    showPinPad = true
                }
            },
            onFallbackToPin = { message ->
                showPinPad = true
                biometricMessage = message
            },
        )
    }

    // Auto-prompt whenever the lock window gains focus while still in biometric mode, not once at
    // mount (item 6): the 30s auto-lock flips isLocked while the app is backgrounded, so this
    // Dialog can mount after onSaveInstanceState() — where BiometricPrompt.authenticate() silently
    // no-ops (verified against biometric 1.1.0 bytecode) and a mount-time one-shot never retries.
    // A focused window is by definition foregrounded (never state-saved), and re-arming on each
    // focus regain also recovers from OEM cancel paths that dismiss the prompt without delivering
    // any callback. No cancel→prompt→cancel loop is possible: every callback-delivered non-success
    // outcome flips showPinPad, which stops the re-arm; the attempt cap bounds the no-callback case.
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(isBiometricEnabled) {
        if (!isBiometricEnabled) return@LaunchedEffect
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused ->
            if (focused && !showPinPad && autoPromptAttempts < MAX_AUTO_PROMPT_ATTEMPTS) {
                autoPromptAttempts++
                Log.d(TAG, "auto-launching biometric prompt (attempt $autoPromptAttempts)")
                launchBiometric()
            }
        }
    }

    val colors = LocalPlayfulColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .playfulBackground()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
            Spacer(Modifier.height(80.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (showPinPad) "Enter your PIN" else "Unlock to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }

            if (showPinPad) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PinDots(length = uiState.pin.length)
                    Spacer(Modifier.height(8.dp))
                    val message = uiState.error ?: biometricMessage
                    if (message != null) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = colors.accent,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 48.dp),
            ) {
                if (showPinPad) {
                    Numpad(
                        onDigit = { digit -> biometricMessage = null; viewModel.onDigit(digit) },
                        onDelete = viewModel::onDelete,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (isBiometricEnabled) {
                        TextButton(onClick = { launchBiometric() }) {
                            Text("Use biometric")
                        }
                    }
                } else {
                    Button(onClick = { showPinPad = true }) {
                        Text("Use PIN instead")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = viewModel::showForgotPin) {
                    Text("Forgot PIN")
                }
            }
    }

    if (uiState.showForgotPinDialog) {
        ForgotPinDialog(
            email = uiState.forgotPinEmail,
            password = uiState.forgotPinPassword,
            isLoading = uiState.isForgotPinLoading,
            error = uiState.forgotPinError,
            onEmailChange = viewModel::onForgotPinEmailChange,
            onPasswordChange = viewModel::onForgotPinPasswordChange,
            onConfirm = viewModel::submitForgotPin,
            onDismiss = viewModel::dismissForgotPin,
        )
    }
}

@Composable
private fun ForgotPinDialog(
    email: String,
    password: String,
    isLoading: Boolean,
    error: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify your identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter your email and password to reset your PIN.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Verify & Reset PIN")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * compose-ui 1.11.3's Dialog implementation (`DialogWrapper`) wraps the host Activity's context in
 * a `ContextThemeWrapper` before handing it to the platform Dialog — confirmed by decompiling the
 * installed `ui-android:1.11.3` bytecode (`DialogWrapper.<init>` constructs
 * `ContextThemeWrapper(composeView.context, themeResId)`). `LocalContext.current` inside the lock
 * Dialog's content (this screen) is therefore that wrapper, not `MainActivity` itself — a plain
 * `context as? FragmentActivity` cast silently returns null on every device, with no exception and
 * no log, which was item 6/7's real root cause (a version-specific regression versus the
 * compose-ui 1.7.5 an earlier investigation had checked). Unwrapping the `ContextWrapper` chain
 * finds the real Activity underneath.
 */
private tailrec fun Context.findActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onSystemCanceled: () -> Unit,
    onFallbackToPin: (lockoutMessage: String?) -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        // Every non-success terminal outcome — negative button ("Use PIN"), user cancel, hardware
        // error, ERROR_LOCKOUT / ERROR_LOCKOUT_PERMANENT — must surface the PIN pad. Biometric is a
        // convenience layer over the PIN, never a replacement; wiring only onAuthenticationSucceeded
        // (the old behaviour) would lock the user out on any biometric failure (ADR-0023). The OS's
        // own lockout codes (item 8) get a distinct message, and unexpected errors surface the OS's
        // error string — only deliberate dismissals fall back silently (item 6).
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            Log.w(TAG, "biometric error $errorCode: $errString")
            when (errorCode) {
                // System-initiated cancel (keyguard transition, sensor handoff, focus race) —
                // retryable, distinct from the user-initiated codes below.
                BiometricPrompt.ERROR_CANCELED -> onSystemCanceled()
                BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                    onFallbackToPin("Biometric temporarily locked out — use your PIN")
                BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_USER_CANCELED ->
                    onFallbackToPin(null)
                else -> onFallbackToPin("Biometric unavailable: $errString")
            }
        }

        // A single unrecognized fingerprint leaves the system prompt open for more tries; surfacing
        // the PIN pad underneath is harmless and keeps the fallback one tap (the negative button)
        // away.
        override fun onAuthenticationFailed() {
            onFallbackToPin(null)
        }
    }
    Log.d(TAG, "launching biometric prompt")
    BiometricPrompt(activity, executor, callback).authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock ${activity.getString(R.string.app_name)}")
            .setNegativeButtonText("Use PIN")
            .build(),
    )
}

/**
 * biometric 1.1.0 keeps an isPromptShowing flag in an Activity-scoped BiometricViewModel, reset
 * only when a dismissal callback is actually delivered. Some cancel paths never deliver one (app
 * backgrounded with the prompt up, OEM focus-change dismissals) — after which every authenticate()
 * call silently returns at showPromptForAuthentication()'s isPromptShowing guard for the life of
 * the Activity, with no log and no callback (verified against the 1.1.0 bytecode; item 6's "Use
 * biometric does nothing"). This is only called right before launching from a focused lock window:
 * if our window has focus, no system prompt can actually be up, so a true flag is provably stale.
 * Reflection failure is non-fatal — we just proceed with the launch as before.
 */
private fun clearStuckBiometricPromptFlag(activity: FragmentActivity) {
    runCatching {
        @Suppress("UNCHECKED_CAST")
        val clazz = Class.forName("androidx.biometric.BiometricViewModel") as Class<ViewModel>
        val viewModel = ViewModelProvider(activity)[clazz]
        val isShowing = clazz.getDeclaredMethod("isPromptShowing")
            .apply { isAccessible = true }
            .invoke(viewModel) as Boolean
        if (isShowing) {
            Log.w(TAG, "clearing stuck BiometricViewModel.isPromptShowing")
            clazz.getDeclaredMethod("setPromptShowing", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(viewModel, false)
        }
    }.onFailure { Log.w(TAG, "stuck-prompt-flag check failed", it) }
}

private const val TAG = "LockScreen"

// Bounds both the focus-regain re-arm (no-callback case) and the ERROR_CANCELED retry loop;
// user-initiated fallbacks stop everything via showPinPad long before the cap is reached.
private const val MAX_AUTO_PROMPT_ATTEMPTS = 5

// Long enough for a keyguard-dismissal / app-transition animation to release the fingerprint
// sensor before the retry (the ~0.1s-flash-then-cancel seen on the Nothing Phone 2a).
private const val SYSTEM_CANCEL_RETRY_DELAY_MS = 500L
