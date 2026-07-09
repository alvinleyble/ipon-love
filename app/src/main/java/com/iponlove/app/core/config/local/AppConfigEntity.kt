package com.iponlove.app.core.config.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room cache of the single `app_config` remote-override row (D3 / ADR-0044). Not a synced
 * entity — pull-only via [AppConfigDao], never pushed. [id] is always true (single-row
 * singleton, mirrors the server-side `check (id)` trick).
 */
@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val id: Boolean = true,
    val enforcementEnabled: Boolean,
    val capOverridesJson: String?,
    val updatedAt: Instant,
)
