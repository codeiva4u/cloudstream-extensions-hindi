package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import org.jsoup.Jsoup

val session = Session(Requests().baseClient)

object SoraExtractor : SoraStream() {

    suspend fun invokeMultimovies(
        apiUrl: String,
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$apiUrl/movies/$fixTitle"
        } else {
            "$apiUrl/episodes/$fixTitle-${season}x${episode}"
        }
        val req = app.get(url)
        val directUrl = getBaseUrl(req.url)
        val iframe = req.document.selectFirst("div.pframe iframe")?.attr("src") ?: return
        if (!iframe.contains("youtube")) {
            loadExtractor(iframe, "$directUrl/", subtitleCallback) { link ->
                if (link.quality == Qualities.Unknown.value) {
                    callback.invoke(
                        ExtractorLink(
                            link.source,
                            link.name,
                            link.url,
                            link.referer,
                            Qualities.P1080.value,
                            link.type,
                            link.headers,
                            link.extractorData
                        )
                    )
                }
            }
        }
    }

    suspend fun invokeNetmovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$netmoviesAPI/movies/$fixTitle-$year"
        } else {
            "$netmoviesAPI/episodes/$fixTitle-${season}x${episode}"
        }
        invokeWpmovies(null, url, subtitleCallback, callback)
    }

    suspend fun invokeZshow(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$zshowAPI/movie/$fixTitle-$year"
        } else {
            "$zshowAPI/episode/$fixTitle-season-$season-episode-$episode"
        }
        invokeWpmovies("ZShow", url, subtitleCallback, callback, encrypt = true)
    }

    suspend fun invokeMMovies(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$mMoviesAPI/movies/$fixTitle"
        } else {
            "$mMoviesAPI/episodes/$fixTitle-${season}x${episode}"
        }

        invokeWpmovies(
            null,
            url,
            subtitleCallback,
            callback,
            true,
            hasCloudflare = true,
        )
    }

    private suspend fun invokeWpmovies(
        name: String? = null,
        url: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        fixIframe: Boolean = false,
        encrypt: Boolean = false,
        hasCloudflare: Boolean = false,
        interceptor: Interceptor? = null,
    ) {
        fun String.fixBloat(): String {
            return this.replace("\"", "").replace("\\", "")
        }

        val res = app.get(url ?: return, interceptor = if (hasCloudflare) interceptor else null)
        val referer = getBaseUrl(res.url)
        val document = res.document
        document.select("ul#playeroptionsul > li").map {
            Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type"))
        }.apmap { (id, nume, type) ->
            delay(1000)
            val json = app.post(
                url = "$referer/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to id,
                    "nume" to nume,
                    "type" to type
                ),
                headers = mapOf("Accept" to "*/*", "X-Requested-With" to "XMLHttpRequest"),
                referer = url,
                interceptor = if (hasCloudflare) interceptor else null
            )
            val source = tryParseJson<ResponseHash>(json.text)?.let {
                when {
                    encrypt -> {
                        val meta = tryParseJson<ZShowEmbed>(it.embed_url)?.meta ?: return@apmap
                        val key = generateWpKey(it.key ?: return@apmap, meta)
                        cryptoAESHandler(it.embed_url, key.toByteArray(), false)?.fixBloat()
                    }

                    fixIframe -> Jsoup.parse(it.embed_url).select("IFRAME").attr("SRC")
                    else -> it.embed_url
                }
            } ?: return@apmap
            when {
                !source.contains("youtube") -> {
                    loadCustomExtractor(name, source, "$referer/", subtitleCallback, callback)
                }
            }
        }
    }

    suspend fun invokeDotmovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        invokeWpredis(
            title,
            year,
            season,
            lastSeason,
            episode,
            subtitleCallback,
            callback,
            dotmoviesAPI
        )
    }

    suspend fun invokeVegamovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        invokeWpredis(
            title,
            year,
            season,
            lastSeason,
            episode,
            subtitleCallback,
            callback,
            vegaMoviesAPI
        )
    }

    private suspend fun invokeWpredis(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        api: String
    ) {
        val (seasonSlug, _) = getEpisodeSlug(season, episode)
        var res = app.get("$api/search/$title").document
        val match = when (season) {
            null -> "$year"
            1 -> "Season 1"
            else -> "Season 1 – $lastSeason"
        }
        val media =
            res.selectFirst("div.blog-items article:has(h3.entry-title:matches((?i)$title.*$match)) a")
                ?.attr("href")

        res = app.get(media ?: return).document
        val hTag = if (season == null) "h5" else "h3"
        val aTag = if (season == null) "Download Now" else "V-Cloud"
        val sTag = if (season == null) "" else "(Season $season|S$seasonSlug)"
        val entries = res.select("div.entry-content > $hTag:matches((?i)$sTag.*(1080p|2160p))")
            .filter { element -> !element.text().contains("Download", true) }.takeLast(2)
        entries.apmap {
            val tags =
                """(?:1080p|2160p)(.*)""".toRegex().find(it.text())?.groupValues?.get(1)?.trim()
            val href = it.nextElementSibling()?.select("a:contains($aTag)")?.attr("href")
            val selector =
                if (season == null) "p a:contains(V-Cloud)" else "h4:matches(0?$episode) + p a:contains(V-Cloud)"
            val server = app.get(
                href ?: return@apmap, interceptor = wpRedisInterceptor
            ).document.selectFirst("div.entry-content > $selector")
                ?.attr("href") ?: return@apmap

            loadCustomTagExtractor(
                tags,
                server,
                "$api/",
                subtitleCallback,
                callback,
                getIndexQuality(it.text())
            )
        }
    }

    suspend fun invokeHdmovies4u(
        title: String? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        fun String.decodeLink(): String {
            return base64Decode(this.substringAfterLast("/"))
        }
        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
        val media =
            app.get("$hdmovies4uAPI/?s=${if (season == null) imdbId else title}").document.let {
                val selector = if (season == null) "a" else "a:matches((?i)$title.*Season $season)"
                it.selectFirst("div.gridxw.gridxe $selector")?.attr("href")
            }
        val selector = if (season == null) "1080p|2160p" else "(?i)Episode.*(?:1080p|2160p)"
        app.get(media ?: return).document.select("section h4:matches($selector)").apmap { ele ->
            val (tags, size) = ele.select("span").map {
                it.text()
            }.let { it[it.lastIndex - 1] to it.last().substringAfter("-").trim() }
            val link = ele.nextElementSibling()?.select("a:contains(DriveTOT)")?.attr("href")
            val iframe = bypassBqrecipes(link?.decodeLink() ?: return@apmap).let {
                if (it?.contains("/pack/") == true) {
                    val href =
                        app.get(it).document.select("table tbody tr:contains(S${seasonSlug}E${episodeSlug}) a")
                            .attr("href")
                    bypassBqrecipes(href.decodeLink())
                } else {
                    it
                }
            }
            invokeDrivetot(iframe ?: return@apmap, tags, size, subtitleCallback, callback)
        }
    }

    suspend fun invokeFDMovies(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$fdMoviesAPI/movies/$fixTitle"
        } else {
            "$fdMoviesAPI/episodes/$fixTitle-s${season}xe${episode}/"
        }

        val request = app.get(url)
        if (!request.isSuccessful) return

        val iframe = request.document.select("div#download tbody tr").map {
            FDMovieIFrame(
                it.select("a").attr("href"),
                it.select("strong.quality").text(),
                it.select("td:nth-child(4)").text(),
                it.select("img").attr("src")
            )
        }.filter {
            it.quality.contains(Regex("(?i)(1080p|4k)")) && it.type.contains(Regex("(gdtot|oiya|rarbgx)"))
        }
        iframe.apmap { (link, quality, size, type) ->
            val qualities = getFDoviesQuality(quality)
            val fdLink = bypassFdAds(link)
            val videoLink = when {
                type.contains("gdtot") -> {
                    val gdBotLink = extractGdbot(fdLink ?: return@apmap null)
                    extractGdflix(gdBotLink ?: return@apmap null)
                }

                type.contains("oiya") || type.contains("rarbgx") -> {
                    val oiyaLink = extractOiya(fdLink ?: return@apmap null)
                    if (oiyaLink?.contains("gdtot") == true) {
                        val gdBotLink = extractGdbot(oiyaLink)
                        extractGdflix(gdBotLink ?: return@apmap null)
                    } else {
                        oiyaLink
                    }
                }

                else -> {
                    return@apmap null
                }
            }

            callback.invoke(
                ExtractorLink(
                    "FDMovies", "FDMovies [$size]", videoLink
                        ?: return@apmap null, "", getQualityFromName(qualities)
                )

            )
        }

    }
}
