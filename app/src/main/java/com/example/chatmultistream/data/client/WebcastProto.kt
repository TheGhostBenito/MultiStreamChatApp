package com.example.chatmultistream.data.client

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/**
 * Codificador/decodificador protobuf mínimo para el WebSocket de TikTok LIVE.
 *
 * TikTok no publica una API de chat: el cliente habla el mismo protocolo
 * binario que la web, con tramas `WebcastPushFrame` que envuelven un
 * `WebcastResponse` con mensajes (`WebcastChatMessage`, regalos, likes…).
 * Generar todo el esquema con `protoc` añadiría cientos de clases y varios MB
 * al APK, así que aquí se decodifica a mano solo lo que la interfaz necesita.
 *
 * Protobuf tolera cambios por diseño: cada campo viaja con un número y un tipo
 * de cable (`wire type`). Eso permite leer los campos conocidos y saltar los
 * demás sin tener el esquema completo; si TikTok añade campos nuevos, esta
 * implementación sigue funcionando.
 *
 * Wire types usados: 0 = varint y 2 = longitud + bytes. Los tipos 1 y 5
 * (8 y 4 bytes fijos) solo aparecen en campos que descartamos, pero
 * [ProtoReader.skip] los soporta para no romper la lectura.
 */
internal object WebcastProto {

    /**
     * Trama exterior `WebcastPushFrame`.
     *
     * [payloadType] dice qué contiene [payload]: `msg` para mensajes de chat,
     * `hb` para latidos e `im_enter_room` para la entrada a la sala. [logId]
     * se devuelve tal cual en el ACK para que el servidor sepa qué bloque de
     * mensajes ya se procesó.
     */
    data class Frame(
        val payloadType: String,
        val payload: ByteArray,
        val logId: Long
    )

    /** Elemento de `WebcastResponse.messages`: tipo de mensaje, carga y id. */
    data class ResponseMessage(
        val method: String,
        val payload: ByteArray,
        val msgId: Long
    )

    /**
     * Respuesta `WebcastResponse`: mensajes y datos de control.
     *
     * [needsAck] junto con [internalExt] obligan a confirmar la recepción; si
     * no se confirma, TikTok reenvía los mismos mensajes al reconectar.
     */
    data class Response(
        val messages: List<ResponseMessage>,
        val needsAck: Boolean,
        val internalExt: ByteArray
    )

    /** Campos de `WebcastChatMessage` que usa la interfaz. */
    data class Chat(
        val msgId: Long,
        val createTime: Long,
        val nickname: String,
        val uniqueId: String,
        val content: String
    )

    /**
     * Campos de `WebcastGiftMessage` que usa la interfaz.
     *
     * [comboCount] es el paso actual del combo y [repeatCount] el total
     * acumulado; [repeatEnd] vale 1 en el último mensaje del combo y [combo]
     * indica si el regalo admite combos. Con esos cuatro valores el cliente
     * decide cuándo publicar una sola tarjeta en lugar de una por paso.
     * [diamondCount] es el valor del regalo en diamantes, no dinero.
     */
    data class Gift(
        val msgId: Long,
        val createTime: Long,
        val nickname: String,
        val uniqueId: String,
        val giftName: String,
        val giftId: Long,
        val repeatCount: Int,
        val comboCount: Int,
        val repeatEnd: Int,
        val combo: Boolean,
        val diamondCount: Int
    )

    // ------------------------------------------------------------------ encode

    /**
     * `WebcastPushFrame { payloadEncoding="pb", payloadType="hb", payload=HeartbeatMessage }`.
     *
     * TikTok cierra la conexión si deja de recibir latidos, así que el cliente
     * los envía periódicamente con el id de sala como int64 (campo 1).
     */
    fun buildHeartbeat(roomId: String): ByteArray {
        val payload = ProtoWriter().apply {
            varint(1, roomId.toLongOrNull() ?: 0L)
        }.toByteArray()
        return pushFrame("hb", payload)
    }

