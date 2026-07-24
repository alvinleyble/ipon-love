package com.iponlove.app.feature.couple.data

import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.feature.couple.data.local.CoupleDao
import com.iponlove.app.feature.couple.data.remote.CoupleRemoteSource
import com.iponlove.app.feature.couple.domain.model.Couple
import com.iponlove.app.feature.couple.domain.model.PairingError
import com.iponlove.app.feature.couple.domain.model.PairingException
import com.iponlove.app.feature.couple.domain.repository.CoupleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pairing is online-only by nature, so each mutation is: flush a sync (push any pending
 * local edits — crucially a dirty `users` row whose stale `couple_id` would otherwise
 * clobber the value the RPC is about to set), run the RPC, then sync again to pull the
 * resulting couple row + updated users rows into Room. RPC failures are translated to a
 * typed [PairingException].
 */
@Singleton
class CoupleRepositoryImpl @Inject constructor(
    private val dao: CoupleDao,
    private val remote: CoupleRemoteSource,
    private val syncEngine: SyncEngine,
) : CoupleRepository {

    override fun observeCouple(coupleId: String): Flow<Couple?> =
        dao.observeById(coupleId).map { it?.toDomain() }

    override suspend fun createCouple(name: String) =
        pairingMutation { remote.createCouple(name) }

    override suspend fun redeemInvite(code: String) =
        pairingMutation { remote.redeemInvite(code) }

    override suspend fun rotateInviteCode() =
        pairingMutation { remote.rotateInviteCode() }

    override suspend fun unpair() =
        pairingMutation { remote.unpair() }

    override suspend fun setCoupleBanner(url: String?) =
        pairingMutation { remote.setCoupleBanner(url) }

    /** Flush → RPC → pull, translating any failure to a [PairingException]. */
    private suspend inline fun pairingMutation(rpc: () -> Unit) {
        try {
            syncEngine.sync()   // push pending local edits before the server-side mutation
            rpc()
            syncEngine.sync()   // pull the new couple row + updated users rows
        } catch (e: PairingException) {
            throw e
        } catch (e: Exception) {
            throw PairingException(PairingErrorClassifier.classify(e.message))
        }
    }
}
