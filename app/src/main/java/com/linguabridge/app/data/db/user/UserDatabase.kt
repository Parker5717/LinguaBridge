package com.linguabridge.app.data.db.user

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CardStateEntity::class,
        ReviewLogEntity::class,
        QuizResultEntity::class,
        DailyStatEntity::class,
        PlacementResultEntity::class,
        ReadPositionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class UserDatabase : RoomDatabase() {

    abstract fun cardStateDao(): CardStateDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun quizResultDao(): QuizResultDao
    abstract fun dailyStatDao(): DailyStatDao
    abstract fun placementResultDao(): PlacementResultDao
    abstract fun readPositionDao(): ReadPositionDao

    companion object {
        private const val DATABASE_NAME = "user.db"

        fun build(context: Context): UserDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                UserDatabase::class.java,
                DATABASE_NAME
            )
                // User progress must survive app/content updates: no destructive fallback.
                // Future schema changes require explicit Migration objects here.
                .build()
    }
}
