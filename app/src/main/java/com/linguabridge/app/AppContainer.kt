package com.linguabridge.app

import android.content.Context
import com.linguabridge.app.data.ContentRepository
import com.linguabridge.app.data.DictionaryRepository
import com.linguabridge.app.data.LibraryRepository
import com.linguabridge.app.data.PracticeRepository
import com.linguabridge.app.data.QuizRepository
import com.linguabridge.app.data.SettingsRepository
import com.linguabridge.app.data.SrsRepository
import com.linguabridge.app.data.StatsRepository
import com.linguabridge.app.data.db.content.ContentDatabase
import com.linguabridge.app.data.db.user.UserDatabase
import com.linguabridge.app.domain.srs.Sm2Scheduler
import com.linguabridge.app.domain.srs.SrsScheduler
import com.linguabridge.app.tts.TtsManager

/**
 * Manual dependency container. Everything the app shares (databases,
 * repositories, TTS, settings) is constructed lazily here and handed to
 * ViewModels explicitly — no DI framework.
 */
class AppContainer(private val appContext: Context) {

    /** Read-only, shipped as a prepackaged asset. Regenerated wholesale by the content packer. */
    val contentDb: ContentDatabase by lazy { ContentDatabase.build(appContext) }

    /** Created on device, holds user progress. Never destructively migrated. */
    val userDb: UserDatabase by lazy { UserDatabase.build(appContext) }

    val contentRepository: ContentRepository by lazy { ContentRepository(contentDb) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    /** SM-2 today; the interface lets FSRS drop in without touching callers. */
    val srsScheduler: SrsScheduler by lazy { Sm2Scheduler() }

    val srsRepository: SrsRepository by lazy { SrsRepository(contentDb, userDb, srsScheduler) }

    val libraryRepository: LibraryRepository by lazy { LibraryRepository(contentDb, userDb) }

    val dictionaryRepository: DictionaryRepository by lazy { DictionaryRepository(contentDb) }

    val practiceRepository: PracticeRepository by lazy { PracticeRepository(contentDb, userDb) }

    val quizRepository: QuizRepository by lazy { QuizRepository(contentDb, userDb, practiceRepository) }

    val statsRepository: StatsRepository by lazy { StatsRepository(contentDb, userDb) }

    val ttsManager: TtsManager by lazy { TtsManager(appContext) }
}
