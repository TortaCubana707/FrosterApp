package com.froster.app

import com.froster.app.datos.Ajustes
import com.froster.app.datos.NivelAlerta
import com.froster.app.datos.Telemetria
import com.froster.app.datos.evaluarAlerta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pruebas del parseo del JSON anidado que publica la ESP32 y de la logica de
 * umbrales. Se ejecutan con:  ./gradlew :app:testDebugUnitTest
 */
class TelemetriaTest {

    /** Paquete real capturado del broker Mosquitto. */
    private val paqueteReal = """
        {"nodo":"esp32_01","ts":"2026-09-02T10:32:05Z","seq":12,
         "sensores":{"luz":85,"temp":24.5,"hum":48.2,"rocio":12.9},
         "actuadores":{"led":"OFF","ventilador_pwm":36,"peltier":"OFF"},
         "red":{"rssi":-58},"status":"OK"}
    """.trimIndent()

    @Test
    fun `desestructura el JSON anidado completo`() {
        val t = Telemetria.desdeJson(paqueteReal)!!

        assertEquals("esp32_01", t.nodo)
        assertEquals("2026-09-02T10:32:05Z", t.marcaTiempo)
        assertEquals(12, t.secuencia)

        assertEquals(85, t.sensores.luz)
        assertEquals(24.5f, t.sensores.temp, 0.001f)
        assertEquals(48.2f, t.sensores.humedad, 0.001f)
        assertEquals(12.9f, t.sensores.rocio, 0.001f)

        assertEquals("OFF", t.actuadores.led)
        assertEquals(36, t.actuadores.ventiladorPwm)
        assertEquals(-58, t.rssi)
        assertEquals("OK", t.estado)
    }

    @Test
    fun `un JSON mal formado devuelve null en vez de romper la app`() {
        assertNull(Telemetria.desdeJson("{esto no es json"))
        assertNull(Telemetria.desdeJson(""))
    }

    @Test
    fun `un paquete sin el bloque sensores se descarta`() {
        assertNull(Telemetria.desdeJson("""{"nodo":"esp32_01","status":"OK"}"""))
    }

    @Test
    fun `un firmware viejo sin el campo rocio no rompe el parseo`() {
        val viejo = """{"nodo":"esp32_01","sensores":{"luz":50,"temp":25.0},"status":"OK"}"""
        val t = Telemetria.desdeJson(viejo)!!
        assertEquals(50, t.sensores.luz)
        assertEquals(0.0f, t.sensores.rocio, 0.001f)   // valor por omision
    }

    @Test
    fun `la alerta se dispara cuando la luz cae bajo el umbral guardado`() {
        val t = Telemetria.desdeJson(paqueteReal.replace("\"luz\":85", "\"luz\":20"))!!
        val ajustes = Ajustes(umbralLuz = 40, umbralTemp = 27.0f, alertasActivas = true)
        assertEquals(NivelAlerta.LUZ_BAJA, evaluarAlerta(t, ajustes))
    }

    @Test
    fun `la alerta se dispara cuando la temperatura supera el umbral guardado`() {
        val t = Telemetria.desdeJson(paqueteReal.replace("\"temp\":24.5", "\"temp\":29.3"))!!
        val ajustes = Ajustes(umbralLuz = 40, umbralTemp = 27.0f, alertasActivas = true)
        assertEquals(NivelAlerta.TEMP_ALTA, evaluarAlerta(t, ajustes))
    }

    @Test
    fun `con las alertas desactivadas nunca cambia el color`() {
        val t = Telemetria.desdeJson(paqueteReal.replace("\"temp\":24.5", "\"temp\":35.0"))!!
        val ajustes = Ajustes(umbralLuz = 90, umbralTemp = 27.0f, alertasActivas = false)
        assertEquals(NivelAlerta.NORMAL, evaluarAlerta(t, ajustes))
    }

    @Test
    fun `el mismo paquete cambia de nivel al cambiar el umbral guardado`() {
        val t = Telemetria.desdeJson(paqueteReal)!!   // luz 85, temp 24.5
        assertEquals(NivelAlerta.NORMAL,
            evaluarAlerta(t, Ajustes(40, 27.0f, true)))
        assertEquals(NivelAlerta.LUZ_BAJA,
            evaluarAlerta(t, Ajustes(90, 27.0f, true)))
        assertEquals(NivelAlerta.AMBAS,
            evaluarAlerta(t, Ajustes(90, 20.0f, true)))
    }
}
