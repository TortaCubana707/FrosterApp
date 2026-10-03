package com.froster.app.datos.historial

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local del historial.
 *
 * Es abstracta porque Room genera la clase concreta (HistorialDatabase_Impl).
 */
/*
 * version = 2: se agregó la columna estadoAlerta. Subir el número es lo que le
 * avisa a Room que el esquema cambió. Si se cambia la entidad pero NO la
 * versión, Room detecta que el esquema guardado no coincide y lanza
 * IllegalStateException al abrir la base.
 */
@Database(entities = [RegistroSensor::class], version = 2, exportSchema = true)
abstract class HistorialDatabase : RoomDatabase() {

    abstract fun registroSensorDao(): RegistroSensorDao

    companion object {
        private const val NOMBRE_ARCHIVO = "historial_froster.db"

        /*
         * Patrón Singleton. Abrir la base cuesta (crea conexiones y cachés) y,
         * sobre todo, dos instancias abiertas sobre el mismo archivo pueden
         * pisarse las escrituras y provocar "database is locked".
         *
         * @Volatile hace que el valor escrito por un hilo sea visible de
         * inmediato para los demás. synchronized garantiza que, si dos hilos
         * llegan a la vez con INSTANCIA en null, sólo uno la construya.
         */
        /*
         * Migración de la versión 1 a la 2.
         *
         * Sin este objeto Room no sabe cómo pasar de un esquema a otro. Las
         * opciones serían fallar al abrir, o usar fallbackToDestructiveMigration(),
         * que BORRA la tabla y la vuelve a crear: se perdería todo el historial
         * guardado en el teléfono. ALTER TABLE ... ADD COLUMN agrega la columna
         * y deja intactas las filas que ya existían.
         *
         * Room ejecuta migrate() una sola vez, cuando detecta que el archivo en
         * disco está en la versión 1 y el código pide la 2. Lo sabe por
         * PRAGMA user_version, un número que SQLite guarda en el propio archivo.
         */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE historial_sensores " +
                        "ADD COLUMN estadoAlerta INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var INSTANCIA: HistorialDatabase? = null

        fun obtener(contexto: Context): HistorialDatabase =
            INSTANCIA ?: synchronized(this) {
                INSTANCIA ?: Room.databaseBuilder(
                    contexto.applicationContext,   // nunca una Activity: la retendría en memoria
                    HistorialDatabase::class.java,
                    NOMBRE_ARCHIVO
                )
                    .addMigrations(MIGRACION_1_2)
                    .build().also { INSTANCIA = it }
            }
    }
}
