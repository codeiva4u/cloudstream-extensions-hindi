package com.hexated

import com.hexated.SoraExtractor.invokeMultimovies
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

class SoraStreamLite : SoraStream() {
    override var name = "SoraStream-Lite"

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val res = AppUtils.parseJson<LinkData>(data)

        argamap(
            {

            },
            {
                if (res.isBollywood) invokeMultimovies(
                    multimoviesAPI,
                    res.title,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (res.isBollywood) invokeMultimovies(
                    multimovies2API,
                    res.title,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {

            }
        )

        return true
    }

}