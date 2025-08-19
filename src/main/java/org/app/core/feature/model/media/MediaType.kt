package org.app.core.feature.model.media

sealed class MediaType {
    data class Photos(val ext: String?) : MediaType()
    data object Videos : MediaType()
    data object Both : MediaType()
}