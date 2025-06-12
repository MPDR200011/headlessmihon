package com.example.source_service

import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.api.addSingletonFactory
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.source.sourcePreferences
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.model.SourceWithCount
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.repository.SourcePagingSource
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class NoopSourceRepository : StubSourceRepository {
    override fun subscribeAll(): Flow<List<StubSource>> {
        return flow {
            emit(listOf())
        }
    }

    override suspend fun getStubSource(id: Long): StubSource? {
        return null
    }

    override suspend fun upsertStubSource(id: Long, lang: String, name: String) {
        return
    }
}

fun Application.routing(sourceManager: SourceManager) {
    val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    routing {
        get("/search/{query}") {
            try {
                val query = call.parameters["query"]
                if (query == null) {
                    call.respond<List<Unit>>(listOf())
                    return@get
                }
                if (query == "empty") {
                    call.respond<List<Unit>>(listOf())
                    return@get
                }

                // TODO: FILTER THIS BY LANGUAGE PLS
                val sources = sourceManager.getCatalogueSources()

                val resultsMap = ConcurrentHashMap<Long, List<Manga>>()
                sources.map { source ->
                    async {
                        try {
                            val page = withContext(coroutineDispatcher) {
                                source.getSearchManga(1, query, source.getFilterList())
                            }

                            val titles = page.mangas
                                .map { it.toDomainManga(source.id) }
                                .distinctBy { it.url }

                            resultsMap.set(source.id, titles)
                        } catch (e: Exception) {
                            Log.e("SOURCE_SERVICE", "Failed to query with error: ${e.message}\n${e.stackTraceToString()}")
                        }
                    }
                }.awaitAll()

                val flattened = resultsMap.values.flatten().map { m -> m.url }.toList()

                call.respond(flattened)
            } catch (e: Exception) {
                Log.e("SOURCE_SERVICE", "Failed to query with error: ${e.message}\\n${e.stackTraceToString()} ")
            }
        }
    }
}

class ServiceModule(val app: android.app.Application ,val context: Context): InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)
        addSingletonFactory<PreferenceStore> {
            AndroidPreferenceStore(context)
        }
        addSingletonFactory {
            NetworkPreferences(
                preferenceStore = get(),
                verboseLogging = isDebugBuildType,
            )
        }
        addSingletonFactory { NetworkHelper(context, get()) }
    }
}

class SourceService: Service() {
    private val CHANNEL_ID = "HTTP_SERVER"

    private lateinit var server: NettyApplicationEngine

    override fun onCreate() {
        Injekt.importModule(ServiceModule(application, this))

        PreferenceManager.getDefaultSharedPreferences(this).edit() {
            putStringSet("source_languages", setOf("en"))
            apply()
        }

        val extensions = ExtensionManager(context = this, preferences = SourcePreferences(AndroidPreferenceStore(this)))
        val sourceManager = AndroidSourceManager(context = this, extensionManager = extensions, sourceRepository = NoopSourceRepository())

        Log.i("SOURCE_SERVICE", "Creating")
        server = embeddedServer(Netty, port = 8080) {
            install(ContentNegotiation) {
                gson()
            }

            routing(sourceManager)
        }
    }

    private fun createNotificationChannel() {
        val name = "HTTP Server"
        val descriptionText = "Channel to post the HTTP Server running notification"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
        mChannel.description = descriptionText
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("SOURCE_SERVICE", "Starting")

        createNotificationChannel()

        startForeground(100, Notification.Builder(this, CHANNEL_ID).setContentTitle("HTTP Server Running").build())

        server.start(wait = false)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i("SOURCE_SERVICE", "Destroying")
        server.stop(0,0)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}
