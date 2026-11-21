package com.example.chatmultistream.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatmultistream.data.local.ChatPreferences
import com.example.chatmultistream.data.repositories.UnifiedChatRepository
import com.example.chatmultistream.model.AppLanguage
import com.example.chatmultistream.model.ChatEvent
import com.example.chatmultistream.model.ChatEventType
import com.example.chatmultistream.model.ChatMessage
import com.example.chatmultistream.model.ConnectionState
import com.example.chatmultistream.model.HistoryLimit
import com.example.chatmultistream.model.MessageRateLimit
import com.example.chatmultistream.model.Platform
import com.example.chatmultistream.model.TextScale
import com.example.chatmultistream.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: UnifiedChatRepository = UnifiedChatRepository()
) : AndroidViewModel(application) {

    private val preferences = ChatPreferences(application.applicationContext)

    // Saved channels and credentials per platform
    private val _savedChannels = MutableStateFlow<Map<Platform, String>>(
        Platform.entries.associateWith { preferences.getSavedChannel(it) }
    )
    val savedChannels: StateFlow<Map<Platform, String>> = _savedChannels.asStateFlow()

    private val _savedTokens = MutableStateFlow<Map<Platform, String?>>(
        Platform.entries.associateWith { preferences.getSavedToken(it) }
    )
    val savedTokens: StateFlow<Map<Platform, String?>> = _savedTokens.asStateFlow()

    val connectionStates: StateFlow<Map<Platform, ConnectionState>> = repository.connectionStates

    // Keep screen on preference & reactive state
    private val _keepScreenOnPreference = MutableStateFlow(preferences.isKeepScreenOnEnabled())
    val keepScreenOnPreference: StateFlow<Boolean> = _keepScreenOnPreference.asStateFlow()

    val shouldKeepScreenOn: StateFlow<Boolean> = combine(
        _keepScreenOnPreference,
        connectionStates
    ) { prefEnabled, states ->
        prefEnabled && states.any { it.value is ConnectionState.Connected || it.value is ConnectionState.Connecting }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val _selectedFilter = MutableStateFlow<Platform?>(null)
    val selectedFilter: StateFlow<Platform?> = _selectedFilter.asStateFlow()

    private val _isAutoScrollEnabled = MutableStateFlow(preferences.isAutoScrollEnabled())
    val isAutoScrollEnabled: StateFlow<Boolean> = _isAutoScrollEnabled.asStateFlow()

    private val _emotesEnabled = MutableStateFlow(preferences.isEmotesEnabled())
    val emotesEnabled: StateFlow<Boolean> = _emotesEnabled.asStateFlow()

    private val _quickTestBarEnabled = MutableStateFlow(preferences.isQuickTestBarEnabled())
    val quickTestBarEnabled: StateFlow<Boolean> = _quickTestBarEnabled.asStateFlow()

    // ---- Límites del chat ----

    private val _messageRateLimit = MutableStateFlow(preferences.getMessageRateLimit())
    val messageRateLimit: StateFlow<MessageRateLimit> = _messageRateLimit.asStateFlow()

    private val _historyLimit = MutableStateFlow(preferences.getHistoryLimit())
    val historyLimit: StateFlow<HistoryLimit> = _historyLimit.asStateFlow()

    private val _language = MutableStateFlow(preferences.getAppLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    // ---- Apariencia ----

    private val _themeMode = MutableStateFlow(preferences.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _highContrast = MutableStateFlow(preferences.isHighContrastEnabled())
    val highContrast: StateFlow<Boolean> = _highContrast.asStateFlow()

    private val _fontScalePercent = MutableStateFlow(preferences.getFontScalePercent())
    val fontScalePercent: StateFlow<Int> = _fontScalePercent.asStateFlow()

    // Filtered message stream
    val displayMessages: StateFlow<List<ChatMessage>> = combine(
        repository.chatMessages,
        _selectedFilter
    ) { messages, filter ->
        if (filter == null) {
            messages
        } else {
            messages.filter { it.platform == filter }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Aplica los límites guardados antes de conectar, para que el primer
        // lote ya respete el historial y el "modo lento".
        repository.setHistoryLimit(_historyLimit.value.messages)
        repository.setMessageRateLimit(_messageRateLimit.value.messagesPerSecond)

        // Auto-reconnect to channels that were previously configured and left active
        viewModelScope.launch {
            Platform.entries.forEach { platform ->
                val savedChannel = preferences.getSavedChannel(platform)
                val isAutoConnect = preferences.isAutoConnectEnabled(platform)
                if (isAutoConnect && savedChannel.isNotBlank()) {
                    val token = preferences.getSavedToken(platform)
                    repository.connect(platform, savedChannel, token)
                }
            }
        }
    }

    fun saveChannelDraft(platform: Platform, channel: String, token: String? = null) {
        val clean = channel.trim()
        preferences.saveChannel(platform, clean)
        preferences.saveToken(platform, token)
        _savedChannels.update { it + (platform to clean) }
        _savedTokens.update { it + (platform to token) }
    }

    fun connect(platform: Platform, channelName: String, token: String? = null) {
        saveChannelDraft(platform, channelName, token)
        preferences.setAutoConnectEnabled(platform, true)
        viewModelScope.launch {
            repository.connect(platform, channelName, token)
        }
    }

    fun disconnect(platform: Platform) {
        preferences.setAutoConnectEnabled(platform, false)
        viewModelScope.launch {
            repository.disconnect(platform)
        }
    }

    fun toggleKeepScreenOn() {
        val newVal = !_keepScreenOnPreference.value
        _keepScreenOnPreference.value = newVal
        preferences.setKeepScreenOnEnabled(newVal)
    }

    fun toggleAutoScroll() {
        val newVal = !_isAutoScrollEnabled.value
        _isAutoScrollEnabled.value = newVal
        preferences.setAutoScrollEnabled(newVal)
    }

    fun toggleEmotes() {
        val newVal = !_emotesEnabled.value
        _emotesEnabled.value = newVal
        preferences.setEmotesEnabled(newVal)
    }

    fun toggleQuickTestBar() {
        val newVal = !_quickTestBarEnabled.value
        _quickTestBarEnabled.value = newVal
        preferences.setQuickTestBarEnabled(newVal)
    }

    fun setMessageRateLimit(limit: MessageRateLimit) {
        if (_messageRateLimit.value == limit) return
        _messageRateLimit.value = limit
        preferences.setMessageRateLimit(limit)
        repository.setMessageRateLimit(limit.messagesPerSecond)
    }

    fun setHistoryLimit(limit: HistoryLimit) {
        if (_historyLimit.value == limit) return
        _historyLimit.value = limit
        preferences.setHistoryLimit(limit)
        repository.setHistoryLimit(limit.messages)
    }

    fun setLanguage(language: AppLanguage) {
        if (_language.value == language) return
        _language.value = language
        preferences.setAppLanguage(language)
    }

    fun setThemeMode(mode: ThemeMode) {
        if (_themeMode.value == mode) return
        _themeMode.value = mode
        preferences.setThemeMode(mode)
    }

    fun toggleHighContrast() {
        val newVal = !_highContrast.value
        _highContrast.value = newVal
        preferences.setHighContrastEnabled(newVal)
    }

    fun increaseFontScale() {
        updateFontScalePercent(_fontScalePercent.value + TextScale.STEP_PERCENT)
    }

    fun decreaseFontScale() {
        updateFontScalePercent(_fontScalePercent.value - TextScale.STEP_PERCENT)
    }

    private fun updateFontScalePercent(percent: Int) {
        val clamped = percent.coerceIn(TextScale.MIN_PERCENT, TextScale.MAX_PERCENT)
        if (clamped == _fontScalePercent.value) return
        _fontScalePercent.value = clamped
        preferences.setFontScalePercent(clamped)
    }

    fun setFilter(platform: Platform?) {
        _selectedFilter.value = platform
    }

    fun clearChat() {
        repository.clearMessages()
    }

    fun injectTestMessage(platform: Platform) {
        val (user, msg) = when (platform) {
            Platform.TWITCH -> "TwitchUser" to "¡GG en esa partida! PogChamp"
            Platform.YOUTUBE -> "YTFan" to "Gran directo, suscrito y con campana activada"
            Platform.KICK -> "KickChad" to "W stream bro, 100% hype"
            Platform.TIKTOK -> "TokLover" to "Envié 5 regalitos rosas 🌹✨"
        }
        repository.injectMessage(platform, user, msg)
    }

    /**
     * Publica un suceso de ejemplo con valores realistas para que el usuario
     * conozca las tarjetas destacadas sin esperar a que ocurran en directo.
     *
     * Cada tipo se asigna a la plataforma que realmente lo produce (YouTube
     * para Super Chat y membresías, Twitch para bits, raids y canjes, Kick
     * para subs regaladas y TikTok para regalos), de modo que el chip y el
     * color de la muestra coincidan con los de un suceso real. El texto queda
     * vacío cuando la tarjeta ya explica todo, igual que ocurre en producción.
     *
     * No pasa por los clientes ni por el "modo lento": es una prueba manual.
     */
    fun injectTestEvent(type: ChatEventType) {
        val platform: Platform
        val user: String
        val text: String
        val event: ChatEvent

        // El `when` cubre todos los tipos y asigna las tres variables: si se
        // añade un ChatEventType nuevo, el compilador obliga a definir su
        // muestra en lugar de dejar un caso sin probar.
        when (type) {
            ChatEventType.SUBSCRIPTION -> {
                platform = Platform.TWITCH
                user = "TwitchUser"
                text = ""
                event = ChatEvent(type, count = 6)
            }
            ChatEventType.GIFT_SUBSCRIPTION -> {
                platform = Platform.KICK
                user = "KickChad"
                text = ""
                event = ChatEvent(type, count = 3, recipient = "Ana")
            }
            ChatEventType.CHEER -> {
                platform = Platform.TWITCH
                user = "BitFan"
                text = "¡Toma 500 bits!"
                event = ChatEvent(type, count = 500)
            }
            ChatEventType.DONATION -> {
                platform = Platform.YOUTUBE
                user = "YTFan"
                text = "¡Gran directo!"
                event = ChatEvent(type, amountText = "$5.00")
            }
            ChatEventType.SUPER_STICKER -> {
                platform = Platform.YOUTUBE
                user = "StickerFan"
                text = ""
                event = ChatEvent(type, amountText = "$2.00")
            }
            ChatEventType.MEMBERSHIP -> {
                platform = Platform.YOUTUBE
                user = "MemberFan"
                text = ""
                event = ChatEvent(type)
            }
            ChatEventType.GIFT_MEMBERSHIP -> {
                platform = Platform.YOUTUBE
                user = "GifterPro"
                text = ""
                event = ChatEvent(type, count = 10)
            }
            ChatEventType.RAID -> {
                platform = Platform.TWITCH
                user = "StreamerX"
                text = ""
                event = ChatEvent(type, count = 250)
            }
            ChatEventType.REDEMPTION -> {
                platform = Platform.TWITCH
                user = "TwitchUser"
                text = "Canje con puntos del canal"
                event = ChatEvent(type)
            }
            ChatEventType.HIGHLIGHT -> {
                platform = Platform.TWITCH
                user = "ModUser"
                text = "Mensaje destacado por el moderador"
                event = ChatEvent(type)
            }
            ChatEventType.ANNOUNCEMENT -> {
                platform = Platform.TWITCH
                user = "Streamer"
                text = "¡Empezamos en 5 minutos!"
                event = ChatEvent(type)
            }
            ChatEventType.GIFT -> {
                platform = Platform.TIKTOK
                user = "TokLover"
                text = ""
                event = ChatEvent(type, count = 5, giftName = "Rosa")
            }
        }

        repository.injectMessage(platform, user, text, event)
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}
