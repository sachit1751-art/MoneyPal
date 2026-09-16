package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.Category
import com.sachit.moneypal.domain.model.Transaction
import javax.inject.Inject

/** A category suggestion for the entry flow (plan 009). */
data class CategorySuggestion(
    val category: Category,
    val score: Int,
)

/**
 * Prebuilt token → (categoryId → count) index over the user's comment→category
 * history. [buildIndex] is O(history); [CategorySuggester.suggest] is
 * O(tokens) against a prebuilt index, so UI layers should hoist the index per
 * transaction-list emission instead of rebuilding per keystroke.
 */
class CategoryIndex internal constructor(
    internal val tokenCounts: Map<String, Map<Long, Int>>,
    private val categoriesById: Map<Long, Category>,
    private val trainingRowCount: Int,
) {
    val rowCount: Int get() = trainingRowCount
}

/**
 * Pure, offline category suggestion engine (plan 009): learns from past
 * comment→category pairs and suggests a category for the comment being typed.
 * Suggest, never auto-assign. Deterministic and unit-testable; no ML.
 */
class CategorySuggester @Inject constructor() {

    companion object {
        /** Below this many training rows the model is too noisy — never suggest. */
        const val MIN_TRAINING_ROWS = 5

        /** Minimum raw token-hit score for a suggestion to be worth showing. */
        const val MIN_SCORE = 2

        /** Tokens shorter than this are ignored. */
        private const val MIN_TOKEN_LENGTH = 3

        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "from", "at", "on", "in", "of", "to",
            "de", "la", "el", "en", "y", "por", "con", "para", "del", "los", "las",
        )

        private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{Nd}]+")
    }

    /**
     * Builds the suggestion index from past transactions that have both a
     * non-blank comment and a category id. Soft-deleted and income rows are
     * excluded (plan 006: income is uncategorized noise for this model).
     */
    fun buildIndex(history: List<Transaction>): CategoryIndex {
        val tokenCounts = mutableMapOf<String, MutableMap<Long, Int>>()
        var trainingRows = 0
        for (tx in history) {
            if (tx.isDeleted || tx.isIncome) continue
            val comment = tx.comment.trim()
            val categoryId = tx.categoryId ?: continue
            if (comment.isEmpty()) continue
            trainingRows++
            for (token in tokenize(comment)) {
                tokenCounts.getOrPut(token) { mutableMapOf() }
                    .merge(categoryId, 1, Int::plus)
            }
        }
        return CategoryIndex(tokenCounts, emptyMap(), trainingRows)
    }

    /**
     * Suggests a category for [comment] using the prebuilt [index].
     *
     * Scoring: summed per-category token hits (longer tokens count double),
     * tie-broken by category usageCount, then lastUsedAt (recent wins), then id
     * (stable). Returns null below [MIN_TRAINING_ROWS]/[MIN_SCORE] or when the
     * top two scores are too close (ambiguity guard).
     */
    fun suggest(
        comment: String,
        categories: List<Category>,
        index: CategoryIndex,
    ): CategorySuggestion? {
        if (index.rowCount < MIN_TRAINING_ROWS) return null
        if (categories.isEmpty()) return null
        val tokens = tokenize(comment)
        if (tokens.isEmpty()) return null

        val categoriesById = categories.associateBy { it.id }
        val scores = mutableMapOf<Long, Int>()
        for (token in tokens.distinct()) {
            val perCategory = index.tokenCounts[token] ?: continue
            val weight = if (token.length >= 5) 2 else 1
            for ((categoryId, count) in perCategory) {
                if (categoryId !in categoriesById) continue
                scores.merge(categoryId, count * weight, Int::plus)
            }
        }
        if (scores.isEmpty()) return null

        val ranked = scores.entries
            .sortedWith(
                compareByDescending<Map.Entry<Long, Int>> { it.value }
                    .thenByDescending { categoriesById[it.key]?.usageCount ?: 0 }
                    .thenByDescending { categoriesById[it.key]?.lastUsedAt ?: 0L }
                    .thenBy { it.key }
            )
        val top = ranked.first()
        if (top.value < MIN_SCORE) return null
        // Ambiguity guard: the runner-up must be clearly worse.
        val second = ranked.getOrNull(1)
        if (second != null && top.value < second.value * 2) return null

        val category = categoriesById[top.key] ?: return null
        if (category.isHidden) return null
        return CategorySuggestion(category = category, score = top.value)
    }

    /** Convenience one-shot path (index + suggest); prefer the two-step flow in UI. */
    fun suggest(
        comment: String,
        categories: List<Category>,
        history: List<Transaction>,
    ): CategorySuggestion? = suggest(comment, categories, buildIndex(history))

    internal fun tokenize(comment: String): List<String> = comment
        .lowercase()
        .split(NON_ALPHANUMERIC)
        .filter { it.length >= MIN_TOKEN_LENGTH && it !in STOP_WORDS }
}
