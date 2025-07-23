package org.app.core.feature.videodownload

import org.app.core.base.extensions.isElonMuskLink
import org.app.core.base.extensions.isValidDouyinLink
import org.app.core.base.extensions.isValidFBlink
import org.app.core.base.extensions.isValidIGlink
import org.app.core.base.extensions.isValidTTLink
import org.app.core.base.extensions.isValidTwitterLink

enum class SupportLink(val url: String) {
    NONE(""),
    FB("https://m.facebook.com/"),
    IG("https://www.instagram.com/"),
    TT("https://www.tiktok.com/"),
    DOUYIN("https://v.douyin.com/"),
    TWITTER("https://twitter.com/"),
    ;

    fun getTitle() : String {
        return when (this) {
            FB -> "Facebook"
            IG -> "Instagram"
            TT -> "Tiktok, Douyin"
            DOUYIN -> "Tiktok, Douyin"
            TWITTER -> "Twitter and more..."
            NONE -> ""
        }
    }

    fun shortName() : String {
        return when (this) {
            FB -> "FB Save"
            IG -> "IG Save"
            TT -> "Tic toc"
            DOUYIN -> "Doyin"
            TWITTER -> "Twit ter"
            NONE -> ""
        }
    }

    fun getPrefixName() : String {
        val prefix = when (this) {
            FB -> "SocialVideo"
            IG -> "IgVideo_"
            TT -> "TTVideo_"
            DOUYIN -> "Douyin_Video_"
            TWITTER -> "Twitter_Video_"
            NONE -> "Video_"
        }

        return prefix + System.currentTimeMillis()
    }

    companion object {
        fun getLinkType(url: String): SupportLink {
            if (url.isValidFBlink()) return FB

            if (url.isValidIGlink()) return IG

            if (url.isValidTTLink()) return TT

            if (url.isValidDouyinLink()) return DOUYIN

            if (url.isValidTwitterLink() || url.isElonMuskLink()) return TWITTER

            return NONE
        }
    }
}