package com.iponlove.app.feature.appupdate.domain.usecase

import com.iponlove.app.feature.appupdate.domain.repository.AppReleaseInfoRepository
import javax.inject.Inject

/**
 * Exact-match, not floor (ADR-0029) — the goal is every tester on the identical build, not
 * merely "not behind." Fails open: any fetch failure (offline, Supabase down) never blocks
 * the app, since this is beta tooling, not core functionality.
 */
class CheckAppVersionUseCase @Inject constructor(
    private val repository: AppReleaseInfoRepository,
) {
    suspend operator fun invoke(installedVersionCode: Int): Boolean {
        val requiredVersionCode = runCatching { repository.getRequiredVersionCode() }
            .getOrNull() ?: return false
        return isMismatched(installedVersionCode, requiredVersionCode)
    }

    companion object {
        fun isMismatched(installedVersionCode: Int, requiredVersionCode: Int): Boolean =
            installedVersionCode != requiredVersionCode
    }
}
