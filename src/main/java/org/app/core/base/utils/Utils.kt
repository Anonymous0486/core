package org.app.core.base.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.*
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings.Secure
import android.text.TextUtils
import android.util.Patterns
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import org.app.core.R
import org.app.core.base.extensions.runIO
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat

var SUB_FOLDER: String = "DownloadVideo/"

fun showMessage(context: Context, message: String?) {
    Toast.makeText(
        context,
        message ?: context.resources.getString(R.string.some_error),
        Toast.LENGTH_SHORT
    )
        .show()
}

fun showNoInternetAlert(activity: Activity) {
//    Alerter.create(activity)
//        .setTitle(activity.resources.getString(R.string.connection_error))
//        .setText(activity.resources.getString(R.string.no_internet))
//        .setIcon(R.drawable.ic_no_internet)
//        .setBackgroundColorRes(R.color.red)
//        .enableClickAnimation(true)
//        .enableSwipeToDismiss()
//        .show()
}

fun showLoadingDialog(activity: Activity?, hint: String?): Dialog? {
    if (activity == null || activity.isFinishing) {
        return null
    }

    val progressDialog = Dialog(activity, R.style.CustomDialogAnimation)
    progressDialog.setContentView(R.layout.progress_dialog)
    val tvHint = progressDialog.findViewById<TextView>(R.id.tv_hint)
    if (!hint.isNullOrEmpty()) {
        tvHint?.visibility = View.VISIBLE
        tvHint?.text = hint
    } else {
        tvHint?.visibility = View.GONE
    }

    progressDialog.setCancelable(false)
    progressDialog.setCanceledOnTouchOutside(false)
    progressDialog.show()

    return progressDialog
}

fun hideLoadingDialog(mProgressDialog: Dialog?, activity: Activity?) {
    try {
        if (activity != null && !activity.isFinishing && mProgressDialog != null && mProgressDialog.isShowing) {
            mProgressDialog.dismiss()
        }
    } catch (_: Exception) {}
}

@SuppressLint("HardwareIds")
fun getDeviceId(context: Context): String {
    return Secure.getString(context.contentResolver, Secure.ANDROID_ID)
}

fun String.isEmailValid(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()

suspend fun Context.getBitmapFromUrl(url: String, overrideSize: Int? = null) = runIO {
    try {
        Glide.with(this@getBitmapFromUrl).asBitmap().load(url)
            .also { if (overrideSize != null) it.override(overrideSize) }.submit().get()
    } catch (e: Exception) {
        null
    }
}

internal fun ImageView.loadSliderImage(imageUrl: String?) {
    if (imageUrl != null && imageUrl.isNotEmpty()) {
        val circularProgressDrawable = CircularProgressDrawable(context)
        circularProgressDrawable.strokeWidth = 5f
        circularProgressDrawable.centerRadius = 30f
        circularProgressDrawable.start()

        Glide
            .with(context)
            .load(imageUrl)
            .placeholder(circularProgressDrawable)
            .error(R.drawable.img_place_holder)
            .into(this)
    } else {
        Glide
            .with(context)
            .clear(this)

        setImageResource(R.drawable.img_place_holder)
    }
}

val Int.dp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

val Int.px: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()

class SafeClickListener(
    private var defaultInterval: Int = 1000,
    private val onSafeCLick: (View?) -> Unit
) : View.OnClickListener {
    private var lastTimeClicked: Long = 0

    override fun onClick(v: View) {
        if (SystemClock.elapsedRealtime() - lastTimeClicked < defaultInterval) {
            return
        }
        lastTimeClicked = SystemClock.elapsedRealtime()
        onSafeCLick(v)
    }
}

fun pasteText(context: Context): String {
    var textToPaste = ""
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?

    clipboard?.let {
        if (it.hasPrimaryClip()) {
            val clip = it.primaryClip
            // if you need text data only, use:
            clip?.let { clipNew ->
                val description = clipNew.description
                val types = listOf(
                    ClipDescription.MIMETYPE_TEXT_PLAIN,
                    ClipDescription.MIMETYPE_TEXT_HTML,
                    ClipDescription.MIMETYPE_TEXT_URILIST,
                    ClipDescription.MIMETYPE_TEXT_INTENT
                )

                if (hasMimeTypes(description, types)) {
                    clipNew.getItemAt(0)?.let { clipItem ->
                        textToPaste = clipItem.coerceToText(context).toString()
                    }
                }
            }
        }
    }

    return textToPaste
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Qr-code",text)
    clipboard?.setPrimaryClip(clip)
}

@SuppressLint("Recycle")
fun checkSaveFileMediaStore(context: Context, displayName: String): DataSave? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        var relativeLocation = Environment.DIRECTORY_DOWNLOADS
        if (!TextUtils.isEmpty(SUB_FOLDER)) {
            relativeLocation += File.separator + SUB_FOLDER
        }

        val contentValue = ContentValues()
        contentValue.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        contentValue.put("is_pending", true)
        contentValue.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        contentValue.put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
        contentValue.put(MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis())
        val resolver: ContentResolver = context.contentResolver
        var uri: Uri? = null
        try {
            val contentUri: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            uri = resolver.insert(contentUri, contentValue)

            uri?.let {
                val file = File(relativeLocation, displayName)
                val outputStream: OutputStream? = resolver.openOutputStream(it)
                outputStream?.let { output ->
                    return DataSave(output, file, uri, "", contentValue, resolver.openFileDescriptor(uri, "r")?.statSize.toString())
                } ?: kotlin.run {
                    return null
                }
            }
        } catch (e: Exception) {
            if (uri != null) {
                resolver.delete(uri, null, null)
            }
            e.printStackTrace()
        }
    } else {
        return try {
            val mPath = Environment.getExternalStorageDirectory().path + "/DownloadVideoFB"
            val pathFile = File(mPath)
            if (!pathFile.exists()) {
                pathFile.mkdirs()
            }
            val path = getPath() + File.separator + "" + displayName
            val filename = File(path)
            DataSave(FileOutputStream(filename), filename, null, filename.path, null, "")
        } catch (e: Exception) {
            null
        }
    }

    return null
}

