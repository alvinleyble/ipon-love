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
        flavor = BuildConfig.FLAVOR,
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        testerName = currentUser.displayName() ?: currentUser.userId(),
    )
}
