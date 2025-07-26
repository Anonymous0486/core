package org.app.core.base

import android.view.View

interface OnItemClickListener<T> {
    fun onItemClick(viewItem: View?, data: T, position: Int, action: String? = null)
}
