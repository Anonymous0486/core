package org.app.core.feature.model

import com.google.gson.annotations.SerializedName

data class TranslateResponse(
    @SerializedName("language_present")
    val languagePresent: String? = null,
    @SerializedName("translate")
    val translate: String? = null
)