    /**
     * `WebcastImEnterRoomMessage` que abre la sala para recibir mensajes.
     *
     * Se declara identidad `audience`: es el rol de espectador anónimo que usa
     * la web sin iniciar sesión. `filterWelcomeMsg="0"` evita que el servidor
     * inunde el chat con mensajes de bienvenida al entrar.
     */
    fun buildEnterRoom(roomId: String): ByteArray {
        val payload = ProtoWriter().apply {
            varint(1, roomId.toLongOrNull() ?: 0L)
            varint(4, 12L)          // liveId
            string(5, "audience")   // identity
            string(9, "0")          // filterWelcomeMsg
        }.toByteArray()
        return pushFrame("im_enter_room", payload)
    }

    /**
     * ACK que TikTok espera cuando `WebcastResponse.needsAck` viene activo.
     *
     * Devuelve el mismo `logId` e `internalExt` de la respuesta; así el
     * servidor marca ese bloque como entregado y no lo reenvía.
     */
    fun buildAck(logId: Long, internalExt: ByteArray): ByteArray =
        ProtoWriter().apply {
            string(6, "pb")
            string(7, "ack")
            varint(2, logId)
            bytes(8, internalExt)
        }.toByteArray()

    /**
     * Envuelve un mensaje saliente en un `WebcastPushFrame` con codificación
     * protobuf (`pb`). Los campos 6, 7 y 8 son los que espera TikTok para
     * codificación, tipo de carga y carga útil.
     */
    private fun pushFrame(payloadType: String, payload: ByteArray): ByteArray =
        ProtoWriter().apply {
            string(6, "pb")
            string(7, payloadType)
            bytes(8, payload)
        }.toByteArray()

    // ------------------------------------------------------------------ decode

    /**
     * Lee la trama exterior y devuelve tipo, carga e id de log.
     *
     * Las tramas sin tipo se descartan: sin él no se puede saber qué formato
     * tiene la carga, y adivinarlo podría interpretar bytes arbitrarios.
     */
    fun decodeFrame(raw: ByteArray): Frame? {
        val reader = ProtoReader(raw)
        var payloadType = ""
        var payload = EMPTY
        var logId = 0L

        // Solo interesan el id de log (2), el tipo (7) y la carga (8); el
        // resto de campos de la trama se salta.
        while (reader.hasMore()) {
            val tag = reader.readTag()
            when (tag.field) {
                2 -> if (tag.wire == WIRE_VARINT) logId = reader.readVarint() else reader.skip(tag.wire)
                7 -> if (tag.wire == WIRE_BYTES) payloadType = reader.readString() else reader.skip(tag.wire)
                8 -> if (tag.wire == WIRE_BYTES) payload = reader.readBytes() else reader.skip(tag.wire)
                else -> reader.skip(tag.wire)
            }
        }

        return if (payloadType.isBlank()) null else Frame(payloadType, payload, logId)
    }

    /**
     * Decodifica un `WebcastResponse`.
     *
     * Algunas respuestas llegan comprimidas con gzip; se descomprimen antes de
     * leer. Los campos 1 (mensajes repetidos), 5 (`internalExt` para el ACK) y
     * 9 (`needsAck`) son los únicos que usa el cliente.
     */
    fun decodeResponse(payload: ByteArray): Response? {
        // La firma gzip se comprueba dentro de gunzipIfNeeded; descomprimir
        // siempre lanzaría excepción en las respuestas que ya vienen en claro.
        val data = gunzipIfNeeded(payload)
        val reader = ProtoReader(data)
        val messages = ArrayList<ResponseMessage>()
        var needsAck = false
        var internalExt = EMPTY

        while (reader.hasMore()) {
            val tag = reader.readTag()
            when (tag.field) {
                1 -> if (tag.wire == WIRE_BYTES) {
                    decodeResponseMessage(reader.readBytes())?.let { messages += it }
                } else reader.skip(tag.wire)

                5 -> if (tag.wire == WIRE_BYTES) internalExt = reader.readBytes() else reader.skip(tag.wire)
                9 -> if (tag.wire == WIRE_VARINT) needsAck = reader.readVarint() != 0L else reader.skip(tag.wire)
                else -> reader.skip(tag.wire)
            }
        }

        return Response(messages, needsAck, internalExt)
    }

