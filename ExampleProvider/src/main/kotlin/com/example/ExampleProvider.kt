package com.example

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup
// import org.jsoup.nodes.Document
// import org.jsoup.nodes.Element

class ExampleProvider(val plugin: TestPlugin) : MainAPI() { 
    // all providers must be an instance of MainAPI
    override var mainUrl = "https://sflix.to" 
    override var name = "Mnemosyne"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "en"

    // enable this when your provider has a main page
    override val hasMainPage = true

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val escaped = query.replace(' ', '-')
        val url = "$mainUrl/search/${escaped}"

        return app.get(
            url,
        ).document.select(".flw-item").mapNotNull { article ->
            val name = article.selectFirst("h2 > a")?.text() ?: ""
            val poster = article.selectFirst("img")?.attr("data-src")
            val url = article.selectFirst("a.btn")?.attr("href") ?: ""
            val type = article.selectFirst("strong")?.text() ?: ""

            if (type == "Movie") {
                newMovieSearchResponse(name, url) {
                    posterUrl = poster
                }
            }
            else {
                newTvSeriesSearchResponse(name, url) {
                    posterUrl = poster
                }
            }
        }
    }


    override val mainPage = mainPageOf(
        "tv-show" to "TV Show",
        "movie" to "Movie",
        "top-imdb" to "Top-IMDB",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // page: An integer > 0, starts on 1 and counts up, Depends on how much the user has scrolled.
        val url = "$mainUrl/${request.data}?page=$page"
        var list = mutableListOf<AnimeSearchResponse>()
        val res = app.get(url).document

        res.select(".flw-item").mapNotNull { article ->
            val name = article.selectFirst("h2 > a")?.text() ?: ""
            val poster = article.selectFirst("img")?.attr("data-src")
            val url = article.selectFirst("a.btn")?.attr("href") ?: ""
            
            list.add(newAnimeSearchResponse(name, url){
                this.posterUrl = poster
            })
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = list,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }


    // start with movie easier than tv series
    // this function only displays info about movies and series
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val posterUrl = document.selectFirst("img.film-poster-img")?.attr("src")
        val details = document.select("div.detail_page-watch")
        val img = details.select("img.film-poster-img")
        val title = img.attr("title") ?: throw ErrorLoadingException("No Title")
        val plot = details.select("div.description").text().replace("Overview:", "").trim()
        val rating = document.selectFirst(".fs-item > .imdb")?.text()?.trim()?.removePrefix("IMDB:")?.toRatingInt()
        val isMovie = url.contains("/movie/")

        val idRegex = Regex(""".*-(\d+)""")
        val dataId = details.attr("data-id")
        val id = if (dataId.isNullOrEmpty())
            idRegex.find(url)?.groupValues?.get(1)
                ?: throw ErrorLoadingException("Unable to get id from '$url'")
        else dataId

        // duration  duration = duration ?: element.ownText().trim()
        // Casts cast = element.select("a").mapNotNull { it.text() }
        // genre
        // country
        // production
        // tags = element.select("a").mapNotNull { it.text() }

        // if (isMovie){
        val episodesUrl = "$mainUrl/ajax/episode/list/$id"
        val episodes = app.get(episodesUrl).text

        val sourceIds: List<String> = Jsoup.parse(episodes).select("a").mapNotNull { element ->
            var sourceId: String? = element.attr("data-id")

            if (sourceId.isNullOrEmpty())
                sourceId = element.attr("data-linkid")

            Log.d("mnemo", "sourceId: $sourceId, type: ${sourceId?.javaClass?.name}")
            if (sourceId.isNullOrEmpty()) {
                null
            }
            else{
                "$mainUrl/ajax/episode/sources/$sourceId"
            }
        }
        Log.d("mnemo", sourceIds.toString());        
        val comingSoon = sourceIds.isEmpty()

        return newMovieLoadResponse(title, url, TvType.Movie, sourceIds) {
            this.posterUrl = posterUrl
            this.plot = plot
            this.comingSoon = comingSoon
            this.rating = rating
            // this.year = year
            // addDuration(duration)
            // addActors(cast)
            // this.tags = tags
            // this.recommendations = recommendations
            // addTrailer(youtubeTrailer)
        }

        // }
        // else{
        //     // TV series
        //     null
        // }
    }



    // this function loads the links (upcloud/vidcloud/doodstream)
    // GET /ajax/episode/sources/8982232
    // {"type":"iframe","link":"https://rabbitstream.net","sources":[],"tracks":[],"title":""}
    // GET /ajax/episode/sources/8982235 HTTP/2
    // {"type":"iframe","link":"https://dood.watch/e/xxxxx","sources":[],"tracks":[],"title":""}
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        // /ajax/episode/sources/8982232
        // {"type":"iframe","link":"https://dood.watch/e/xxxxx","sources":[],"tracks":[],"title":""}
        // force the URL in variable ? src="https://dood.watch/e/t2zwohrke3a5"
        // DoodWatchExtractor 
        Log.d("mnemo", "LOADLINKS")
        Log.d("mnemo", data)
        // i -> e

        // val links = app.get(data, interceptor = ddosGuardKiller).document.select("div.h-vw-65 iframe.w-full").attr("src").toString()
        // val file = parseJson<GDFile>(data)
        // val token = getToken()
        // val path = "$downloadApi/tgarchive/${file._id}?token=$token"
        callback(
            ExtractorLink(
                "sflix",
                "DoodWatch",
                "https://dood.watch/e/t2zwohrke3a5",
                "$mainUrl/",
                Qualities.Unknown.value,
            )
        )

        return true
    }

}

/*
ExtractorLink(
    source = name,
    name = name,
    url = it.streamlink!!,
    referer = "$mainUrl/",
    quality = getQualityFromName(it.qualityfile) // getQualityFromName("1080")
    URI(link).path.contains(".m3u") 
)

callback(
    ExtractorLink(
        "AllAnime - " + URI(link).host,
        "",
        link,
        data,
        getQualityFromName("1080"),
        URI(link).path.contains(".m3u")
    )
 */