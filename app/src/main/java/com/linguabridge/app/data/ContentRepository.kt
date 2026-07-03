package com.linguabridge.app.data

import com.linguabridge.app.data.db.content.ContentDatabase

data class ContentCounts(
    val vocab: Int,
    val cards: Int,
    val texts: Int,
    val dialogues: Int,
    val hskWords: Int,
    val grammarTerms: Int,
    val stemTerms: Int,
)

class ContentRepository(private val db: ContentDatabase) {

    suspend fun counts(): ContentCounts = ContentCounts(
        vocab = db.vocabDao().count(),
        cards = db.cardDao().count(),
        texts = db.readingTextDao().count(),
        dialogues = db.dialogueDao().count(),
        hskWords = db.hskDao().wordCount(),
        grammarTerms = db.grammarTermDao().count(),
        stemTerms = db.stemTermDao().count(),
    )
}
