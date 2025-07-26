package org.app.core.base.utils

import android.text.format.DateFormat
import android.text.format.DateUtils
import org.app.core.base.utils.DateUtils.Companion.DEFAULT_DATE_FORMAT
import org.app.core.base.utils.DateUtils.Companion.EXTENDED_DATE_FORMAT
import org.app.core.base.utils.DateUtils.Companion.UI_DATE_FORMAT
import org.app.core.base.utils.DateUtils.Companion.WEEKLY_DATE_FORMAT
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DateUtils {
    companion object {
        const val API_DATE_FORMAT = "yyyy-MM-dd"
        const val UI_DATE_FORMAT = "dd-MM-yyyy"
        const val UI_MONTH_FORMAT = "MM-yyyy"
        const val FULL_DATE_TIME_FORMAT_24 = "yyyy-MM-dd'T'HH:mm:ss"
        const val EXTENDED_DATE_FORMAT = "EEE, MMM d, yyyy"
        const val FULL_DATE_TIME_FORMAT_12 = "EEEE, MMMM d, yyyy, hh:mm a"
        const val WEEKLY_DATE_FORMAT = "EEEE"
        const val DEFAULT_DATE_FORMAT = "EEE, MMMM d"
        const val MONTH_UI_DATE_FORMAT = "dd MMM yyyy"
        const val DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss"
        const val TIME_12_FORMAT = "hh:mm a"
        const val TIME_24_FORMAT = "HH:mm"
        const val TIME_24_FORMAT_WITH_SECONDS = "HH:mm:ss"
        const val TIME_HOUR_ONLY = "HH"
        const val TIME_MINUTE_ONLY = "mm"
        const val DATE_TIME_12_FORMAT = "yyyy-MM-dd hh:mm a"
        const val DATE_TIME_UI_FORMAT = "dd MMM yyyy, hh:mm a"
        const val SHORT_DAY_NAME = "E"
        const val DATE_WITH_DAY_NAME = "EEE, dd MMM yyyy"
        const val HOUR = "HH"
        const val MINUTE = "mm"
        const val DAY = "dd"
        const val MONTH = "MM"
        const val YEAR = "yyyy"
        const val DATE_TIME_PDF_NAME_FORMAT = "yyyy-MM-dd_HH-mm-ss"
        const val BIRTHDAY_DATE_FORMAT = "dd/MM/yyyy"
        
        fun toSimpleString(date: Date) : String {
            val format = SimpleDateFormat(UI_DATE_FORMAT, Locale.US)
            return format.format(date)
        }
    
        fun fromSimpleString(dateString: String) : Date? {
            val format = SimpleDateFormat(UI_DATE_FORMAT, Locale.US)
            return format.parse(dateString)
        }
    }
}

fun Long.formatLastUpdate(): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = this
    val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm", Locale.US)

    return dateFormat.format(calendar.time)
}

fun String.fromFormatLastUpdate() : Long {
    try {
        val format = SimpleDateFormat("dd/MM/yyyy hh:mm", Locale.US)
        return format.parse(this)?.time ?: 0
    } catch (_: Exception) {}
    return 0
}

fun Long.toFormat(template: String): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = this
    val fm = SimpleDateFormat(template, Locale.US)
    return fm.format(calendar.time)
}

fun Date.toFormat(template: String): String {
    val fm = SimpleDateFormat(template, getLocale())
    return fm.format(this)
}

fun Long.formatChart(): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = this

    val dateFormat = SimpleDateFormat("dd/MM")

    return dateFormat.format(calendar.time)
}

fun Long.secondToSimpleDate(
    format: CharSequence = DEFAULT_DATE_FORMAT,
): String {
    val mediaDate = Calendar.getInstance(Locale.US)
    mediaDate.timeInMillis = this * 1000L
    return DateFormat.format(format, mediaDate).toString()
}

fun Long.secondToDate(
    format: CharSequence = DEFAULT_DATE_FORMAT,
    weeklyFormat: CharSequence = WEEKLY_DATE_FORMAT,
    extendedFormat: CharSequence = EXTENDED_DATE_FORMAT,
    stringToday: String = "Today",
    stringYesterday: String = "Yesterday"
): String {
    val currentDate = Calendar.getInstance(Locale.US)
    currentDate.timeInMillis = System.currentTimeMillis()
    val mediaDate = Calendar.getInstance(Locale.US)
    mediaDate.timeInMillis = this * 1000L
    val different: Long = System.currentTimeMillis() - mediaDate.timeInMillis
    val secondsInMilli: Long = 1000
    val minutesInMilli = secondsInMilli * 60
    val hoursInMilli = minutesInMilli * 60
    val daysInMilli = hoursInMilli * 24
    
    val daysDifference = different / daysInMilli
    
    return when (daysDifference.toInt()) {
        0 -> {
            if (currentDate.get(Calendar.DATE) != mediaDate.get(Calendar.DATE)) {
                stringYesterday
            } else {
                stringToday
            }
        }
        
        1 -> {
            stringYesterday
        }
        
        else -> {
            if (daysDifference.toInt() in 2..5) {
                DateFormat.format(weeklyFormat, mediaDate).toString()
            } else {
                if (currentDate.get(Calendar.YEAR) > mediaDate.get(Calendar.YEAR)) {
                    DateFormat.format(extendedFormat, mediaDate).toString()
                } else DateFormat.format(format, mediaDate).toString()
            }
        }
    }
}

fun String.changeDataFormat(oldFormat: String, newFormat: String): String {
    val formatView = SimpleDateFormat(oldFormat, getLocale())
    val newFormatView = SimpleDateFormat(newFormat, getLocale())
    var dateObj: Date? = null
    try {
        dateObj = formatView.parse(this)
    } catch (e: ParseException) {
        e.printStackTrace()
    }

    return if (dateObj == null) "" else newFormatView.format(dateObj)
}

fun String.convertDateTimeToTimesAgo(format: String): String {
    val inputFormat = SimpleDateFormat(format, getLocale())
    val date: Date?
    return try {
        date = inputFormat.parse(this)

        // the new date style
        DateUtils.getRelativeTimeSpanString(
            date.time,
            Calendar.getInstance().timeInMillis,
            DateUtils.MINUTE_IN_MILLIS
        ) as String
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

fun getLocale(): Locale? {
    return when (Locale.getDefault().language) {
        "ar" -> {
            Locale("ar")
        }
        "fr" -> {
            Locale.FRENCH
        }
        else -> {
            Locale.ENGLISH
        }
    }
}

fun Date.isYesterday(): Boolean = DateUtils.isToday(this.time + DateUtils.DAY_IN_MILLIS)

fun Date.isToday(): Boolean = DateUtils.isToday(this.time)
