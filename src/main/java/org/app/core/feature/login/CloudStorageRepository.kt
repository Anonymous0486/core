package org.app.core.feature.login

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.http.HttpExecuteInterceptor
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.app.core.base.extensions.coroutinesIO
import org.app.core.base.extensions.runIO
import org.app.core.base.utils.removePrefixIfNeed
import org.app.core.feature.model.DriveSyncInfo
import org.app.core.feature.model.SyncData
import timber.log.Timber
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudStorageRepository @Inject constructor(
    @ApplicationContext val context: Context,
) {
    private val SEPARATOR = "_ACR_"

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val authorizer = DriveAuthorizer(context)

    // Bumped on logout so requests started for the previous account do not write their result back
    @Volatile
    private var generation = 0

    private var _isInitialize = false
    private var _syncInfoFileId: String? = null
    private var _syncInfo: DriveSyncInfo? = null

    private var _driveService: DriveService? = null
    private val driveService: DriveService?
        get() = _driveService

    private var _syncInfoMap: HashMap<String, SyncData> = HashMap()
    val syncInfoMap: HashMap<String, SyncData>
        get() = _syncInfoMap

    private val _authRequired = MutableStateFlow(false)

    /** True when Drive access was revoked or expired and the user has to sign in again. */
    val authRequired: StateFlow<Boolean> = _authRequired

    val accountEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }

    init {
        migrateLegacyAccount()

        coroutinesIO {
            if (isLoggedIn()) {
                Timber.tag("###DEBUG").d("Logged in account...$accountEmail")
                ensureDriveService()
                fetchSyncInfo()
            }
        }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_LOGGED_IN, false)

    /** False after logout: the user has to pick the Google account again. */
    val hasAccount: Boolean
        get() = accountEmail != null

    fun accountChooserIntent() = authorizer.accountChooserIntent()

    /** Requests Drive access for [email] or the stored account; the result may ask for the consent screen. */
    suspend fun authorize(email: String? = null) = authorizer.authorize(email ?: accountEmail)

    fun parseAuthorizationResult(data: Intent?) = authorizer.parseResult(data)

    /** Call after the user granted Drive access. */
    fun initializeDriveService(email: String?) = coroutinesIO {
        prefs.edit().apply {
            putBoolean(KEY_LOGGED_IN, true)
            if (!email.isNullOrBlank()) putString(KEY_EMAIL, email)
        }.apply()
        _authRequired.value = false

        ensureDriveService()
        fetchSyncInfo()
    }

    fun consumeAuthRequired() {
        _authRequired.value = false
    }

    /**
     * Signs out inside the app only: the Drive permission stays granted on the Google account.
     * Nothing is deleted from the device or from Google Drive.
     */
    fun logout() {
        prefs.edit().apply {
            putBoolean(KEY_LOGGED_IN, false)
            remove(KEY_EMAIL)
            // Do not sign in again from the legacy GoogleSignIn session on the next launch
            putBoolean(KEY_LEGACY_CHECKED, true)
        }.apply()

        _driveService = null
        _syncInfoFileId = null
        _syncInfo = null
        _syncInfoMap = HashMap()
        _isInitialize = false
        _authRequired.value = false
        generation++
    }

    // Users who signed in with the legacy GoogleSignIn API keep their session (checked only once).
    @Suppress("DEPRECATION")
    private fun migrateLegacyAccount() {
        if (isLoggedIn() || prefs.getBoolean(KEY_LEGACY_CHECKED, false)) return

        prefs.edit().putBoolean(KEY_LEGACY_CHECKED, true).apply()
        GoogleSignIn.getLastSignedInAccount(context)?.let { account ->
            prefs.edit().apply {
                putBoolean(KEY_LOGGED_IN, true)
                if (!account.email.isNullOrBlank()) putString(KEY_EMAIL, account.email)
            }.apply()
        }
    }

    private fun ensureDriveService() {
        if (_driveService != null) return

        val initializer = HttpRequestInitializer { request ->
            request.interceptor = HttpExecuteInterceptor { it.headers.authorization = "Bearer ${fetchAccessToken()}" }
        }
        val googleDriveService = Drive.Builder(NetHttpTransport(), GsonFactory(), initializer)
            .setApplicationName("record.phone.call")
            .build()
        _driveService = DriveService(googleDriveService)
    }

    // Runs on the thread executing the Drive request (never the main thread)
    private fun fetchAccessToken(): String {
        // Signed out by the user, so this is not an expired authorization
        if (!isLoggedIn()) throw IOException("Not signed in")

        val result = authorizer.authorizeBlocking(accountEmail)
        if (result is DriveAuthorizer.Result.Success) {
            return result.accessToken
        }

        // The user may have logged out while the request was running
        if (!isLoggedIn()) throw IOException("Not signed in")

        Timber.tag("###DEBUG").e("Drive authorization required again: $result")
        prefs.edit().putBoolean(KEY_LOGGED_IN, false).apply()
        _driveService = null
        _authRequired.value = true
        throw IOException("Drive authorization required")
    }

    suspend fun uploadFile(sync: SyncData, file: java.io.File, type: String, override: Boolean) : String? {
        val service = _driveService ?: return null
        val syncInfo = _syncInfo ?: DriveSyncInfo().also { _syncInfo = it }

        val index = syncInfo.items?.indexOfFirst { it.name == sync.name &&
                it.duration == sync.duration &&
                it.callTime == sync.callTime
        } ?: -1

        if (index == -1 || !override) {
            val items = syncInfo.items?.toMutableList() ?: mutableListOf()
            if (index == -1) {
                items.add(sync)
            } else {
                val names = items.map { it.name }
                var count = 1
                val filterName = sync.name?.removePrefixIfNeed() ?: "${System.currentTimeMillis()}"
                while (names.contains("copy${count}_of_${filterName}")) {
                    count++
                }
                val newName = "copy${count}_of_${filterName}"
                sync.name = newName
                items.add(sync)
            }
            syncInfo.items = items.toTypedArray()
        }
        val json = Gson().toJson(syncInfo)
        val infoFileId = service.uploadOrUpdateFile(
            _syncInfoFileId,
            info_file_name,
            json
        )
        if (!infoFileId.isNullOrBlank()) {
            _syncInfoFileId = infoFileId
        }

        val uploadId = service.uploadFile(sync.name ?: "${System.currentTimeMillis()}", file, type)
        if (!uploadId.isNullOrBlank()) {
            _syncInfoMap[uploadId] = sync
        }

        return uploadId
    }

    suspend fun downloadFile(
        fileId: String,
        outputPath: String
    ) = _driveService?.downloadFile(fileId, outputPath)

    suspend fun queryFiles() : HashMap<String, SyncData> {
        fetchSyncInfo()
        return _syncInfoMap
    }

    fun queryDriveFiles() = flow {
        emit(_syncInfoMap)
        fetchSyncInfo()
        emit(_syncInfoMap)
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchSyncInfo() = runIO {
        Timber.tag("###DEBUG").d("Start fetchSyncInfo...")
        val service = _driveService ?: return@runIO
        val startGeneration = generation
        val driveFile = service.queryFiles()
        if (startGeneration != generation) return@runIO
        if (driveFile != null && driveFile.files.isNotEmpty()) {
            val info = driveFile.files.firstOrNull { it.name == info_file_name }
            if (info != null && !info.id.isNullOrBlank()) {
                _syncInfoFileId = info.id
                val infoContent = service.readFile(info.id)
                if (startGeneration != generation) return@runIO
                try {
                    _syncInfo = Gson().fromJson(infoContent, DriveSyncInfo::class.java)
                    _syncInfo?.items?.forEach { item ->
                        val file = driveFile.files.firstOrNull { it.name == item.name }
                        file?.let { _syncInfoMap[file.id] = item }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@runIO
                }
            }

            Timber.tag("###DEBUG").d("After fetchSyncInfo...${_syncInfoMap.size}")
            _isInitialize = true
        }
    }

    fun encodeFileName(fileName: String): String {
        return URLEncoder.encode(fileName.replace(" ", SEPARATOR), StandardCharsets.UTF_8.toString())
    }

    fun decodeFileName(encodedFileName: String): String {
        return URLDecoder.decode(encodedFileName, StandardCharsets.UTF_8.toString()).replace(SEPARATOR, " ")
    }

    companion object {
        const val info_file_name = "sync_info.json"
        private const val PREFS_NAME = "cloud_storage_prefs"
        private const val KEY_LOGGED_IN = "key_logged_in"
        private const val KEY_EMAIL = "key_email"
        private const val KEY_LEGACY_CHECKED = "key_legacy_checked"
    }
}