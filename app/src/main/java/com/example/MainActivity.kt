package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifisentinel.WifiSentinelApplication
import com.wifisentinel.di.SentinelViewModelFactory
import com.wifisentinel.presentation.components.AppLockOverlay
import com.wifisentinel.presentation.dashboard.DashboardViewModel
import com.wifisentinel.presentation.navigation.SentinelNavShell
import com.wifisentinel.presentation.networks.NetworksViewModel
import com.wifisentinel.presentation.securitylab.SecurityLabViewModel
import com.wifisentinel.presentation.settings.SettingsViewModel
import com.wifisentinel.presentation.theme.WifiSentinelTheme
import com.wifisentinel.presentation.wordlists.WordlistsViewModel

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        val container = (application as WifiSentinelApplication).container
        val factory = SentinelViewModelFactory(container)

        val dashboardViewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]
        val networksViewModel = ViewModelProvider(this, factory)[NetworksViewModel::class.java]
        val wordlistsViewModel = ViewModelProvider(this, factory)[WordlistsViewModel::class.java]
        val securityLabViewModel = ViewModelProvider(this, factory)[SecurityLabViewModel::class.java]
        val settingsViewModel = ViewModelProvider(this, factory)[SettingsViewModel::class.java]

        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            var isUnlocked by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(settings.blockScreenshots) {
                if (settings.blockScreenshots) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(settings.darkTheme) {
                if (settings.darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    )
                }
            }

            val requiresLock = settings.appLockEnabled &&
                (settings.appLockPinHash != null || settings.biometricProtectionEnabled) &&
                !isUnlocked

            WifiSentinelTheme(
                darkTheme = settings.darkTheme,
                language = settings.language
            ) {
                if (requiresLock) {
                    LaunchedEffect(settings.biometricProtectionEnabled) {
                        if (settings.biometricProtectionEnabled) {
                            launchBiometricPrompt(onSuccess = { isUnlocked = true })
                        }
                    }
                    AppLockOverlay(
                        hasPin = settings.appLockPinHash != null,
                        biometricEnabled = settings.biometricProtectionEnabled,
                        onVerifyPin = { pin -> settingsViewModel.verifyPin(pin) },
                        onRequestBiometric = { launchBiometricPrompt(onSuccess = { isUnlocked = true }) },
                        onUnlocked = { isUnlocked = true }
                    )
                } else {
                    SentinelNavShell(
                        settings = settings,
                        dashboardViewModel = dashboardViewModel,
                        networksViewModel = networksViewModel,
                        wordlistsViewModel = wordlistsViewModel,
                        securityLabViewModel = securityLabViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }

    private fun launchBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("WiFi Sentinel")
            .setSubtitle("Authenticate to access local security workspace")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: Exception) {
            // Device may lack biometric hardware; PIN fallback remains available.
        }
    }
}
