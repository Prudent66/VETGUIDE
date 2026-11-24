package com.prudent66.vetguide.data.report

import android.graphics.Paint
import android.graphics.Typeface

/** Inner horizontal padding applied inside every report card. */
internal const val BOX_PADDING = 18f

internal fun paint(color: Int, size: Float, style: Int = Typeface.NORMAL): Paint =
    Paint().apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, style)
        isAntiAlias = true
    }

/** Usable width inside a card: box width minus both horizontal paddings. */
internal fun innerContentWidth(boxWidth: Float, padding: Float = BOX_PADDING): Float =
    (boxWidth - padding * 2f).coerceAtLeast(1f)

/** Word-wraps text so no rendered line can exceed [maxWidth]. */
internal fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> =
    wrap(text, { value -> paint.measureText(value) }, maxWidth)

/**
 * Test-friendly overload: [measure] returns the rendered width of a string.
 * Long words that exceed [maxWidth] are split so the width is never exceeded.
 */
internal fun wrap(text: String, measure: (String) -> Float, maxWidth: Float): List<String> =
    buildList {
        text.split("\n").forEach { raw ->
            var current = ""
            raw.split(" ").filter { word -> word.isNotEmpty() }.forEach { word ->
                breakWord(word, measure, maxWidth).forEach { piece ->
                    val candidate = if (current.isEmpty()) piece else "$current $piece"
                    if (measure(candidate) <= maxWidth) {
                        current = candidate
                    } else {
                        if (current.isNotEmpty()) add(current)
                        current = piece
                    }
                }
            }
            add(current)
        }
    }

/** Splits a single over-long word into chunks that each fit [maxWidth]. */
private fun breakWord(
    word: String,
    measure: (String) -> Float,
    maxWidth: Float,
): List<String> {
    if (word.isEmpty() || measure(word) <= maxWidth) return listOf(word)
    val parts = mutableListOf<String>()
    var start = 0
    while (start < word.length) {
        var end = start + 1
        while (end < word.length && measure(word.substring(start, end + 1)) <= maxWidth) end++
        parts += word.substring(start, end)
        start = end
    }
    return parts
}
