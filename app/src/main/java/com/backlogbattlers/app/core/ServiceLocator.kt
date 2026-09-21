package com.backlogbattlers.app.core

import android.content.Context
import com.backlogbattlers.app.BuildConfig
import com.backlogbattlers.app.data.local.AppDatabase
import com.backlogbattlers.app.data.local.TokenStorage
import com.backlogbattlers.app.data.remote.AuthApi
import com.backlogbattlers.app.data.remote.GameApi
import com.backlogbattlers.app.data.repository.AuthRepository
import com.backlogbattlers.app.data.repository.AuthRepositoryImpl
import com.backlogbattlers.app.data.repository.GameRepository
import com.backlogbattlers.app.data.repository.GameRepositoryImpl
import com.backlogbattlers.app.data.repository.LibraryRepository
import com.backlogbattlers.app.data.repository.LibraryRepositoryImpl
import com.backlogbattlers.app.data.repository.SettingsRepository
import com.backlogbattlers.app.data.repository.SettingsRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

// dependency locator holding application singletons for database, network, and repository instances
object ServiceLocator {

    // the android engine defaults both timeouts to 100s, which leaves screens loading for over a minute when the backend is unreachable
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val SOCKET_TIMEOUT_MS = 30_000

    @Volatile
    private var applicationContext: Context? = null

    // for writes that have to finish even though the screen that started them
    // is going away, such as saving the appearance right before the activity
    // is recreated to pick the new theme up
    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    val database: AppDatabase by lazy {
        val context = applicationContext ?: error("ServiceLocator must be initialized with context before accessing database")
        AppDatabase.getInstance(context)
    }

    val tokenStorage: TokenStorage by lazy {
        val context = applicationContext ?: error("ServiceLocator must be initialized with context before accessing tokenStorage")
        TokenStorage(context)
    }

    val httpClient: HttpClient by lazy {
        createHttpClient()
    }

    val authApi: AuthApi by lazy {
        AuthApi(httpClient = httpClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    val gameApi: GameApi by lazy {
        GameApi(httpClient = httpClient, baseUrl = BuildConfig.API_BASE_URL)
    }

    val authRepository: AuthRepository by lazy {
        val context = applicationContext ?: error("ServiceLocator must be initialized with context before accessing authRepository")
        AuthRepositoryImpl(
            context = context,
            userDao = database.userDao(),
            authApi = authApi,
            tokenStorage = tokenStorage,
            serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID,
        )
    }

    val gameRepository: GameRepository by lazy {
        GameRepositoryImpl(database.cachedGameDao(), gameApi)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepositoryImpl(database.libraryEntryDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        val context = applicationContext ?: error("ServiceLocator must be initialized with context before accessing settingsRepository")
        SettingsRepositoryImpl(context)
    }

    //------------------------------
    // initializes the ServiceLocator with the application context
    fun init(context: Context) {
        if (applicationContext == null) {
            synchronized(this) {
                if (applicationContext == null) {
                    applicationContext = context.applicationContext
                }
            }
        }
    }

    //------------------------------
    // builds and configures an HTTP client with Ktor Android engine and JSON content negotiation
    private fun createHttpClient(): HttpClient {
        return HttpClient(Android) {
            engine {
                connectTimeout = CONNECT_TIMEOUT_MS
                socketTimeout = SOCKET_TIMEOUT_MS
            }
            install(ContentNegotiation) {
                json(
                    json = Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                    contentType = ContentType.Any,
                )
            }
        }
    }
}
//------------------------------EOF------------------------------\\