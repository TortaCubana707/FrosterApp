package com.froster.app.datos

/**
 * Dirección del broker Mosquitto al que se conecta la app.
 *
 * Antes estaba escrita en el código ("tcp://192.168.100.21:1883"). Eso tenía dos
 * problemas: la IP de la computadora cambia cada vez que el router le asigna
 * otra por DHCP (y de hecho cambió a 192.168.150.215), y cambiarla obligaba a
 * recompilar. Ahora el usuario la escribe en Ajustes y se guarda en DataStore.
 */
data class ConfigBroker(val host: String, val puerto: Int) {

    /** Formato que exige Paho: esquema tcp, host y puerto. */
    val uri: String get() = "tcp://$host:$puerto"

    val estaConfigurado: Boolean get() = host.isNotBlank()

    companion object {
        const val PUERTO_MQTT = 1883
        val SIN_CONFIGURAR = ConfigBroker("", PUERTO_MQTT)
    }
}

/** Resultado de revisar lo que el usuario escribió en Ajustes. */
sealed interface Validacion {
    data class Valida(val config: ConfigBroker) : Validacion
    data class Error(val motivo: String) : Validacion
}

private val REGEX_IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

/**
 * Revisa la dirección antes de guardarla.
 *
 * La regla importante es la de loopback. En la computadora, "localhost" es la
 * computadora; pero en el teléfono, "localhost" es el TELÉFONO, donde no hay
 * ningún broker. Por eso una app que funciona con localhost en las pruebas de
 * escritorio nunca funciona en el teléfono real. Se rechaza con una explicación
 * para que el error se entienda, no sólo se corrija.
 */
fun validarBroker(hostTexto: String, puertoTexto: String): Validacion {
    val host = hostTexto.trim()
    val puerto = puertoTexto.trim().toIntOrNull()

    if (host.isEmpty()) {
        return Validacion.Error("Escribe la IP de la computadora donde corre Mosquitto.")
    }
    if (host.equals("localhost", ignoreCase = true) || host == "::1" || host.startsWith("127.")) {
        return Validacion.Error(
            "\"$host\" es el propio teléfono, no tu computadora. " +
                "Usa la IP de la computadora en la red Wi-Fi (en Ubuntu: ip -4 addr)."
        )
    }
    if (host == "0.0.0.0") {
        return Validacion.Error(
            "0.0.0.0 sirve para que el broker ESCUCHE en todas las interfaces; " +
                "para conectarse hay que usar una IP concreta."
        )
    }
    if (host == "10.0.2.2") {
        return Validacion.Error(
            "10.0.2.2 sólo existe dentro del emulador. En un teléfono real usa la IP " +
                "de la computadora en la red Wi-Fi."
        )
    }

    val octetos = REGEX_IPV4.matchEntire(host)?.groupValues?.drop(1)?.map { it.toInt() }
    if (octetos == null || octetos.any { it > 255 }) {
        return Validacion.Error("\"$host\" no es una IPv4 válida. Ejemplo: 192.168.150.215")
    }

    if (puerto == null || puerto !in 1..65535) {
        return Validacion.Error("El puerto debe ser un número entre 1 y 65535 (Mosquitto usa 1883).")
    }

    return Validacion.Valida(ConfigBroker(host, puerto))
}
