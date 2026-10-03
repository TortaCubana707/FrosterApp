package com.froster.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.froster.app.datos.historial.HistorialDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de la Migration(1, 2) en un dispositivo Android real (o emulador).
 *
 * MigrationTestHelper crea una base EXACTAMENTE como era en la versión 1, a
 * partir de schemas/.../1.json, le mete datos, ejecuta la migración y luego
 * compara la tabla resultante contra 2.json. Si la migración dejara la tabla
 * distinta de lo que Room espera (por ejemplo, sin el DEFAULT 0), la prueba
 * falla aquí y no en el teléfono de un usuario.
 */
@RunWith(AndroidJUnit4::class)
class MigracionHistorialTest {

    private val nombre = "migracion-prueba.db"

    @get:Rule
    val ayudante = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HistorialDatabase::class.java
    )

    @Test
    fun migra_de_1_a_2_sin_perder_las_lecturas() {
        // Base en versión 1 con dos lecturas, como la tendría un usuario antes de actualizar
        ayudante.createDatabase(nombre, 1).apply {
            execSQL("INSERT INTO historial_sensores (tipoSensor, valorLectura, timestamp) VALUES ('temperatura', 28.4, 1000)")
            execSQL("INSERT INTO historial_sensores (tipoSensor, valorLectura, timestamp) VALUES ('luz', 30, 1000)")
            close()
        }

        // Migra y VALIDA que el esquema final coincide con 2.json
        val db = ayudante.runMigrationsAndValidate(nombre, 2, true, HistorialDatabase.MIGRACION_1_2)

        db.query("SELECT tipoSensor, valorLectura, estadoAlerta FROM historial_sensores ORDER BY id").use { c ->
            assertEquals("las dos filas siguen ahí", 2, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("temperatura", c.getString(0))
            assertEquals(28.4, c.getDouble(1), 0.0)
            assertEquals("las filas viejas reciben el DEFAULT 0", 0, c.getInt(2))
        }
        assertEquals(2, db.version)
    }
}
