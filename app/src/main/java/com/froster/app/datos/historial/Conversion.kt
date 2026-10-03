package com.froster.app.datos.historial

import com.froster.app.datos.Ajustes
import com.froster.app.datos.Telemetria

/**
 * Convierte un paquete de la ESP32 en las filas que se guardan: una por sensor.
 * Es una función pura (sin base de datos ni Android) para poder probarla sola.
 *
 * estadoAlerta usa los mismos umbrales que el usuario guardó en DataStore
 * (Semana 4). Sólo luz y temperatura tienen umbral; humedad y punto de rocío
 * se guardan siempre sin alerta.
 */
fun Telemetria.aRegistros(ahora: Long, umbrales: Ajustes): List<RegistroSensor> {
    val activas = umbrales.alertasActivas
    return listOf(
        RegistroSensor(
            tipoSensor = TipoSensor.LUZ,
            valorLectura = sensores.luz.toDouble(),
            timestamp = ahora,
            estadoAlerta = activas && sensores.luz < umbrales.umbralLuz
        ),
        RegistroSensor(
            tipoSensor = TipoSensor.TEMPERATURA,
            valorLectura = sensores.temp.aDoubleExacto(),
            timestamp = ahora,
            estadoAlerta = activas && sensores.temp > umbrales.umbralTemp
        ),
        RegistroSensor(
            tipoSensor = TipoSensor.HUMEDAD,
            valorLectura = sensores.humedad.aDoubleExacto(),
            timestamp = ahora
        ),
        RegistroSensor(
            tipoSensor = TipoSensor.PUNTO_ROCIO,
            valorLectura = sensores.rocio.aDoubleExacto(),
            timestamp = ahora
        )
    )
}

/*
 * 11.6f no se puede representar exacto en binario; en Float se guarda como
 * 11.6000003814... y toDouble() conserva ese error: 11.600000381469727. Así
 * quedaron las filas de la versión 1. Pasar por el texto ("11.6") y de ahí a
 * Double da el 11.6 que realmente midió el sensor.
 */
private fun Float.aDoubleExacto(): Double = toString().toDouble()

/** Nombres fijos de tipoSensor, para no repetir cadenas sueltas por el código. */
object TipoSensor {
    const val LUZ = "luz"
    const val TEMPERATURA = "temperatura"
    const val HUMEDAD = "humedad"
    const val PUNTO_ROCIO = "punto_rocio"
}
