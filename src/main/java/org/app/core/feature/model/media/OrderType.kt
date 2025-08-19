package org.app.core.feature.model.media

sealed class OrderType {
    data object Ascending : OrderType()
    data object Descending : OrderType()
}
