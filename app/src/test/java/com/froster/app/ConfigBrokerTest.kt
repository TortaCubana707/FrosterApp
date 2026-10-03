package com.froster.app

import com.froster.app.datos.ConfigBroker
import com.froster.app.datos.Validacion
import com.froster.app.datos.validarBroker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La retroalimentación de la Semana 4 fue que todas las pruebas usaban
 * "localhost". Estas pruebas fijan la regla que lo impide: la app sólo acepta
 * direcciones que un teléfono en Wi-Fi pueda alcanzar.
 */
class ConfigBrokerTest {

    private fun esError(v: Validacion) = v is Validacion.Error

    @Test
    fun `acepta la IP de la computadora en la red local`() {
        val v = validarBroker("192.168.150.215", "1883")
        assertTrue(v is Validacion.Valida)
        assertEquals(ConfigBroker("192.168.150.215", 1883), (v as Validacion.Valida).config)
    }

    @Test
    fun `rechaza localhost porque en el telefono apunta al propio telefono`() {
        assertTrue(esError(validarBroker("localhost", "1883")))
        assertTrue(esError(validarBroker("LocalHost", "1883")))
    }

    @Test
    fun `rechaza cualquier direccion de loopback, no solo 127_0_0_1`() {
        assertTrue(esError(validarBroker("127.0.0.1", "1883")))
        assertTrue(esError(validarBroker("127.1.2.3", "1883")))
        assertTrue(esError(validarBroker("::1", "1883")))
    }

    @Test
    fun `rechaza 0_0_0_0 porque es para escuchar, no para conectarse`() {
        assertTrue(esError(validarBroker("0.0.0.0", "1883")))
    }

    @Test
    fun `rechaza 10_0_2_2 porque solo existe dentro del emulador`() {
        assertTrue(esError(validarBroker("10.0.2.2", "1883")))
    }

    @Test
    fun `rechaza IPs mal formadas`() {
        assertTrue(esError(validarBroker("192.168.1", "1883")))
        assertTrue(esError(validarBroker("192.168.1.300", "1883")))
        assertTrue(esError(validarBroker("", "1883")))
    }

    @Test
    fun `rechaza puertos fuera de rango o no numericos`() {
        assertTrue(esError(validarBroker("192.168.150.215", "0")))
        assertTrue(esError(validarBroker("192.168.150.215", "70000")))
        assertTrue(esError(validarBroker("192.168.150.215", "abc")))
    }

    @Test
    fun `quita espacios antes de validar`() {
        val v = validarBroker("  192.168.150.215 ", " 1883 ")
        assertTrue(v is Validacion.Valida)
    }

    @Test
    fun `arma la URI que espera Paho`() {
        assertEquals("tcp://192.168.150.215:1883", ConfigBroker("192.168.150.215", 1883).uri)
    }

    @Test
    fun `una configuracion vacia no se considera lista para conectar`() {
        assertFalse(ConfigBroker.SIN_CONFIGURAR.estaConfigurado)
        assertTrue(ConfigBroker("192.168.150.215", 1883).estaConfigurado)
    }
}
