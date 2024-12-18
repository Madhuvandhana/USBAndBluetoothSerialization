package com.example.usbserialization.presentation.common

import android.os.Build
import android.util.Log
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 * @see https://github.com/Kotlin-Android-Open-Source
 * Base view model for MVI pattern
 */

abstract class BaseViewModel<I : MviIntent, S : MviViewState, E : MviSingleEvent> :
    MviViewModel<I, S, E>, ViewModel() {
    protected open val rawLogTag: String? = null

    protected val logTag by lazy(PUBLICATION) {
        (rawLogTag ?: this::class.java.simpleName).let { tag: String ->
            // Tag length limit was removed in API 26.
            if (tag.length <= MAX_TAG_LENGTH || Build.VERSION.SDK_INT >= 26) {
                tag
            } else {
                tag.take(MAX_TAG_LENGTH)
            }
        }
    }

    private val eventChannel = Channel<E>(Channel.UNLIMITED)
    private val intentMutableFlow = MutableSharedFlow<I>(extraBufferCapacity = Int.MAX_VALUE)

    final override val singleEvent: Flow<E> = eventChannel.receiveAsFlow()

    @MainThread
    final override suspend fun processIntent(intent: I) {
        check(intentMutableFlow.tryEmit(intent)) { "Failed to emit intent: $intent" }
    }

    @CallSuper
    override fun onCleared() {
        super.onCleared()
        eventChannel.close()
    }

    // Send event and access intent flow.

    /**
     * Must be called in [kotlinx.coroutines.Dispatchers.Main.immediate],
     * otherwise it will throw an exception.
     *
     * If you want to send an event from other [kotlinx.coroutines.CoroutineDispatcher],
     * use `withContext(Dispatchers.Main.immediate) { sendEvent(event) }`.
     */
    protected suspend fun sendEvent(event: E) {
        eventChannel.trySend(event)
            .onSuccess { Log.d("TAG", "sendEvent: event=$event") }
            .onFailure {
                Log.e("TAG", "$it Failed to send event: $event")
            }
            .getOrThrow()
    }

    protected val intentSharedFlow: SharedFlow<I> get() = intentMutableFlow

    /**
     * Share the flow in [viewModelScope],
     * start when the first subscriber arrives,
     * and stop when the last subscriber leaves.
     */
    protected fun <T> Flow<T>.shareWhileSubscribed(): SharedFlow<T> =
        shareIn(viewModelScope, SharingStarted.WhileSubscribed())

    protected fun <T> Flow<T>.stateWithInitialNullWhileSubscribed(): StateFlow<T?> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    @Deprecated(
        message = "This Flow is already shared in viewModelScope, so you don't need to share it again.",
        replaceWith = ReplaceWith("this"),
        level = DeprecationLevel.ERROR,
    )
    protected fun <T> SharedFlow<T>.shareWhileSubscribed(): SharedFlow<T> = this

    private companion object {
        private const val MAX_TAG_LENGTH = 23
    }
}
