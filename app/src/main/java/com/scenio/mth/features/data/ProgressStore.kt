package com.scenio.mth.features.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sf_progress")
data class StudyProgress(
    @PrimaryKey val projectId: String,
    val completedSteps: Int,
    val totalSteps: Int,
    val updatedAtMillis: Long
) {
    init {
        require(projectId.isNotBlank() && projectId.length <= 200)
        require(totalSteps > 0 && completedSteps in 0..totalSteps)
    }
}

val StudyProgress.fraction: Float get() = completedSteps.toFloat() / totalSteps

@Dao
interface ProgressDao {
    @Query("SELECT * FROM sf_progress WHERE projectId = :id")
    fun observe(id: String): Flow<StudyProgress?>
    @Query("SELECT * FROM sf_progress WHERE projectId = :id")
    suspend fun get(id: String): StudyProgress?
    @Upsert suspend fun upsert(progress: StudyProgress)
}

@Database(entities = [StudyProgress::class], version = 1, exportSchema = true)
abstract class ProgressDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
}

class ProgressRepository(private val db: ProgressDatabase) {
    fun observe(id: String) = db.progressDao().observe(id)
    suspend fun recordStep(id: String, step: Int, total: Int) {
        val candidate = StudyProgress(id, step, total, System.currentTimeMillis())
        db.withTransaction {
            val previous = db.progressDao().get(id)
            require(previous == null || previous.totalSteps == total) {
                "Progress total changed; use a versioned project identifier"
            }
            db.progressDao().upsert(candidate.copy(
                completedSteps = maxOf(previous?.completedSteps ?: 0, step)
            ))
        }
    }
}
