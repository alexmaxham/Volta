package com.alexmaxham.volta.bench

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Browser
import android.provider.MediaStore
import com.alexmaxham.volta.core.BenchStep
import com.alexmaxham.volta.core.Benchmark

/** Something the runner does inside an app. Positions are fractions of the screen. */
sealed interface Move {
    data class Swipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val ms: Long = 300) : Move

    data class Tap(val x: Float, val y: Float) : Move

    /**
     * Taps the first control whose label contains one of [words] (in order of preference), or is
     * exactly one of them when [exact] is set. Taps [x], [y] if none does, or nothing without them.
     */
    data class TapLabel(
        val words: List<String>,
        val x: Float? = null,
        val y: Float? = null,
        val avoid: List<String> = emptyList(),
        val exact: Boolean = false,
    ) : Move

    /** Opens [url] in the app, replacing what it showed. */
    data class Open(val url: String) : Move
}

/**
 * How the runner drives one app: how to open it, a few [start] moves once it's open, then [loop]
 * moves one at a time every [every] ms, and [leave] moves before switching to the next app.
 * [packages] are the apps the step may show; the runner brings the step back when something else
 * stays in front, and closes them when the step ends so the next round starts fresh.
 *
 * For apps that play video or music, [play] moves run whenever nothing is playing, and playback
 * is paused before leaving. [ads] are labels of buttons that close an ad; for apps whose own
 * screens have no labelled buttons, like games, the runner taps those instead of its usual moves
 * and holds off while some other labelled button is on screen. When none of the [active] labels
 * is on screen, the step has dropped out of what it was doing, such as a call, and the runner
 * goes through [start] again. [walk] has the runner walk a test location along [MockWalk]'s
 * route while the step runs, opening [open] again for each walk.
 */
class Workload(
    val packages: Set<String>,
    val open: Intent,
    val start: List<Move> = emptyList(),
    val loop: List<Move> = emptyList(),
    val every: Long = 0,
    val leave: List<Move> = emptyList(),
    val play: List<Move> = emptyList(),
    val ads: List<String> = emptyList(),
    val active: List<String> = emptyList(),
    val walk: Boolean = false,
) {
    val media: Boolean get() = play.isNotEmpty()
}

object Workloads {
    const val CHROME = "com.android.chrome"
    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"
    val TIKTOK = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    const val X = "com.twitter.android"
    const val YT_MUSIC = "com.google.android.apps.youtube.music"
    const val SUBWAY = "com.kiloo.subwaysurf"
    const val MAPS = "com.google.android.apps.maps"

    /** Google Meet, which took over Duo's app, then the older Meet app. */
    val MEET = listOf("com.google.android.apps.tachyon", "com.google.android.apps.meetings")

    /** Walking directions to Grand Central, from wherever the phone is: Penn Station, during the walk. */
    const val NAVIGATE = "google.navigation:q=Grand+Central+Terminal,+New+York,+NY&mode=w"

    /** The same route as a preview, for when Volta can't set a test location. */
    const val ROUTE = "https://www.google.com/maps/dir/?api=1&origin=Penn+Station,+New+York,+NY" +
        "&destination=Grand+Central+Terminal,+New+York,+NY&travelmode=walking"

    /**
     * The same pages every run, so results compare between phones. Chrome reads each for five
     * minutes, so the twelve fill the hour. Long reads sit between news and sports pages so the
     * scrolling doesn't run out: books, and Wikipedia in its desktop layout, because the mobile
     * one folds each section away.
     */
    val PAGES = listOf(
        "https://www.bbc.com/news",
        "https://en.wikipedia.org/w/index.php?title=World_War_II&mobileaction=toggle_view_desktop",
        "https://www.theverge.com",
        "https://www.gutenberg.org/cache/epub/1342/pg1342-images.html",
        "https://www.espn.com",
        "https://en.wikipedia.org/w/index.php?title=Lithium-ion_battery&mobileaction=toggle_view_desktop",
        "https://www.imdb.com/chart/top/",
        "https://en.wikipedia.org/w/index.php?title=History_of_the_Internet&mobileaction=toggle_view_desktop",
        "https://www.cnn.com",
        "https://www.gutenberg.org/cache/epub/2701/pg2701-images.html",
        "https://github.com/trending",
        "https://en.wikipedia.org/w/index.php?title=Smartphone&mobileaction=toggle_view_desktop",
    )

