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
 * and holds off while some other labelled button is on screen. [drive] has the runner move a test
 * location along [MockDrive]'s route while the step runs, opening [open] again for each trip.
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
    val drive: Boolean = false,
) {
    val media: Boolean get() = play.isNotEmpty()
}

object Workloads {
    const val CHROME = "com.android.chrome"
    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"
    val TIKTOK = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    const val X = "com.twitter.android"
    const val SPOTIFY = "com.spotify.music"
    const val SUBWAY = "com.kiloo.subwaysurf"
    const val MAPS = "com.google.android.apps.maps"

    /** Driving directions to Central Park, from wherever the phone is: the World Trade Center, during the drive. */
    const val NAVIGATE = "google.navigation:q=Columbus+Circle,+New+York,+NY&mode=d"

    /** The same route as a preview, for when Volta can't set a test location. */
    const val ROUTE = "https://www.google.com/maps/dir/?api=1&origin=One+World+Trade+Center,+New+York,+NY" +
        "&destination=Columbus+Circle,+New+York,+NY&travelmode=driving"

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
        "spotify" -> "Plays the same playlist every run, with the screen on. Works without Premium, with ads"
        "subway" -> "Plays with swipes and taps every second or so"
        "maps" -> "Drives from the World Trade Center to Central Park at city speed, about 3 trips an hour"
        else -> ""
    }

    /** Whether a step takes a link to play. */
    fun takesLink(id: String): Boolean = id == "youtube" || id == "spotify"

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
            "spotify" -> listOf(SPOTIFY).filter(::has)
            "subway" -> listOf(SUBWAY).filter(::has)
            "maps" -> listOf(MAPS).filter(::has)
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
            "spotify" -> Workload(
                setOf(pkg), link?.let { view(it, pkg) } ?: launch,
                // Free accounts shuffle most playlists, so either play button starts it.
                play = listOf(Move.TapLabel(listOf("play", "shuffle play", "play playlist"), exact = true)),
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
                val drive = MockDrive.allowed(context)
                Workload(
                    setOf(pkg),
                    if (drive) Intent(Intent.ACTION_VIEW, Uri.parse(NAVIGATE)).setPackage(pkg) else view(ROUTE, pkg),
                    // Navigation shows a safety note the first time; then it needs nothing.
                    start = listOf(Move.TapLabel(listOf("got it", "ok", "continue"), exact = true)),
                    leave = listOf(Move.TapLabel(listOf("close navigation", "exit navigation", "end navigation"))),
                    drive = drive,
                )
            }
            else -> null
        }
    }
}
