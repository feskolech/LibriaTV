package ru.feskolech.libriatv.ui.components

import androidx.annotation.StringRes
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Short one-off notices ("could not save") that would otherwise fail silently; shown by the activity. */
@Singleton
class UserMessages @Inject constructor() {
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages
    fun show(@StringRes text: Int) { _messages.tryEmit(text) }
}
