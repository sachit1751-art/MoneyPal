package com.sachit.moneypal.wear.presentation

import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.TileBuilders.Tile
import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.sync.contract.BudgetStatePayload
import org.junit.Test

class BudgetTileLayoutTest {

    private val texts = BudgetTileTexts(
        setupHint = "Set up MoneyPal on your phone",
        daysLeft = "Days left: 3",
        syncPending = "Sync pending",
    )

    private fun payload(updatedAtMs: Long, daysRemaining: Int = 3) = BudgetStatePayload(
        remainingToday = "12.34",
        dailyBudget = "20.00",
        currencyCode = "USD",
        progressPercent = 45,
        daysRemaining = daysRemaining,
        isOverBudget = false,
        updatedAtEpochMs = updatedAtMs,
    )

    @Test
    fun `null payload renders setup layout`() {
        val tile = BudgetTileLayout.budgetTile(
            payload = null,
            nowMs = 1_000_000L,
            texts = texts,
        )

        assertThat(tile.resourcesVersion).isEqualTo(BudgetTileLayout.RESOURCES_VERSION)
        assertThat(tile.timeline!!.timelineEntries).hasSize(1)
        // Renderer should find the setup hint text somewhere in the tree.
        assertThat(treeText(tile)).contains("Set up MoneyPal on your phone")
    }

    @Test
    fun `fresh payload renders remaining today and days left without sync pending`() {
        val updatedAt = 1_000_000L
        val tile = BudgetTileLayout.budgetTile(
            payload = payload(updatedAt),
            nowMs = updatedAt + BudgetTileLayout.STALE_AFTER_MS - 1,
            texts = texts,
        )

        val text = treeText(tile)
        assertThat(text).contains("12.34")
        assertThat(text).contains("Days left: 3")
        assertThat(text).doesNotContain("Sync pending")
    }

    @Test
    fun `stale payload adds sync pending line`() {
        val updatedAt = 1_000_000L
        val tile = BudgetTileLayout.budgetTile(
            payload = payload(updatedAt),
            nowMs = updatedAt + BudgetTileLayout.STALE_AFTER_MS,
            texts = texts,
        )

        val text = treeText(tile)
        assertThat(text).contains("Sync pending")
        // Staleness dims but keeps the numbers ("staleness beats wrongness").
        assertThat(text).contains("12.34")
        assertThat(text).contains("Days left: 3")
    }

    @Test
    fun `isStale respects the 24h boundary`() {
        val updatedAt = 5_000_000L
        val p = payload(updatedAt)

        assertThat(BudgetTileLayout.isStale(p, updatedAt + BudgetTileLayout.STALE_AFTER_MS - 1))
            .isFalse()
        assertThat(BudgetTileLayout.isStale(p, updatedAt + BudgetTileLayout.STALE_AFTER_MS))
            .isTrue()
    }

    // ---- helpers -----------------------------------------------------------

    /** Walks the tile's layout tree and concatenates every text node's content. */
    private fun treeText(tile: Tile): String {
        val sb = StringBuilder()
        val root = tile.timeline!!.timelineEntries
            .single()
            .layout!!
            .root!!
        walk(root, sb)
        return sb.toString()
    }

    private fun walk(element: LayoutElementBuilders.LayoutElement, sb: StringBuilder) {
        when (element) {
            is LayoutElementBuilders.Text ->
                sb.append(element.text?.value.orEmpty()).append('\n')
            is LayoutElementBuilders.Box ->
                element.contents.forEach { walk(it, sb) }
            is LayoutElementBuilders.Column ->
                element.contents.forEach { walk(it, sb) }
            is LayoutElementBuilders.Row ->
                element.contents.forEach { walk(it, sb) }
        }
    }
}
