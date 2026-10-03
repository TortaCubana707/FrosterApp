package com.froster.app.datos

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Único punto de contacto con Firebase Realtime Database.
 *
 * Estructura de nodos que crea y consume:
 *
 *   froster/
 *     ├── led1          (boolean)  Android escribe · ESP32 lee
 *     ├── brillo_led    (0-255)    Android escribe · ESP32 lee
 *     ├── relay         (boolean)  Android escribe · ESP32 lee
 *     ├── sensor_ldr    (0-100)    ESP32 escribe  · Android lee
 *     ├── sensor_dht11/            ESP32 escribe  · Android lee
 *     │     ├── temperatura (float)
 *     │     └── humedad     (float)
 *     └── estado/
 *           ├── online (boolean, con onDisconnect)
 *           └── ts     (string ISO 8601)
 *
 * Los tres primeros son de mando y los demás de telemetría. Separar los dos
 * sentidos evita el problema clásico de este tipo de paneles: que el listener
 * reciba de vuelta lo que la propia aplicación acaba de escribir y se produzca
 * un rebote entre la interfaz y la base.
 */
class RepositorioFirebase(contexto: Context) {

    companion object {
        private const val TAG = "FrosterFirebase"
        private const val NOMBRE_APP = "froster"
    }

    private val app: FirebaseApp = inicializar(contexto)
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance(app, FirebaseConfig.DATABASE_URL)
    private val raiz: DatabaseReference = db.getReference(FirebaseConfig.RAIZ)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(app)

    init {
        /*
         * La persistencia en disco es la pieza que cumple el requisito de "sin
         * pérdidas de conexión" de la tarea. Con ella el SDK guarda en el
         * teléfono la última copia de los nodos y, sobre todo, encola las
         * escrituras hechas sin red para reenviarlas al reconectar. Sin esto,
         * pulsar el Switch en un punto sin cobertura perdería la orden.
         *
         * Debe llamarse ANTES de cualquier otra operación con la base, y una
         * sola vez por instancia; de lo contrario lanza excepción.
         */
        runCatching { db.setPersistenceEnabled(true) }
            .onFailure { Log.d(TAG, "persistencia ya estaba activa") }
        runCatching { raiz.keepSynced(true) }
    }

    private fun inicializar(contexto: Context): FirebaseApp {
        // Si ya existe una instancia con este nombre se reutiliza: volver a
        // inicializarla lanza IllegalStateException al rotar la pantalla.
        FirebaseApp.getApps(contexto).firstOrNull { it.name == NOMBRE_APP }?.let { return it }

        val opciones = FirebaseOptions.Builder()
            .setApplicationId(FirebaseConfig.APP_ID)
            .setApiKey(FirebaseConfig.API_KEY)
            .setDatabaseUrl(FirebaseConfig.DATABASE_URL)
            .setProjectId(FirebaseConfig.PROJECT_ID)
            .build()

        return FirebaseApp.initializeApp(contexto, opciones, NOMBRE_APP)
    }

    /**
     * Inicia sesión con el usuario de Authentication. Las reglas de la base
     * exigen auth != null, así que sin esto toda lectura devuelve
     * PERMISSION_DENIED. Devuelve null si todo fue bien, o el mensaje de error.
     */
    suspend fun autenticar(): String? = try {
        if (auth.currentUser == null) {
            auth.signInWithEmailAndPassword(
                FirebaseConfig.USUARIO, FirebaseConfig.CONTRASENA
            ).await()
        }
        null
    } catch (e: Exception) {
        Log.w(TAG, "fallo de autenticacion", e)
        e.message ?: "error de autenticación"
    }

    /**
     * Escucha la rama completa del proyecto con un ValueEventListener y la
     * entrega como Flow.
     *
     * Se escucha el nodo padre y no cinco nodos por separado a propósito: un
     * solo listener significa una sola conexión, un solo callback y un objeto
     * coherente. Cinco listeners entregarían los valores por separado y la
     * pantalla podría mostrar una temperatura nueva junto a un brillo viejo.
     *
     * awaitClose quita el listener cuando la pantalla deja de recolectar. Sin
     * esa línea el listener sobrevive a la Activity y sigue recibiendo datos:
     * es la fuga de memoria más común al trabajar con Firebase en Android.
     */
    fun escuchar(): Flow<PanelEstado> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(leerSnapshot(snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listener cancelado: ${error.message}")
                trySend(PanelEstado(conectado = false))
            }
        }
        raiz.addValueEventListener(listener)
        awaitClose { raiz.removeEventListener(listener) }
    }

    /** Convierte el snapshot en el objeto de la interfaz, tolerando nodos ausentes. */
    private fun leerSnapshot(s: DataSnapshot): PanelEstado {
        val dht = s.child(FirebaseConfig.NODO_DHT11)
        return PanelEstado(
            led1 = s.child(FirebaseConfig.NODO_LED1).getValue(Boolean::class.java) ?: false,
            brilloLed = (s.child(FirebaseConfig.NODO_BRILLO).getValue(Long::class.java) ?: 0L)
                .toInt().coerceIn(0, 255),
            relay = s.child(FirebaseConfig.NODO_RELAY).getValue(Boolean::class.java) ?: false,
            luzPct = (s.child(FirebaseConfig.NODO_LDR).getValue(Long::class.java) ?: 0L)
                .toInt().coerceIn(0, 100),
            temperatura = dht.child("temperatura").getValue(Double::class.java)?.toFloat() ?: Float.NaN,
            humedad = dht.child("humedad").getValue(Double::class.java)?.toFloat() ?: Float.NaN,
            online = s.child(FirebaseConfig.NODO_ESTADO).child("online")
                .getValue(Boolean::class.java) ?: false,
            marcaTiempo = s.child(FirebaseConfig.NODO_ESTADO).child("ts")
                .getValue(String::class.java) ?: "",
            conectado = s.exists()
        )
    }

    // ---------- Escrituras de mando ----------

    fun escribirLed(encendido: Boolean) {
        raiz.child(FirebaseConfig.NODO_LED1).setValue(encendido)
    }

    fun escribirBrillo(valor: Int) {
        raiz.child(FirebaseConfig.NODO_BRILLO).setValue(valor.coerceIn(0, 255))
    }

    fun escribirRelay(activo: Boolean) {
        raiz.child(FirebaseConfig.NODO_RELAY).setValue(activo)
    }

    /**
     * Crea los cinco nodos con valores neutros si la rama está vacía. Sirve para
     * que la estructura aparezca completa en la consola desde la primera
     * ejecución, sin esperar a que la ESP32 publique.
     */
    fun sembrarSiVacio() {
        raiz.get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                raiz.updateChildren(
                    mapOf(
                        FirebaseConfig.NODO_LED1 to false,
                        FirebaseConfig.NODO_BRILLO to 0,
                        FirebaseConfig.NODO_RELAY to false,
                        FirebaseConfig.NODO_LDR to 0,
                        "${FirebaseConfig.NODO_DHT11}/temperatura" to 0.0,
                        "${FirebaseConfig.NODO_DHT11}/humedad" to 0.0
                    )
                )
            }
        }
    }
}
