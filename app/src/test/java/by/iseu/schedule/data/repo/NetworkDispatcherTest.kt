package by.iseu.schedule.data.repo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDispatcherTest {

    @Test
    fun `blocking network work runs off the caller thread`() = runBlocking {
        val caller = Thread.currentThread()
        var worker: Thread? = null

        onIo { worker = Thread.currentThread() }

        assertNotSame(caller, worker)
        assertTrue(worker!!.name.startsWith("DefaultDispatcher-"))
    }
}
