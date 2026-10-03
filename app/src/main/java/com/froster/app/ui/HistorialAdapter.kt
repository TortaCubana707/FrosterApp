package com.froster.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.froster.app.R
import com.froster.app.databinding.ItemRegistroBinding
import com.froster.app.datos.historial.RegistroSensor
import com.froster.app.datos.historial.TipoSensor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adaptador del historial. Une cada RegistroSensor con una fila item_registro.
 *
 * Se usa ListAdapter en lugar de RecyclerView.Adapter con notifyDataSetChanged().
 * Cuando Room emite la lista nueva, DiffUtil la compara con la anterior en un
 * hilo de fondo y sólo anima las filas que realmente cambiaron. Con
 * notifyDataSetChanged() se redibujaría la lista completa en cada paquete, y
 * con cuatro inserciones cada tres segundos la lista parpadearía.
 */
class HistorialAdapter : ListAdapter<RegistroSensor, HistorialAdapter.Fila>(Diferencias) {

    /** Guarda las referencias a las vistas de UNA fila, para no buscarlas cada vez. */
    class Fila(val binding: ItemRegistroBinding) : RecyclerView.ViewHolder(binding.root)

    // Se crea una vez por fila VISIBLE, no una por registro de la base.
    override fun onCreateViewHolder(padre: ViewGroup, tipoVista: Int): Fila =
        Fila(ItemRegistroBinding.inflate(LayoutInflater.from(padre.context), padre, false))

    // Se llama cada vez que una fila reciclada va a mostrar otro registro.
    override fun onBindViewHolder(fila: Fila, posicion: Int) {
        val r = getItem(posicion)
        val ctx = fila.binding.root.context

        fila.binding.textoSensor.text = ctx.getString(nombreSensor(r.tipoSensor))
        fila.binding.textoFechaHora.text = FORMATO_FECHA_HORA.format(Date(r.timestamp))
        fila.binding.textoValor.text = formatearValor(r.tipoSensor, r.valorLectura)

        /* Como las filas se RECICLAN, cada propiedad se asigna en los dos
           casos. Si sólo se pintara de rojo cuando hay alerta, una fila que
           antes mostró una alerta seguiría roja al reutilizarse para una
           lectura normal. */
        fila.binding.textoAlerta.visibility = if (r.estadoAlerta) View.VISIBLE else View.GONE
        fila.binding.textoValor.setTextColor(
            ContextCompat.getColor(ctx, if (r.estadoAlerta) R.color.alerta_texto else R.color.texto)
        )
    }

    private fun nombreSensor(tipo: String) = when (tipo) {
        TipoSensor.LUZ -> R.string.sensor_luz
        TipoSensor.TEMPERATURA -> R.string.sensor_temperatura
        TipoSensor.HUMEDAD -> R.string.sensor_humedad
        else -> R.string.sensor_rocio
    }

    private fun formatearValor(tipo: String, valor: Double) = when (tipo) {
        TipoSensor.LUZ -> String.format(Locale.US, "%.0f %%", valor)
        TipoSensor.HUMEDAD -> String.format(Locale.US, "%.1f %%", valor)
        else -> String.format(Locale.US, "%.1f °C", valor)
    }

    /**
     * Le dice a DiffUtil cómo comparar. "Mismo ítem" = misma fila de la base
     * (mismo id). "Mismo contenido" = los campos no cambiaron; como
     * RegistroSensor es data class, == compara todos los campos.
     */
    private object Diferencias : DiffUtil.ItemCallback<RegistroSensor>() {
        override fun areItemsTheSame(a: RegistroSensor, b: RegistroSensor) = a.id == b.id
        override fun areContentsTheSame(a: RegistroSensor, b: RegistroSensor) = a == b
    }

    private companion object {
        val FORMATO_FECHA_HORA = SimpleDateFormat("dd/MM/yyyy · HH:mm:ss", Locale.getDefault())
    }
}
