package dev.merqadyn.mobile

import android.app.Application
import androidx.room.Room
import dev.merqadyn.mobile.data.MerchantRepository
import dev.merqadyn.mobile.data.CredentialStore
import dev.merqadyn.mobile.data.local.MerqadynDatabase
import dev.merqadyn.mobile.sync.SyncWorker

class AppContainer(application: Application) {
    private val database = Room.databaseBuilder(
        application,
        MerqadynDatabase::class.java,
        "merqadyn.db",
    ).build()

    private val credentials = CredentialStore(application)

    val repository = MerchantRepository(database, credentials) {
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
