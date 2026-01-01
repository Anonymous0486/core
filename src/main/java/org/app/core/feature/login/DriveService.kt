package org.app.core.feature.login

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.FileContent
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.app.core.feature.extension.runIO
import timber.log.Timber
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class DriveService(driveService: Drive) {
    
    private val mExecutor: Executor = Executors.newSingleThreadExecutor()
    private val mDriveService: Drive = driveService

    private var _backupFolderId: String? = null

    private suspend fun findFolderByName(folderName: String) = runIO {
        try {
            val query = "mimeType='application/vnd.google-apps.folder' and name='$folderName' and trashed=false"
            val result = mDriveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val retId = result.files?.firstOrNull()?.id
            Timber.tag("###DBUG").i("findFolderByName: $retId")
            retId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun createFolderBy(folderName: String) = runIO {
        try {
            val folderMetadata = File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
            }

            val folder = mDriveService.files().create(folderMetadata)
                .setFields("id")
                .execute()

            Timber.tag("###DBUG").i("findFolderByName: ${folder.id}")
            folder.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun readFileContent(fileId: String) = runIO {
        val metadata = mDriveService.files()[fileId].execute()
        val name = metadata.name

        mDriveService.files()[fileId].executeMediaAsInputStream().use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val stringBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line)
                }
                val contents = stringBuilder.toString()
                Pair(name, contents)
            }
        }
    }

    suspend fun uploadOrUpdateFile(
        fileId: String?,
        fileName: String,
        jsonContent: String
    ) = runIO {
        if (_backupFolderId.isNullOrBlank()) {
            _backupFolderId = findFolderByName(BACKUP_FOLDER) ?: createFolderBy(BACKUP_FOLDER)
        }

        if (!_backupFolderId.isNullOrBlank()) {
            val contentStream = ByteArrayInputStream(jsonContent.toByteArray())
            val mediaContent = InputStreamContent("application/json", contentStream)

            if (fileId.isNullOrBlank()) {
                val fileMetadata = File().apply {
                    name = fileName
                    parents = listOf(_backupFolderId)
                }
                val file = mDriveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, mimeType")
                    .execute()
                file.id
            } else {
                val fileMetadata = File().apply {
                    name = fileName
                }

                val file = mDriveService.files().update(fileId, fileMetadata, mediaContent)
                    .setFields("id, name, mimeType")
                    .execute()
                file.id
            }
        } else {
            null
        }
    }

    suspend fun downloadFile(
        fileId: String,
        outputPath: String
    ) = runIO {
        try {
            val localFile = java.io.File(outputPath)
            FileOutputStream(localFile).use { outputStream ->
                mDriveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            }

            outputPath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun deleteFile(fileId: String?): Task<Void?> {
        return Tasks.call(mExecutor) {
            mDriveService.files().delete(fileId).execute()
            null
        }
    }
    
    fun createFilePickerIntent(): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "text/plain"
        return intent
    }
    
    fun openFileUsingStorageAccessFramework(
        contentResolver: ContentResolver,
        uri: Uri
    ): Task<Pair<String, String>> {
        return Tasks.call(mExecutor, Callable {
            var name = ""
            contentResolver.query(uri, null, null, null, null).use { cursor ->
                name = if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.getString(nameIndex)
                } else {
                    throw IOException("Empty cursor returned for file.")
                }
            }
            var content = ""
            contentResolver.openInputStream(uri).use { `is` ->
                BufferedReader(InputStreamReader(`is`!!)).use { reader ->
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line)
                    }
                    content = stringBuilder.toString()
                }
            }
            Pair(name, content)
        }
        )
    }

    suspend fun uploadFile(name: String, file: java.io.File, type: String, context: Context): String? = runIO {
        try {
            if (_backupFolderId.isNullOrBlank()) {
                _backupFolderId = findFolderByName(BACKUP_FOLDER) ?: createFolderBy(BACKUP_FOLDER)
            }
            if (_backupFolderId.isNullOrBlank()) {
                null
            } else {
                val existingFile = searchFile(name)

                if (existingFile != null) {
                    val metadata = File()
                        .setName(name)
                        .setMimeType(type)

                    val mediaContent = FileContent(type, file)
                    val uploadedFile = mDriveService.files().update(existingFile.id, metadata, mediaContent)
                        .execute()

                    uploadedFile.id
                } else {
                    val metadata = File()
                        .setName(name)
                        .setMimeType(type)
                        .setParents(listOf(_backupFolderId))

                    // For call: .m4p -> audio/mpeg, voice: .amr -> audio/amr
                    val mediaContent = FileContent(type, file)
                    val uploadedFile = mDriveService.files().create(metadata, mediaContent)
                        .execute()

                    uploadedFile.id
                }
            }
        } catch (userEx: UserRecoverableAuthIOException)  {
            context.startActivity(userEx.intent)
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun queryFiles() = runIO {
        try {
            if (_backupFolderId.isNullOrBlank()) {
                _backupFolderId = findFolderByName(BACKUP_FOLDER) ?: createFolderBy(BACKUP_FOLDER)
            }

            if (_backupFolderId.isNullOrBlank()) {
                null
            } else {
                val query = "'$_backupFolderId' in parents and trashed=false"
                val files = mDriveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name)") // returned fields: may be add "mimeType" if need.
                    .execute()
                files
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun searchFile(fileName: String) = runIO {
        try {
            if (_backupFolderId.isNullOrBlank()) {
                null
            } else {
                val query = "name='$fileName' and '$_backupFolderId' in parents and trashed=false"
                val result = mDriveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .execute()

                result.files?.firstOrNull()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun readFile(fileId: String) = runIO {
        try {
            mDriveService.files().get(fileId).executeMediaAsInputStream().use { insputStream ->
                BufferedReader(InputStreamReader(insputStream)).use { reader ->
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line)
                    }
                    val contents = stringBuilder.toString()
                    contents
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listFilesFlow(): Flow<List<File>> = flow {
        try {
            val files = mDriveService.files().list().execute()
            if (files != null && files.files.isNotEmpty()) {
                emit(files.files)
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val BACKUP_FOLDER = "CallRecord_Backup"
    }
}