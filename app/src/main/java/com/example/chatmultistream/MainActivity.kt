package com.example.chatmultistream

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chatmultistream.data.local.ChatPreferences
import com.example.chatmultistream.model.ThemeMode
import com.example.chatmultistream.ui.screens.UnifiedChatScreen
import com.example.chatmultistream.ui.theme.ChatMultiStreamTheme
import com.example.chatmultistream.ui.viewmodels.ChatViewModel

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    /** Tema oscuro efectivo; se usa al reaplicar las barras del sistema. */
    private var darkThemeActive = true

    override fun attachBaseContext(newBase: Context) {
        // Aplica el idioma guardado antes de crear los recursos de la actividad.
        // Con "Sistema" se conserva el idioma del dispositivo.
        super.attachBaseContext(newBase.withAppLanguage())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkThemeActive = resolveDarkTheme()
        applySystemBarStyle(darkThemeActive)

        setContent {
            val themeMode by chatViewModel.themeMode.collectAsStateWithLifecycle()
            val highContrast by chatViewModel.highContrast.collectAsStateWithLifecycle()
            val fontScalePercent by chatViewModel.fontScalePercent.collectAsStateWithLifecycle()
            val shouldKeepScreenOn by chatViewModel.shouldKeepScreenOn.collectAsStateWithLifecycle()
            val language by chatViewModel.language.collectAsStateWithLifecycle()

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Textos y recursos en el idioma elegido, sin recrear la actividad.
            // Se parte de los recursos de la aplicación para que "Sistema"
            // vuelva siempre al idioma del dispositivo.
            val context = LocalContext.current
            val localizedResources = remember(context, language) {
                val systemContext = context.applicationContext ?: context
                context.localized(language, systemContext)
            }

            // Los iconos de las barras del sistema deben contrastar con el tema
            // elegido, no solo con el del dispositivo (por ejemplo: tema "Oscuro"
            // con el móvil en modo claro).
            LaunchedEffect(darkTheme) {
                applySystemBarStyle(darkTheme)
            }

            // Evitar que el teléfono se suspenda o apague la pantalla mientras haya streams conectados
            LaunchedEffect(shouldKeepScreenOn) {
                if (shouldKeepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            CompositionLocalProvider(LocalResources provides localizedResources) {
                ChatMultiStreamTheme(
                    darkTheme = darkTheme,
                    highContrast = highContrast,
                    fontScale = fontScalePercent / 100f
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        UnifiedChatScreen(viewModel = chatViewModel)
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Algunos fabricantes conservan el color claro de las barras tras
        // cambios de ventana; reaplicamos el estilo al recuperar el foco.
        if (hasFocus) applySystemBarStyle(darkThemeActive)
    }

    private fun resolveDarkTheme(): Boolean = when (ChatPreferences(this).getThemeMode()) {
        ThemeMode.SYSTEM -> {
            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode == Configuration.UI_MODE_NIGHT_YES
        }
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    private fun applySystemBarStyle(darkTheme: Boolean) {
        darkThemeActive = darkTheme
        val barStyle = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle
        )
    }
}
