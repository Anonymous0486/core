package org.app.core.feature.login

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.app.core.base.extensions.coroutinesIO
import org.app.core.base.extensions.runIO
import org.app.core.base.utils.removePrefixIfNeed
import org.app.core.feature.model.DriveSyncInfo
import org.app.core.feature.model.SyncData
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudStorageRepository @Inject constructor(
    @ApplicationContext val context: Context,
) {
    private val SEPARATOR = "_ACR_"

    private var _isInitialize = false
    private var _syncInfoFileId: String? = null
    private var _syncInfo: DriveSyncInfo? = null

    private var _driveService: DriveService? = null
    private val driveService: DriveService?
        get() = _driveService

    private var _syncInfoMap: HashMap<String, SyncData> = HashMap()
    val syncInfoMap: HashMap<String, SyncData>
        get() = _syncInfoMap

    val googleAccount: GoogleSignInAccount?
        get() = GoogleSignIn.getLastSignedInAccount(context)

    init {
        coroutinesIO {
            GoogleSignIn.getLastSignedInAccount(context)?.let { googleSignInAccount ->
                Timber.tag("###DEBUG").d("Last signed in account...${googleSignInAccount.email}")
                if (_driveService == null) {
                    val credential = GoogleAccountCredential.usingOAuth2(
                        context, Collections.singleton(DriveScopes.DRIVE_FILE)
                    )
                    credential.selectedAccount = googleSignInAccount.account

                    val googleDriveService =
                        Drive.Builder(NetHttpTransport(), GsonFactory(), credential)
                            .setApplicationName("record.phone.call")
                            .build()
                    _driveService = DriveService(googleDriveService)
                }
            }

            fetchSyncInfo()

        }
    }

    fun isLoggedIn(): Boolean {
        if (_driveService != null) return true

        if (GoogleSignIn.getLastSignedInAccount(context) != null) return true

        return false
    }

    fun initializeDriveService(googleSignInAccount: GoogleSignInAccount) = coroutinesIO {
        if (_driveService == null) {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = googleSignInAccount.account

            val googleDriveService =
                Drive.Builder(NetHttpTransport(), GsonFactory(), credential)
                    .setApplicationName("record.phone.call")
                    .build()
            _driveService = DriveService(googleDriveService)
        }
        fetchSyncInfo()
    }

    suspend fun uploadFile(sync: SyncData, file: java.io.File, type: String, override: Boolean) : String? {
        if (_syncInfo == null) {
            _syncInfo = DriveSyncInfo()
        }


        val index = _syncInfo!!.items?.indexOfFirst { it.name == sync.name &&
                it.duration == sync.duration &&
                it.callTime == sync.callTime
        } ?: -1

        if (index == -1 || !override) {
            val items = _syncInfo!!.items?.toMutableList() ?: mutableListOf()
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
            _syncInfo!!.items = items.toTypedArray()
        }
        val json = Gson().toJson(_syncInfo)
        val infoFileId = _driveService?.uploadOrUpdateFile(
            _syncInfoFileId,
            info_file_name,
            json
        )
        if (!infoFileId.isNullOrBlank()) {
            _syncInfoFileId = infoFileId
        }

        val uploadId = _driveService?.uploadFile(sync.name ?: "${System.currentTimeMillis()}", file, type, context)
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
        val driveFile = _driveService?.queryFiles()
        if (driveFile != null && driveFile.files.isNotEmpty()) {
            val info = driveFile.files.firstOrNull { it.name == info_file_name }
            if (info != null && !info.id.isNullOrBlank()) {
                _syncInfoFileId = info.id
                val infoContent = _driveService!!.readFile(info.id)
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
    }
}