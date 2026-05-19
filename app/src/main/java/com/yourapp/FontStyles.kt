data class FontStyle(
    val id: String,
    val label: String,
    val ffmpegFont: String,      // fontfile or fontname for FFmpeg drawtext
    val fontColor: String,       // hex color for FFmpeg
    val borderColor: String,     // bordercolor for FFmpeg
    val borderWidth: Int,        // borderw
    val shadowX: Int,
    val shadowY: Int,
    val fontSize: Int = 40
)

object FontStyles {
    val all = listOf(
        FontStyle("bold_outline",   "Bold White + Black Outline", "Impact",        "white",   "black",   4, 0, 0),
        FontStyle("impact_caps",    "All Caps Impact",             "Impact",        "yellow",  "black",   3, 0, 0),
        FontStyle("neon_glow",      "Neon Glow",                   "Arial",         "00ffe7",  "00ffe7",  0, 0, 0, 38),
        FontStyle("bubble",         "Bubble Letters",              "Arial",         "white",   "ff6ec7",  5, 3, 3),
        FontStyle("chunky_shadow",  "Chunky Drop Shadow",          "Georgia",       "white",   "black",   0, 5, 5),
        FontStyle("retro_vhs",      "Retro VHS",                   "Courier New",   "ff003c",  "00ffe7",  2, 2, 0, 32),
        FontStyle("minimal",        "Minimal Clean",               "Helvetica",     "white",   "black",   0, 0, 0, 34),
        FontStyle("outlined",       "Outlined Only",               "Impact",        "black@0", "white",   3, 0, 0),
        FontStyle("fire_glow",      "Fire / Warm Glow",            "Impact",        "FFD700",  "ff4e00",  0, 0, 0),
        FontStyle("ice_glow",       "Ice / Cool Glow",             "Impact",        "e0f7ff",  "00c6ff",  0, 0, 0),
        FontStyle("comic",          "Comic Bold",                  "Comic Sans MS", "white",   "222222",  4, 3, 3, 36),
        FontStyle("typewriter",     "Typewriter Mono",             "Courier New",   "e8e8e8",  "black",   0, 0, 0, 32),
        FontStyle("chalk",          "Chalk on Blackboard",         "Georgia",       "f5f5dc",  "white",   0, 1, 1, 38),
        FontStyle("gold",           "Gold Luxury",                 "Georgia",       "f6d365",  "c8a951",  2, 0, 0),
        FontStyle("gradient_pop",   "Gradient Pop",                "Impact",        "ffd200",  "f7971e",  2, 0, 0)
    )

    fun buildDrawtextFilter(style: FontStyle, text: String, xPercent: Float, yPercent: Float): String {
        val safeText = text.replace("'", "\\'").replace(":", "\\:")
        val x = "(w*${xPercent}-text_w/2)"
        val y = "(h*${yPercent}-text_h/2)"
        var filter = "drawtext=text='$safeText'" +
            ":fontsize=${style.fontSize}" +
            ":fontcolor=${style.fontColor}" +
            ":x=$x:y=$y"

        if (style.borderWidth > 0) {
            filter += ":borderw=${style.borderWidth}:bordercolor=${style.borderColor}"
        }
        if (style.shadowX != 0 || style.shadowY != 0) {
            filter += ":shadowx=${style.shadowX}:shadowy=${style.shadowY}"
        }
        return filter
    }
}
