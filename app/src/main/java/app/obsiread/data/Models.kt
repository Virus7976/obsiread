package app.obsiread.data

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import kotlinx.serialization.Serializable

@Serializable
data class ArticleMeta(
    val id: String,
    val title: String,
    val author: String,
    val description: String = "",
    val published: String = "",
    /** Not used for articles. Kept so old code that reads it still builds. */
    val durationSec: Int = 0,
    /** The page the article came from, after redirects. */
    val url: String = "",
    /** The site name, like "The Verge". */
    val site: String = "",
    /** The main picture of the article. Blank when it has none. */
    val image: String = "",
    /** Words in the article, for the reading time. */
    val words: Int = 0,
    /** The page's language code, like "en". */
    val lang: String = "",
) {
    val thumb: String get() = image
    /** Minutes to read at 230 words a minute. At least 1. */
    val minutes: Int get() = ((words + 229) / 230).coerceAtLeast(1)
    val host: String get() = runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
    val siteName: String get() = site.ifBlank { host }
}

@Serializable
data class Article(
    val meta: ArticleMeta,
    val chapters: List<Chapter> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val positionSec: Double = 0.0,
    val pinned: Boolean = false,
    val updatedAt: Long = 0L,
    val clippedAt: Long = 0L,
    val tags: List<String> = emptyList(),
    val category: String = "",
    /** Empty = in the main library. Otherwise the video lives in that playlist only. */
    val playlistId: String = "",
    val addedAt: Long = 0L,
    /** Where the last clip of this video went, so it can be opened in Obsidian. */
    val noteVault: String = "",
    val notePath: String = "",
    /** What was filled in the last time this video was clipped. It comes back next time. */
    val lastClip: ClipMemo? = null,
    /** When you finished it, by hand or by watching it to the end. 0 means not done. */
    val doneAt: Long = 0L,
    /** Last time it played, so what you just watched comes first. */
    val playedAt: Long = 0L,
) {
    val done: Boolean get() = doneAt > 0

    /** Where to start. A video you finished starts over, so it does not end again in a few seconds. */
    val resumeSec: Double
        get() = if (meta.durationSec > 0 && positionSec >= meta.durationSec - 10) 0.0 else positionSec
}