    /**
     * Elemento de `WebcastResponse.messages`: método (tipo de mensaje),
     * carga útil binaria e id del mensaje.
     */
    private fun decodeResponseMessage(data: ByteArray): ResponseMessage? {
        val reader = ProtoReader(data)
        var method = ""
        var payload = EMPTY
        var msgId = 0L

        while (reader.hasMore()) {
            val tag = reader.readTag()
            when (tag.field) {
                1 -> if (tag.wire == WIRE_BYTES) method = reader.readString() else reader.skip(tag.wire)
                2 -> if (tag.wire == WIRE_BYTES) payload = reader.readBytes() else reader.skip(tag.wire)
                3 -> if (tag.wire == WIRE_VARINT) msgId = reader.readVarint() else reader.skip(tag.wire)
                else -> reader.skip(tag.wire)
            }
        }

        return if (method.isBlank()) null else ResponseMessage(method, payload, msgId)
    }

    /**
     * Decodifica un `WebcastChatMessage`:
     *  1: Common { 2: msgId, 4: createTime }
     *  2: User   { 3: nickname, 38: uniqueId }
     *  3: content
     *
     * Los mensajes anidados (`Common`, `User`) se leen con su propio lector
     * porque protobuf precede cada uno de su longitud. Los campos que no se
     * usan se saltan.
     *
     * [fallbackMsgId] viene del sobre de la respuesta: dentro del mensaje a
     * veces falta el id, y sin él no se podría deduplicar un reenvío.
     */
    fun decodeChat(payload: ByteArray, fallbackMsgId: Long = 0L): Chat? {
        val reader = ProtoReader(payload)
        var msgId = fallbackMsgId
        var createTime = 0L
        var nickname = ""
        var uniqueId = ""
        var content = ""

        while (reader.hasMore()) {
            val tag = reader.readTag()
            when (tag.field) {
                1 -> if (tag.wire == WIRE_BYTES) {
                    val common = ProtoReader(reader.readBytes())
                    while (common.hasMore()) {
                        val commonTag = common.readTag()
                        when (commonTag.field) {
                            2 -> if (commonTag.wire == WIRE_VARINT) msgId = common.readVarint() else common.skip(commonTag.wire)
                            4 -> if (commonTag.wire == WIRE_VARINT) createTime = common.readVarint() else common.skip(commonTag.wire)
                            else -> common.skip(commonTag.wire)
                        }
                    }
                } else reader.skip(tag.wire)

                2 -> if (tag.wire == WIRE_BYTES) {
                    val user = ProtoReader(reader.readBytes())
                    while (user.hasMore()) {
                        val userTag = user.readTag()
                        when (userTag.field) {
                            3 -> if (userTag.wire == WIRE_BYTES) nickname = user.readString() else user.skip(userTag.wire)
                            38 -> if (userTag.wire == WIRE_BYTES) uniqueId = user.readString() else user.skip(userTag.wire)
                            else -> user.skip(userTag.wire)
                        }
                    }
                } else reader.skip(tag.wire)

                3 -> if (tag.wire == WIRE_BYTES) content = reader.readString() else reader.skip(tag.wire)

                else -> reader.skip(tag.wire)
            }
        }

        // Un chat sin texto no aporta nada al usuario (suelen ser mensajes de
        // sistema o emotes sueltos); se descarta antes de llegar a la interfaz.
        if (content.isBlank()) return null
        return Chat(
            msgId = msgId,
            createTime = createTime,
            nickname = nickname,
            uniqueId = uniqueId,
            content = content
        )
    }

