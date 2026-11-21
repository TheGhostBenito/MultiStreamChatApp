package com.example.chatmultistream.model

import java.util.Locale

/**
 * Idioma de la interfaz elegido en los ajustes.
 *
 * [SYSTEM] deja que Android use el idioma del dispositivo: si es español se
 * carga `values-es`, y para cualquier otro idioma se usa el inglés, que es el
 * recurso por defecto.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    SPANISH("es"),
    ENGLISH("en");

    val locale: Locale? get() = tag?.let(Locale::forLanguageTag)

    companion object {
        val DEFAULT: AppLanguage = SYSTEM

        fun fromName(name: String?): AppLanguage =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