    /** Scrolls six seconds apart before Chrome opens the next page: five minutes a page. */
    private const val SCROLLS_PER_PAGE = 49

    /** Chrome tags the tab it opens for this id and reuses it, so pages don't pile up as new tabs. */
    private const val TAB_ID = "com.alexmaxham.volta.bench"

    /** A short scroll through a feed, and a full-page swipe for one-video-at-a-time apps. */
    private val SCROLL = Move.Swipe(0.5f, 0.72f, 0.5f, 0.32f, 350)
    private val NEXT_VIDEO = Move.Swipe(0.5f, 0.8f, 0.5f, 0.2f, 250)

    /** The middle of YouTube's player, which sits at the top of the screen in portrait. */
    private const val PLAYER_Y = 0.16f

    /** Height of Subway Surfers' sideways swipes: below the Continue? prompt and clear of the results screen's buttons. */
    private const val SUBWAY_SWIPE_Y = 0.76f

    /** Buttons that end an ad in a game, most specific first. */
    private val SUBWAY_ADS = listOf("reward granted", "close ad", "skip ad", "skip video", "close", "skip", "no thanks")

    /** The button that ends a call; while it's on screen, the call is still going. */
    private val LEAVE_CALL = listOf("leave call", "end call", "hang up", "leave meeting")

    private val RECORD = listOf("start recording", "start video", "record video", "begin recording", "record", "shutter")
    private val STOP = listOf("stop recording", "stop video", "stop")

    /** What a step needs installed, for the setup screen. */
    fun installed(context: Context, id: String): Boolean = packagesFor(context, id).isNotEmpty()

    fun hint(id: String): String = when (id) {
        "chrome" -> "Opens ${PAGES.size} set web pages in turn and scrolls each one"
        "youtube" -> "Plays the same video every run"
        "instagram" -> "Scrolls the home feed every 5 seconds"
        "camera" -> "Records video in your camera app. Set it to 4K 60 fps first"
        "tiktok" -> "Swipes to the next video every 10 seconds"
        "x" -> "Scrolls the home timeline every 6 seconds"
        "ytmusic" -> "Plays the same playlist every run, with the screen on"
        "subway" -> "Plays with swipes and taps every second or so"
        "maps" -> "Navigates on foot from Penn Station to Grand Central, again and again"
        "meet" -> "Starts a video call on its own with the camera on"
        else -> ""
    }

    /** Whether a step takes a link to play. */
    fun takesLink(id: String): Boolean = id == "youtube" || id == "ytmusic"

    private fun packagesFor(context: Context, id: String): List<String> {
        val pm = context.packageManager
        fun has(pkg: String) = pm.getLaunchIntentForPackage(pkg) != null
        return when (id) {
            "chrome" -> listOf(CHROME).filter(::has)
            "youtube" -> listOf(YOUTUBE).filter(::has)
            "instagram" -> listOf(INSTAGRAM).filter(::has)
            "camera" -> listOfNotNull(camera(context))
            "tiktok" -> TIKTOK.filter(::has)
            "x" -> listOf(X).filter(::has)
            "ytmusic" -> listOf(YT_MUSIC).filter(::has)
            "subway" -> listOf(SUBWAY).filter(::has)
            "maps" -> listOf(MAPS).filter(::has)
            "meet" -> MEET.filter(::has)
            else -> emptyList()
        }
    }

    /** The phone's default video camera app. */
    private fun camera(context: Context): String? =
        context.packageManager.resolveActivity(Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }

    fun view(url: String, pkg: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg).putExtra(Browser.EXTRA_APPLICATION_ID, TAB_ID)

