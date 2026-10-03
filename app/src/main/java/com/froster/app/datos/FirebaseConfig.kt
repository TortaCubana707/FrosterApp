package com.froster.app.datos

/**
 * Configuración del proyecto de Firebase.
 *
 * POR QUÉ NO SE USA google-services.json
 * --------------------------------------
 * El camino habitual es registrar la app en la consola de Firebase, descargar
 * google-services.json y aplicar el plugin com.google.gms.google-services. Aquí
 * se inicializa en código con FirebaseOptions, que es una vía igual de oficial
 * (FirebaseApp.initializeApp(context, options)) y tiene dos ventajas para este
 * proyecto: el módulo compila aunque el archivo JSON no esté presente, y todos
 * los valores quedan a la vista en un solo lugar, que es justo lo que hay que
 * documentar en el entregable.
 *
 * DE DÓNDE SALE CADA VALOR (consola de Firebase)
 * ----------------------------------------------
 *   PROJECT_ID    Configuración del proyecto ▸ General ▸ ID del proyecto
 *   API_KEY       Configuración del proyecto ▸ General ▸ Clave de API web
 *   DATABASE_URL  Realtime Database ▸ la URL que aparece arriba de los datos
 *   APP_ID        Configuración del proyecto ▸ Tus apps ▸ ID de la aplicación
 *
 * IMPORTANTE sobre APP_ID: hay que registrar la aplicación com.froster.app en
 * la consola (Agregar app ▸ Android) y pegar aquí el identificador que genere.
 * Mientras tanto se usa el de otra app del MISMO proyecto: la base de datos y
 * la autenticación funcionan igual porque lo que valida el servidor es la clave
 * de API y la URL, pero conviene dejarlo correcto antes de entregar.
 *
 * Sobre la clave de API: en Firebase para cliente es pública por diseño. Lo que
 * protege la base son las reglas de seguridad de Realtime Database, no ocultar
 * esta cadena. La CONTRASEÑA del usuario sí es secreta y por eso está vacía.
 */
object FirebaseConfig {

    const val PROJECT_ID   = "sqliteemi"
    const val API_KEY      = "TU_API_KEY_DE_FIREBASE"
    const val DATABASE_URL = "https://sqliteemi-default-rtdb.firebaseio.com"
    const val APP_ID       = "1:519700348606:android:46096642949f978e9328ef"

    /** Usuario creado en Authentication para que las reglas no queden abiertas. */
    const val USUARIO      = "esp32@correo.com"
    const val CONTRASENA   = "tortacubana"

    /** Raíz del proyecto dentro de la base. Bajo ella cuelgan los cinco nodos. */
    const val RAIZ = "froster"

    // Nombres de nodo exactamente como los pide la práctica
    const val NODO_LED1      = "led1"
    const val NODO_BRILLO    = "brillo_led"
    const val NODO_RELAY     = "relay"
    const val NODO_LDR       = "sensor_ldr"
    const val NODO_DHT11     = "sensor_dht11"
    const val NODO_ESTADO    = "estado"
}