    /**
     * Decodifica un `WebcastGiftMessage`:
     *  1: Common { 2: msgId, 4: createTime }
     *  2: giftId
     *  5: repeatCount
     *  6: comboCount
     *  7: User { 3: nickname, 38: uniqueId }
     *  9: repeatEnd
     * 15: Gift { 16: name, 12: diamondCount, 10: combo }
     *
     * TikTok envía un mensaje por cada paso de un combo ("×1, ×2…"), así que
     * aquí se extraen también los campos que permiten al cliente publicar una
     * sola tarjeta en lugar de una por paso. El sub-mensaje `Gift` (campo 15)
     * aporta el nombre visible y si el regalo admite combos.
     *
     * [fallbackMsgId] cumple la misma función que en [decodeChat]: evitar
     * duplicados cuando el id interior no viene informado.
     *
     * Se devuelve `null` si no hay nombre: sin nombre no hay nada legible que
     * mostrar, y suele indicar una carga que no es realmente un regalo.
     */
    fun decodeGift(payload: ByteArray, fallbackMsgId: Long = 0L): Gift? {
        val reader = ProtoReader(payload)
        var msgId = fallbackMsgId
        var createTime = 0L
        var giftId = 0L
        var repeatCount = 0
        var comboCount = 0
        var repeatEnd = 0
        var nickname = ""
        var uniqueId = ""
        var giftName = ""
        var diamondCount = 0
        var combo = false

        while (reader.hasMore()) {
            val tag = reader.readTag()
            when (tag.field) {
                1 -> if (tag.wire == WIRE_BYTES) {
                    val common = ProtoReader(reader.readBytes())
                    while (common.hasMore()) {
                        val commonTag = common.readTag()
                        when (commonTag.field) {
                            2 -> if (commonTag.wire == WIRE_VARINT) msgId = common.readVarint() else common.skip(commonTag.wire)
                            4 -> if (commonTag.wire == WIRE_VARINT) createTime = common.readVarint() else common.skip(commonTag.wire)
                            else -> common.skip(commonTag.wire)
                        }
                    }
                } else reader.skip(tag.wire)

                2 -> if (tag.wire == WIRE_VARINT) giftId = reader.readVarint() else reader.skip(tag.wire)
                // 5 y 6: total acumulado y paso actual del combo; 9 marca si
                // este mensaje cierra el combo.
                5 -> if (tag.wire == WIRE_VARINT) repeatCount = reader.readVarint().toInt() else reader.skip(tag.wire)
                6 -> if (tag.wire == WIRE_VARINT) comboCount = reader.readVarint().toInt() else reader.skip(tag.wire)
                9 -> if (tag.wire == WIRE_VARINT) repeatEnd = reader.readVarint().toInt() else reader.skip(tag.wire)

                7 -> if (tag.wire == WIRE_BYTES) {
                    val user = ProtoReader(reader.readBytes())
                    while (user.hasMore()) {
                        val userTag = user.readTag()
                        when (userTag.field) {
                            3 -> if (userTag.wire == WIRE_BYTES) nickname = user.readString() else user.skip(userTag.wire)
                            38 -> if (userTag.wire == WIRE_BYTES) uniqueId = user.readString() else user.skip(userTag.wire)
                            else -> user.skip(userTag.wire)
                        }
                    }
                } else reader.skip(tag.wire)

                15 -> if (tag.wire == WIRE_BYTES) {
                    val gift = ProtoReader(reader.readBytes())
                    while (gift.hasMore()) {
                        val giftTag = gift.readTag()
                        when (giftTag.field) {
                            10 -> if (giftTag.wire == WIRE_VARINT) combo = gift.readVarint() != 0L else gift.skip(giftTag.wire)
                            12 -> if (giftTag.wire == WIRE_VARINT) diamondCount = gift.readVarint().toInt() else gift.skip(giftTag.wire)
                            16 -> if (giftTag.wire == WIRE_BYTES) giftName = gift.readString() else gift.skip(giftTag.wire)
                            else -> gift.skip(giftTag.wire)
                        }
                    }
                } else reader.skip(tag.wire)

                else -> reader.skip(tag.wire)
            }
        }

        if (giftName.isBlank()) return null
        return Gift(
            msgId = msgId,
            createTime = createTime,
            nickname = nickname,
            uniqueId = uniqueId,
            giftName = giftName,
            giftId = giftId,
            repeatCount = repeatCount,
            comboCount = comboCount,
            repeatEnd = repeatEnd,
            combo = combo,
            diamondCount = diamondCount
        )
    }

