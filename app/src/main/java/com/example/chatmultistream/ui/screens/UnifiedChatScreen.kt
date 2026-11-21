package com.example.chatmultistream.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.chatmultistream.R
import com.example.chatmultistream.findActivity
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
import com.example.chatmultistream.ui.theme.LocalDarkTheme
import com.example.chatmultistream.ui.theme.LocalHighContrast
import com.example.chatmultistream.ui.theme.eventAccentColor
import com.example.chatmultistream.ui.viewmodels.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.displayMessages.collectAsStateWithLifecycle()
    val connectionStates by viewModel.connectionStates.collectAsStateWithLifecycle()
    val savedChannels by viewModel.savedChannels.collectAsStateWithLifecycle()
    val savedTokens by viewModel.savedTokens.collectAsStateWithLifecycle()
    val activeFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val isAutoScrollEnabled by viewModel.isAutoScrollEnabled.collectAsStateWithLifecycle()
    val emotesEnabled by viewModel.emotesEnabled.collectAsStateWithLifecycle()
    val quickTestBarEnabled by viewModel.quickTestBarEnabled.collectAsStateWithLifecycle()
    val messageRateLimit by viewModel.messageRateLimit.collectAsStateWithLifecycle()
    val historyLimit by viewModel.historyLimit.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val highContrast by viewModel.highContrast.collectAsStateWithLifecycle()
    val fontScalePercent by viewModel.fontScalePercent.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOnPreference.collectAsStateWithLifecycle()
    val shouldKeepScreenOn by viewModel.shouldKeepScreenOn.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    // rememberSaveable: el diálogo sigue abierto si el sistema recrea la
    // actividad (por ejemplo al girar el móvil).
    var showConnectionDialog by rememberSaveable { mutableStateOf(false) }

    // Follow mode: true while the user is at (or returns to) the bottom of the list.
    // It is disabled when the user drags the list up, so incoming messages don't yank
    // them away from what they are reading.
    var isFollowing by remember { mutableStateOf(true) }
    var unseenMessages by remember { mutableIntStateOf(0) }
    var knownMessageCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start && listState.canScrollForward) {
                isFollowing = false
            }
        }
    }

    // Re-enable follow as soon as the user reaches the bottom again.
    LaunchedEffect(listState) {
        snapshotFlow { !listState.canScrollForward }.collect { atBottom ->
            if (atBottom) {
                isFollowing = true
                unseenMessages = 0
            }
        }
    }

    // Auto-scroll when new messages arrive if enabled and the user is following the stream.
    LaunchedEffect(messages.size, isAutoScrollEnabled, isFollowing) {
        val previousCount = knownMessageCount
        knownMessageCount = messages.size

        if (messages.isEmpty()) {
            unseenMessages = 0
            return@LaunchedEffect
        }

        if (isAutoScrollEnabled && isFollowing) {
            // requestScrollToItem no suspende y se agrupa con el siguiente
            // remeasure; evita un salto por cada mensaje en chats rápidos.
            listState.requestScrollToItem(messages.lastIndex)
            unseenMessages = 0
        } else if (messages.size > previousCount) {
            unseenMessages += messages.size - previousCount
        } else if (messages.size < previousCount) {
            unseenMessages = 0
        }
    }

    // Changing the platform filter starts a fresh view at the latest messages.
    LaunchedEffect(activeFilter) {
        unseenMessages = 0
        isFollowing = true
        if (messages.isNotEmpty()) {
            listState.requestScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val connectedCount = connectionStates.count { it.value is ConnectionState.Connected }
                        val messagesLabel = stringResource(R.string.status_messages, messages.size)
                        val activeLabel = pluralStringResource(
                            R.plurals.status_active,
                            connectedCount,
                            connectedCount
                        )
                        val screenOnLabel = stringResource(R.string.status_screen_on)
                        Text(
                            text = messagesLabel + " • " + activeLabel +
                                    (if (shouldKeepScreenOn) " • $screenOnLabel" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    // Toggle para mantener la pantalla siempre encendida
                    IconButton(onClick = { viewModel.toggleKeepScreenOn() }) {
                        Icon(
                            imageVector = if (keepScreenOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                            contentDescription = stringResource(R.string.screen_always_on),
                            tint = if (shouldKeepScreenOn) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.clear_chat)
                        )
                    }
                    IconButton(onClick = { showConnectionDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.configure_connections)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            val showJumpToLatest = messages.isNotEmpty() && (!isAutoScrollEnabled || !isFollowing)
            AnimatedVisibility(
                visible = showJumpToLatest,
                enter = fadeIn(tween(durationMillis = 150)) +
                        scaleIn(initialScale = 0.85f, animationSpec = tween(durationMillis = 180)),
                exit = fadeOut(tween(durationMillis = 120)) +
                        scaleOut(targetScale = 0.85f, animationSpec = tween(durationMillis = 120))
            ) {
                FloatingActionButton(
                    onClick = {
                        if (!isAutoScrollEnabled) {
                            viewModel.toggleAutoScroll()
                        }
                        isFollowing = true
                        unseenMessages = 0
                        if (messages.isNotEmpty()) {
                            listState.requestScrollToItem(messages.lastIndex)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.jump_to_latest)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (unseenMessages > 0) {
                                pluralStringResource(
                                    R.plurals.new_messages,
                                    unseenMessages,
                                    unseenMessages
                                )
                            } else {
                                stringResource(R.string.jump_to_latest)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Platform filter and status chips
            PlatformFilterRow(
                activeFilter = activeFilter,
                connectionStates = connectionStates,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            HorizontalDivider()

            // Chat stream list
            if (messages.isEmpty()) {
                // El marcador de "sin mensajes" debe ocupar solo el espacio
                // restante (weight). Si se mide a fillMaxSize dentro del
                // Column, consume toda la altura y deja la barra de
                // simulación con 0 px: seguía ahí, pero invisible.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    EmptyChatPlaceholder(
                        onOpenConfig = { showConnectionDialog = true }
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = messages,
                        key = { _, message -> message.id },
                        contentType = { _, _ -> "chat_message" }
                    ) { index, message ->
                        ChatMessageItem(
                            message = message,
                            emotesEnabled = emotesEnabled,
                            // Solo animamos la aparición cuando el usuario está
                            // siguiendo el chat y el mensaje llega aislado. En una
                            // ráfaga (varios mensajes seguidos) todos aparecen de
                            // golpe: encadenar decenas de animaciones superpuestas
                            // producía huecos y tarjetas que se "aplastaban".
                            animateAppearance = isFollowing && isIsolatedArrival(messages, index),
                            // Sin animaciones de reubicación ni de salida: al
                            // recortar el historial el ítem que desaparece se
                            // seguía dibujando encima del siguiente durante
                            // unos milisegundos (se veía como solapamiento).
                            // La aparición ya se anima dentro de ChatMessageItem.
                            modifier = Modifier
                        )
                    }
                }
            }

            // Quick simulation toolbar at bottom (opcional desde Ajustes)
            if (quickTestBarEnabled) {
                QuickTestBar(
                    onSendSample = { platform -> viewModel.injectTestMessage(platform) },
                    onSendEvent = { type -> viewModel.injectTestEvent(type) }
                )
            }
        }
    }

    if (showConnectionDialog) {
        ConnectionConfigDialog(
            connectionStates = connectionStates,
            savedChannels = savedChannels,
            savedTokens = savedTokens,
            keepScreenOn = keepScreenOn,
            emotesEnabled = emotesEnabled,
            quickTestBarEnabled = quickTestBarEnabled,
            messageRateLimit = messageRateLimit,
            historyLimit = historyLimit,
            themeMode = themeMode,
            language = language,
            highContrast = highContrast,
            fontScalePercent = fontScalePercent,
            onToggleKeepScreenOn = { viewModel.toggleKeepScreenOn() },
            onToggleEmotes = { viewModel.toggleEmotes() },
            onToggleQuickTestBar = { viewModel.toggleQuickTestBar() },
            onMessageRateLimitSelected = { viewModel.setMessageRateLimit(it) },
            onHistoryLimitSelected = { viewModel.setHistoryLimit(it) },
            onThemeModeSelected = { viewModel.setThemeMode(it) },
            onToggleHighContrast = { viewModel.toggleHighContrast() },
            onDecreaseFontScale = { viewModel.decreaseFontScale() },
            onIncreaseFontScale = { viewModel.increaseFontScale() },
            onConnect = { platform, channel, token ->
                viewModel.connect(platform, channel, token)
            },
            onDisconnect = { platform ->
                viewModel.disconnect(platform)
            },
            onSaveDraft = { platform, channel, token ->
                viewModel.saveChannelDraft(platform, channel, token)
            },
            onLanguageSelected = { selected ->
                // El idioma se aplica al instante: MainActivity aporta los
                // recursos localizados a Compose, sin recrear la actividad.
                viewModel.setLanguage(selected)
            },
            onDismiss = { showConnectionDialog = false }
        )
    }
}

@Composable
private fun PlatformFilterRow(
    activeFilter: Platform?,
    connectionStates: Map<Platform, ConnectionState>,
    onFilterSelected: (Platform?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "all") {
            FilterChip(
                selected = activeFilter == null,
                onClick = { onFilterSelected(null) },
                label = {
                    Text(
                        text = stringResource(R.string.filter_all),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }

        items(items = Platform.entries, key = { it.name }) { platform ->
            val isConnected = connectionStates[platform] is ConnectionState.Connected
            FilterChip(
                selected = activeFilter == platform,
                onClick = {
                    if (activeFilter == platform) onFilterSelected(null) else onFilterSelected(platform)
                },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnected) Color(0xFF00E676) else Color.Gray.copy(alpha = 0.5f)
                            )
                    )
                },
                label = {
                    Text(
                        text = platform.displayName,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = platform.brandColor.copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    emotesEnabled: Boolean = true,
    animateAppearance: Boolean = true,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }
    val highContrast = LocalHighContrast.current
    val darkTheme = LocalDarkTheme.current
    val event = message.event
    // Color propio del suceso, no el de la plataforma: así un canje del canal
    // no se confunde con un mensaje normal ni con una donación. Los avisos
    // (destacado/anuncio) sí usan el color de su plataforma porque no son
    // dinero ni recompensas. Un mensaje normal no pinta franja.
    val eventAccent = event?.let {
        eventAccentColor(it.type, message.platform, darkTheme)
    }

    // Solo se animan los mensajes que acaban de llegar mientras el usuario
    // sigue el chat en vivo. Al scrollear por el historial los items se
    // componen directamente en su estado final, así el desplazamiento no se
    // llena de animaciones y se mantiene fluido.
    val shouldAnimateIn = remember(message.id) {
        animateAppearance &&
            abs(System.currentTimeMillis() - message.receivedAt) <= NEW_MESSAGE_ANIMATION_WINDOW_MS
    }
    // Animatable + graphicsLayer: la animación se resuelve en la fase de dibujo
    // (alpha y escala), sin recomponer la tarjeta en cada frame. No usamos
    // desplazamiento vertical: al moverse 10 dp la tarjeta invadía la siguiente
    // y en chats rápidos se veían solapadas.
    val enterAlpha = remember(message.id) { Animatable(if (shouldAnimateIn) 0f else 1f) }
    val enterScale = remember(message.id) { Animatable(if (shouldAnimateIn) NEW_MESSAGE_INITIAL_SCALE else 1f) }

    LaunchedEffect(message.id) {
        if (shouldAnimateIn) {
            launch {
                enterScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                )
            }
            enterAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            // Franja vertical de acento. drawBehind no añade un nodo de
            // layout, así que el coste por mensaje es mínimo.
            .drawBehind {
                if (eventAccent != null) {
                    drawRect(
                        color = eventAccent,
                        size = Size(4.dp.toPx(), size.height)
                    )
                }
            }
            .graphicsLayer {
                alpha = enterAlpha.value
                scaleX = enterScale.value
                scaleY = enterScale.value
            },
        shape = RoundedCornerShape(12.dp),
        // Las tarjetas de suceso llevan borde más grueso para separarlas del
        // resto; en alto contraste siempre es grueso y usa el color del tema,
        // porque los acentos podrían perder contraste sobre el fondo.
        border = BorderStroke(
            width = if (highContrast || event != null) 1.5.dp else 1.dp,
            color = when {
                highContrast -> MaterialTheme.colorScheme.outline
                eventAccent != null -> eventAccent.copy(alpha = 0.55f)
                else -> message.platform.brandColor.copy(alpha = 0.22f)
            }
        ),
        colors = CardDefaults.cardColors(
            // El tinte es el mismo acento con alfa bajo: identifica el tipo
            // sin reducir el contraste del texto. En tema claro basta menos
            // alfa porque los acentos ya son oscuros; en oscuro se sube para
            // que el tinte no desaparezca. En alto contraste se usa el fondo
            // sólido del tema y se renuncia al color.
            containerColor = when {
                highContrast -> MaterialTheme.colorScheme.surface
                eventAccent != null -> eventAccent.copy(
                    alpha = if (darkTheme) 0.20f else 0.10f
                )
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    // Los sucesos tienen una franja de 4 dp a la izquierda;
                    // el padding extra evita que el chip de plataforma la tape.
                    start = if (event != null) 14.dp else 10.dp,
                    end = 10.dp,
                    top = 6.dp,
                    bottom = 6.dp
                ),
            verticalAlignment = Alignment.Top
        ) {
            // El chip conserva siempre el color de marca de la plataforma:
            // es la identidad del canal y no compite con el color del suceso,
            // que va en la franja, el borde y el tinte de la tarjeta.
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = message.platform.brandColor,
                contentColor = message.platform.onBrandColor,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = message.platform.displayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    ),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (event != null && eventAccent != null) {
                    ChatEventBanner(event = event, color = eventAccent)
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message.username,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            // En alto contraste ignoramos el color del usuario
                            // (elegido por él, a veces con poco contraste) y
                            // usamos el color de su plataforma en una variante
                            // legible sobre el fondo actual.
                            color = if (highContrast) {
                                message.platform.highContrastTextColor(darkTheme)
                            } else {
                                message.userColor ?: MaterialTheme.colorScheme.primary
                            }
                        )
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (message.message.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))

                    ChatMessageBody(
                        message = message,
                        emotesEnabled = emotesEnabled
                    )
                }
            }
        }
    }
}

/**
 * Icono representativo de cada tipo de suceso.
 *
 * Se elige por significado y no por plataforma: la estrella indica compromiso
 * recurrente, la caja de regalo algo que se obsequia, el rayo bits, la moneda
 * dinero y el megáfono anuncios o raids. Así el mismo icono sirve aunque el
 * suceso venga de plataformas distintas.
 */
private fun eventIcon(type: ChatEventType): ImageVector = when (type) {
    ChatEventType.SUBSCRIPTION -> Icons.Filled.Star
    ChatEventType.GIFT_SUBSCRIPTION -> Icons.Filled.CardGiftcard
    ChatEventType.CHEER -> Icons.Filled.Bolt
    ChatEventType.DONATION -> Icons.Filled.MonetizationOn
    ChatEventType.SUPER_STICKER -> Icons.Filled.MonetizationOn
    ChatEventType.MEMBERSHIP -> Icons.Filled.WorkspacePremium
    ChatEventType.GIFT_MEMBERSHIP -> Icons.Filled.CardGiftcard
    ChatEventType.RAID -> Icons.Filled.Campaign
    ChatEventType.REDEMPTION -> Icons.Filled.Redeem
    ChatEventType.HIGHLIGHT -> Icons.Filled.PushPin
    ChatEventType.ANNOUNCEMENT -> Icons.Filled.Campaign
    ChatEventType.GIFT -> Icons.Filled.CardGiftcard
}

/** Etiqueta localizada del suceso (tipo + cantidad o importe). */
@Composable
private fun eventTitle(event: ChatEvent): String = when (event.type) {
    ChatEventType.SUBSCRIPTION -> if (event.count > 1) {
        pluralStringResource(
            R.plurals.event_subscription_months,
            event.count,
            event.count
        )
    } else {
        stringResource(R.string.event_subscription)
    }

    ChatEventType.GIFT_SUBSCRIPTION -> when {
        event.recipient != null ->
            stringResource(R.string.event_gift_sub_recipient, event.recipient)
        event.count > 1 -> pluralStringResource(
            R.plurals.event_gift_subs,
            event.count,
            event.count
        )
        else -> stringResource(R.string.event_gift_sub)
    }

    ChatEventType.CHEER -> stringResource(R.string.event_cheer, event.count)

    ChatEventType.DONATION -> event.amountText?.let {
        stringResource(R.string.event_donation_amount, it)
    } ?: stringResource(R.string.event_donation)

    ChatEventType.SUPER_STICKER -> event.amountText?.let {
        stringResource(R.string.event_super_sticker_amount, it)
    } ?: stringResource(R.string.event_super_sticker)

    ChatEventType.MEMBERSHIP -> stringResource(R.string.event_membership)

    ChatEventType.GIFT_MEMBERSHIP -> if (event.count > 1) {
        pluralStringResource(R.plurals.event_gift_memberships, event.count, event.count)
    } else {
        stringResource(R.string.event_gift_membership)
    }

    ChatEventType.RAID -> if (event.count > 0) {
        pluralStringResource(R.plurals.event_raid_viewers, event.count, event.count)
    } else {
        stringResource(R.string.event_raid)
    }

    ChatEventType.REDEMPTION -> stringResource(R.string.event_redemption)
    ChatEventType.HIGHLIGHT -> stringResource(R.string.event_highlight)
    ChatEventType.ANNOUNCEMENT -> stringResource(R.string.event_announcement)

    ChatEventType.GIFT -> {
        // El nombre del regalo lo entrega la plataforma (Rosa, Galaxia…) y no
        // se traduce; lo que sí se localiza es "Regalo" y el plural.
        val name = event.giftName
        when {
            name != null && event.count > 1 -> stringResource(
                R.string.event_gift_named_count,
                name,
                event.count
            )
            name != null -> stringResource(R.string.event_gift_named, name)
            event.count > 1 -> pluralStringResource(
                R.plurals.event_gifts,
                event.count,
                event.count
            )
            else -> stringResource(R.string.event_gift)
        }
    }
}

@Composable
private fun ChatEventBanner(
    event: ChatEvent,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = eventIcon(event.type),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = eventTitle(event),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

private const val EMOTE_INLINE_PREFIX = "emote_"
private val EMOTE_SIZE = 22.sp

/** Ventana de tiempo en la que un mensaje recién llegado se anima al aparecer. */
private const val NEW_MESSAGE_ANIMATION_WINDOW_MS = 2_500L

/** Escala inicial de la animación de aparición (sin desplazamiento). */
private const val NEW_MESSAGE_INITIAL_SCALE = 0.96f

/**
 * Separación mínima (ms) respecto a los mensajes contiguos para animar la
 * aparición. Si el mensaje forma parte de una ráfaga, se muestra directamente:
 * animar todos los mensajes de un chat rápido superpone decenas de animaciones
 * y produce huecos y saltos.
 */
private const val NEW_MESSAGE_ANIMATION_MIN_GAP_MS = 400L

/**
 * Un mensaje "aislado" tiene un hueco suficiente con el anterior y el siguiente.
 * Así, en una ráfaga no se anima ninguno y en un chat tranquilo sí se anima cada
 * mensaje nuevo.
 */
private fun isIsolatedArrival(messages: List<ChatMessage>, index: Int): Boolean {
    val current = messages[index]
    val previousGap = if (index > 0) {
        current.receivedAt - messages[index - 1].receivedAt
    } else {
        Long.MAX_VALUE
    }
    val nextGap = if (index < messages.lastIndex) {
        messages[index + 1].receivedAt - current.receivedAt
    } else {
        Long.MAX_VALUE
    }
    return minOf(previousGap, nextGap) >= NEW_MESSAGE_ANIMATION_MIN_GAP_MS
}

@Composable
private fun ChatMessageBody(
    message: ChatMessage,
    emotesEnabled: Boolean
) {
    val textStyle = MaterialTheme.typography.bodyMedium
    val textColor = MaterialTheme.colorScheme.onSurface
    val spans = if (emotesEnabled) message.emotes else emptyList()

    if (spans.isEmpty()) {
        Text(
            text = message.message,
            style = textStyle,
            color = textColor
        )
        return
    }

    val annotatedText = remember(message.id, message.message, spans) {
        buildAnnotatedString {
            var cursor = 0
            spans.forEachIndexed { index, span ->
                if (span.start > cursor) {
                    append(message.message.substring(cursor, span.start))
                }
                appendInlineContent(
                    id = EMOTE_INLINE_PREFIX + index,
                    alternateText = span.name
                )
                cursor = span.end
            }
            if (cursor < message.message.length) {
                append(message.message.substring(cursor))
            }
        }
    }

    val inlineContent = remember(spans) {
        spans.mapIndexed { index, span ->
            (EMOTE_INLINE_PREFIX + index) to InlineTextContent(
                placeholder = Placeholder(
                    width = EMOTE_SIZE,
                    height = EMOTE_SIZE,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                AsyncImage(
                    model = span.url,
                    contentDescription = span.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }.toMap()
    }

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        style = textStyle,
        color = textColor
    )
}

@Composable
private fun EmptyChatPlaceholder(
    onOpenConfig: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenConfig) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.empty_action))
        }
    }
}

/**
 * Barra de pruebas manuales, visible solo si el ajuste está activo.
 *
 * La primera fila simula mensajes normales por plataforma; la segunda,
 * sucesos destacados. Ambas filas se desplazan en horizontal porque con el
 * texto ampliado (hasta 150 %) los botones no caben en una pantalla estrecha,
 * y se mantienen fuera del flujo del chat para no robarle altura.
 */
@Composable
private fun QuickTestBar(
    onSendSample: (Platform) -> Unit,
    onSendEvent: (ChatEventType) -> Unit
) {
    val darkTheme = LocalDarkTheme.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Mensajes normales: el color del botón es el de la plataforma
            // para que se distinga de los sucesos, que usan la paleta de
            // EventColors.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Scroll horizontal: con el texto al 150 % los botones no
                    // caben en una pantalla estrecha y no deben recortarse.
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.simulate_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 2.dp)
                )

                Platform.entries.forEach { platform ->
                    Button(
                        onClick = { onSendSample(platform) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = platform.brandColor.copy(alpha = 0.85f),
                            contentColor = platform.onBrandColor
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.heightIn(min = 28.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.simulate_button, platform.displayName),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                        )
                    }
                }
            }

            // Sucesos: cada botón usa el acento de EventColors, el mismo que
            // tendrá la tarjeta generada, para asociar color y tipo antes de
            // pulsar. Al igual que la fila anterior, se desplaza en horizontal.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.simulate_events_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 2.dp)
                )

                SimulatedEvent.entries.forEach { sample ->
                    // El botón usa el mismo color que tendrá la tarjeta del
                    // suceso, así el usuario asocia color y tipo de un vistazo.
                    val accent = eventAccentColor(sample.type, sample.platform, darkTheme)
                    Button(
                        onClick = { onSendEvent(sample.type) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent.copy(alpha = 0.85f),
                            // Los acentos son claros en tema oscuro y oscuros
                            // en tema claro: se elige el texto de más contraste.
                            contentColor = if (darkTheme) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.heightIn(min = 28.dp)
                    ) {
                        Icon(
                            imageVector = eventIcon(sample.type),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(sample.labelRes),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Catálogo de la fila "Eventos:".
 *
 * [labelRes] es una etiqueta corta para el botón (no cabe la frase completa);
 * la tarjeta vuelve a componer el texto con [eventTitle], que incluye la
 * cantidad o el importe. [platform] determina el chip y el color base, igual
 * que en un suceso real.
 */
private enum class SimulatedEvent(
    val type: ChatEventType,
    val platform: Platform,
    val labelRes: Int
) {
    SUBSCRIPTION(ChatEventType.SUBSCRIPTION, Platform.TWITCH, R.string.simulate_event_sub),
    GIFT_SUBSCRIPTION(ChatEventType.GIFT_SUBSCRIPTION, Platform.KICK, R.string.simulate_event_gift_sub),
    CHEER(ChatEventType.CHEER, Platform.TWITCH, R.string.simulate_event_cheer),
    DONATION(ChatEventType.DONATION, Platform.YOUTUBE, R.string.simulate_event_donation),
    SUPER_STICKER(ChatEventType.SUPER_STICKER, Platform.YOUTUBE, R.string.simulate_event_super_sticker),
    MEMBERSHIP(ChatEventType.MEMBERSHIP, Platform.YOUTUBE, R.string.simulate_event_membership),
    GIFT_MEMBERSHIP(ChatEventType.GIFT_MEMBERSHIP, Platform.YOUTUBE, R.string.simulate_event_gift_membership),
    RAID(ChatEventType.RAID, Platform.TWITCH, R.string.simulate_event_raid),
    REDEMPTION(ChatEventType.REDEMPTION, Platform.TWITCH, R.string.simulate_event_redemption),
    GIFT(ChatEventType.GIFT, Platform.TIKTOK, R.string.simulate_event_gift),
    HIGHLIGHT(ChatEventType.HIGHLIGHT, Platform.TWITCH, R.string.simulate_event_highlight),
    ANNOUNCEMENT(ChatEventType.ANNOUNCEMENT, Platform.TWITCH, R.string.simulate_event_announcement)
}

@Composable
fun ConnectionConfigDialog(
    connectionStates: Map<Platform, ConnectionState>,
    savedChannels: Map<Platform, String>,
    savedTokens: Map<Platform, String?>,
    keepScreenOn: Boolean,
    emotesEnabled: Boolean,
    quickTestBarEnabled: Boolean,
    messageRateLimit: MessageRateLimit,
    historyLimit: HistoryLimit,
    themeMode: ThemeMode,
    language: AppLanguage,
    highContrast: Boolean,
    fontScalePercent: Int,
    onToggleKeepScreenOn: () -> Unit,
    onToggleEmotes: () -> Unit,
    onToggleQuickTestBar: () -> Unit,
    onMessageRateLimitSelected: (MessageRateLimit) -> Unit,
    onHistoryLimitSelected: (HistoryLimit) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onToggleHighContrast: () -> Unit,
    onDecreaseFontScale: () -> Unit,
    onIncreaseFontScale: () -> Unit,
    onConnect: (Platform, String, String?) -> Unit,
    onDisconnect: (Platform) -> Unit,
    onSaveDraft: (Platform, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogScaledContent(fontScalePercent) {
                Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            DialogScaledContent(fontScalePercent) {
                val tabs = listOf(
                    stringResource(R.string.tab_appearance),
                    stringResource(R.string.tab_chat),
                    stringResource(R.string.tab_platforms)
                )
                var selectedTab by remember { mutableIntStateOf(0) }

                Column(modifier = Modifier.fillMaxWidth()) {
                    // La barra usa el mismo contenedor que el diálogo
                    // (surfaceContainerHigh) para no destacar como un bloque
                    // oscuro aparte; el indicador y el divisor la delimitan.
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        divider = {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant
                                    .copy(alpha = 0.7f)
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                // ---- Apariencia ----
                                // Tema: sistema / claro / oscuro
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.theme_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stringResource(R.string.theme_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ThemeMode.entries.forEach { mode ->
                                                FilterChip(
                                                    selected = themeMode == mode,
                                                    onClick = { onThemeModeSelected(mode) },
                                                    label = {
                                                        Text(
                                                            text = themeModeLabel(mode),
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = when (mode) {
                                                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                                                ThemeMode.DARK -> Icons.Default.DarkMode
                                                            },
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                SettingsDivider()

                                // Idioma de la interfaz (sistema, español o inglés)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.language_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stringResource(R.string.language_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            AppLanguage.entries.forEach { option ->
                                                FilterChip(
                                                    selected = language == option,
                                                    onClick = { onLanguageSelected(option) },
                                                    label = {
                                                        Text(
                                                            text = appLanguageLabel(option),
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                SettingsDivider()

                                // Alto contraste (aplica a claro y oscuro)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.high_contrast_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = stringResource(R.string.high_contrast_description),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Switch(
                                            checked = highContrast,
                                            onCheckedChange = { onToggleHighContrast() }
                                        )
                                    }
                                }

                                SettingsDivider()

                                // Tamaño del texto (se aplica a toda la app al instante)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.font_scale_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stringResource(R.string.font_scale_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = onDecreaseFontScale,
                                                enabled = fontScalePercent > TextScale.MIN_PERCENT,
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Remove,
                                                    contentDescription = stringResource(R.string.decrease_text_size)
                                                )
                                            }
                                            Text(
                                                text = stringResource(R.string.font_scale_value, fontScalePercent),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 20.dp)
                                            )
                                            OutlinedButton(
                                                onClick = onIncreaseFontScale,
                                                enabled = fontScalePercent < TextScale.MAX_PERCENT,
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = stringResource(R.string.increase_text_size)
                                                )
                                            }
                                        }
                                        Text(
                                            text = stringResource(R.string.font_scale_preview),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            1 -> {
                                // ---- Chat ----
                                // Configuración de pantalla despierta
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.keep_screen_on_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = stringResource(R.string.keep_screen_on_description),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Switch(
                                            checked = keepScreenOn,
                                            onCheckedChange = { onToggleKeepScreenOn() }
                                        )
                                    }
                                }

                                SettingsDivider()

                                // Renderizado de emotes (imágenes remotas)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.emotes_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = stringResource(R.string.emotes_description),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Switch(
                                            checked = emotesEnabled,
                                            onCheckedChange = { onToggleEmotes() }
                                        )
                                    }
                                }

                                SettingsDivider()

                                // Barra de simulación (botones +Twitch, +YouTube, ...)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.quick_bar_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = stringResource(R.string.quick_bar_description),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Switch(
                                            checked = quickTestBarEnabled,
                                            onCheckedChange = { onToggleQuickTestBar() }
                                        )
                                    }
                                }

                                SettingsDivider()

                                // "Modo lento": limita cuántos mensajes se
                                // publican en pantalla por segundo.
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.rate_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stringResource(R.string.rate_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            MessageRateLimit.entries.forEach { option ->
                                                FilterChip(
                                                    selected = messageRateLimit == option,
                                                    onClick = { onMessageRateLimitSelected(option) },
                                                    label = {
                                                        Text(
                                                            text = if (option.isLimited) {
                                                                stringResource(
                                                                    R.string.rate_option_value,
                                                                    option.messagesPerSecond
                                                                )
                                                            } else {
                                                                stringResource(R.string.rate_option_unlimited)
                                                            },
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (messageRateLimit.isLimited) {
                                                stringResource(
                                                    R.string.rate_status_limited,
                                                    messageRateLimit.messagesPerSecond
                                                )
                                            } else {
                                                stringResource(R.string.rate_status_unlimited)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                SettingsDivider()

                                // Límite de historial conservado en memoria.
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.history_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stringResource(R.string.history_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HistoryLimit.entries.forEach { option ->
                                                FilterChip(
                                                    selected = historyLimit == option,
                                                    onClick = { onHistoryLimitSelected(option) },
                                                    label = {
                                                        Text(
                                                            text = if (option == HistoryLimit.UNLIMITED) {
                                                                stringResource(R.string.history_option_unlimited)
                                                            } else {
                                                                stringResource(
                                                                    R.string.history_option_value,
                                                                    option.messages
                                                                )
                                                            },
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (historyLimit == HistoryLimit.UNLIMITED) {
                                                stringResource(R.string.history_status_unlimited)
                                            } else {
                                                stringResource(
                                                    R.string.history_status_limited,
                                                    historyLimit.messages
                                                )
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            2 -> {
                                // ---- Plataformas ----
                                Platform.entries.forEach { platform ->
                                    val state = connectionStates[platform] ?: ConnectionState.Disconnected
                                    PlatformConfigCard(
                                        platform = platform,
                                        state = state,
                                        initialChannel = savedChannels[platform] ?: "",
                                        initialToken = savedTokens[platform] ?: "",
                                        onConnect = { channel, token -> onConnect(platform, channel, token) },
                                        onDisconnect = { onDisconnect(platform) },
                                        onSaveDraft = { channel, token -> onSaveDraft(platform, channel, token) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogScaledContent(fontScalePercent) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

@Composable
private fun appLanguageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.SPANISH -> R.string.language_spanish
        AppLanguage.ENGLISH -> R.string.language_english
    }
)

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp)
    )
}

/**
 * Línea fina que marca el final de un apartado de configuración.
 *
 * Los bloques del diálogo comparten el color del contenedor, así que el
 * divisor es lo que hace visible dónde acaba un ajuste y empieza el siguiente.
 * En alto contraste se usa el contorno sólido para que siga viéndose.
 */
@Composable
private fun SettingsDivider() {
    val highContrast = LocalHighContrast.current
    HorizontalDivider(
        thickness = if (highContrast) 1.5.dp else 1.dp,
        color = if (highContrast) {
            MaterialTheme.colorScheme.outline
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
        }
    )
}

/**
 * Aplica el tamaño de texto elegido dentro de un diálogo.
 *
 * Los diálogos se dibujan en su propio AndroidComposeView, que vuelve a
 * proporcionar LocalDensity con la densidad de su ventana; por eso el ajuste
 * hecho en [com.example.chatmultistream.ui.theme.ChatMultiStreamTheme] no les
 * llega solo y hay que repetirlo aquí.
 */
@Composable
private fun DialogScaledContent(
    fontScalePercent: Int,
    content: @Composable () -> Unit
) {
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, fontScalePercent) {
        Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * (fontScalePercent / 100f)
        )
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        content()
    }
}

@Composable
private fun PlatformConfigCard(
    platform: Platform,
    state: ConnectionState,
    initialChannel: String,
    initialToken: String,
    onConnect: (String, String?) -> Unit,
    onDisconnect: () -> Unit,
    onSaveDraft: (String, String?) -> Unit
) {
    var channelName by remember(initialChannel) { mutableStateOf(initialChannel) }
    var token by remember(initialToken) { mutableStateOf(initialToken) }

    val (labelHint, placeholderHint) = when (platform) {
        Platform.TWITCH -> stringResource(R.string.channel_label_twitch) to
                stringResource(R.string.channel_hint_twitch)
        Platform.YOUTUBE -> stringResource(R.string.channel_label_youtube) to
                stringResource(R.string.channel_hint_youtube)
        Platform.KICK -> stringResource(R.string.channel_label_kick) to
                stringResource(R.string.channel_hint_kick)
        Platform.TIKTOK -> stringResource(R.string.channel_label_tiktok) to
                stringResource(R.string.channel_hint_tiktok)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = platform.brandColor,
                        shape = CircleShape,
                        modifier = Modifier.size(10.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = platform.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                when (state) {
                    is ConnectionState.Connected -> {
                        Text(
                            text = stringResource(R.string.connected_format, state.channel),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00C853),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is ConnectionState.Connecting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.connecting),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        }
                    }
                    is ConnectionState.Error -> {
                        Text(
                            text = stringResource(R.string.error),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ConnectionState.Disconnected -> {
                        Text(
                            text = stringResource(R.string.disconnected),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            if (state is ConnectionState.Error) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (state is ConnectionState.Connected) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp)
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.disconnect), style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = channelName,
                        onValueChange = {
                            channelName = it
                            onSaveDraft(it, token)
                        },
                        label = { Text(labelHint) },
                        placeholder = { Text(placeholderHint, style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                val cleanToken = token.takeIf { it.isNotBlank() }
                                onConnect(channelName, cleanToken)
                            },
                            enabled = state !is ConnectionState.Connecting && channelName.isNotBlank(),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.save_and_connect))
                        }
                    }
                }
            }
        }
    }
}
