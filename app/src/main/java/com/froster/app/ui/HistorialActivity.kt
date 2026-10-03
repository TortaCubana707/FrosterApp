package com.froster.app.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.froster.app.R
import com.froster.app.databinding.ActivityHistorialBinding
import com.froster.app.datos.historial.HistorialDatabase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Muestra el historial guardado en Room. No consulta la base "a mano": se
 * suscribe al Flow del DAO, y Room vuelve a emitir la lista cada vez que el
 * monitor inserta filas. Por eso, si se abre esta pantalla mientras llegan
 * datos, las filas nuevas aparecen arriba sin recargar nada.
 */
class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding
    private val adaptador = HistorialAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.titulo_historial)

        val manejador = LinearLayoutManager(this)
        binding.listaHistorial.layoutManager = manejador
        binding.listaHistorial.adapter = adaptador

        /* Si el usuario está viendo lo más reciente (arriba) y entran filas
           nuevas, se desplaza para mostrarlas. Si bajó a revisar registros
           viejos, no se le mueve la lista de debajo del dedo. */
        adaptador.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(inicio: Int, cantidad: Int) {
                if (inicio == 0 && manejador.findFirstVisibleItemPosition() <= 1) {
                    binding.listaHistorial.scrollToPosition(0)
                }
            }
        })

        val dao = HistorialDatabase.obtener(applicationContext).registroSensorDao()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dao.historial().collect { lista ->
                        adaptador.submitList(lista)   // DiffUtil calcula los cambios
                        binding.textoVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    combine(dao.total(), dao.totalEnAlerta()) { n, alertas -> n to alertas }
                        .collect { (n, alertas) ->
                            binding.textoTotal.text = getString(R.string.fmt_total_historial, n, alertas)
                        }
                }
            }
        }
    }
}
