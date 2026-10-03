package com.froster.app.datos.historial

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una fila del historial: UNA lectura de UN sensor en UN instante.
 *
 * Cada paquete JSON de la ESP32 trae cuatro valores (luz, temperatura, humedad y
 * punto de rocío), así que genera cuatro filas. Se eligió una fila por lectura
 * y no una fila por paquete con cuatro columnas porque así agregar un sensor
 * nuevo no obliga a cambiar el esquema: basta con otro valor de tipoSensor.
 *
 * @Entity convierte la clase en la tabla historial_sensores. Room revisa en
 * COMPILACIÓN que las consultas del DAO coincidan con estas columnas.
 */
@Entity(
    tableName = "historial_sensores",
    // Índice sobre timestamp: la consulta principal ordena por esta columna.
    // Sin él SQLite tendría que ordenar la tabla completa en cada lectura.
    indices = [Index("timestamp")]
)
data class RegistroSensor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,            // 0 = "que SQLite asigne el siguiente"
    val tipoSensor: String,      // "luz", "temperatura", "humedad", "punto_rocio"
    val valorLectura: Double,
    val timestamp: Long,         // milisegundos desde 1970, hora del teléfono

    /*
     * Columna agregada en la VERSIÓN 2 del esquema.
     *
     * SQLite no tiene tipo BOOLEAN: Room guarda true/false como INTEGER 1/0.
     *
     * defaultValue = "0" no es opcional. La Migration(1, 2) crea la columna con
     * DEFAULT 0 para que las filas viejas tengan un valor. Después de migrar,
     * Room compara la tabla real con la que describe esta clase; si aquí no se
     * declarara el mismo valor por omisión, las dos no coincidirían y la app se
     * cerraría con "Migration didn't properly handle historial_sensores".
     */
    @ColumnInfo(defaultValue = "0")
    val estadoAlerta: Boolean = false
)