    /** How to run [step] on this phone, or null when its app isn't installed. */
    fun of(context: Context, step: BenchStep): Workload? {
        val pkgs = packagesFor(context, step.id)
        val pkg = pkgs.firstOrNull() ?: return null
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: Intent()
        val link = (step.link.trim().takeIf { it.startsWith("https://") } ?: Benchmark.defaultLink(step.id)).takeIf { it.isNotBlank() }
        return when (step.id) {
            "chrome" -> Workload(
                setOf(pkg), view(PAGES.first(), pkg),
                // Five minutes a page: scrolls six seconds apart, then the next page.
                loop = PAGES.drop(1).plus(PAGES.first()).flatMap { List(SCROLLS_PER_PAGE) { SCROLL } + Move.Open(it) },
                every = 6_000,
            )
            "youtube" -> Workload(
                setOf(pkg), link?.let { view(it, pkg) } ?: launch,
                // A tap on the player shows its controls; then press play, in the middle of the player.
                play = listOf(Move.Tap(0.5f, PLAYER_Y), Move.TapLabel(listOf("play video", "play"), 0.5f, PLAYER_Y, exact = true)),
            )
            "instagram" -> Workload(setOf(pkg), launch, loop = listOf(SCROLL), every = 5_000)
            "camera" -> Workload(
                setOf(pkg),
                Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA).setPackage(pkg),
                start = listOf(Move.TapLabel(RECORD, 0.5f, 0.87f, avoid = listOf("stop", "settings", "timer", "mode"))),
                leave = listOf(Move.TapLabel(STOP, 0.5f, 0.87f)),
            )
            "tiktok" -> Workload(pkgs.toSet(), launch, loop = listOf(NEXT_VIDEO), every = 10_000)
            "x" -> Workload(setOf(pkg), launch, loop = listOf(SCROLL), every = 6_000)
            "ytmusic" -> Workload(
                setOf(pkg), link?.let { view(it, pkg) } ?: launch,
                play = listOf(Move.TapLabel(listOf("play", "shuffle", "play all"), exact = true)),
            )
            "subway" -> Workload(
                setOf(pkg), launch,
                // Swipes dodge left, right, up and down. They stay below the Continue? prompt after a
                // crash, whose buttons spend a key or play an ad; left alone, it times out. Then the
                // results screen's green Play button, bottom right, starts the next run. Tapping
                // there mid-run or over the prompt does nothing.
                start = listOf(Move.Tap(0.5f, 0.6f)),
                loop = List(2) {
                    listOf(
                        Move.Swipe(0.65f, SUBWAY_SWIPE_Y, 0.3f, SUBWAY_SWIPE_Y, 120),
                        Move.Swipe(0.5f, 0.9f, 0.5f, 0.74f, 120),
                        Move.Swipe(0.35f, SUBWAY_SWIPE_Y, 0.7f, SUBWAY_SWIPE_Y, 120),
                        Move.Swipe(0.5f, 0.74f, 0.5f, 0.9f, 120),
                    )
                }.flatten() + Move.Tap(0.8f, 0.95f),
                every = 1_200,
                ads = SUBWAY_ADS,
            )
            "maps" -> {
                val walk = MockWalk.allowed(context)
                Workload(
                    setOf(pkg),
                    if (walk) Intent(Intent.ACTION_VIEW, Uri.parse(NAVIGATE)).setPackage(pkg) else view(ROUTE, pkg),
                    // Navigation shows a safety note the first time; then it needs nothing.
                    start = listOf(Move.TapLabel(listOf("got it", "ok", "continue"), exact = true)),
                    leave = listOf(Move.TapLabel(listOf("close navigation", "exit navigation", "end navigation"))),
                    walk = walk,
                )
            }
            "meet" -> Workload(
                pkgs.toSet(), launch,
                // New → Create a new meeting starts a call with only this phone in it; then join
                // and close the sheet offering to share the link.
                start = listOf(
                    Move.TapLabel(listOf("new", "new meeting"), exact = true),
                    Move.TapLabel(listOf("create a new meeting", "start an instant meeting", "instant meeting", "create meeting", "start meeting")),
                    Move.TapLabel(listOf("join now", "join", "start"), exact = true),
                    Move.TapLabel(listOf("close", "dismiss", "got it"), exact = true),
                ),
                leave = listOf(Move.TapLabel(LEAVE_CALL)),
                active = LEAVE_CALL,
            )
            else -> null
        }
    }
}
