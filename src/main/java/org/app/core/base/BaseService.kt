package org.app.core.base

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

abstract class BaseService : LifecycleService() {

    protected val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private val safetyScope by lazy { CoroutineScope(Dispatchers.IO) }

    abstract val notificationId: Int

    abstract val notification: Notification

    override fun onDestroy() {
        safetyScope.cancel()
        super.onDestroy()
    }


}