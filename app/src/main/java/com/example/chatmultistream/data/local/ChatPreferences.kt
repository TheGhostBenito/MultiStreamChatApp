package com.example.chatmultistream.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.chatmultistream.model.AppLanguage
import com.example.chatmultistream.model.HistoryLimit
import com.example.chatmultistream.model.MessageRateLimit
import com.example.chatmultistream.model.Platform
import com.example.chatmultistream.model.TextScale
import com.example.chatmultistream.model.ThemeMode

class ChatPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedChannel(platform: Platform): String {
        val defaultVal = when (platform) {
            Platform.TWITCH -> ""
            Platform.YOUTUBE -> ""
            Platform.KICK -> ""
            Platform.TIKTOK -> ""
        }
        return prefs.getString(KEY_CHANNEL_PREFIX + platform.name, defaultVal) ?: defaultVal
    }

    fun saveChannel(platform: Platform, channel: String) {
        prefs.edit().putString(KEY_CHANNEL_PREFIX + platform.name, channel.trim()).apply()
    }

    fun getSavedToken(platform: Platform): String? {
        return prefs.getString(KEY_TOKEN_PREFIX + platform.name, null)?.takeIf { it.isNotBlank() }
    }

    fun saveToken(platform: Platform, token: String?) {
        val cleanToken = token?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().putString(KEY_TOKEN_PREFIX + platform.name, cleanToken).apply()
    }

    fun isAutoConnectEnabled(platform: Platform): Boolean {
        // Por defecto, si el usuario ya guardó un canal, auto-conectar
        return prefs.getBoolean(KEY_AUTOCONNECT_PREFIX + platform.name, false)
    }

    fun setAutoConnectEnabled(platform: Platform, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOCONNECT_PREFIX + platform.name, enabled).apply()
    }

    fun isKeepScreenOnEnabled(): Boolean {
        return prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
    }

    fun setKeepScreenOnEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }

    fun isAutoScrollEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SCROLL, true)
    }

    fun setAutoScrollEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SCROLL, enabled).apply()
    }

    fun isEmotesEnabled(): Boolean {
        return prefs.getBoolean(KEY_EMOTES_ENABLED, true)
    }

    fun setEmotesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMOTES_ENABLED, enabled).apply()
    }

    fun isQuickTestBarEnabled(): Boolean {
        return prefs.getBoolean(KEY_QUICK_TEST_BAR_ENABLED, true)
    }

    fun setQuickTestBarEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUICK_TEST_BAR_ENABLED, enabled).apply()
    }

    // ---- Límites del chat ----

    fun getMessageRateLimit(): MessageRateLimit {
        val raw = prefs.getString(KEY_MESSAGE_RATE_LIMIT, null)
        return MessageRateLimit.fromName(raw)
    }

    fun setMessageRateLimit(limit: MessageRateLimit) {
        prefs.edit().putString(KEY_MESSAGE_RATE_LIMIT, limit.name).apply()
    }

    fun getHistoryLimit(): HistoryLimit {
        val raw = prefs.getString(KEY_HISTORY_LIMIT, null)
        return HistoryLimit.fromName(raw)
    }

    fun setHistoryLimit(limit: HistoryLimit) {
        prefs.edit().putString(KEY_HISTORY_LIMIT, limit.name).apply()
    }

    // ---- Idioma ----

    fun getAppLanguage(): AppLanguage {
        val raw = prefs.getString(KEY_APP_LANGUAGE, null)
        return AppLanguage.fromName(raw)
    }

    fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
    }

    // ---- Apariencia ----

    fun getThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun isHighContrastEnabled(): Boolean {
        return prefs.getBoolean(KEY_HIGH_CONTRAST, false)
    }

    fun setHighContrastEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
    }

    fun getFontScalePercent(): Int {
        return prefs.getInt(KEY_FONT_SCALE_PERCENT, TextScale.DEFAULT_PERCENT)
            .coerceIn(TextScale.MIN_PERCENT, TextScale.MAX_PERCENT)
    }

    fun setFontScalePercent(percent: Int) {
        prefs.edit()
            .putInt(KEY_FONT_SCALE_PERCENT, percent.coerceIn(TextScale.MIN_PERCENT, TextScale.MAX_PERCENT))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "multistream_chat_prefs"
        private const val KEY_CHANNEL_PREFIX = "channel_"
        private const val KEY_TOKEN_PREFIX = "token_"
        private const val KEY_AUTOCONNECT_PREFIX = "autoconnect_"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_AUTO_SCROLL = "auto_scroll"
        private const val KEY_EMOTES_ENABLED = "emotes_enabled"
        private const val KEY_QUICK_TEST_BAR_ENABLED = "quick_test_bar_enabled"
        private const val KEY_MESSAGE_RATE_LIMIT = "message_rate_limit"
        private const val KEY_HISTORY_LIMIT = "history_limit"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_FONT_SCALE_PERCENT = "font_scale_percent"
    }
}