    /**
     * Descomprime la carga si empieza por la firma gzip (`1f 8b`).
     *
     * Se comprueba la firma en lugar de intentar descomprimir siempre:
     * `GZIPInputStream` lanzaría una excepción en cada respuesta ya en claro,
     * lo que además de costoso llenaría el log de errores normales.
     */
    fun gunzipIfNeeded(data: ByteArray): ByteArray {
        if (data.size < 2) return data
        if (data[0] != 0x1f.toByte() || data[1] != 0x8b.toByte()) return data
        return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }

    // ----------------------------------------------------------------- plumbing

    private const val WIRE_VARINT = 0
    private const val WIRE_BYTES = 2
    private val EMPTY = ByteArray(0)

    private data class Tag(val field: Int, val wire: Int)

    /**
     * Lector secuencial de protobuf.
     *
     * Cada campo empieza por un `tag` = (número de campo << 3) | wire type.
     * El lector solo avanza y nunca reinterpreta bytes ya leídos; por eso
     * saltar campos desconocidos es barato y no depende del esquema completo.
     */
    private class ProtoReader(private val data: ByteArray) {
        private var position = 0

        fun hasMore(): Boolean = position < data.size

        /** Separa el número de campo y su tipo de cable del tag crudo. */
        fun readTag(): Tag {
            val key = readVarint()
            return Tag((key ushr 3).toInt(), (key and 0x07).toInt())
        }

        /**
         * Lee un entero en base 128 con el bit alto como señal de
         * continuación. Es el formato de todos los enteros y longitudes de
         * protobuf.
         */
        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (position < data.size) {
                val byte = data[position++].toInt() and 0xFF
                result = result or ((byte.toLong() and 0x7F) shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
                if (shift > 63) throw IllegalArgumentException("varint demasiado largo")
            }
            throw IllegalArgumentException("varint truncado")
        }

        /** Lee un campo de longitud: primero el tamaño, después los bytes. */
        fun readBytes(): ByteArray {
            val length = readVarint().toInt()
            if (length < 0 || position + length > data.size) {
                throw IllegalArgumentException("longitud inválida: $length")
            }
            val result = data.copyOfRange(position, position + length)
            position += length
            return result
        }

        fun readString(): String = String(readBytes(), Charsets.UTF_8)

        /**
         * Avanza sobre un campo que no interesa. El wire type determina
         * cuántos bytes ocupa: varint (variable), longitud + bytes, o 8/4
         * bytes fijos.
         */
        fun skip(wire: Int) {
            when (wire) {
                WIRE_VARINT -> readVarint()
                WIRE_BYTES -> {
                    val length = readVarint().toInt()
                    if (length < 0 || position + length > data.size) {
                        throw IllegalArgumentException("skip inválido: $length")
                    }
                    position += length
                }
                1 -> position += 8
                5 -> position += 4
                else -> throw IllegalArgumentException("wire type no soportado: $wire")
            }
        }
    }

    /**
     * Escritor mínimo para las pocas tramas que envía el cliente (latido,
     * entrada a la sala y ACK). No se usa para decodificar.
     */
    private class ProtoWriter {
        private val out = ByteArrayOutputStream()

        /** Escribe un campo entero (tag + varint). */
        fun varint(field: Int, value: Long) {
            tag(field, WIRE_VARINT)
            writeVarint(value)
        }

        /** Escribe un campo de texto como bytes UTF-8. */
        fun string(field: Int, value: String) {
            bytes(field, value.toByteArray(Charsets.UTF_8))
        }

        /** Escribe un campo de longitud (tag + tamaño + bytes). */
        fun bytes(field: Int, value: ByteArray) {
            tag(field, WIRE_BYTES)
            writeVarint(value.size.toLong())
            out.write(value)
        }

        fun toByteArray(): ByteArray = out.toByteArray()

        /** tag = (campo << 3) | wire type, empaquetado como varint. */
        private fun tag(field: Int, wire: Int) = writeVarint(((field shl 3) or wire).toLong())

        /** Base 128: el bit 0x80 indica que siguen más bytes del mismo entero. */
        private fun writeVarint(value: Long) {
            var remaining = value
            while (true) {
                val byte = (remaining and 0x7F).toInt()
                remaining = remaining ushr 7
                if (remaining == 0L) {
                    out.write(byte)
                    return
                }
                out.write(byte or 0x80)
            }
        }
    }
}
