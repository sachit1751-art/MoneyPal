package com.sachit.moneypal.domain.datahealth

/**
 * The reviewable issue kinds surfaced by the data health dashboard (plan 049).
 * Used both to render the dashboard's issue rows and to deep-link into History
 * pre-filtered to the affected rows ("Review" action).
 */
enum class DataHealthIssue {
    /** Transactions whose receipt file no longer exists on disk. */
    MISSING_RECEIPTS,

    /** Soft-deleted rows sharing a clientGeneratedId with another active row. */
    DUPLICATE_IDS,
}
