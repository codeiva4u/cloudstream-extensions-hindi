
package com.hexated

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SoraStreamPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(SoraStream())
        registerMainAPI(SoraStreamLite())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(MMovies())
        registerExtractorAPI(Hdmovies4u())
        registerExtractorAPI(Dotmovies())
    }
}
