package org.app.core.base.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import androidx.fragment.app.FragmentActivity
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import com.permissionx.guolindev.PermissionX
import com.tapadoo.alerter.Alerter
import org.app.core.base.extensions.runIO
import org.app.core.R
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat

var SUB_FOLDER: String = "DownloadVideoFB/"
var BASE_URL: String = "https://facebook-download.merryblue.llc/api/v3/facebook/download?url="

fun showMessage(context: Context, message: String?) {
    Toast.makeText(
        context,
        message ?: context.resources.getString(R.string.some_error),
        Toast.LENGTH_SHORT
    )
        .show()
}

fun showNoInternetAlert(activity: Activity) {
    Alerter.create(activity)
        .setTitle(activity.resources.getString(R.string.connection_error))
        .setText(activity.resources.getString(R.string.no_internet))
        .setIcon(R.drawable.ic_no_internet)
        .setBackgroundColorRes(R.color.red)
        .enableClickAnimation(true)
        .enableSwipeToDismiss()
        .show()
}

fun showLoadingDialog(activity: Activity?, hint: String?): Dialog? {
    if (activity == null || activity.isFinishing) {
        return null
    }

    val progressDialog = Dialog(activity, R.style.CustomDialogAnimation)
    progressDialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
    if (activity != null && !activity.isFinishing && mProgressDialog != null && mProgressDialog.isShowing) {
        mProgressDialog.dismiss()
    }
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
    } catch (e:Exception){
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

fun pasteText(context: Context) : String {
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
        val resoler: ContentResolver = context.contentResolver
        var uri: Uri? = null
        try {
            val contentUri: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            uri = resoler.insert(contentUri, contentValue)

            uri?.let {
                val file = File(relativeLocation, displayName)
                val outputStream: OutputStream? = resoler.openOutputStream(it)
                outputStream?.let { output ->
                    return DataSave(output, file, uri, "", contentValue, resoler.openFileDescriptor(uri, "r")?.statSize.toString())
                } ?: kotlin.run {
                    return null
                }
            }
        } catch (e: Exception) {
            if (uri != null) {
                resoler.delete(uri, null, null)
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
            DataSave(FileOutputStream(filename), filename, null, filename.path, null,getFileSize(filename))
        } catch (e: Exception) {
            null
        }
    }
    return null
}

fun createOutputPath(displayName: String): String? {
    val path = getPath()
    if (path.isNotBlank()) {
        return path + File.separator + displayName
    }

    return null
}

fun getFileSize(file: File): String {
    val fileSizeInBytes = file.length();
    val fileSizeInKB = fileSizeInBytes / 1024
    val fileSizeInMB = fileSizeInKB / 1024;
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
    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
    cursor.moveToFirst()
    val s = cursor.getString(columnIndex)
    cursor.close()
    return s
}

@SuppressLint("SimpleDateFormat")
fun getMediaDuration(context: Context?, uri: Uri?): String {
    val simpleDateFormat = SimpleDateFormat("mm:s")
    var timeInMillisec : Long? = null
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        timeInMillisec = time?.toLong()
        retriever.release()
    }catch (e : Exception){
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
    var size : String? = ""
)