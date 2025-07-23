package org.app.core.feature.model

data class DictionaryModel(
    val word: String? = "",
    val pronunciation: String? = "",
    val phonetic: String? = "",
    val meaning: List<WordMeaningModel>? = null
)
