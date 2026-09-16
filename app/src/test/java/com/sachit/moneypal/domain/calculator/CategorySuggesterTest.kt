package com.sachit.moneypal.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.domain.model.Category
import com.sachit.moneypal.domain.model.Transaction
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class CategorySuggesterTest {

    private val suggester = CategorySuggester()

    private var nextId = 1L

    private fun tx(
        comment: String,
        categoryId: Long?,
        isIncome: Boolean = false,
        isDeleted: Boolean = false,
    ): Transaction = Transaction(
        id = nextId++,
        amount = BigDecimal("10.00"),
        comment = comment,
        date = LocalDateTime.of(2026, 9, 1, 12, 0),
        categoryId = categoryId,
        isIncome = isIncome,
        isDeleted = isDeleted,
    )

    private fun category(
        id: Long,
        name: String,
        isHidden: Boolean = false,
        usageCount: Int = 0,
        lastUsedAt: Long? = null,
    ): Category = Category(
        id = id,
        name = name,
        isHidden = isHidden,
        usageCount = usageCount,
        lastUsedAt = lastUsedAt,
    )

    @Test
    fun `cold start with fewer than five training rows suggests nothing`() {
        val history = List(4) { tx("coffee run", 1L) }
        val categories = listOf(category(1L, "Coffee"))
        val index = suggester.buildIndex(history)

        assertThat(suggester.suggest("coffee", categories, index)).isNull()
    }

    @Test
    fun `exact repeated comment maps to its category`() {
        val history = List(6) { tx("starbucks morning", 1L) }
        val categories = listOf(category(1L, "Coffee"))
        val index = suggester.buildIndex(history)

        val suggestion = suggester.suggest("starbucks morning", categories, index)

        assertThat(suggestion).isNotNull()
        assertThat(suggestion!!.category.id).isEqualTo(1L)
    }

    @Test
    fun `token overlap across different comments suggests the shared category`() {
        val history = listOf(
            tx("starbucks coffee", 1L),
            tx("starbucks latte", 1L),
            tx("starbucks coffee", 1L),
            tx("starbucks latte", 1L),
            tx("starbucks coffee", 1L),
            tx("grocery store", 2L),
        )
        val categories = listOf(category(1L, "Coffee"), category(2L, "Groceries"))
        val index = suggester.buildIndex(history)

        val suggestion = suggester.suggest("starbucks", categories, index)

        assertThat(suggestion).isNotNull()
        assertThat(suggestion!!.category.id).isEqualTo(1L)
    }

    @Test
    fun `tie between two equal scores suggests nothing`() {
        val history = listOf(
            tx("alpha beta", 1L),
            tx("alpha", 1L),
            tx("beta", 1L),
            tx("alpha beta", 2L),
            tx("alpha", 2L),
            tx("beta", 2L),
        )
        val categories = listOf(category(1L, "One"), category(2L, "Two"))
        val index = suggester.buildIndex(history)

        assertThat(suggester.suggest("alpha beta", categories, index)).isNull()
    }

    @Test
    fun `short tokens and stop words are ignored`() {
        val history = List(6) { tx("at the movies", 1L) }
        val categories = listOf(category(1L, "Fun"))
        val index = suggester.buildIndex(history)

        assertThat(suggester.suggest("at the", categories, index)).isNull()
        assertThat(suggester.tokenize("at the")).isEmpty()
    }

    @Test
    fun `hidden categories are excluded from suggestions`() {
        val history = List(6) { tx("starbucks coffee", 1L) }
        val categories = listOf(category(1L, "Coffee", isHidden = true))
        val index = suggester.buildIndex(history)

        assertThat(suggester.suggest("starbucks coffee", categories, index)).isNull()
    }

    @Test
    fun `income transactions are excluded from the training set`() {
        val history = List(6) { tx("salary deposit", 1L, isIncome = true) }
        val categories = listOf(category(1L, "Income"))
        val index = suggester.buildIndex(history)

        assertThat(index.rowCount).isEqualTo(0)
        assertThat(suggester.suggest("salary deposit", categories, index)).isNull()
    }

    @Test
    fun `soft deleted transactions are excluded from the training set`() {
        val history = List(6) { tx("starbucks coffee", 1L, isDeleted = true) }
        val categories = listOf(category(1L, "Coffee"))
        val index = suggester.buildIndex(history)

        assertThat(index.rowCount).isEqualTo(0)
        assertThat(suggester.suggest("starbucks coffee", categories, index)).isNull()
    }

    @Test
    fun `blank comment suggests nothing`() {
        val history = List(6) { tx("starbucks coffee", 1L) }
        val categories = listOf(category(1L, "Coffee"))
        val index = suggester.buildIndex(history)

        assertThat(suggester.suggest("   ", categories, index)).isNull()
        assertThat(suggester.suggest("", categories, index)).isNull()
    }

    @Test
    fun `longer tokens count double`() {
        val history = listOf(
            tx("walmart groceries", 1L),
            tx("walmart run", 2L),
            tx("walmart groceries", 1L),
            tx("walmart run", 2L),
            tx("walmart groceries", 1L),
        )
        val categories = listOf(category(1L, "Groceries"), category(2L, "Errands"))
        val index = suggester.buildIndex(history)

        // "walmart" hits both categories equally, but "groceries" (9 chars,
        // weight 2) breaks the tie toward category 1.
        val suggestion = suggester.suggest("walmart groceries", categories, index)

        assertThat(suggestion).isNotNull()
        assertThat(suggestion!!.category.id).isEqualTo(1L)
    }

    @Test
    fun `usage count breaks score ties deterministically`() {
        val history = listOf(
            tx("taxi ride", 1L),
            tx("taxi ride", 1L),
            tx("taxi ride", 2L),
            tx("taxi ride", 2L),
            tx("taxi ride", 2L),
        )
        val categories = listOf(
            category(1L, "Transport", usageCount = 9),
            category(2L, "Other", usageCount = 2),
        )
        val index = suggester.buildIndex(history)

        assertThat(suggester.suggest("taxi", categories, index)).isNull()
    }
}
