package com.iponlove.app.feature.appupdate.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire shape of the single `app_release_info` row (ADR-0029). Read-only — never pushed. */
@Serializable
data class AppReleaseInfoDto(
    @SerialName("required_version_code") val requiredVersionCode: Int,
)
