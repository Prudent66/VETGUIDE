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

/** Returns a row height based on line count: at least 14dp, plus 14dp padding. */
internal fun rowRowHeight(lineCount: Int): Float = maxOf(14f, lineCount * 13f) + 14f

/** Extracts the numeric percentage from a "95%" style string. */
internal fun rowPercent(pctText: String): Int = pctText.removeSuffix("%").toIntOrNull() ?: 0

internal const val DIFFERENTIAL_BAR_HEIGHT = 8f
internal const val DIFFERENTIAL_LABEL_PADDING = 14f
internal const val DIFFERENTIAL_ROW_SPACING = 18f

/** Vertical layout of one ranked-condition row, measured from its top [y]. */
internal data class DifferentialRowLayout(
    val labelBottom: Float,
    val barTop: Float,
    val rowHeight: Float,
)

/**
 * Advances past the complete label block before the bar: the bar can never
 * overlap the condition title or percentage, including on rows 2 and 3 where
 * the accumulated [y] previously lagged behind the wrapped text.
 */
internal fun differentialRowLayout(y: Float, lineCount: Int): DifferentialRowLayout {
    val labelHeight = maxOf(1, lineCount) * 13f
    val labelBottom = y + labelHeight
    val barTop = labelBottom + DIFFERENTIAL_LABEL_PADDING
    val rowHeight = (barTop - y) + DIFFERENTIAL_BAR_HEIGHT + DIFFERENTIAL_ROW_SPACING
    return DifferentialRowLayout(labelBottom, barTop, rowHeight)
}

/** Bar top for a row starting at [y]; kept for callers that only need that. */
internal fun differentialBarTop(y: Float, lineCount: Int): Float =
    differentialRowLayout(y, lineCount).barTop

/** Full row height including label, padding, bar and spacing to the next row. */
internal fun differentialRowHeight(lineCount: Int): Float =
    differentialRowLayout(0f, lineCount).rowHeight

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
