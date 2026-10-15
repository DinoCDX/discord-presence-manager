package com.dpm.app

import android.content.pm.ApplicationInfo

class AppDef(val pkg: String, val type: Int, val details: String? = null, val auto: Boolean = true, val icon: String? = null)

object Apps {
    private fun dash(slug: String) = "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/png/$slug.png"

    private val catalog = listOf(
        AppDef("com.google.android.youtube", 3, icon = dash("youtube")),
        AppDef("com.google.android.apps.youtube.kids", 3, icon = dash("youtube-kids")),
        AppDef("com.netflix.mediaclient", 3, icon = dash("netflix")),
        AppDef("tv.twitch.android.app", 3, icon = dash("twitch")),
        AppDef("com.disney.disneyplus", 3, icon = dash("disney-plus")),
        AppDef("com.amazon.avod.thirdpartyclient", 3, icon = dash("amazon-prime-video")),
        AppDef("com.wbd.stream", 3),
        AppDef("com.hulu.plus", 3, icon = dash("hulu")),
        AppDef("com.crunchyroll.crunchyroid", 3, icon = dash("crunchyroll")),
        AppDef("com.cbs.app", 3),
        AppDef("com.peacocktv.peacockandroid", 3),
        AppDef("com.plexapp.android", 3, icon = dash("plex")),
        AppDef("org.videolan.vlc", 3, icon = dash("vlc")),
        AppDef("com.mxtech.videoplayer.ad", 3),
        AppDef("com.spotify.music", 2, auto = false, icon = dash("spotify")),
        AppDef("com.google.android.apps.youtube.music", 2, auto = false, icon = dash("youtube-music")),
        AppDef("com.apple.android.music", 2, auto = false, icon = dash("apple-music")),
        AppDef("deezer.android.app", 2, auto = false, icon = dash("deezer")),
        AppDef("com.soundcloud.android", 2, auto = false, icon = dash("soundcloud")),
        AppDef("com.aspiro.tidal", 2, auto = false, icon = dash("tidal")),
        AppDef("com.amazon.mp3", 2, auto = false),
        AppDef("com.pandora.android", 2, auto = false, icon = dash("pandora")),
        AppDef("com.audiomack", 2, auto = false),
        AppDef("com.instagram.android", 0, "Scrolling", icon = dash("instagram")),
        AppDef("com.instagram.barcelona", 0, "Scrolling Threads"),
        AppDef("com.zhiliaoapp.musically", 0, "Scrolling", icon = dash("tiktok")),
        AppDef("com.ss.android.ugc.trill", 0, "Scrolling"),
        AppDef("com.snapchat.android", 0, "Snapping", icon = dash("snapchat")),
        AppDef("com.reddit.frontpage", 0, "Browsing", icon = dash("reddit")),
        AppDef("com.twitter.android", 0, "Scrolling", icon = dash("twitter")),
        AppDef("com.facebook.katana", 0, "Scrolling", icon = dash("facebook")),
        AppDef("com.pinterest", 0, "Browsing", icon = dash("pinterest")),
        AppDef("com.tumblr", 0, "Browsing", icon = dash("tumblr")),
        AppDef("com.bereal.ft", 0, "BeReal-ing"),
        AppDef("com.amazon.kindle", 0, "Reading", icon = dash("amazon-kindle")),
        AppDef("com.google.android.apps.books", 0, "Reading"),
        AppDef("com.duolingo", 0, "Learning", icon = dash("duolingo")),
        AppDef("com.android.chrome", 0, "Browsing the web", auto = false, icon = dash("google-chrome")),
        AppDef("org.mozilla.firefox", 0, "Browsing the web", auto = false, icon = dash("firefox")),
        AppDef("com.google.android.apps.maps", 0, "Navigating", auto = false, icon = dash("google-maps")),
        AppDef("com.waze", 0, "Driving", auto = false, icon = dash("waze")),
        AppDef("com.strava", 0, "Tracking activity", auto = false, icon = dash("strava"))
    )

    private val byPkg = catalog.associateBy { it.pkg }

    fun def(pkg: String): AppDef? = byPkg[pkg]

    fun typeFor(pkg: String, category: Int): Int =
        byPkg[pkg]?.type ?: when (category) {
            ApplicationInfo.CATEGORY_VIDEO -> 3
            ApplicationInfo.CATEGORY_AUDIO -> 2
            else -> 0
        }

    fun isWatching(pkg: String, category: Int): Boolean =
        byPkg[pkg]?.type == 3 || category == ApplicationInfo.CATEGORY_VIDEO

    fun autoShow(pkg: String, category: Int): Boolean {
        byPkg[pkg]?.let { return it.auto }
        return category == ApplicationInfo.CATEGORY_VIDEO || category == ApplicationInfo.CATEGORY_SOCIAL
    }
}
