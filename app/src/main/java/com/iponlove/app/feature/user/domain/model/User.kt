package com.iponlove.app.feature.user.domain.model

/** A Supabase-auth user and their profile. Couple-facing fields (displayName, accentColor)
 *  are set during onboarding and used for combined-view attribution. */
data class User(
    val id: String,
    val displayName: String?,
    val accentColor: String?,
    val coupleId: String?,
)
