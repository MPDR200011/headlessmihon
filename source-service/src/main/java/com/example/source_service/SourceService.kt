package com.example.source_service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.example.source_service.di.ServiceModule
import com.example.source_service.gson.PageAdapter
import com.google.errorprone.annotations.Immutable
import com.google.gson.LongSerializationPolicy
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapterImpl
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.source.online.HttpSource
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.slf4j.event.Level
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.repository.StubSourceRepository
import uy.kohesive.injekt.Injekt
import java.io.Serializable
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

val LOG_TAG = "SOURCE_SERVICE"

@Immutable
data class SourceManga(val source: Long, val manga: SMangaImpl) : Serializable;

fun Application.routing(sourceManager: AndroidSourceManager) {
    val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    routing {
        get("/search") {
            try {
                val query = call.request.queryParameters["q"]
                val language = call.request.queryParameters["l"] ?: "en"
                if (query == null) {
                    call.respond<Map<Long, List<Manga>>>(mapOf())
                    return@get
                }

                val sources = sourceManager.getCatalogueSources().filter { it.lang == language }

                val resultsMap = ConcurrentHashMap<Long, List<SManga>>()
                sources.map { source ->
                    async {
                        try {
                            val page = withContext(coroutineDispatcher) {
                                source.getSearchManga(1, query, source.getFilterList())
                            }

                            val titles = page.mangas
                                .distinctBy { it.url }

                            resultsMap.set(source.id, titles)
                        } catch (e: Exception) {
                            Log.e(
                                "SOURCE_SERVICE",
                                "Failed to query with error: ${e.message}\n${e.stackTraceToString()}",
                            )
                        }
                    }
                }.awaitAll()

                call.respond(resultsMap.toMap())
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to query with error: ${e.message}\\n${e.stackTraceToString()} ")
            }
        }

        get("/manga/details") {

            val mangaToFetch: SourceManga;
            try {
                mangaToFetch = call.receive<SourceManga>()
            } catch (e: ContentTransformationException) {
                Log.i(LOG_TAG, "${e}");
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val sourceId = mangaToFetch.source

            val source = sourceManager.get(sourceId)
            if (source == null) {
                call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                return@get
            }

            val mangaDetails = source.getMangaDetails(mangaToFetch.manga)

            call.respond(mangaDetails)
        }

        get("/manga/chapters") {
            val mangaToFetch = call.receive<SourceManga>()
            val sourceId = mangaToFetch.source

            val source = sourceManager.get(sourceId)
            if (source == null) {
                call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                return@get
            }

            val chapterList = source.getChapterList(mangaToFetch.manga)

            call.respond(chapterList)
        }

        get("/manga/chapter/pages") {
            call.application.environment.log.info("Received request for pages")
            Log.i(LOG_TAG, "Received request for pages")
            try {
                val chapterToFetch = call.receive<SChapterImpl>()
                val sourceId = call.request.queryParameters["sourceId"]?.toLong()

                if (sourceId == null) {
                    call.response.status(HttpStatusCode(400, "Missing sourceId"))
                    return@get
                }

                val source = sourceManager.get(sourceId)
                if (source == null) {
                    call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                    return@get
                }

                val pageList = source.getPageList(chapterToFetch)

                call.respond(pageList)
                return@get
            } catch (e: Exception) {
                Log.e(LOG_TAG, "$e: ${e.printStackTrace()}")
            }
        }

        get("/manga/chapter/page/image") {
            val page: Page
            try {
                page = call.receive<Page>()
            } catch (e: Exception) {
                Log.e("LOG_TAG", "$e: ${e.stackTraceToString()}")
                throw e
            }
            val sourceId = call.request.queryParameters["sourceId"]?.toLong()

            if (sourceId == null) {
                call.response.status(HttpStatusCode(400, "Missing sourceId"))
                return@get
            }

            val source = sourceManager.get(sourceId)
            if (source == null) {
                call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                return@get
            }

            if (source is HttpSource) {
                val response = source.getImage(page)
                val bodyContentType = response.body.contentType()!!
                call.respondBytes(contentType = ContentType(bodyContentType.type, bodyContentType.subtype)) {
                    response.body.bytes()
                }
                return@get
            }
        }
    }
}

class SourceService : Service() {
    private val CHANNEL_ID = "HTTP_SERVER"

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    override fun onCreate() {
        Injekt.importModule(ServiceModule(application, this))

        PreferenceManager.getDefaultSharedPreferences(this).edit() {
            putStringSet("source_languages", setOf("en"))
            apply()
        }

        val extensions = ExtensionManager(context = this, preferences = SourcePreferences(AndroidPreferenceStore(this)))
        val sourceManager = AndroidSourceManager(
            context = this,
            extensionManager = extensions,
            sourceRepository = NoopSourceRepository(),
        )

        server = embeddedServer(Netty, port = 8080) {
            install(ContentNegotiation) {
                gson {
                    // Needs to be parsed to string because JSON can't handle all Long values and truncates some
                    // e.g: in memory 2499283573021220255 => 2499283573021220400 in JSON
                    setLongSerializationPolicy(LongSerializationPolicy.STRING)

                    registerTypeAdapter(Page::class.java, PageAdapter())
                }
            }
            install(CallLogging) {
                level = Level.INFO
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
        Log.i(LOG_TAG, "Starting")

        createNotificationChannel()

        startForeground(100, Notification.Builder(this, CHANNEL_ID).setContentTitle("HTTP Server Running").build())

        server.start(wait = false)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(LOG_TAG, "Destroying")
        server.stop(0, 0)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}
