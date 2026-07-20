package dev.merqadyn.mobile

import android.app.Application
import androidx.room.Room
import dev.merqadyn.mobile.data.MerchantRepository
import dev.merqadyn.mobile.data.local.MerqadynDatabase
import dev.merqadyn.mobile.data.remote.ApiFactory
import dev.merqadyn.mobile.sync.SyncWorker

class AppContainer(application: Application) {
    private val database = Room.databaseBuilder(
        application,
        MerqadynDatabase::class.java,
        "merqadyn.db",
    ).build()

    val repository = MerchantRepository(database, ApiFactory.create()) {
        SyncWorker.enqueue(application)
    }
}

class MerqadynApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
