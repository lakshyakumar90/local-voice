package dev.localvoice.app

import android.app.Application
import dev.localvoice.app.data.*
import dev.localvoice.app.session.SessionCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class LocalVoiceApplication : Application() {
    lateinit var graph: AppGraph; private set
    override fun onCreate() { super.onCreate(); graph = AppGraph(this) }
}
class AppGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val settings = SettingsRepository(app)
    val preferences = settings.data.stateIn(scope,SharingStarted.Eagerly,UserSettings())
    val catalog = ModelCatalog(app)
    val models = ModelRepository(app,catalog)
    val history = HistoryRepository(app)
    val session = SessionCoordinator(this)
    init { scope.launch(Dispatchers.IO) { runCatching { history.prune() } } }
}
val android.content.Context.graph: AppGraph get() = (applicationContext as LocalVoiceApplication).graph
