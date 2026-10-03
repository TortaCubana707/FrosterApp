package com.froster.app

import com.froster.app.datos.Ajustes
import com.froster.app.datos.Telemetria
import com.froster.app.datos.historial.TipoSensor
import com.froster.app.datos.historial.aRegistros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conversión de un paquete MQTT en filas de historial_sensores (versión 2). */
class ConversionHistorialTest {

    private val paquete = Telemetria.desdeJson(
        """{"nodo":"esp32_01","ts":"x","seq":1,
            "sensores":{"luz":30,"temp":28.4,"hum":48.2,"rocio":11.6},
            "actuadores":{},"red":{},"status":"ALERTA"}"""
    )!!
    private val umbrales = Ajustes(umbralLuz = 40, umbralTemp = 27.0f, alertasActivas = true)
    private val ahora = 1_789_830_428_498L

    private fun fila(tipo: String) = paquete.aRegistros(ahora, umbrales).single { it.tipoSensor == tipo }

    @Test
    fun `un paquete produce una fila por sensor`() {
        val filas = paquete.aRegistros(ahora, umbrales)
        assertEquals(4, filas.size)
        assertEquals(
            setOf(TipoSensor.LUZ, TipoSensor.TEMPERATURA, TipoSensor.HUMEDAD, TipoSensor.PUNTO_ROCIO),
            filas.map { it.tipoSensor }.toSet()
        )
    }

    @Test
    fun `todas las filas de un paquete comparten el mismo timestamp`() {
        assertTrue(paquete.aRegistros(ahora, umbrales).all { it.timestamp == ahora })
    }

    @Test
    fun `el id queda en cero para que SQLite lo asigne`() {
        assertTrue(paquete.aRegistros(ahora, umbrales).all { it.id == 0L })
    }

    @Test
    fun `los decimales del sensor no arrastran el error binario del Float`() {
        // Float 11.6f convertido directo a Double da 11.600000381469727
        assertEquals(11.6, fila(TipoSensor.PUNTO_ROCIO).valorLectura, 0.0)
        assertEquals(28.4, fila(TipoSensor.TEMPERATURA).valorLectura, 0.0)
    }

    @Test
    fun `la temperatura sobre el umbral queda marcada en alerta`() {
        assertTrue(fila(TipoSensor.TEMPERATURA).estadoAlerta)
    }

    @Test
    fun `la luz bajo el minimo queda marcada en alerta`() {
        assertTrue(fila(TipoSensor.LUZ).estadoAlerta)
    }

    @Test
    fun `humedad y punto de rocio no tienen umbral y nunca se marcan`() {
        assertFalse(fila(TipoSensor.HUMEDAD).estadoAlerta)
        assertFalse(fila(TipoSensor.PUNTO_ROCIO).estadoAlerta)
    }

    @Test
    fun `con las alertas apagadas ninguna fila se marca`() {
        val sinAlertas = umbrales.copy(alertasActivas = false)
        assertTrue(paquete.aRegistros(ahora, sinAlertas).none { it.estadoAlerta })
    }
}
