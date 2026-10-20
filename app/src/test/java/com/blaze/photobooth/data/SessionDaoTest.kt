package com.blaze.photobooth.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.db.BoothDatabase
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.db.ShotEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/** README §11.2: the album's queries, soft delete, favourites and the shots cascade, on an in-memory `booth.db`. */
@RunWith(AndroidJUnit4::class)
class SessionDaoTest {

    private lateinit var db: BoothDatabase
    private val dao get() = db.sessions()

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BoothDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = db.close()

    private fun session(id: String, created: Long) = SessionEntity(
        id = id,
        createdAt = created,
        updatedAt = created,
        layoutId = "classic",
        frameId = "theme:strawberry",
        filterId = "milky",
        caption = null,
        showDate = true,
    )

    private fun shots(id: String, count: Int, cuts: Int) =
        List(count) { ShotEntity(id, it, slotIndex = it.takeIf { i -> i < cuts }, width = 2400, height = 1680) }

    @Test
    fun theAlbumIsNewestFirstAndHidesDeletedSessions() = runTest {
        dao.insert(session("a", 1_000), shots("a", 6, 4))
        dao.insert(session("b", 3_000), shots("b", 4, 4))
        dao.insert(session("c", 2_000), shots("c", 5, 3))
        assertEquals(listOf("b", "c", "a"), dao.observeSessions().first().map { it.id })
        assertEquals(3, dao.observeCount().first())

        dao.softDelete("c", now = 5_000)
        assertEquals(listOf("b", "a"), dao.observeSessions().first().map { it.id })
        assertEquals(2, dao.observeCount().first())
        assertEquals(listOf("c"), dao.deletedBefore(cutoff = 6_000))
        assertEquals(emptyList<String>(), dao.deletedBefore(cutoff = 4_000))

        dao.restore("c")
        assertEquals(3, dao.observeCount().first())
    }

    @Test
    fun everyShotIsKeptInTakingOrderWithUnusedBonusShotsUnplaced() = runTest {
        dao.insert(session("a", 1_000), shots("a", 6, 4).reversed())
        val stored = dao.shots("a")
        assertEquals((0..5).toList(), stored.map { it.shotIndex })
        assertEquals(listOf(0, 1, 2, 3, null, null), stored.map { it.slotIndex })
    }

    @Test
    fun favouritesAndDefaults() = runTest {
        dao.insert(session("a", 1_000), shots("a", 4, 4))
        val fresh = dao.session("a")
        assertEquals(false, fresh?.isFavorite)
        assertNull(fresh?.deletedAt)
        dao.setFavorite("a", favorite = true, now = 9_000)
        val fav = dao.observeSession("a").first()
        assertEquals(true, fav?.isFavorite)
        assertEquals(9_000L, fav?.updatedAt)
    }

    @Test
    fun deletingASessionTakesItsShotsWithIt() = runTest {
        dao.insert(session("a", 1_000), shots("a", 4, 4))
        dao.delete("a")
        assertNull(dao.session("a"))
        assertTrue(dao.shots("a").isEmpty())
    }

    @Test
    fun aFailedInsertLeavesNothingBehind() = runTest {
        dao.insert(session("a", 1_000), shots("a", 2, 2))
        try {
            // A duplicate shot index fails halfway through: the transaction rolls the session row back too.
            dao.insert(session("b", 2_000), listOf(ShotEntity("b", 0, 0, 1, 1), ShotEntity("b", 0, 1, 1, 1)))
            fail("Expected a constraint failure")
        } catch (e: Exception) {
            // expected
        }
        assertNull(dao.session("b"))
        assertEquals(listOf("a"), dao.allIds())
    }
}
