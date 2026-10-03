package com.froster.app.datos

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Valores que el usuario configura en la pantalla de ajustes.
 *
 * Se agrupan en una data class para que la interfaz reciba un solo objeto
 * coherente en lugar de tres flujos sueltos que podrian llegar desfasados.
 */
data class Ajustes(
    val umbralLuz: Int,       // % por debajo del cual se considera oscuridad
    val umbralTemp: Float,    // grados Celsius por encima de los cuales hay alerta
    val alertasActivas: Boolean
)

/**
 * Capa de persistencia clave-valor sobre Preferences DataStore.
 *
 * Por que DataStore y no SharedPreferences
 * ----------------------------------------
 * SharedPreferences expone metodos sincronos (getInt, commit) que bloquean el
 * hilo principal, y no avisa de los errores de escritura. DataStore guarda en
 * disco dentro de una corrutina, entrega los datos como Flow —de modo que la
 * pantalla se actualiza sola cuando el valor cambia— y las escrituras son
 * transaccionales: o se aplica todo el bloque edit o no se aplica nada.
 *
 * La extension preferencesDataStore crea una unica instancia por proceso. Si se
 * crearan dos DataStore apuntando al mismo archivo, la libreria lanza una
 * excepcion; por eso la propiedad se declara a nivel de archivo y no dentro de
 * la clase.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ajustes_froster"
)

class Preferencias(private val contexto: Context) {

    companion object {
        // Las claves son tipadas: pedir un Int con una clave de String no compila.
        private val CLAVE_UMBRAL_LUZ = intPreferencesKey("umbral_luz")
        private val CLAVE_UMBRAL_TEMP = floatPreferencesKey("umbral_temp")
        private val CLAVE_ALERTAS = booleanPreferencesKey("alertas_activas")

        // Dirección del broker MQTT. Se guarda aquí y no en el código porque la
        // IP de la computadora cambia cada vez que el router le asigna otra.
        private val CLAVE_BROKER_HOST = stringPreferencesKey("broker_host")
        private val CLAVE_BROKER_PUERTO = intPreferencesKey("broker_puerto")

        // Valores de fabrica. El de temperatura sale del limite superior que
        // ASHRAE recomienda para el aire de entrada de un equipo de computo.
        const val UMBRAL_LUZ_POR_OMISION = 40
        const val UMBRAL_TEMP_POR_OMISION = 27.0f
    }

    /**
     * Flujo con los ajustes guardados. Emite el valor actual en cuanto alguien
     * lo recolecta y vuelve a emitir cada vez que algo cambia, sin necesidad de
     * volver a leer el archivo a mano.
     *
     * El catch es obligatorio en produccion: si el archivo se corrompe o el
     * disco falla, DataStore lanza IOException dentro del flujo. Sin este
     * bloque la aplicacion se cerraria; con el, se cae de vuelta a los valores
     * por omision y la pantalla sigue funcionando.
     */
    val ajustes: Flow<Ajustes> = contexto.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferencias ->
            Ajustes(
                umbralLuz = preferencias[CLAVE_UMBRAL_LUZ] ?: UMBRAL_LUZ_POR_OMISION,
                umbralTemp = preferencias[CLAVE_UMBRAL_TEMP] ?: UMBRAL_TEMP_POR_OMISION,
                alertasActivas = preferencias[CLAVE_ALERTAS] ?: true
            )
        }

    /** Dirección del broker. Vacía hasta que el usuario la escribe en Ajustes. */
    val broker: Flow<ConfigBroker> = contexto.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { p ->
            ConfigBroker(
                host = p[CLAVE_BROKER_HOST] ?: "",
                puerto = p[CLAVE_BROKER_PUERTO] ?: ConfigBroker.PUERTO_MQTT
            )
        }

    suspend fun guardarBroker(config: ConfigBroker) {
        contexto.dataStore.edit { p ->
            p[CLAVE_BROKER_HOST] = config.host
            p[CLAVE_BROKER_PUERTO] = config.puerto
        }
    }

    /** Guarda los tres valores en una sola transaccion. */
    suspend fun guardar(ajustes: Ajustes) {
        contexto.dataStore.edit { preferencias ->
            preferencias[CLAVE_UMBRAL_LUZ] = ajustes.umbralLuz
            preferencias[CLAVE_UMBRAL_TEMP] = ajustes.umbralTemp
            preferencias[CLAVE_ALERTAS] = ajustes.alertasActivas
        }
    }

    /** Devuelve los ajustes a los valores de fabrica. */
    suspend fun restablecer() {
        // Sólo los umbrales: borrar también el broker desconectaría la app.
        contexto.dataStore.edit {
            it.remove(CLAVE_UMBRAL_LUZ)
            it.remove(CLAVE_UMBRAL_TEMP)
            it.remove(CLAVE_ALERTAS)
        }
    }
}
