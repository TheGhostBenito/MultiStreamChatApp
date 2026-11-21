package com.example.chatmultistream

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import com.example.chatmultistream.data.local.ChatPreferences
import com.example.chatmultistream.model.AppLanguage

/**
 * Envuelve el contexto base de la actividad con el idioma elegido por el
 * usuario. Al construir la actividad con ese contexto, todos los recursos
 * (`stringResource`, textos del sistema, etc.) se resuelven en ese idioma.
 *
 * Con [AppLanguage.SYSTEM] se deja el contexto tal cual para que Android use la
 * configuración del dispositivo.
 */
fun Context.withAppLanguage(): Context = withAppLanguage(ChatPreferences(this).getAppLanguage())

fun Context.withAppLanguage(language: AppLanguage): Context {
    val locale = language.locale ?: return this
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    return createConfigurationContext(configuration)
}

/**
 * Recursos resueltos en el idioma elegido **sin recrear la actividad**.
 *
 * Compose resuelve `stringResource` a través de `LocalResources`, así que
 * basta con aportar unos [Resources] con la configuración correcta. Esto evita
 * el parpadeo y los efectos secundarios de `recreate()` (barras del sistema
 * desincronizadas en algunos fabricantes, pérdida del scroll, etc.).
 *
 * [systemContext] es el contexto del dispositivo: se usa como base para no
 * arrastrar un idioma forzado anteriormente y para resolver "Sistema".
 */
fun Context.localized(
    language: AppLanguage,
    systemContext: Context = this
): Resources {
    val configuration = Configuration(systemContext.resources.configuration)
    language.locale?.let(configuration::setLocale)
    return createConfigurationContext(configuration).resources
}

/** Busca la actividad real aunque el contexto esté envuelto por Compose. */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
