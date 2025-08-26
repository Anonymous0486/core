package org.app.core.base.extensions

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

fun String.isValidEmail() = Regex(
    "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
            "\\@" +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
            "(" +
            "\\." +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
            ")+"
).matches(this)

fun String.isValidFBlink() = Regex(
    "(?:https?:\\/{2})?(?:w{3}\\.)?(facebook|fb|m.facebook|m.fb).*"
).matches(this)

fun String.isValidIGlink() = Regex(
    "(?:https?:\\/{2})?(?:w{3}\\.)?(instagram|m.instagram).*"
).matches(this)

fun String.isValidTTLink() = Regex(
    "(?:https?:\\/{2})?(?:w{3}\\.)?(.*tiktok|m.tiktok).*"
).matches(this)

fun String.isValidDouyinLink() = Regex(
    "(?:https?:\\/{2})?(?:w{3}\\.)?(.douyin|v.douyin|iesdouyin).*"
).matches(this)

fun String.isValidTwitterLink() = Regex(
    "(?:https?:\\/{2})?(?:w{3}\\.)?(.*twitter|m.twitter).*"
).matches(this)

fun String.isElonMuskLink() : Boolean {
    return this.startsWith("https://x.com/") || this.startsWith("http://x.com/")
}

fun String.isValidThreadLink() = Regex(
    "(?:https?:\\/{2})?(?:w{3}\\.)?(threads|m.threads).*"
).matches(this)

fun String.encryptCBC(ivKey: String, secretKey: String): String {
    val iv = IvParameterSpec(ivKey.toByteArray())
    val keySpec = SecretKeySpec(secretKey.toByteArray(), "AES")
    val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv)
    val encrypted = cipher.doFinal(this.toByteArray())
    val encodedByte = Base64.encode(encrypted, Base64.DEFAULT)
    
    return String(encodedByte)
}

fun String.decryptCBC(ivKey: String, secretKey: String): String {
    val decodedByte: ByteArray = Base64.decode(this, Base64.DEFAULT)
    val iv = IvParameterSpec(ivKey.toByteArray())
    val keySpec = SecretKeySpec(secretKey.toByteArray(), "AES")
    val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.DECRYPT_MODE, keySpec, iv)
    val output = cipher.doFinal(decodedByte)
    
    return String(output)
}