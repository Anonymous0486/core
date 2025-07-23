package org.app.core.base.utils

import timber.log.Timber
import java.util.regex.Pattern

class TimberDebugTree : Timber.DebugTree() {
    val CALL_STACK_INDEX: Int = 4
    val ANONYMOUS_CLASS: Pattern = Pattern.compile("(\\$\\d+)+$")

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val stackTrace = Throwable().stackTrace
        check(stackTrace.size > CALL_STACK_INDEX) { "Synthetic stacktrace didn't have enough elements: are you using proguard?" }
        val clazz: String = extractClassName(stackTrace[CALL_STACK_INDEX])
        val lineNumber = stackTrace[CALL_STACK_INDEX].lineNumber
        val logMessage = "[$clazz.kt:$lineNumber]: $message"
        super.log(priority, tag, logMessage, t)
    }

    private fun extractClassName(element: StackTraceElement): String {
        var tag = element.className
        val m = ANONYMOUS_CLASS.matcher(tag)
        if (m.find()) {
            tag = m.replaceAll("")
        }
        return tag.substring(tag.lastIndexOf('.') + 1)
    }
}