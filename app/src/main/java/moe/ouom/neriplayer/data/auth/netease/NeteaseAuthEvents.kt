package moe.ouom.neriplayer.data.auth.netease

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NeteaseAuthEvents {
    private val _loginSuccessEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginSuccessEvents: SharedFlow<Unit> = _loginSuccessEvents.asSharedFlow()

    fun notifyLoginSuccess() {
        _loginSuccessEvents.tryEmit(Unit)
    }
}
