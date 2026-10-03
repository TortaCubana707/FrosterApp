package com.froster.app.datos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/** Lo que la pantalla necesita saber de la conexión, además de los datos. */
sealed interface EventoMqtt {
    data class Conectando(val uri: String) : EventoMqtt
    data class Conectado(val uri: String) : EventoMqtt
    data class Paquete(val telemetria: Telemetria) : EventoMqtt
    data class Error(val uri: String, val motivo: String) : EventoMqtt
}

/**
 * Suscripción al broker Mosquitto por la red Wi-Fi.
 *
 * Corrección respecto a la Semana 4. La versión anterior tenía la IP escrita en
 * el código y, además, tres fallas que nunca se vieron porque la app corría con
 * una fuente simulada:
 *
 *  1. connect() es una llamada BLOQUEANTE de red. Se ejecutaba en el hilo que
 *     recolectaba el flujo, que era el principal, y en un teléfono real Android
 *     lanza NetworkOnMainThreadException. Ahora todo corre en Dispatchers.IO
 *     gracias a flowOn() al final.
 *  2. Si el broker no respondía se hacía close(e): la excepción subía hasta la
 *     Activity y cerraba la app. Ahora se emite EventoMqtt.Error y se reintenta.
 *  3. Con cleanSession=true, al reconectarse tras un corte de Wi-Fi Paho NO
 *     vuelve a suscribirse solo. La app se quedaba conectada pero sin recibir
 *     nada. Ahora connectComplete() repite la suscripción en cada reconexión.
 */
class FuenteMqtt(
    private val config: ConfigBroker,
    private val topico: String = TOPICO_TELEMETRIA
) {
    companion object {
        const val TOPICO_TELEMETRIA = "froster/equipo01/rack01/esp32_01/telemetria"
        private const val REINTENTO_MS = 5_000L
    }

    fun eventos(): Flow<EventoMqtt> = callbackFlow {
        val uri = config.uri
        // Identificador único: si dos clientes usan el mismo, el broker
        // desconecta al primero y ambos entran en un ciclo de expulsiones.
        val cliente = MqttClient(uri, "froster-android-${System.currentTimeMillis()}", MemoryPersistence())

        val opciones = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 10       // segundos
            keepAliveInterval = 30       // segundos
            isAutomaticReconnect = true
        }

        cliente.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconexion: Boolean, servidor: String?) {
                // Se llama tanto en la primera conexión como en cada reconexión
                // automática: por eso la suscripción vive aquí y no después
                // de connect().
                runCatching { cliente.subscribe(topico, 0) }
                    .onSuccess { trySend(EventoMqtt.Conectado(uri)) }
                    .onFailure { trySend(EventoMqtt.Error(uri, it.message ?: "no se pudo suscribir")) }
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val texto = message?.payload?.toString(Charsets.UTF_8) ?: return
                // Un paquete que no es JSON válido devuelve null y se descarta.
                Telemetria.desdeJson(texto)?.let { trySend(EventoMqtt.Paquete(it)) }
            }

            override fun connectionLost(causa: Throwable?) {
                trySend(EventoMqtt.Error(uri, "se perdió la conexión: ${causa?.message ?: "sin detalle"}"))
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })

        // Primer intento y reintentos. isAutomaticReconnect sólo aplica DESPUÉS
        // de una conexión exitosa, así que el primer contacto se reintenta a mano.
        launch {
            while (isActive && !cliente.isConnected) {
                trySend(EventoMqtt.Conectando(uri))
                try {
                    cliente.connect(opciones)
                } catch (e: Exception) {
                    trySend(EventoMqtt.Error(uri, e.message ?: e.javaClass.simpleName))
                    delay(REINTENTO_MS)
                }
            }
        }

        // Al dejar de recolectar (pantalla en segundo plano o broker nuevo en
        // Ajustes) se cierra la conexión. Sin esto el socket queda abierto.
        awaitClose {
            runCatching {
                if (cliente.isConnected) cliente.disconnect()
                cliente.close()
            }
        }
    }.flowOn(Dispatchers.IO)
}
