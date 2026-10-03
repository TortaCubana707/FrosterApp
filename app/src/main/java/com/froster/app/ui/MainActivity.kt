package com.froster.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.froster.app.R
import com.froster.app.databinding.ActivityMainBinding
import com.froster.app.datos.AlertaPanel
import com.froster.app.datos.Ajustes
import com.froster.app.datos.PanelEstado
import com.froster.app.datos.Preferencias
import com.froster.app.datos.RepositorioFirebase
import com.froster.app.datos.evaluarPanel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Panel de control de FROSTER — Semana 5.
 *
 * Reúne los cuatro widgets que pide la práctica y los ata a Firebase Realtime
 * Database:
 *
 *   Switch + Button  ->  froster/led1         (booleano)
 *   SeekBar 0-255    ->  froster/brillo_led   (entero, ciclo LEDC)
 *   ImageButton      ->  froster/relay        (booleano, cambia de icono)
 *   TextView x3      <-  froster/sensor_ldr y froster/sensor_dht11
 *
 * El umbral crítico que pinta de rojo el TextView de temperatura no está
 * codificado aquí: se lee del DataStore de la Semana 4, así que el usuario lo
 * configura en la pantalla de Ajustes y esta pantalla reacciona sola.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: RepositorioFirebase
    private lateinit var preferencias: Preferencias

    /**
     * Distingue un cambio hecho por el usuario de uno que llegó de la base.
     *
     * Sin esta bandera se produce un rebote: llega un valor de Firebase, se
     * escribe en el widget, el widget dispara su listener, el listener vuelve a
     * escribir en Firebase, y así indefinidamente. Se pone en true mientras la
     * interfaz se está pintando desde la red y los listeners la consultan antes
     * de escribir.
     */
    private var pintandoDesdeRed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = RepositorioFirebase(applicationContext)
        preferencias = Preferencias(applicationContext)

        configurarMandos()
        conectar()
    }

    // =====================================================================
    //  Mandos: Switch, Button, SeekBar e ImageButton
    // =====================================================================
    private fun configurarMandos() {

        // --- Switch del LED1 ---
        binding.switchLed.setOnCheckedChangeListener { _, marcado ->
            if (!pintandoDesdeRed) repo.escribirLed(marcado)
        }

        // --- Button: alterna el mismo nodo, para demostrar que dos controles
        //     distintos pueden gobernar el mismo valor sin desincronizarse ---
        binding.botonAlternarLed.setOnClickListener {
            repo.escribirLed(!binding.switchLed.isChecked)
        }

        // --- SeekBar de 0 a 255 ---
        binding.deslizadorBrillo.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(sb: SeekBar?, valor: Int, deUsuario: Boolean) {
                    // La etiqueta sigue al dedo siempre, para que el arrastre
                    // se sienta fluido aunque todavía no se haya escrito nada.
                    binding.valorBrillo.text = getString(R.string.fmt_brillo, valor)
                }

                override fun onStartTrackingTouch(sb: SeekBar?) = Unit

                /*
                 * La escritura ocurre al SOLTAR, no en cada paso.
                 *
                 * Arrastrar el deslizador de punta a punta genera más de
                 * doscientos eventos onProgressChanged. Escribir cada uno en
                 * Firebase satura la conexión, gasta cuota y hace que el brillo
                 * de la ESP32 llegue con retraso a un valor que el usuario ya
                 * abandonó. Escribiendo sólo el valor final, el movimiento se ve
                 * fluido en pantalla y el hardware recibe una sola orden.
                 */
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    sb?.let { repo.escribirBrillo(it.progress) }
                }
            })

        // --- ImageButton del relay ---
        binding.botonRelay.setOnClickListener {
            val nuevoEstado = binding.botonRelay.tag != true
            repo.escribirRelay(nuevoEstado)
        }

        binding.botonAjustesPanel.setOnClickListener {
            startActivity(Intent(this, AjustesActivity::class.java))
        }
        binding.botonMonitor.setOnClickListener {
            startActivity(Intent(this, MonitorActivity::class.java))
        }
    }

    // =====================================================================
    //  Conexión y recolección
    // =====================================================================
    private fun conectar() {
        lifecycleScope.launch {
            binding.textoConexion.text = getString(R.string.conexion_autenticando)

            val error = repo.autenticar()
            if (error != null) {
                binding.textoConexion.text = getString(R.string.conexion_error, error)
                binding.textoConexion.setTextColor(color(R.color.alerta_texto))
                return@launch
            }
            repo.sembrarSiVacio()

            /*
             * Igual que en la Semana 4, se unen dos flujos con combine: la
             * telemetría de Firebase y los umbrales de DataStore. Así, cambiar
             * el umbral en Ajustes repinta la alerta de inmediato con la última
             * lectura recibida, sin esperar a que la ESP32 publique otra vez.
             */
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repo.escuchar(), preferencias.ajustes) { estado, ajustes ->
                    estado to ajustes
                }.collect { (estado, ajustes) ->
                    pintar(estado, ajustes)
                }
            }
        }
    }

    // =====================================================================
    //  Pintado
    // =====================================================================
    private fun pintar(e: PanelEstado, ajustes: Ajustes) {
        pintandoDesdeRed = true

        // --- Conexión ---
        binding.textoConexion.text = when {
            !e.conectado -> getString(R.string.conexion_sin_datos)
            e.online -> getString(R.string.conexion_ok, e.marcaTiempo)
            else -> getString(R.string.conexion_nodo_caido)
        }
        binding.textoConexion.setTextColor(
            color(if (e.online) R.color.ok_texto else R.color.texto_tenue))

        // --- Telemetría ---
        binding.valorTemperatura.text =
            if (e.temperatura.isNaN()) getString(R.string.sin_dato)
            else getString(R.string.fmt_grados, e.temperatura)
        binding.valorHumedadDht.text =
            if (e.humedad.isNaN()) getString(R.string.sin_dato)
            else getString(R.string.fmt_porcentaje_f, e.humedad)
        binding.valorLdr.text = getString(R.string.fmt_porcentaje, e.luzPct)

        // --- Mandos: se reflejan sin volver a escribir en la base ---
        binding.switchLed.isChecked = e.led1
        binding.estadoLed.text = getString(
            R.string.fmt_estado_led,
            getString(if (e.led1) R.string.encendido else R.string.apagado))

        binding.deslizadorBrillo.progress = e.brilloLed
        binding.valorBrillo.text = getString(R.string.fmt_brillo, e.brilloLed)

        binding.botonRelay.setImageResource(
            if (e.relay) R.drawable.ic_relay_on else R.drawable.ic_relay_off)
        binding.botonRelay.tag = e.relay
        binding.estadoRelay.text = getString(
            R.string.fmt_estado_relay,
            getString(if (e.relay) R.string.cerrado else R.string.abierto))

        aplicarAlerta(evaluarPanel(e, ajustes), ajustes)

        pintandoDesdeRed = false
    }

    /**
     * Requisito de la tarea: si la lectura supera el umbral crítico, el texto
     * se pone rojo. Se cambia también el mensaje, porque el color por sí solo
     * no es accesible para quien no distingue el rojo del blanco.
     */
    private fun aplicarAlerta(alerta: AlertaPanel, ajustes: Ajustes) {
        val enAlertaTemp =
            alerta == AlertaPanel.TEMPERATURA || alerta == AlertaPanel.AMBAS

        binding.valorTemperatura.setTextColor(
            color(if (enAlertaTemp) R.color.alerta_texto else R.color.texto))

        binding.valorLdr.setTextColor(
            color(if (alerta == AlertaPanel.LUZ || alerta == AlertaPanel.AMBAS)
                R.color.aviso_texto else R.color.texto))

        val (texto, tono) = when (alerta) {
            AlertaPanel.NINGUNA -> getString(R.string.alerta_ninguna) to R.color.ok_texto
            AlertaPanel.TEMPERATURA -> getString(R.string.alerta_temp) to R.color.alerta_texto
            AlertaPanel.LUZ -> getString(R.string.alerta_luz) to R.color.aviso_texto
            AlertaPanel.AMBAS -> getString(R.string.alerta_ambas) to R.color.alerta_texto
        }
        binding.textoAlerta.text = texto
        binding.textoAlerta.setTextColor(color(tono))

        binding.textoUmbral.text =
            getString(R.string.fmt_umbral_critico, ajustes.umbralTemp, ajustes.umbralLuz)
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
}
