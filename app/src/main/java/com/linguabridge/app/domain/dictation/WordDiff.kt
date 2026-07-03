package com.linguabridge.app.domain.dictation

/** One token of the dictation feedback. */
data class DiffToken(val text: String, val kind: Kind) {
    enum class Kind { CORRECT, WRONG, MISSING, EXTRA }
}

/**
 * Word-level diff between the expected sentence and what the learner typed,
 * based on the longest common subsequence. Comparison ignores case and
 * punctuation; the returned tokens use the expected sentence's spelling so
 * feedback shows the correct form.
 */
fun diffWords(expected: String, typed: String): List<DiffToken> {
    val exp = tokenize(expected)
    val typ = tokenize(typed)
    val expNorm = exp.map { normalize(it) }
    val typNorm = typ.map { normalize(it) }

    // LCS table over normalized tokens.
    val lcs = Array(exp.size + 1) { IntArray(typ.size + 1) }
    for (i in exp.indices.reversed()) {
        for (j in typ.indices.reversed()) {
            lcs[i][j] = if (expNorm[i] == typNorm[j]) {
                lcs[i + 1][j + 1] + 1
            } else {
                maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
    }

    val result = mutableListOf<DiffToken>()
    var i = 0
    var j = 0
    while (i < exp.size && j < typ.size) {
        when {
            expNorm[i] == typNorm[j] -> {
                result += DiffToken(exp[i], DiffToken.Kind.CORRECT)
                i++; j++
            }
            // A wrong word at an aligned position: neither side is part of the LCS.
            lcs[i + 1][j] == lcs[i][j + 1] && lcs[i + 1][j + 1] == lcs[i + 1][j] -> {
                result += DiffToken(exp[i], DiffToken.Kind.WRONG)
                i++; j++
            }
            lcs[i + 1][j] >= lcs[i][j + 1] -> {
                result += DiffToken(exp[i], DiffToken.Kind.MISSING)
                i++
            }
            else -> {
                result += DiffToken(typ[j], DiffToken.Kind.EXTRA)
                j++
            }
        }
    }
    while (i < exp.size) { result += DiffToken(exp[i], DiffToken.Kind.MISSING); i++ }
    while (j < typ.size) { result += DiffToken(typ[j], DiffToken.Kind.EXTRA); j++ }
    return result
}

/** True when every expected word was typed correctly (extras still fail). */
fun isPerfect(diff: List<DiffToken>): Boolean = diff.all { it.kind == DiffToken.Kind.CORRECT }

private fun tokenize(s: String): List<String> =
    s.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

private fun normalize(token: String): String =
    token.lowercase().replace(Regex("[^a-z0-9']"), "")
