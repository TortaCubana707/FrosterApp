package com.froster.app.datos.historial

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Operaciones sobre historial_sensores. Room escribe la implementación al
 * compilar (HistorialDatabase_Impl / RegistroSensorDao_Impl): aquí sólo se
 * declara QUÉ se quiere.
 *
 * Las inserciones son `suspend`: Room las ejecuta en un hilo de fondo y el
 * compilador impide llamarlas desde el hilo principal sin una corrutina. Si se
 * intentara acceder a la base desde el hilo de la interfaz sin corrutinas,
 * Room lanza IllegalStateException a propósito, para que la pantalla no se
 * congele mientras se escribe en disco.
 */
@Dao
interface RegistroSensorDao {

    /** Inserta una lectura y devuelve el id que le asignó SQLite. */
    @Insert
    suspend fun insertar(registro: RegistroSensor): Long

    /**
     * Inserta varias lecturas en UNA sola transacción. Para las cuatro lecturas
     * de un paquete es más rápido que cuatro insert sueltos, y además es
     * atómico: o entran las cuatro o ninguna.
     */
    @Insert
    suspend fun insertarTodos(registros: List<RegistroSensor>): List<Long>

    /**
     * Historial completo, lo más reciente primero. Devuelve Flow: cada vez que
     * se inserta una fila, Room vuelve a ejecutar la consulta y emite la lista
     * nueva. Por eso el RecyclerView se actualiza solo.
     */
    @Query("SELECT * FROM historial_sensores ORDER BY timestamp DESC, id DESC")
    fun historial(): Flow<List<RegistroSensor>>

    @Query("SELECT COUNT(*) FROM historial_sensores")
    fun total(): Flow<Int>

    /** Cuántas lecturas quedaron en alerta. Usa la columna de la versión 2. */
    @Query("SELECT COUNT(*) FROM historial_sensores WHERE estadoAlerta = 1")
    fun totalEnAlerta(): Flow<Int>
}
