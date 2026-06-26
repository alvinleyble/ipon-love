package com.iponlove.app.feature.user.domain.usecase

import com.iponlove.app.core.session.CurrentUserProvider
import javax.inject.Inject

/**
 * The signed-in account's email, for read-only display in Profile ("which account am I in").
 * Wraps the session read so the ViewModel stays off session infrastructure.
 */
class GetAccountEmailUseCase @Inject constructor(
    private val currentUserProvider: CurrentUserProvider,
) {
    operator fun invoke(): String? = currentUserProvider.email()
}
