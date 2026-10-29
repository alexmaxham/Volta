import CoreGraphics
import Foundation

/// Something the runner does inside an app. Positions are fractions of the screen.
enum Move {
    case swipe(CGFloat, CGFloat, CGFloat, CGFloat, seconds: Double)
    case tap(CGFloat, CGFloat)
    /// Taps the first control whose label or identifier contains one of [words] (in order of
    /// preference), or equals it when [exact]. Taps [fallback] if none does, or nothing without one.
    case tapLabel([String], exact: Bool = false, avoid: [String] = [], fallback: CGPoint?)
    /// Opens [url] in the app, replacing what it showed.
    case open(String)
}

/// How the runner drives one app: how to open it, a few [start] moves once it's open, then [loop]
/// moves one at a time every [every] seconds, and [leave] moves before switching to the next app.
struct Workload {
    var bundleId: String
    /// A URL to open the app with, or nil to launch it as it was.
    var url: String?
    var start: [Move] = []
    var loop: [Move] = []
    var every: Double = 0
    var leave: [Move] = []
}

/// The iPhone versions of the Android app benchmark's workloads (app/.../bench/Workloads.kt).
enum Workloads {
    static let chrome = "com.google.chrome.ios"
    static let safari = "com.apple.mobilesafari"
    static let youtube = "com.google.ios.youtube"
    static let instagram = "com.burbn.instagram"
    static let camera = "com.apple.camera"
    static let tiktok = "com.zhiliaoapp.musically"
    static let x = "com.atebits.Tweetie2"
    static let ytMusic = "com.google.ios.youtubemusic"
    static let subway = "com.kiloo.subwaysurfers"

    /// The same pages as on Android, so results compare between phones. Each is read for about a minute.
    static let pages = [
        "https://en.wikipedia.org/wiki/Lithium-ion_battery",
        "https://www.bbc.com/news",
        "https://www.theverge.com",
        "https://en.wikipedia.org/wiki/IOS",
        "https://github.com/trending",
        "https://www.espn.com",
        "https://en.wikipedia.org/wiki/Smartphone",
        "https://www.imdb.com/chart/top/",
    ]

    static let youtubeSearch = "4K nature documentary 1 hour"
    static let musicSearch = "Today's top hits"

    /// A short scroll through a feed, and a full-page swipe for one-video-at-a-time apps.
    private static let scroll = Move.swipe(0.5, 0.72, 0.5, 0.32, seconds: 0.35)
    private static let nextVideo = Move.swipe(0.5, 0.8, 0.5, 0.2, seconds: 0.25)

    /// URL schemes Volta checks to see whether an app is installed (listed in Info.plist).
    /// Subway Surfers has no known scheme, and the camera is always there.
    static func scheme(_ id: String) -> String? {
        switch id {
        case "chrome": return "googlechromes"
        case "youtube": return "youtube"
        case "instagram": return "instagram"
        case "tiktok": return "snssdk1233"
        case "x": return "twitter"
        case "ytmusic": return "youtubemusic"
        default: return nil
        }
    }

    static func hint(_ id: String) -> String {
        switch id {
        case "chrome": return "Opens \(pages.count) set web pages in turn and scrolls each one"
        case "youtube": return "Plays your video link, or the first result for “\(youtubeSearch)”"
        case "instagram": return "Scrolls the home feed every 5 seconds"
        case "camera": return "Records video in Camera. Set it to 4K at 60 fps first"
        case "tiktok": return "Swipes to the next video every 10 seconds"
        case "x": return "Scrolls the home timeline every 6 seconds"
        case "ytmusic": return "Plays your playlist link, or “\(musicSearch)”, with the screen on"
        case "subway": return "Plays with swipes and taps every second or so"
        case "safari": return "Opens the same web pages in Safari"
        default: return ""
        }
    }

    /// Whether a step takes a link to play.
    static func takesLink(_ id: String) -> Bool { id == "youtube" || id == "ytmusic" }

    /// An https link as the app's own URL scheme, so it opens in that app rather than the browser.
    private static func scheme(_ scheme: String, _ url: String) -> String {
        scheme + "://" + url.replacingOccurrences(of: "https://", with: "").replacingOccurrences(of: "http://", with: "")
    }

    private static func query(_ s: String) -> String {
        s.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed.subtracting(CharacterSet(charactersIn: "&+="))) ?? s
    }

    /// How to run [step] on this iPhone, or nil for a step iOS has no workload for.
    static func of(_ step: StepConfig) -> Workload? {
        let link = step.link.trimmingCharacters(in: .whitespaces).hasPrefix("https://") ? step.link.trimmingCharacters(in: .whitespaces) : nil
        switch step.id {
        case "chrome", "safari":
            let chrome = step.id == "chrome"
            let url = { (page: String) in chrome ? scheme("googlechromes", page) : page }
            // About a minute a page: nine scrolls six seconds apart, then the next page.
            return Workload(
                bundleId: chrome ? Self.chrome : safari,
                url: url(pages[0]),
                loop: (pages.dropFirst() + [pages[0]]).flatMap { Array(repeating: scroll, count: 9) + [Move.open(url($0))] },
                every: 6
            )
        case "youtube":
            return Workload(
                bundleId: youtube,
                url: scheme("youtube", link ?? "https://www.youtube.com/results?search_query=" + query(youtubeSearch)),
                start: link == nil ? [.tapLabel(["play video"], fallback: CGPoint(x: 0.5, y: 0.3))] : []
            )
        case "instagram":
            return Workload(bundleId: instagram, loop: [scroll], every: 5)
        case "camera":
            // Camera opens in the last mode used with Preserve Settings on; picking Video first makes sure.
            return Workload(
                bundleId: camera,
                start: [
                    .tapLabel(["video"], exact: true, fallback: nil),
                    .tapLabel(["record video", "videocapture", "record"], avoid: ["stop"], fallback: CGPoint(x: 0.5, y: 0.87)),
                ],
                leave: [.tapLabel(["stop recording", "stop"], fallback: CGPoint(x: 0.5, y: 0.87))]
            )
        case "tiktok":
            return Workload(bundleId: tiktok, loop: [nextVideo], every: 10)
        case "x":
            return Workload(bundleId: x, loop: [scroll], every: 6)
        case "ytmusic":
            return Workload(
                bundleId: ytMusic,
                url: scheme("youtubemusic", link ?? "https://music.youtube.com/search?q=" + query(musicSearch)),
                start: [.tapLabel(["shuffle", "play all", "play"], avoid: ["pause"], fallback: CGPoint(x: 0.5, y: 0.35))]
            )
        case "subway":
            // A tap starts a run or dismisses the end screen; swipes dodge left, right, up and down.
            return Workload(
                bundleId: subway,
                start: [.tap(0.5, 0.6)],
                loop: [
                    .swipe(0.65, 0.6, 0.3, 0.6, seconds: 0.12),
                    .swipe(0.5, 0.65, 0.5, 0.35, seconds: 0.12),
                    .swipe(0.35, 0.6, 0.7, 0.6, seconds: 0.12),
                    .swipe(0.5, 0.4, 0.5, 0.7, seconds: 0.12),
                    .tap(0.5, 0.6),
                ],
                every: 1.2
            )
        default:
            return nil
        }
    }
}
