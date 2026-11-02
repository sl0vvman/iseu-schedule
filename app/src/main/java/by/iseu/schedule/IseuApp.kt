package by.iseu.schedule

import android.app.Application
import androidx.room.Room
import by.iseu.schedule.data.db.AppDatabase
import by.iseu.schedule.data.network.MemoryCookieJar
import by.iseu.schedule.data.prefs.SelectionStore
import by.iseu.schedule.data.repo.DefaultScheduleRepository
import by.iseu.schedule.data.repo.ScheduleRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class IseuApp : Application() {

    lateinit var repository: ScheduleRepository
        private set

    override fun onCreate() {
        super.onCreate()

        val cookieJar = MemoryCookieJar()
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

        val database = Room.databaseBuilder(this, AppDatabase::class.java, "iseu.db").build()
        val store = SelectionStore(this)

        repository = DefaultScheduleRepository(
            client = client,
            cookieJar = cookieJar,
            weekDao = database.weekCacheDao(),
            catalogDao = database.catalogDao(),
            selectionStore = store,
        )
    }
}
