package org.app.core.base.extensions

import com.google.gson.Gson
import com.google.gson.GsonBuilder

fun Any.toJsonString(): String = Gson().toJson(this)

fun <A : Any> String.toJsonModel(modelClass: Class<A>): A = Gson().fromJson(this, modelClass)

inline fun <reified T : Any> Any.mapTo(): T =
    GsonBuilder().create().run {
        fromJson(toJson(this@mapTo), T::class.java)
    }