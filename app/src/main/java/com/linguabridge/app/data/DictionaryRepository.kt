package com.linguabridge.app.data

import com.linguabridge.app.data.db.content.ContentDatabase
import com.linguabridge.app.data.db.content.GrammarTermEntity
import com.linguabridge.app.data.db.content.HskWordEntity
import com.linguabridge.app.data.db.content.StemTermEntity
import com.linguabridge.app.data.db.content.VocabEntity

/** Results of a dictionary search across all bundled content types. */
data class DictionaryResults(
    val vocab: List<VocabEntity>,
    val hsk: List<HskWordEntity>,
    val stem: List<StemTermEntity>,
    val gramTerms: List<GrammarTermEntity>,
) {
    val isEmpty: Boolean get() = vocab.isEmpty() && hsk.isEmpty() && stem.isEmpty() && gramTerms.isEmpty()

    companion object {
        val EMPTY = DictionaryResults(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

private const val MIN_QUERY_LENGTH = 2

/** Backs the dictionary search screen: a single free-text query fanned out
 *  across all bundled content tables (English vocab, HSK, STEM, grammar terms). */
class DictionaryRepository(private val contentDb: ContentDatabase) {

    suspend fun search(query: String): DictionaryResults {
        val q = query.trim()
        if (q.length < MIN_QUERY_LENGTH) return DictionaryResults.EMPTY

        return DictionaryResults(
            vocab = contentDb.vocabDao().search(q),
            hsk = contentDb.hskDao().search(q),
            stem = contentDb.stemTermDao().search(q),
            gramTerms = contentDb.grammarTermDao().search(q),
        )
    }
}