@Serializable
data class AppSettings(
    // look
    val themePreset: String = "ink",
    val customPalettes: List<CustomPalette> = emptyList(),
    /** Home tabs that are shown: add, library. */    val homeTabs: List<String> = listOf("add", "library", "playlists", "queue"),
    val libraryLayout: String = "list", // list | grid | compact
    val librarySort: String = "recent", // recent | added | title | author | highlights
    /** The library sort runs the other way. */
    val librarySortReversed: Boolean = false,
    val hideDone: Boolean = false,
    /** Bumped when new home tabs ship, so the tabs show up for people who changed the list. */
    val tabsVersion: Int = 0,
    /** Video ids waiting to play, in order. */
    val queue: List<String> = emptyList(),
    val pixelChar: String = "ghost", // ghost | heart | star | invader | off
    val customFontPath: String = "",
    val customFontName: String = "",
    val uiFontCustom: Boolean = false,
    val appearance: String = "auto", // auto | light | dusk | dark | oled
    val accentOverride: Long = 0L, // 0 = use the preset accent
    val motion: String = "lively", // calm | lively | playful
    val roundness: Float = 1f,
    // reading
    val readerFont: String = "serif", // serif | sans | system
    val readerFontSize: Float = 18f,
    val lineHeight: Float = 1.6f,
    val maxWidth: Float = 38f,
    val lineLength: Int = 1, // 0 short, 1 medium, 2 long
    /** The reader's own colours. "app" follows the app theme. */
    val readerTheme: String = "app",
    val fontWeight: Int = 400,
    /** The gap between paragraphs, as a part of one line. */
    val paragraphSpacing: Float = 1f,
    /** Extra room between letters, in em. */
    val letterSpacing: Float = 0f,
    /** The space at each side of the text, in dp. */
    val sideMargin: Int = 18,
    val justify: Boolean = false,
    val hyphenate: Boolean = true,
    /** A tap on the page shows or hides the bars. */
    val tapForBars: Boolean = true,
    /** The bars go away when you read down. */
    val hideBarsOnScroll: Boolean = true,
    /** The status bar goes too when the bars are hidden. */
    val fullScreenReading: Boolean = true,
    /** A small "12%" in the corner while the bars are hidden. */
    val showProgressHidden: Boolean = true,
    /** A link opens in a reading card in the app. Off opens your browser. */
    val linksInApp: Boolean = true,
    val pinPlayer: Boolean = true,
    val highlightActiveLine: Boolean = true,
    val autoScroll: Boolean = false,
    val showTimestamps: Boolean = true,
    // highlighting
    val highlighterEnabled: Boolean = true,
    val alwaysShowHighlights: Boolean = true,
    val highlightBehavior: String = "replace-content", // replace-content | keep-content
    val highlightColor: Int = 0, // the colour new highlights start with
    val holdSelects: String = "word", // word | sentence | paragraph
    val highlightSyntax: String = "obsidian", // obsidian | plain
    val highlightFormat: String = "> {text} [↗]({link})",
    val autoPlay: Boolean = true,
    /** Save a video's highlights to Obsidian by itself when it ends. */
    val autoClip: Boolean = false,
    /** A daily nudge with one old highlight. */
    val reviewReminder: Boolean = false,
    val reviewHour: Int = 9,
    /** Cards in one Review round. 0 means no limit. */
    val reviewBatch: Int = 5,
    /** Playback waits while a note on a highlight is being typed. */
    val pauseWhileNoting: Boolean = true,
    val recentSearches: List<String> = emptyList(),
    // obsidian
    val saveBehavior: String = "addToObsidian", // addToObsidian | saveFile | copyToClipboard | share
    /** When the video is already in the vault, fill the save sheet from that note so you can add to it. */
    val prefillExisting: Boolean = true,
    /** Name notes like 2026-10-28_subject_description_v1. Off keeps the template's own name. */
    val nameSchema: Boolean = false,
    val nameSubject: String = "clipping",
    val recentSubjects: List<String> = emptyList(),
    val vaults: List<String> = emptyList(),
    val vault: String = "",
    val vaultFolderUri: String = "",
    /** Vault name -> folder the user picked, so the app can read tags, folders and notes. */
    val vaultFolders: Map<String, String> = emptyMap(),
    val recentTags: List<String> = emptyList(),
    /** Added to every clip, whatever the template says. */
    val autoTags: List<String> = emptyList(),
    /** Properties added to every clip. A template value that is already filled in wins. */
    val autoProps: List<TemplateProperty> = emptyList(),
    val templates: List<ClipTemplate> = emptyList(),
    val selectedTemplateId: String = "",
    val propertyTypes: Map<String, String> = emptyMap(),
    // playback + transcript
    val backgroundPlay: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** Pause when the volume goes to zero, and carry on when it comes back. */
    val pauseOnMute: Boolean = true,
    // lock screen
    val lockScreen: Boolean = false,
    val lockStyle: String = "page", // page | cover | minimal
    val lockBackground: String = "black", // black | art | accent
    val lockAccent: String = "theme", // theme | white
    val lockTextSize: Float = 20f,
    val lockClock: Boolean = true,
    val lockClockSize: String = "small", // small | large
    val lockTitle: Boolean = true,
    val lockSeekBar: Boolean = true,
    val lockHighlight: Boolean = true,
    val lockFollow: Boolean = true,
    val lockKeepOn: Boolean = false,
    /** Start with the control panel dragged away, so only the text shows. */
    val lockHideControls: Boolean = false,
    val captionLang: String = "",
    // advanced: client versions can be changed here if YouTube ever rejects the built-in ones
    val androidClientVersion: String = "20.10.38",
    val iosClientVersion: String = "20.10.4",
    val vrClientVersion: String = "1.62.27",
    // updates
    val autoCheckUpdates: Boolean = true,
    val updateRepo: String = "Virus7976/obsiread",
    val updateToken: String = "",
    val updateMirror: String = "",
    val ignoredVersion: String = "",
    val installMethod: String = "system", // system | shizuku | adb
    val lastUpdateCheck: Long = 0L,
) {
    /**
     * The vault name that has a folder behind it. A template may say "Liam's Git Vault" while the folder
     * was added as "LiamsGitSyncObsidian". With only one folder linked, that one is used.
     */
    fun linkedVault(name: String): String? =
        name.takeIf { vaultFolders.containsKey(it) } ?: vaultFolders.keys.singleOrNull()
}
