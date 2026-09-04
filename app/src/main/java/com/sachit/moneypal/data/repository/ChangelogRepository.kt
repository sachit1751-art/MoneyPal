package com.sachit.moneypal.data.repository

import com.sachit.moneypal.domain.model.changelog.VersionRelease
import com.sachit.moneypal.generated.GeneratedChangelog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChangelogRepository @Inject constructor() {

    private val cachedReleases: List<VersionRelease> = GeneratedChangelog.releases

    fun getAllReleases(): List<VersionRelease> = cachedReleases

    fun getLatestRelease(): VersionRelease? = cachedReleases.firstOrNull()

    fun getRelease(versionCode: Int): VersionRelease? =
        cachedReleases.firstOrNull { it.versionCode == versionCode }
}