package com.iponlove.app.core.session

import com.iponlove.app.core.database.IponDatabase
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.navigation.NavConfigRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalDataWiperTest {

    private val database = mockk<IponDatabase>(relaxed = true)
    private val cursors = mockk<SyncCursorStore>(relaxed = true)
    private val navConfig = mockk<NavConfigRepository>(relaxed = true)
    private val wiper = LocalDataWiper(database, cursors, navConfig)

    @Test
    fun wipe_clearsRoom_resetsCursors_andNavConfig() = runTest {
        wiper.wipe()

        coVerify(exactly = 1) { database.clearAll() }
        coVerify(exactly = 1) { cursors.reset() }
        coVerify(exactly = 1) { navConfig.reset() }
    }
}
