package org.app.core.feature.translate

import android.util.LruCache
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import okhttp3.RequestBody
import org.app.core.feature.datasource.TranslateRemoteDataSource
import org.app.core.feature.extension.runIO
import org.app.core.feature.model.BaseResponse
import org.app.core.feature.model.DictionaryModel
import org.app.core.feature.model.ResponseData
import org.app.core.feature.model.TranslateResponse
import javax.inject.Inject

class TranslateRepository @Inject constructor(
    private val dataSource: TranslateRemoteDataSource
) {
    // New translate solution with MLKit
    private val modelManager: RemoteModelManager = RemoteModelManager.getInstance()
    private val pendingDownloads: HashMap<String, Task<Void>> = hashMapOf()
    val availableModels = ArrayList<String>()
    val availableLanguages = TranslateLanguage.getAllLanguages()
    private val _translatedBlock = ArrayList<String>()
    private val translators = object : LruCache<TranslatorOptions, Translator>(3) {
        override fun create(options: TranslatorOptions): Translator {
            return Translation.getClient(options)
        }
        override fun entryRemoved(
            evicted: Boolean,
            key: TranslatorOptions,
            oldValue: Translator,
            newValue: Translator?,
        ) {
            oldValue.close()
        }
    }

    init {
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java).addOnSuccessListener {
                remoteModels ->
            availableModels.clear()
            availableModels.addAll(remoteModels.sortedBy { it.language }.map { it.language })
        }
    }

    suspend fun translate(
        content: String,
        languageCode: String
    ): BaseResponse<TranslateResponse> {

        return when (val response = dataSource.translate(content, languageCode)) {
            is ResponseData.Success -> {
                if (response.value?.result != null) {
                    val data = response.value?.result
                    BaseResponse(status = true, result = data)
                } else {
                    BaseResponse(status = true, result = TranslateResponse())
                }
            }
            is ResponseData.Failure -> BaseResponse(status = false, message = response.message)
            else -> {
                BaseResponse(status = false)
            }
        }
    }

    suspend fun multipleTranslate(
        contents: List<String>,
        languageCode: RequestBody
    ): BaseResponse<List<String>?> {
        return when (val response = dataSource.multipleTranslate(contents, languageCode)) {
            is ResponseData.Success -> {
                if (response.value?.result != null) {
                    val data = response.value?.result
                    BaseResponse(status = true, result = data)
                } else {
                    BaseResponse(status = true, result = null)
                }
            }
            is ResponseData.Failure -> BaseResponse(status = false, message = response.message)
            else -> {
                BaseResponse(status = false)
            }
        }
    }

    suspend fun translateWebBy(url: String, from: String, to: String): BaseResponse<String> {
        return when (val response = dataSource.translateWebBy(url, from, to)) {
            is ResponseData.Success -> {
                if (response.value?.result != null) {
                    val data = response.value?.result
                    BaseResponse(status = true, result = data)
                } else {
                    BaseResponse(status = true, result = "")
                }
            }
            is ResponseData.Failure -> BaseResponse(status = false, message = response.message)
            else -> {
                BaseResponse(status = false)
            }
        }
    }

    suspend fun dictionaryGet(
        term: String
    ): BaseResponse<DictionaryModel> {

        return when (val response = dataSource.dictionary(term)) {
            is ResponseData.Success -> {
                if (response.value?.result != null) {
                    val data = response.value?.result
                    BaseResponse(status = true, result = data)
                } else {
                    BaseResponse(status = true, result = DictionaryModel())
                }
            }
            is ResponseData.Failure -> BaseResponse(status = false, message = response.message)
        }
    }


    suspend fun mlVisionTranslate(sourceText: List<String>, sourceLan: String, targetLag: String) = runIO {
        _translatedBlock.clear()
        if (sourceLan.isNotBlank() &&
            availableLanguages.contains(sourceLan) && availableModels.contains(sourceLan)
            && availableLanguages.contains(targetLag) && availableModels.contains(targetLag)) {
            val sourceLangCode = TranslateLanguage.fromLanguageTag(sourceLan) ?: return@runIO emptyList()
            val targetLangCode = TranslateLanguage.fromLanguageTag(targetLag) ?: return@runIO emptyList()
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLangCode)
                .setTargetLanguage(targetLangCode)
                .build()

            sourceText.forEach { text ->
                val translated = doMLVisionTranslate(text, options)
                _translatedBlock.add(translated)
            }
        } else {
            if (!availableModels.contains(targetLag)) {
                downloadLanguage(targetLag)
            }
        }

        return@runIO _translatedBlock
    }

    private suspend fun doMLVisionTranslate(text: String, options: TranslatorOptions): String {
        val task = translators[options].downloadModelIfNeeded().continueWithTask { task ->
            if (task.isSuccessful) {
                translators[options].translate(text)
            } else {
                Tasks.forResult("")
            }
        }
        return task.await()
    }

    fun downloadLanguage(languageCode: String) {
        if (!availableLanguages.contains(languageCode) || availableModels.contains(languageCode)) return
        val model = TranslateRemoteModel.Builder(languageCode).build()
        var downloadTask: Task<Void>?
        if (pendingDownloads.containsKey(languageCode)) {
            downloadTask = pendingDownloads[languageCode]
            // found existing task. exiting
            if (downloadTask != null && !downloadTask.isCanceled) {
                return
            }
        }
        downloadTask =
            modelManager.download(model, DownloadConditions.Builder().build()).addOnCompleteListener {
                pendingDownloads.remove(languageCode)
                fetchDownloadedModels()
            }
        pendingDownloads[languageCode] = downloadTask
    }

    private fun fetchDownloadedModels() {
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java).addOnSuccessListener {
                remoteModels ->
            availableModels.clear()
            availableModels.addAll(remoteModels.sortedBy { it.language }.map { it.language })
        }
    }
}