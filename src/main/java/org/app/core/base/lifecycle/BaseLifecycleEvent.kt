package org.app.core.base.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.*
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

abstract class BaseLifecycleEvent : LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            ON_CREATE -> onCreate()
            ON_DESTROY -> onDestroy()
            ON_STOP -> onStop()
            ON_RESUME -> onResume()
            ON_PAUSE -> onPause()
            ON_START -> onStart()
            ON_ANY -> onAny()
        }
    }

    protected open fun onAny() {

    }

    protected open fun onStart() {
    }

    protected open fun onPause() {
    }

    protected open fun onResume() {
    }

    protected open fun onStop() {}

    protected open fun onDestroy() {}

    protected open fun onCreate() {}
}