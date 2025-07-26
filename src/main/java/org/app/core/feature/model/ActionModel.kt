package org.app.core.feature.model

internal data class ActionModel(
    val id: String?,
    val flag: Boolean?,
    val percent: Int?,
    val banner: DisplayModel?,
    val small: DisplayModel?,
    val medium: DisplayModel?,
)
