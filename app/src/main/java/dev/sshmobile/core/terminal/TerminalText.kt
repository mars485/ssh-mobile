package dev.sshmobile.core.terminal

/** Minimal wcwidth-style implementation used to lay out double-width glyphs. */
object TextWidth {
    fun of(codePoint: Int): Int {
        if (codePoint == 0) return 0
        if (isZeroWidth(codePoint)) return 0
        if (isWide(codePoint)) return 2
        return 1
    }

    private fun isZeroWidth(cp: Int): Boolean {
        return cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0xFEFF ||
            cp in 0x0300..0x036F ||
            cp in 0x0483..0x0489 ||
            cp in 0x0591..0x05BD ||
            cp in 0x0610..0x061A ||
            cp in 0x064B..0x065F ||
            cp in 0x0670..0x0670 ||
            cp in 0x06D6..0x06DC ||
            cp in 0x0900..0x0902 ||
            cp in 0x093A..0x093A ||
            cp in 0x093C..0x093C ||
            cp in 0x0941..0x0948 ||
            cp in 0x094D..0x094D ||
            cp in 0x0951..0x0957 ||
            cp in 0x0962..0x0963 ||
            cp in 0x1AB0..0x1AFF ||
            cp in 0x1DC0..0x1DFF ||
            cp in 0x20D0..0x20FF ||
            cp in 0xFE00..0xFE0F ||
            cp in 0xFE20..0xFE2F ||
            cp in 0xE0100..0xE01EF
    }

    private fun isWide(cp: Int): Boolean {
        return cp in 0x1100..0x115F ||
            cp in 0x2329..0x232A ||
            cp in 0x2E80..0x303E ||
            cp in 0x3041..0x33FF ||
            cp in 0x3400..0x4DBF ||
            cp in 0x4E00..0x9FFF ||
            cp in 0xA000..0xA4CF ||
            cp in 0xA960..0xA97F ||
            cp in 0xAC00..0xD7A3 ||
            cp in 0xF900..0xFAFF ||
            cp in 0xFE10..0xFE19 ||
            cp in 0xFE30..0xFE6F ||
            cp in 0xFF00..0xFF60 ||
            cp in 0xFFE0..0xFFE6 ||
            cp in 0x1F004..0x1F004 ||
            cp in 0x1F0CF..0x1F0CF ||
            cp in 0x1F18E..0x1F18E ||
            cp in 0x1F191..0x1F19A ||
            cp in 0x1F200..0x1F320 ||
            cp in 0x1F32D..0x1F335 ||
            cp in 0x1F337..0x1F37C ||
            cp in 0x1F37E..0x1F393 ||
            cp in 0x1F3A0..0x1F3CA ||
            cp in 0x1F3CF..0x1F3D3 ||
            cp in 0x1F3E0..0x1F3F0 ||
            cp in 0x1F3F4..0x1F3F4 ||
            cp in 0x1F3F8..0x1F43E ||
            cp in 0x1F440..0x1F440 ||
            cp in 0x1F442..0x1F4FC ||
            cp in 0x1F4FF..0x1F53D ||
            cp in 0x1F54B..0x1F54E ||
            cp in 0x1F550..0x1F567 ||
            cp in 0x1F57A..0x1F57A ||
            cp in 0x1F595..0x1F596 ||
            cp in 0x1F5A4..0x1F5A4 ||
            cp in 0x1F5FB..0x1F64F ||
            cp in 0x1F680..0x1F6C5 ||
            cp in 0x1F6CC..0x1F6CC ||
            cp in 0x1F6D0..0x1F6D2 ||
            cp in 0x1F6EB..0x1F6EC ||
            cp in 0x1F6F4..0x1F6FC ||
            cp in 0x1F7E0..0x1F7EB ||
            cp in 0x1F90C..0x1F93A ||
            cp in 0x1F93C..0x1F945 ||
            cp in 0x1F947..0x1F9FF ||
            cp in 0x1FA70..0x1FAFF ||
            cp in 0x20000..0x3FFFD
    }
}

/** DEC special-graphics / UK character set mapping. */
internal fun mapCharset(codePoint: Int, charset: Int): Int {
    if (charset == 0) return codePoint
    if (charset == 2) {
        return if (codePoint == '#'.code) 0x00A3 else codePoint
    }
    // DEC Special Graphics, applied to printable ASCII.
    return when (codePoint) {
        '`'.code -> 0x25C6 // ◆
        'a'.code -> 0x2592 // ▒
        'b'.code -> 0x2409 // ␉
        'c'.code -> 0x240C // ␌
        'd'.code -> 0x240D // ␍
        'e'.code -> 0x240A // ␊
        'f'.code -> 0x00B0 // °
        'g'.code -> 0x00B1 // ±
        'h'.code -> 0x2424 // ␤
        'i'.code -> 0x240B // ␋
        'j'.code -> 0x2518 // ┘
        'k'.code -> 0x2510 // ┐
        'l'.code -> 0x250C // ┌
        'm'.code -> 0x2514 // └
        'n'.code -> 0x253C // ┼
        'o'.code -> 0x23BA // ⎺
        'p'.code -> 0x23BB // ⎻
        'q'.code -> 0x2500 // ─
        'r'.code -> 0x23BC // ⎼
        's'.code -> 0x23BD // ⎽
        't'.code -> 0x251C // ├
        'u'.code -> 0x2524 // ┤
        'v'.code -> 0x2534 // ┴
        'w'.code -> 0x252C // ┬
        'x'.code -> 0x2502 // │
        'y'.code -> 0x2264 // ≤
        'z'.code -> 0x2265 // ≥
        '{'.code -> 0x03C0 // π
        '|'.code -> 0x2260 // ≠
        '}'.code -> 0x00A3 // £
        '~'.code -> 0x00B7 // ·
        '_'.code -> 0x0020
        else -> codePoint
    }
}
