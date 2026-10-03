package com.froster.app.datos

/**
 * Fotografía completa del panel: lo que la ESP32 reporta y lo que el usuario
 * ordenó. Se maneja como un solo objeto para que la interfaz nunca pinte una
 * mezcla de datos viejos y nuevos.
 */
data class PanelEstado(
    val led1: Boolean = false,
    val brilloLed: Int = 0,          // 0-255, ciclo de trabajo del canal LEDC
    val relay: Boolean = false,
    val luzPct: Int = 0,             // sensor_ldr, 0-100 %
    val temperatura: Float = Float.NaN,
    val humedad: Float = Float.NaN,
    val online: Boolean = false,
    val marcaTiempo: String = "",
    val conectado: Boolean = false   // hay enlace con Firebase
)

/** Motivo por el que el panel entra en alerta. Decide el color del TextView. */
enum class AlertaPanel { NINGUNA, TEMPERATURA, LUZ, AMBAS }

/**
 * Compara la telemetría contra los umbrales que el usuario guardó en DataStore
 * durante la Semana 4. Es la misma función de negocio, reutilizada: el umbral
 * no se vuelve a codificar a mano en esta pantalla.
 */
fun evaluarPanel(estado: PanelEstado, ajustes: Ajustes): AlertaPanel {
    if (!ajustes.alertasActivas) return AlertaPanel.NINGUNA

    val tempAlta = !estado.temperatura.isNaN() && estado.temperatura > ajustes.umbralTemp
    val luzBaja = estado.luzPct < ajustes.umbralLuz

    return when {
        tempAlta && luzBaja -> AlertaPanel.AMBAS
        tempAlta -> AlertaPanel.TEMPERATURA
        luzBaja -> AlertaPanel.LUZ
        else -> AlertaPanel.NINGUNA
    }
}
