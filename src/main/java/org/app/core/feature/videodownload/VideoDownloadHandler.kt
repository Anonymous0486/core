package org.app.core.feature.videodownload

import org.app.core.ads.CoreAds
import org.app.core.feature.datasource.CrawlDataSource
import org.app.core.feature.datasource.FbLinkDataSource
import org.app.core.feature.datasource.SocialVideoDataSource
import org.app.core.feature.model.BaseResponse
import org.app.core.feature.model.ResponseData
import javax.inject.Inject
import org.app.core.feature.videodownload.FBUtil.getFbLink
import org.app.core.feature.videodownload.FBUtil.getThumbnail

interface VideoDownloadInterface {
    suspend fun getLinkBy(url: String, type: SupportLink) : BaseResponse<DownloadInfo>

    suspend fun crawlFDownLinkBy(url: String): BaseResponse<DownloadInfo>

    suspend fun getTWLinks(url: String) : BaseResponse<DownloadInfo>
}

class VideoDownloadHandler @Inject constructor(
    private val fbDataSource: FbLinkDataSource,
    private val socialDataSource: SocialVideoDataSource,
    private val crawlDataSource: CrawlDataSource,
) : VideoDownloadInterface {

    override suspend fun getLinkBy(url: String, type: SupportLink): BaseResponse<DownloadInfo> {
        val response = when(type) {
            SupportLink.FB -> fbDataSource.getLinks(url)
            SupportLink.IG -> socialDataSource.getInstagramLinks(url)
            SupportLink.TT -> socialDataSource.getTiktokLinks(url)
            SupportLink.DOUYIN -> socialDataSource.getDouyinLinks(url)
            SupportLink.TWITTER -> {
                val crawlResult = getTWLinks(url)
                if (crawlResult.result != null && crawlResult.result!!.links.isNotEmpty()) {
                    CoreAds.instance.logFirebaseEvent("crawlTwSuccess")
                    return crawlResult
                } else {
                    CoreAds.instance.logFirebaseEvent("crawlTwFailed")
                    socialDataSource.getTwitterLinks(url)
                }
            }
            else -> null
        }

        var event = ""
        val result =  when (response) {
            is ResponseData.Success -> {
                if (response.value.result != null) {
                    event = eventDownloadLinkSuccess
                    val model = DownloadInfo(response.value.result!!)
                    BaseResponse(status = true, result = model)
                } else {
                    event = eventDownloadLinkFail + "_${type.getTitle()}"
                    BaseResponse(status = false)
                }
            }
            is ResponseData.Failure -> {
                event = eventDownloadLinkFail + "_${type.getTitle()}"
                BaseResponse(status = false, message = response.message)
            }
            else -> {
                BaseResponse(status = false)
            }
        }

        CoreAds.instance.logFirebaseEvent(event)
        return result
    }

    override suspend fun crawlFDownLinkBy(url: String): BaseResponse<DownloadInfo> {
        return when (val response = crawlDataSource.crawlFDownLinks(url)) {
            is ResponseData.Success -> {
                val body = response.value.string()
                val links = mutableListOf<LinkInfo>()
                val thumbnail = getThumbnail(body)?.trim() ?: ""

                val hd: String? = getFbLink(body, true)
                if (!hd.isNullOrBlank()) {
                    links.add(LinkInfo(hd, "hd", "mp4"))
                }

                val sd: String? = getFbLink(body, false)
                if (!sd.isNullOrBlank()) {
                    links.add(LinkInfo(sd, "sd", "mp4"))
                }

                if (links.isEmpty()) {
                    BaseResponse(status = false)
                } else {
                    val result = DownloadInfo(links, thumbnail, SupportLink.FB.getPrefixName() + ".mp4")
                    BaseResponse(status = true, result = result)
                }
            }
            is ResponseData.Failure -> {
                BaseResponse(status = false, message = response.message)
            }
            else -> {
                BaseResponse(status = false)
            }
        }
    }

    override suspend fun getTWLinks(url: String) : BaseResponse<DownloadInfo> {
        val response = crawlDataSource.crawlTwLinks(url)
        if (response is ResponseData.Success) {
            val body = response.value.string()
            val model = TWUtil.fetch(body)

            return if (model == null) {
                BaseResponse(status = false)
            } else {
                BaseResponse(status = true, result = model)
            }
        }

        return BaseResponse(status = false)
    }

    companion object {
        const val eventDownloadLinkSuccess = "DownloadLinkSuccess"
        const val eventDownloadLinkFail = "DownloadLinkFail"
    }
}