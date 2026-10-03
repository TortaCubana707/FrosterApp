package com.froster.app.ui

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.froster.app.R
import com.froster.app.databinding.ActivityMonitorBinding
import com.froster.app.datos.Ajustes
import com.froster.app.datos.EventoMqtt
import com.froster.app.datos.FuenteMqtt
import com.froster.app.datos.NivelAlerta
import com.froster.app.datos.Preferencias
import com.froster.app.datos.Telemetria
import com.froster.app.datos.evaluarAlerta
import com.froster.app.datos.historial.HistorialDatabase
import com.froster.app.datos.historial.aRegistros
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.net.Inet4Address

/**
 * Monitor MQTT (Semana 4, corregido): recibe por Wi-Fi el JSON que publica la
 * ESP32 en el broker Mosquitto, y cambia de color cuando una lectura cruza el
 * umbral guardado en DataStore.
 *
 * Ya no hay fuente simulada: todos los datos que se ven aquí vienen del broker,
 * y la primera línea de la pantalla dice exactamente a qué dirección está
 * conectada la app y qué IP tiene el teléfono en la red.
 */
class MonitorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitorBinding
    private lateinit var preferencias: Preferencias

    /** Última dirección a la que se conectó, para mostrarla junto a cada paquete. */
    private var uriActual = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencias = Preferencias(applicationContext)

        binding.botonAjustes.setOnClickListener {
            startActivity(Intent(this, AjustesActivity::class.java))
        }
        binding.botonHistorial.setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                binding.textoIpTelefono.text =
                    getString(R.string.fmt_ip_telefono, ipDelTelefono() ?: "sin red")

                eventosDelBroker()
                    /* Se guarda AQUÍ, antes de combine(). combine() vuelve a
                       emitir el último paquete cada vez que cambian los
                       umbrales; si se guardara después, un mismo paquete
                       quedaría insertado dos veces en Room. */
                    .onEach { evento -> if (evento is EventoMqtt.Paquete) guardarEnHistorial(evento) }
                    .combine(preferencias.ajustes) { evento, ajustes -> evento to ajustes }
                    .collect { (evento, ajustes) -> atender(evento, ajustes) }
            }
        }
    }

    /**
     * La dirección del broker sale de DataStore. flatMapLatest hace que, si el
     * usuario la cambia en Ajustes, se cierre la conexión vieja y se abra una
     * nueva, sin reiniciar la app.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun eventosDelBroker() = preferencias.broker.flatMapLatest { config ->
        if (config.estaConfigurado) FuenteMqtt(config).eventos()
        else flowOf(EventoMqtt.Error("", getString(R.string.mqtt_sin_configurar)))
    }

    private fun atender(evento: EventoMqtt, ajustes: Ajustes) {
        when (evento) {
            is EventoMqtt.Conectando -> {
                uriActual = evento.uri
                mostrarConexion(getString(R.string.mqtt_conectando, evento.uri), R.color.texto_tenue)
            }
            is EventoMqtt.Conectado ->
                mostrarConexion(getString(R.string.mqtt_conectado, evento.uri), R.color.ok_texto)
            is EventoMqtt.Error -> {
                // Sin URI significa que aún no hay broker configurado.
                val texto = if (evento.uri.isEmpty()) evento.motivo
                            else getString(R.string.mqtt_error, evento.uri, evento.motivo)
                mostrarConexion(texto, R.color.alerta_texto)
            }
            is EventoMqtt.Paquete -> {
                mostrarConexion(getString(R.string.mqtt_recibiendo, uriActual, evento.telemetria.secuencia), R.color.ok_texto)
                pintar(evento.telemetria, ajustes)
            }
        }
    }

    /**
     * Cada paquete recibido se convierte en cuatro filas (una por sensor) y se
     * inserta con una sola transacción. insertarTodos es suspend: Room la corre
     * en su propio hilo de fondo, así que la pantalla no espera al disco.
     */
    private suspend fun guardarEnHistorial(evento: EventoMqtt.Paquete) {
        val dao = HistorialDatabase.obtener(applicationContext).registroSensorDao()
        // first(): el valor de los umbrales EN ESTE MOMENTO, sin quedarse escuchando
        val umbrales = preferencias.ajustes.first()
        dao.insertarTodos(evento.telemetria.aRegistros(System.currentTimeMillis(), umbrales))
    }

    private fun mostrarConexion(texto: String, color: Int) {
        binding.textoConexion.text = texto
        binding.textoConexion.setTextColor(ContextCompat.getColor(this, color))
    }

    /**
     * IP que el router le dio al teléfono. Mostrarla en pantalla es la prueba
     * de que la app está en la misma red Wi-Fi que el broker y la ESP32, y no
     * hablando consigo misma por localhost.
     */
    private fun ipDelTelefono(): String? {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return null
        val red = cm.activeNetwork ?: return null
        return cm.getLinkProperties(red)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    /** Vuelca los valores del paquete JSON en los componentes de la interfaz. */
    private fun pintar(t: Telemetria, ajustes: Ajustes) {
        binding.valorLuz.text = getString(R.string.fmt_porcentaje, t.sensores.luz)
        binding.valorTemp.text = getString(R.string.fmt_grados, t.sensores.temp)
        binding.valorHumedad.text = getString(R.string.fmt_porcentaje_f, t.sensores.humedad)
        binding.valorRocio.text = getString(R.string.fmt_grados, t.sensores.rocio)

        binding.valorLed.text = t.actuadores.led
        binding.valorVentilador.text = getString(R.string.fmt_porcentaje, t.actuadores.ventiladorPwm)
        binding.valorPeltier.text = t.actuadores.peltier

        binding.textoNodo.text = getString(R.string.fmt_nodo, t.nodo, t.secuencia)
        binding.textoMarcaTiempo.text = t.marcaTiempo
        binding.textoRssi.text = getString(R.string.fmt_rssi, t.rssi)

        binding.textoUmbrales.text = getString(
            R.string.fmt_umbrales, ajustes.umbralLuz, ajustes.umbralTemp
        )

        aplicarAlerta(evaluarAlerta(t, ajustes))
    }

    /**
     * Traduce el nivel de alerta a color de fondo y mensaje.
     * Es la parte de la tarea que pide reaccionar visualmente al umbral.
     */
    private fun aplicarAlerta(nivel: NivelAlerta) {
        val (colorFondo, mensaje) = when (nivel) {
            NivelAlerta.NORMAL -> R.color.fondo_normal to getString(R.string.estado_normal)
            NivelAlerta.LUZ_BAJA -> R.color.fondo_aviso to getString(R.string.estado_luz_baja)
            NivelAlerta.TEMP_ALTA -> R.color.fondo_alerta to getString(R.string.estado_temp_alta)
            NivelAlerta.AMBAS -> R.color.fondo_alerta to getString(R.string.estado_ambas)
        }

        binding.raiz.setBackgroundColor(ContextCompat.getColor(this, colorFondo))
        binding.bannerAlerta.text = mensaje
        binding.bannerAlerta.setBackgroundColor(ContextCompat.getColor(this, colorFondo))
    }
}
