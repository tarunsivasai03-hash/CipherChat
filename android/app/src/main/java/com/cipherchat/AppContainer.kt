package com.cipherchat

import android.content.Context
import androidx.room.Room
import com.cipherchat.core.database.AppDatabase
import com.cipherchat.core.network.ApiClient
import com.cipherchat.core.network.SocketEvent
import com.cipherchat.core.network.SocketManager
import com.cipherchat.data.AppSettings
import com.cipherchat.data.AuthRepository
import com.cipherchat.data.ChatRepository
import com.cipherchat.data.ContactsRepository
import com.cipherchat.data.RuntimeConnectionConfig
import com.cipherchat.data.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Hand-wired dependency graph (small enough that a DI framework would add more than it removes). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = AppSettings(context)
    private val config = RuntimeConnectionConfig(BuildConfig.DEFAULT_BASE_URL)
    val database: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "cipherchat.db").build()
    val apiClient = ApiClient(config)
    val socket = SocketManager(config)

    val auth = AuthRepository(settings, apiClient, config, database)
    val chat = ChatRepository(apiClient, database.conversationDao(), database.messageDao(), socket, myId = { auth.currentUserId }, scope = appScope)
    val contacts = ContactsRepository(apiClient)

    init {
        // Socket + chat sync live exactly as long as there is a signed-in session.
        appScope.launch {
            auth.session.map { it is SessionState.LoggedIn }.distinctUntilChanged().collect { loggedIn ->
                if (loggedIn) {
                    chat.start()
                    socket.connect()
                } else {
                    chat.stop()
                    socket.disconnect()
                }
            }
        }
        // A 401 from REST or a refused socket both mean the session is gone: sign out locally.
        appScope.launch { apiClient.unauthorized.collect { if (auth.session.value is SessionState.LoggedIn) auth.clearLocalSession() } }
        appScope.launch {
            socket.events.filter { it is SocketEvent.Unauthorized }.collect {
                if (auth.session.value is SessionState.LoggedIn) auth.restore() // re-verify with REST; clears if really invalid
            }
        }
        appScope.launch { auth.restore() }
    }
}
