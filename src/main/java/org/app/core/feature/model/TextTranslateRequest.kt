package org.app.core.feature.model

/*
    "content_translate": "PBM Bách Chiến Trường Ca - Quà 7 Ngày 14 triệu",
    "language_code": "en",
    "type": "modelai"   // modelai OR openai
 */
data class TextTranslateRequest(
    val content_translate: String?,
    val language_code: String?,
    val type: String? = null,
)
