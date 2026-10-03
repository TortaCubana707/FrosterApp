package com.froster.app.datos

import org.json.JSONObject

/**
 * Representacion en Kotlin del paquete JSON que publica la ESP32.
 *
 * El JSON que llega es anidado:
 *
 *   {
 *     "nodo": "esp32_01",
 *     "ts": "2026-09-02T10:32:05Z",
 *     "seq": 12,
 *     "sensores":   { "luz": 85, "temp": 24.5, "hum": 48.2, "rocio": 12.9 },
 *     "actuadores": { "led": "ON", "ventilador_pwm": 62, "peltier": "OFF" },
 *     "red":        { "rssi": -58 },
 *     "status": "OK"
 *   }
 *
 * Se desestructura en tres objetos planos porque la interfaz no necesita saber
 * como venia agrupado: solo necesita valores tipados.
 */
data class Sensores(
    val luz: Int,
    val temp: Float,
    val humedad: Float,
    val rocio: Float
)

data class Actuadores(
    val led: String,
    val ventiladorPwm: Int,
    val peltier: String
)

data class Telemetria(
    val nodo: String,
    val marcaTiempo: String,
    val secuencia: Int,
    val sensores: Sensores,
    val actuadores: Actuadores,
    val rssi: Int,
    val estado: String
) {
    companion object {

        /**
         * Convierte la cadena recibida por MQTT en un objeto Telemetria.
         *
         * Devuelve null si el texto no es JSON valido o si le falta alguno de
         * los bloques obligatorios. Se prefiere null antes que lanzar una
         * excepcion porque un paquete corrupto es una situacion esperable en
         * una red inalambrica: la aplicacion debe descartarlo y seguir viva,
         * no cerrarse.
         *
         * Se usa optString / optInt / optDouble en vez de getString / getInt.
         * Los get lanzan JSONException si la clave no existe; los opt devuelven
         * el valor por omision que se les indique, de modo que un firmware
         * mas viejo que aun no envie "rocio" no rompe la aplicacion.
         */
        fun desdeJson(texto: String): Telemetria? {
            return try {
                val raiz = JSONObject(texto)

                // Bloques anidados obligatorios
                val objSensores = raiz.optJSONObject("sensores") ?: return null
                val objActuadores = raiz.optJSONObject("actuadores") ?: JSONObject()
                val objRed = raiz.optJSONObject("red") ?: JSONObject()

                Telemetria(
                    nodo = raiz.optString("nodo", "desconocido"),
                    marcaTiempo = raiz.optString("ts", ""),
                    secuencia = raiz.optInt("seq", 0),
                    sensores = Sensores(
                        luz = objSensores.optInt("luz", 0),
                        temp = objSensores.optDouble("temp", 0.0).toFloat(),
                        humedad = objSensores.optDouble("hum", 0.0).toFloat(),
                        rocio = objSensores.optDouble("rocio", 0.0).toFloat()
                    ),
                    actuadores = Actuadores(
                        led = objActuadores.optString("led", "?"),
                        ventiladorPwm = objActuadores.optInt("ventilador_pwm", 0),
                        peltier = objActuadores.optString("peltier", "?")
                    ),
                    rssi = objRed.optInt("rssi", 0),
                    estado = raiz.optString("status", "OK")
                )
            } catch (e: Exception) {
                // JSON mal formado: el paquete se descarta y el flujo continua.
                null
            }
        }
    }
}

/**
 * Resultado de comparar una lectura contra los umbrales guardados en DataStore.
 * Es lo que decide el color de fondo de la pantalla principal.
 */
enum class NivelAlerta { NORMAL, LUZ_BAJA, TEMP_ALTA, AMBAS }

fun evaluarAlerta(telemetria: Telemetria, ajustes: Ajustes): NivelAlerta {
    if (!ajustes.alertasActivas) return NivelAlerta.NORMAL

    val luzBaja = telemetria.sensores.luz < ajustes.umbralLuz
    val tempAlta = telemetria.sensores.temp > ajustes.umbralTemp

    return when {
        luzBaja && tempAlta -> NivelAlerta.AMBAS
        tempAlta -> NivelAlerta.TEMP_ALTA
        luzBaja -> NivelAlerta.LUZ_BAJA
        else -> NivelAlerta.NORMAL
    }
}
