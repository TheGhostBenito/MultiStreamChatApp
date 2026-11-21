package com.example.chatmultistream.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageRateGateTest {

    private var nowMs = 0L
    private val gate = MessageRateGate(initialLimit = 0, clockMs = { nowMs })

    @Test
    fun `sin limite deja pasar todos los mensajes`() {
        assertEquals(500, gate.available(500))
    }

    @Test
    fun `limita los mensajes dentro de la misma ventana`() {
        gate.setLimit(2)

        assertEquals(2, gate.available(10))
        assertEquals(0, gate.available(10))
        assertEquals(0, gate.available(1))
    }

    @Test
    fun `recupera cupo al pasar un segundo`() {
        gate.setLimit(2)
        assertEquals(2, gate.available(2))

        nowMs = 999
        assertEquals(0, gate.available(2))

        nowMs = 1_000
        assertEquals(2, gate.available(2))

        nowMs = 2_000
        assertEquals(2, gate.available(5))
    }

    @Test
    fun `una rafaga grande solo publica el cupo disponible`() {
        gate.setLimit(4)
        assertEquals(4, gate.available(200))

        nowMs = 500
        assertEquals(0, gate.available(200))

        nowMs = 1_000
        assertEquals(4, gate.available(200))
    }

    @Test
    fun `cambiar el limite reinicia la ventana`() {
        gate.setLimit(1)
        assertEquals(1, gate.available(5))
        assertEquals(0, gate.available(5))

        gate.setLimit(3)
        assertEquals(3, gate.available(5))
    }

    @Test
    fun `un limite de cero equivale a ilimitado`() {
        gate.setLimit(5)
        gate.setLimit(0)

        assertEquals(100, gate.available(100))
    }

    @Test
    fun `cantidades vacias o negativas no consumen cupo`() {
        gate.setLimit(1)

        assertEquals(0, gate.available(0))
        assertEquals(0, gate.available(-3))
        assertEquals(1, gate.available(3))
    }
}
