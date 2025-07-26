package org.app.core.base.utils

import androidx.lifecycle.Observer

open class SingleEvent<out T>(private val content: T) {

    var hasBeenHandled = false
        private set // Allow external read but not write

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    fun peekContent(): T = content
}

class SingleEventObserver<T>(private val onEventUnhandledContent: (T) -> Unit) : Observer<SingleEvent<T>> {
    override fun onChanged(value: SingleEvent<T>) {
        value.getContentIfNotHandled()?.let { v ->
            onEventUnhandledContent(v)
        }
    }
}