package com.froster.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.froster.app.R
import com.froster.app.databinding.ActivityAjustesBinding
import com.froster.app.datos.Ajustes
import com.froster.app.datos.Preferencias
import com.froster.app.datos.Validacion
import com.froster.app.datos.validarBroker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Pantalla de ajustes. Guarda los umbrales en Preferences DataStore.
 *
 * La parte de la tarea que pide que el ajuste sobreviva al cierre completo de
 * la aplicacion se resuelve sola aqui: DataStore escribe en un archivo dentro
 * del almacenamiento privado de la aplicacion, asi que el valor sigue ahi
 * despues de matar el proceso. Lo unico que hace falta es LEERLO al abrir la
 * pantalla, que es lo que hace cargarValoresGuardados().
 */
class AjustesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAjustesBinding
    private lateinit var preferencias: Preferencias

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAjustesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencias = Preferencias(applicationContext)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.titulo_ajustes)

        cargarValoresGuardados()

        // Etiquetas que siguen al deslizador mientras se arrastra
        binding.deslizadorLuz.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, valor: Int, deUsuario: Boolean) {
                    binding.etiquetaLuz.text = getString(R.string.fmt_umbral_luz, valor)
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) = Unit
            })

        binding.deslizadorTemp.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, valor: Int, deUsuario: Boolean) {
                    binding.etiquetaTemp.text = getString(R.string.fmt_umbral_temp, temperaturaDesde(valor))
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) = Unit
            })

        binding.botonGuardar.setOnClickListener { guardar() }
        binding.botonRestablecer.setOnClickListener { restablecer() }
    }

    /**
     * Lee UNA sola emision del flujo con first() y llena los controles.
     *
     * Aqui se usa first() y no collect a proposito: la pantalla de ajustes
     * quiere el valor guardado en el momento de abrirse, no seguir escuchando
     * cambios. Si recolectara de forma continua, el propio acto de guardar
     * reescribiria los controles mientras el usuario los esta moviendo.
     */
    private fun cargarValoresGuardados() {
        lifecycleScope.launch {
            val ajustes = preferencias.ajustes.first()

            binding.deslizadorLuz.progress = ajustes.umbralLuz
            binding.etiquetaLuz.text = getString(R.string.fmt_umbral_luz, ajustes.umbralLuz)

            binding.deslizadorTemp.progress = posicionDesde(ajustes.umbralTemp)
            binding.etiquetaTemp.text = getString(R.string.fmt_umbral_temp, ajustes.umbralTemp)

            binding.interruptorAlertas.isChecked = ajustes.alertasActivas

            val broker = preferencias.broker.first()
            binding.campoBrokerHost.setText(broker.host)
            binding.campoBrokerPuerto.setText(broker.puerto.toString())
        }
    }

    private fun guardar() {
        /* Primero se valida la direccion del broker. Si no sirve (por ejemplo
           "localhost"), no se guarda NADA y se explica el motivo en pantalla:
           guardar a medias dejaria la app apuntando a un broker inalcanzable. */
        val validacion = validarBroker(
            binding.campoBrokerHost.text.toString(),
            binding.campoBrokerPuerto.text.toString()
        )
        if (validacion is Validacion.Error) {
            binding.errorBroker.text = validacion.motivo
            binding.errorBroker.visibility = View.VISIBLE
            binding.campoBrokerHost.requestFocus()
            return
        }
        binding.errorBroker.visibility = View.GONE
        val broker = (validacion as Validacion.Valida).config

        val ajustes = Ajustes(
            umbralLuz = binding.deslizadorLuz.progress,
            umbralTemp = temperaturaDesde(binding.deslizadorTemp.progress),
            alertasActivas = binding.interruptorAlertas.isChecked
        )

        // La escritura es asincrona: el Toast se muestra hasta que edit()
        // confirmo que el archivo quedo escrito.
        lifecycleScope.launch {
            preferencias.guardar(ajustes)
            preferencias.guardarBroker(broker)
            Toast.makeText(
                this@AjustesActivity,
                getString(R.string.mensaje_guardado, ajustes.umbralLuz, ajustes.umbralTemp) +
                    "\n" + getString(R.string.mensaje_broker_guardado, broker.uri),
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun restablecer() {
        lifecycleScope.launch {
            preferencias.restablecer()
            cargarValoresGuardados()
            Toast.makeText(this@AjustesActivity,
                getString(R.string.mensaje_restablecido), Toast.LENGTH_SHORT).show()
        }
    }

    // El SeekBar solo maneja enteros. El deslizador de temperatura recorre de
    // 15.0 a 40.0 grados en pasos de medio grado: 51 posiciones.
    private fun temperaturaDesde(posicion: Int): Float = 15.0f + posicion * 0.5f
    private fun posicionDesde(temperatura: Float): Int =
        ((temperatura - 15.0f) / 0.5f).toInt().coerceIn(0, 50)
}
