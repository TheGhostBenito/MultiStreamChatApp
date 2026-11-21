package com.example.chatmultistream

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade

/**
 * Configura Coil con un perfil de bajo consumo: caché en memoria del 10 %
 * (en vez del 25 % por defecto) y sin animación de fundido.
 * La caché en disco usa el valor conservador por defecto de Coil.
 */
class ChatApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.10)
                    .build()
            }
            .crossfade(false)
            .build()
}
