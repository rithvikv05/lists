package com.minimal.lists.data.api

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Direct CDN requests stay fast; the cached engine relay handles unreachable hosts. */
object CoverHttpClient {
    private val hosts = setOf(
        "images.igdb.com", "image.tmdb.org", "covers.openlibrary.org",
        "books.google.com", "books.googleusercontent.com", "m.media-amazon.com",
        "cdn.myanimelist.net", "media.kitsu.app", "media.kitsu.io", "uploads.mangadex.org",
        "static.tvmaze.com", "images-na.ssl-images-amazon.com", "images.gr-assets.com",
        "i.gr-assets.com", "cdn.hardcover.app", "assets.hardcover.app", "upload.wikimedia.org", "thumb.wikimedia.org", "inventaire.io"
    )

    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val host = original.url.host
            val supported = host in hosts || listOf("mzstatic.com", "steamstatic.com", "steamcdn-a.akamaihd.net")
                .any { host == it || host.endsWith(".$it") }
            val relay = SearchRepository.remoteEngineUrl.trimEnd('/').removeSuffix("/v2").removeSuffix("/v1")
                .let { "$it/v2/cover" }.toHttpUrlOrNull()
            val canRelay = supported && original.url.isHttps && relay != null
            val direct = try {
                chain.proceed(original)
            } catch (error: IOException) {
                if (!canRelay || chain.call().isCanceled()) throw error
                null
            }
            if (!canRelay || direct?.isSuccessful == true || chain.call().isCanceled()) {
                direct ?: throw IOException("Cover request cancelled")
            } else {
                direct?.close()
                val fallback = Request.Builder().url(relay!!.newBuilder()
                    .addQueryParameter("url", original.url.toString()).build()).build()
                chain.withConnectTimeout(5, TimeUnit.SECONDS)
                    .withReadTimeout(10, TimeUnit.SECONDS).proceed(fallback)
            }
        }
        .build()
}
