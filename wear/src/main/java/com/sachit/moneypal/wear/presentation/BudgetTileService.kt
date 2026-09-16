package com.sachit.moneypal.wear.presentation

import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import androidx.wear.tiles.TypeBuilders
import androidx.wear.tiles.ColorBuilders
import com.google.common.util.concurrent.ListenableFuture
import com.sachit.moneypal.sync.contract.BudgetStatePayload
import com.sachit.moneypal.wear.R
import com.sachit.moneypal.wear.data.BudgetStateStore
import com.sachit.moneypal.wear.sync.WearSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import logcat.logcat

/**
 * Wear OS tile showing remaining-today budget state (plan 010).
 *
 * Renders the last phone-published [BudgetStatePayload] cached in
 * [BudgetStateStore]. Empty state asks the user to set up on the phone and
 * fires a one-shot state request; stale state (>= [BudgetTileLayout.STALE_AFTER_MS])
 * dims the numbers and shows "Sync pending". Tap opens the watch app.
 *
 * The layout builder is pure (see [BudgetTileLayout]) so fresh/stale/empty
 * branches are testable without Android.
 */
class BudgetTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val context = applicationContext
        return serviceScope.future {
            val payload = BudgetStateStore(context).getAllOnce()
            val nowMs = System.currentTimeMillis()

            // Self-healing: on a missing/stale render, quietly ask the phone for
            // fresh state; the next tile request (or listener-pushed refresh)
            // shows it. Best-effort, never blocks the tile response.
            if (payload == null || BudgetTileLayout.isStale(payload, nowMs)) {
                serviceScope.launch {
                    WearSyncManager(context).requestBudgetState()
                }
            }

            val texts = BudgetTileTexts(
                setupHint = context.getString(R.string.tile_budget_setup),
                daysLeft = context.getString(
                    R.string.tile_budget_days_left,
                    payload?.daysRemaining ?: 0
                ),
                syncPending = context.getString(R.string.tile_budget_sync_pending),
            )
            logcat {
                "onTileRequest: hasPayload=${payload != null}, " +
                    "stale=${payload != null && BudgetTileLayout.isStale(payload, nowMs)}"
            }
            BudgetTileLayout.budgetTile(payload, nowMs, texts)
        }
    }

    // No onTileResourcesRequest override: the tile is text-only, and the base
    // class's default empty-resources response is sufficient.
}

/** Resolved user-facing strings handed to the pure layout builder. */
internal data class BudgetTileTexts(
    val setupHint: String,
    val daysLeft: String,
    val syncPending: String,
)

/**
 * Pure tile layout construction — no Android framework calls, safe for JVM
 * unit tests. Money strings come pre-formatted in the payload and are shown
 * as-is (money never floats; see the BackupModels convention).
 */
internal object BudgetTileLayout {

    const val RESOURCES_VERSION = "1"

    /** Freshness rule from the plan: >= 24h old means "stale — sync pending". */
    const val STALE_AFTER_MS: Long = 24L * 60L * 60L * 1000L

    private const val COLOR_TEXT_FRESH = 0xFFFFFFFF.toInt()
    private const val COLOR_TEXT_STALE = 0x8CFFFFFF.toInt() // ~55% white, dimmed but readable
    private const val COLOR_SYNC_PENDING = 0xB3FFFFFF.toInt() // ~70% white

    fun isStale(payload: BudgetStatePayload, nowMs: Long): Boolean =
        nowMs - payload.updatedAtEpochMs >= STALE_AFTER_MS

    fun budgetTile(
        payload: BudgetStatePayload?,
        nowMs: Long,
        texts: BudgetTileTexts,
    ): TileBuilders.Tile {
        val root: LayoutElementBuilders.LayoutElement = when {
            payload == null -> setupLayout(texts.setupHint)
            isStale(payload, nowMs) -> stateLayout(
                remainingToday = payload.remainingToday,
                daysLeft = texts.daysLeft,
                syncPending = texts.syncPending,
                textColor = COLOR_TEXT_STALE,
            )
            else -> stateLayout(
                remainingToday = payload.remainingToday,
                daysLeft = texts.daysLeft,
                syncPending = null,
                textColor = COLOR_TEXT_FRESH,
            )
        }

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(root)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    /** Empty state: no cached payload from the phone yet. */
    private fun setupLayout(setupHint: String): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(expanded())
            .setHeight(expanded())
            .setModifiers(clickable())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setWidth(expanded())
                    .setHorizontalAlignment(
                        LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(setupHint)
                            .setMaxLines(3)
                            .setFontStyle(fontStyle(sizeSp = 14f, color = COLOR_TEXT_FRESH))
                            .build()
                    )
                    .build()
            )
            .build()

    /** Fresh or stale state: big remaining-today, days left, optional sync line. */
    private fun stateLayout(
        remainingToday: String,
        daysLeft: String,
        syncPending: String?,
        textColor: Int,
    ): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .addContent(centeredText(remainingToday, sizeSp = 22f, color = textColor))
            .addContent(spacer(4f))
            .addContent(centeredText(daysLeft, sizeSp = 12f, color = textColor))

        if (syncPending != null) {
            column
                .addContent(spacer(2f))
                .addContent(centeredText(syncPending, sizeSp = 10f, color = COLOR_SYNC_PENDING))
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expanded())
            .setHeight(expanded())
            .setModifiers(clickable())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column.build())
            .build()
    }

    // ---- small pure helpers ------------------------------------------------

    private fun centeredText(text: String, sizeSp: Float, color: Int) =
        LayoutElementBuilders.Column.Builder()
            .setWidth(expanded())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(text)
                    .setMaxLines(1)
                    .setFontStyle(fontStyle(sizeSp, color))
                    .build()
            )
            .build()

    private fun fontStyle(sizeSp: Float, color: Int) =
        LayoutElementBuilders.FontStyle.Builder()
            .setSize(DimensionBuilders.SpProp.Builder().setValue(sizeSp).build())
            .setColor(ColorBuilders.argb(color))
            .build()

    private fun spacer(heightDp: Float) =
        LayoutElementBuilders.Spacer.Builder()
            .setHeight(DimensionBuilders.DpProp.Builder().setValue(heightDp).build())
            .build()

    private fun expanded() =
        DimensionBuilders.ExpandedDimensionProp.Builder().build()

    private fun clickable(): ModifiersBuilders.Modifiers =
        ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId(CLICKABLE_ID)
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setPackageName(MAIN_ACTIVITY_PACKAGE)
                                    .setClassName(MAIN_ACTIVITY_CLASS)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    private const val CLICKABLE_ID = "budget_tile_root"
    private const val MAIN_ACTIVITY_PACKAGE = "com.sachit.moneypal"
    private const val MAIN_ACTIVITY_CLASS = "com.sachit.moneypal.wear.presentation.MainActivity"
}
