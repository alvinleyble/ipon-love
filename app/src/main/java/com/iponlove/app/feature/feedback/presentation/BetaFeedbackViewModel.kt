package com.iponlove.app.feature.feedback.presentation

import android.os.Build
import androidx.lifecycle.ViewModel
import com.iponlove.app.BuildConfig
import com.iponlove.app.core.session.CurrentUserProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BetaFeedbackViewModel @Inject constructor(
    currentUser: CurrentUserProvider,
) : ViewModel() {

    val feedbackUrl: String = BetaFeedbackConfig.buildPrefillUrl(
        versionName = BuildConfig.VERSION_NAME,
        // Hardcoded: this screen is only reachable when IS_BETA_BUILD is true, but the "prod"
        // flavor is currently also the Play Console internal-testing build (see
        // staging-prod-environment memory), so BuildConfig.FLAVOR would misleadingly say "prod".
        flavor = "beta",
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        testerName = currentUser.displayName() ?: currentUser.userId(),
    )
}
