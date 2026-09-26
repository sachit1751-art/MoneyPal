package com.sachit.moneypal.navigation

import com.sachit.moneypal.domain.datahealth.DataHealthIssue

sealed class Screen(val route: String) {

    data object Onboarding : Screen("onboarding")

    data object Main :
        Screen("main?openWallet={openWallet}&forceWalletSetup={forceWalletSetup}&openHistoryIssue={openHistoryIssue}") {
        fun createRoute(
            openWallet: Boolean = false,
            forceWalletSetup: Boolean = false,
            openHistoryIssue: DataHealthIssue? = null,
        ): String {
            val base = "main?openWallet=$openWallet&forceWalletSetup=$forceWalletSetup"
            return if (openHistoryIssue != null) {
                "$base&openHistoryIssue=${android.net.Uri.encode(openHistoryIssue.name)}"
            } else {
                base
            }
        }

        const val ARG_OPEN_WALLET = "openWallet"
        const val ARG_FORCE_WALLET_SETUP = "forceWalletSetup"
        const val ARG_OPEN_HISTORY_ISSUE = "openHistoryIssue"
    }

    data object Analytics : Screen("analytics")

    data object Settings : Screen("settings")

    data object BugReport : Screen("bug_report")

    data object Changelog : Screen("changelog")

    data object Appearance : Screen("appearance")

    data object DataHealth : Screen("data_health")
}