fun getFileSize(file: File): String {
    val fileSizeInBytes = file.length()
    val fileSizeInKB = fileSizeInBytes / 1024
    val fileSizeInMB = fileSizeInKB / 1024
    return fileSizeInKB.toString()
}

fun getPath(): String {
    var folderPath = ""
    folderPath =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/DownloadVideoFB"
    val folder = File(folderPath)
    if (!folder.exists()) {
        val wallpaperDirectory = File(folderPath)
        wallpaperDirectory.mkdirs()
    }
    return folderPath
}

fun getPath(context: Context, uri: Uri): String? {
    val projection = arrayOf(MediaStore.Images.Media.DATA)
    val cursor: Cursor =
        context.contentResolver.query(uri, projection, null, null, null)
            ?: return null
    
    try {
        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor.moveToFirst()
        val s = cursor.getString(columnIndex)
        return s
    } catch (_: Exception) {}
    finally {
        cursor.close()
    }
    return null
}

@SuppressLint("SimpleDateFormat")
fun getMediaDuration(context: Context?, uri: Uri?): String {
    val simpleDateFormat = SimpleDateFormat("mm:s")
    var timeInMillisec: Long? = null
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        timeInMillisec = time?.toLong()
        retriever.release()
    } catch (e: Exception) {
        return "00:00"
    }
    return simpleDateFormat.format(timeInMillisec?.toString())
}

fun hasMimeTypes(description: ClipDescription, types: List<String>): Boolean {
    for (type in types) {
        if (description.hasMimeType(type)) {
            return true
        }
    }
    return false
}

data class DataSave(
    var outputStream: OutputStream,
    var file: File,
    var uri: Uri?,
    var path: String? = "",
    var contentValues: ContentValues?,
    var size: String? = ""
)

fun takeScreenShot(activity: Activity, margin: Int): Bitmap? {
    val view = activity.window.decorView
    view.isDrawingCacheEnabled = true
    view.buildDrawingCache()
    val b1 = view.drawingCache
    val frame = Rect()
    activity.window.decorView.getWindowVisibleDisplayFrame(frame)
    val statusBarHeight = frame.top - if (frame.top > margin) margin else 0
    val width = activity.windowManager.defaultDisplay.width
    val height = activity.windowManager.defaultDisplay.height
    val b = Bitmap.createBitmap(b1, 0, statusBarHeight, width, height - statusBarHeight)
    view.destroyDrawingCache()
    return b
}

fun takeImageViewScreenShot(image: ImageView) : Bitmap? {
    return try {
        image.isDrawingCacheEnabled = true
        image.buildDrawingCache()
        val bitmap = Bitmap.createBitmap(image.drawingCache)
        image.isDrawingCacheEnabled = false
        bitmap
    } catch (_: Exception) {
        null
    }
}

fun getBitmap(filePath: String, options: BitmapFactory.Options? = null) : Bitmap {
    val bitmap = BitmapFactory.decodeFile(filePath, options)
    val exif = ExifInterface(filePath)
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)
    val matrix = Matrix().apply {
        when (orientation) {
            6 -> postRotate(90f)
            3 -> postRotate(180f)
            8 -> postRotate(270f)
        }
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

fun uriToBitmap(uri: Uri, context: Context?, options: BitmapFactory.Options? = null) : Bitmap? {
    val inputStream = context?.contentResolver?.openInputStream(uri)
    inputStream?.let {
        val exif = ExifInterface(inputStream)
        val rotate = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            else -> 0f
        }
        val matrix = Matrix().apply {
            postRotate(rotate)
        }
        val bitmap = BitmapFactory.decodeStream(inputStream)
        return Bitmap.createBitmap(bitmap ?: return null, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    return null
}

inline fun <reified T : Enum<T>> Intent.putExtra(victim: T): Intent =
    putExtra(T::class.java.name, victim.ordinal)

inline fun <reified T: Enum<T>> Intent.getEnumExtra(): T? =
    getIntExtra(T::class.java.name, -1)
        .takeUnless { it == -1 }
        ?.let { T::class.java.enumConstants[it] }