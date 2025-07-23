package org.app.core.base.utils

class ViewClickThrottle {
    companion object {
        const val MIN_INTERVAL = 350
    }
    private var lastEventTime = System.currentTimeMillis()

    fun throttle(code: () -> Unit) {
        val eventTime = System.currentTimeMillis()
        if (eventTime - lastEventTime > MIN_INTERVAL) {
            lastEventTime = eventTime
            code()
        }
    }
}