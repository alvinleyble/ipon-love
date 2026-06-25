package com.iponlove.app.feature.user.domain.usecase

import com.iponlove.app.feature.user.domain.model.User
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCurrentUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    operator fun invoke(): Flow<User?> = userRepository.observeCurrentUser()
}
