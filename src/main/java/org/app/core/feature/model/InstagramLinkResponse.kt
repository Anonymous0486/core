package org.app.core.feature.model

data class InstagramLinkResponse(
    var status : Boolean,
    var code : Long?= 0,
    var msg : String?= "",
    var response : DownloadLinkResponse?
)
