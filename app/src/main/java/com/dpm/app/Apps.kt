package com.dpm.app

import android.content.pm.ApplicationInfo

class AppDef(val pkg: String, val type: Int, val details: String? = null, val auto: Boolean = true)

object Apps {
    private val catalog = listOf(
        AppDef("com.google.android.youtube", 3),
        AppDef("com.google.android.apps.youtube.kids", 3),
        AppDef("com.netflix.mediaclient", 3),
        AppDef("tv.twitch.android.app", 3),
        AppDef("com.disney.disneyplus", 3),
        AppDef("com.amazon.avod.thirdpartyclient", 3),
        AppDef("com.wbd.stream", 3),
        AppDef("com.hulu.plus", 3),
        AppDef("com.crunchyroll.crunchyroid", 3),
        AppDef("com.cbs.app", 3),
        AppDef("com.peacocktv.peacockandroid", 3),
        AppDef("com.plexapp.android", 3),
        AppDef("org.videolan.vlc", 3),
        AppDef("com.mxtech.videoplayer.ad", 3),

        AppDef("com.spotify.music", 2, auto = false),
        AppDef("com.google.android.apps.youtube.music", 2, auto = false),
        AppDef("com.apple.android.music", 2, auto = false),
        AppDef("deezer.android.app", 2, auto = false),
        AppDef("com.soundcloud.android", 2, auto = false),
        AppDef("com.aspiro.tidal", 2, auto = false),
        AppDef("com.amazon.mp3", 2, auto = false),
        AppDef("com.pandora.android", 2, auto = false),
        AppDef("com.audiomack", 2, auto = false),

        AppDef("com.instagram.android", 0, "Scrolling"),
        AppDef("com.instagram.barcelona", 0, "Scrolling Threads"),
        AppDef("com.zhiliaoapp.musically", 0, "Scrolling"),
        AppDef("com.ss.android.ugc.trill", 0, "Scrolling"),
        AppDef("com.snapchat.android", 0, "Snapping"),
        AppDef("com.reddit.frontpage", 0, "Browsing"),
        AppDef("com.twitter.android", 0, "Scrolling"),
        AppDef("com.facebook.katana", 0, "Scrolling"),
        AppDef("com.pinterest", 0, "Browsing"),
        AppDef("com.tumblr", 0, "Browsing"),
        AppDef("com.bereal.ft", 0, "BeReal-ing"),

        AppDef("com.amazon.kindle", 0, "Reading"),
        AppDef("com.google.android.apps.books", 0, "Reading"),
        AppDef("com.duolingo", 0, "Learning"),

        AppDef("com.android.chrome", 0, "Browsing the web", auto = false),
        AppDef("org.mozilla.firefox", 0, "Browsing the web", auto = false),
        AppDef("com.google.android.apps.maps", 0, "Navigating", auto = false),
        AppDef("com.waze", 0, "Driving", auto = false),
        AppDef("com.strava", 0, "Tracking activity", auto = false)
    )

    private val byPkg = catalog.associateBy { it.pkg }

    fun def(pkg: String): AppDef? = byPkg[pkg]

    fun isYouTube(pkg: String?): Boolean = pkg != null && pkg.startsWith("com.google.android") && pkg.contains("youtube")

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
