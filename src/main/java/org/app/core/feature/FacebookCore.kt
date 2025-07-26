package org.app.core.feature

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.app.core.feature.model.MediaMergeWorker
import java.util.concurrent.TimeUnit

interface FacebookCoreInterface {
    fun mergeMedia(
        context: Context,
        videoPath: String,
        audioPath: String,
        outputPath: String,
        workerId: Long)

    fun getDomParseJs() : String

    fun getFBVideoParseJs() : String

    fun getStoryParseJs() : String
}

class FacebookCore : FacebookCoreInterface {
    
    override fun mergeMedia(
        context: Context,
        videoPath: String,
        audioPath: String,
        outputPath: String,
        workerId: Long) {
        
        val workerName = "media_merge_worker_$workerId"
        val inputData = Data.Builder()
            .putString("key_audio_path", audioPath)
            .putString("key_video_path", videoPath)
            .putString("key_output_path", outputPath)
            .putString("key_worker_name", workerName)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<MediaMergeWorker>()
            .setInputData(inputData)
            .addTag("Media_Merge")
            .setInitialDelay(1, TimeUnit.SECONDS)
            .build()
    
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            workerName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    override fun getDomParseJs(): String = jsDomParsing

    override fun getFBVideoParseJs(): String = jsFbVideoParsing

    override fun getStoryParseJs(): String = jsFbStoryParsing

    companion object {
        val jsDomParsing = "var divs = document.getElementsByTagName(\"div\");\n" +
                "var noVideo = true;\n" +
                "var thumbnail = '';\n" +
                "var uri = '';\n" +
                "for (let i = 0; i < divs.length; i++) {\n" +
                "  const div = divs[i];\n" +
                "  let dataElement;\n" +
                "  if (div.hasAttribute('data-extra')) {\n" +
                "    dataElement = div.attributes['data-extra'].textContent\n" +
                "  } else if (div.hasAttribute('data-store')) {\n" +
                "    dataElement = div.attributes['data-store'].textContent\n" +
                "  }\n" +
                "\n" +
                "  if (dataElement) {\n" +
                "    const parser = new DOMParser();\n" +
                "    let dashManifest = JSON.parse(dataElement).dash_manifest;\n" +
                "    if(!dashManifest) {\n" +
                "      dashManifest = JSON.parse(dataElement).dashManifest;\n" +
                "    }\n" +
                "    if (dashManifest) {\n" +
                "      const images = div.getElementsByClassName(\"img\");\n" +
                "        if (images) {\n" +
                "            if(images[0].src) {\n" +
                "                thumbnail = images[0].src;\n" +
                "            } else {\n" +
                "                thumbnail = images[0].style.backgroundImage.slice(5, -2);\n" +
                "            }\n" +
                "      }\n" +
                "      var newDoc = parser.parseFromString(dashManifest, \"text/xml\");\n" +
                "      const mpdElement = newDoc.getElementsByTagName(\"MPD\");\n" +
                "      if (mpdElement) {\n" +
                "        const periodElement = mpdElement[0].getElementsByTagName(\"Period\");\n" +
                "        if (periodElement) {\n" +
                "          const adaptation = periodElement[0].getElementsByTagName(\"AdaptationSet\");\n" +
                "          for (let j = 0; j < adaptation.length; j++) {\n" +
                "            const adapItem = adaptation[j];\n" +
                "            const representations = adapItem.getElementsByTagName(\"Representation\");\n" +
                "            for (let k = 0; k < representations.length; k++) {\n" +
                "              const mineType = representations[k].getAttribute('mimeType');\n" +
                "              const format = representations[k].getAttribute('FBQualityClass');\n" +
                "              const qualityLabel = representations[k].getAttribute('FBQualityLabel');\n" +
                "              const baseUrl = representations[k].getElementsByTagName(\"BaseURL\")[0].textContent;\n" +
                "              mJava.onVideoClicked(mineType, format, qualityLabel, baseUrl);\n" +
                "            }\n" +
                "          }\n" +
                "          noVideo = false;\n" +
                "          uri = div.baseURI;\n" +
                "          break;\n" +
                "        }\n" +
                "      }\n" +
                "    } else {\n" +
                "      if (div.hasAttribute('data-video-url')) {\n" +
                "        const images = div.getElementsByClassName(\"img\");\n" +
                "        if (images) {\n" +
                "            if(images[0].src) {\n" +
                "                thumbnail = images[0].src;\n" +
                "            } else {\n" +
                "                thumbnail = images[0].style.backgroundImage.slice(5, -2);\n" +
                "            }\n" +
                "        }\n" +
                "        uri = div.baseURI;\n" +
                "        mJava.onVideoClicked('video/mp4', 'hd', '', div.getAttribute('data-video-url'));\n" +
                "        break;\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "mJava.onComplete(thumbnail, uri);"

        val jsFbVideoParsing = "var ic_download_base64 = \"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAKrSURBVHgB7Zuxb1JBHMe/vwMKGIw4CZMMkrpRNxNTQzddrItuuunWxN2hT5Nq4iThD6hx1aHtYBMnEt2ho5N0qmONpgVJ77w7iiWkEIbjnuF3n+SFe+EF+H3u3v1+vORHGOFmdFxNkrovgVUilDAPKLRA1EpJvGhE2fbwWzQYVCOV74njdT18hvnmbUp2tYjLh+bECugHf9TQpxVwQK+IlOquGAnCnPdnnknwBsJST6TX7VDf86WEUN/BEJK0IhIkIzBF6s1eQIDP0h9BZ7lVAUVL4EtJgDlBAJgTBIA5QQCYEwSAOUEAmBOrgPIVgUKeECdJxIQJfPNp2o7vvOngd1chDmJbAcVLZ19dLsS3CsIeAOYEAWBOEADmBAHwxLRVXy5DXqtDLwLKBWGrvndP0lbEOK7pgujDWgYf9XHjqp+58fItufTpq57d+uOFcyWY4OuPMriY6Z8f/PRTGnsR0NyX2Nj5Y8f/JAyVv2aFDAf/Sl/749CPALr18sjbv5C7lQSe31uYeI0J/tPeCXzhNQvs6sAGK+E8fAdv8J4Gx0mII3iDcwHTpLFRCdMEX8wL+9mucf5AZFOnuqIWsPa+aze/cRgJzX39IKQDfUzehpYXE3j9cAEHemN8UO/AJc4FFE9nv2AfeMiJ10670w/SaHEGBVIohcEc57eAebiZSxNuX9duyc3HLy/Obp6cC2i2pd20BodLdmeQJp2r3djpOf+hv7rAl28nqH3uwTVeS+H/kZAFwJwgAMwJAsCcIADMCQLAnCAAzBFKoQ2uKLT0CpDb4ApRSwiV2AJTTCut+BplG6RkDcwghZrpI7abYFL1IttgzAWl9pIqG5mhFWB6aE0vLYeVYGY+pS5UGxGdNU8PY1tpbTcpVUyPLeYAk+n0TG9D0Za55Yff+wvP7Njo5NjUyQAAAABJRU5ErkJggg==\"; setInterval(function () {\n" +
                "  const divs = document.getElementsByTagName(\"div\"); for (let i = 0; i < divs.length; i++) {\n" +
                "    const div = divs[i];\n" +
                "    if (div.hasAttribute('data-video-url')) {\n" +
                "      const playBtns = div.getElementsByClassName(\"inline-video-icon play\"); if (playBtns.length > 0) {\n" +
                "        div.removeChild(playBtns[0]);\n" +
                "      } const downloadBtn = div.getElementsByClassName(\"fbDownloadBtn\"); if (downloadBtn.length === 0) {\n" +
                "        const img = document.createElement(\"img\"); img.className = \"fbDownloadBtn\"; img.setAttribute(\"src\", ic_download_base64); img.setAttribute(\"style\",\n" +
                "          \"position: absolute; width: 64px; height: 64px; background-size: 64px; margin: 64px 0px 0px 16px; z-index: 9999999999;\"); div.appendChild(img);\n" +
                "          div.onclick = () => {\n" +
                "                    var thumbnail = '';\n" +
                "                    const images = div.getElementsByClassName(\"img\");\n" +
                "                    if (images && images.length > 0) { thumbnail = images[0].src; }\n" +
                "                      onVideoClicked(div.getAttribute('data-video-url'), thumbnail);\n" +
                "                }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}, 1000);\n" +
                "function onVideoClicked(url, thumbnail) {\n" +
                "  BrowserWB.onVideoClicked(url, thumbnail);\n" +
                "}\n"

        val jsFbStoryParsing = """
            var ic_download_base64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAKrSURBVHgB7Zuxb1JBHMe/vwMKGIw4CZMMkrpRNxNTQzddrItuuunWxN2hT5Nq4iThD6hx1aHtYBMnEt2ho5N0qmONpgVJ77w7iiWkEIbjnuF3n+SFe+EF+H3u3v1+vORHGOFmdFxNkrovgVUilDAPKLRA1EpJvGhE2fbwWzQYVCOV74njdT18hvnmbUp2tYjLh+bECugHf9TQpxVwQK+IlOquGAnCnPdnnknwBsJST6TX7VDf86WEUN/BEJK0IhIkIzBF6s1eQIDP0h9BZ7lVAUVL4EtJgDlBAJgTBIA5QQCYEwSAOUEAmBOrgPIVgUKeECdJxIQJfPNp2o7vvOngd1chDmJbAcVLZ19dLsS3CsIeAOYEAWBOEADmBAHwxLRVXy5DXqtDLwLKBWGrvndP0lbEOK7pgujDWgYf9XHjqp+58fItufTpq57d+uOFcyWY4OuPMriY6Z8f/PRTGnsR0NyX2Nj5Y8f/JAyVv2aFDAf/Sl/749CPALr18sjbv5C7lQSe31uYeI0J/tPeCXzhNQvs6sAGK+E8fAdv8J4Gx0mII3iDcwHTpLFRCdMEX8wL+9mucf5AZFOnuqIWsPa+aze/cRgJzX39IKQDfUzehpYXE3j9cAEHemN8UO/AJc4FFE9nv2AfeMiJ10670w/SaHEGBVIohcEc57eAebiZSxNuX9duyc3HLy/Obp6cC2i2pd20BodLdmeQJp2r3djpOf+hv7rAl28nqH3uwTVeS+H/kZAFwJwgAMwJAsCcIADMCQLAnCAAzBFKoQ2uKLT0CpDb4ApRSwiV2AJTTCut+BplG6RkDcwghZrpI7abYFL1IttgzAWl9pIqG5mhFWB6aE0vLYeVYGY+pS5UGxGdNU8PY1tpbTcpVUyPLeYAk+n0TG9D0Za55Yff+wvP7Njo5NjUyQAAAABJRU5ErkJggg==";
            setInterval(function () {
              const divs = document.getElementsByTagName("div");
              for (let i = 0; i < divs.length; i++) {
                const div = divs[i];
                if (div.hasAttribute('data-video-url')) {
                  const downloadBtn = div.getElementsByClassName("fbDownloadBtn");
                  if (downloadBtn.length === 0) {
                    const img = document.createElement("img");
                    img.className = "fbDownloadBtn";
                    img.setAttribute("src", ic_download_base64);
                    img.setAttribute("style", "position: fixed; width: 64px; height: 64px; background-size: 64px; margin: 64px 0px 0px 16px; z-index: 9999;");
                    div.appendChild(img);
                    var thumbnail = '';
                    const images = div.getElementsByClassName("img");
                    if (images) { thumbnail = images[0].src; }
                    const rootDiv = document.getElementById('screen-root');
                    rootDiv.onclick = () => {
                        console.log("Console Clicked!!!!");
                        onVideoClicked(div.getAttribute('data-video-url'), thumbnail);
                    }
                    img.onclick = () => {
                        onVideoClicked(div.getAttribute('data-video-url'), thumbnail);
                    };
                  }
                }
              }
            }, 1000);
            function onVideoClicked(url, thumbnail) {
              BrowserWB.onStoryClicked(url, thumbnail);
            }
        """.trimIndent()
    }
}