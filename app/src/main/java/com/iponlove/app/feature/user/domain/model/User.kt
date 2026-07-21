package com.iponlove.app.feature.user.domain.model

import java.time.Instant

/** A Supabase-auth user and their profile. Couple-facing fields (displayName, accentColor)
 *  are set during onboarding and used for combined-view attribution. */
data class User(
    val id: String,
    val displayName: String?,
    val accentColor: String?,
    /** Free motif-avatar key (v1.6.7 Item 3 Leg 1); null → Heart default. */
    val avatarMotif: String? = null,
    val coupleId: String?,
    val createdAt: Instant? = null,
)
