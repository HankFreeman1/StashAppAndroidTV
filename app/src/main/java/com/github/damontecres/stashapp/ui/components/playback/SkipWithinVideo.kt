package com.github.damontecres.stashapp.ui.components.playback

import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Below this, halving what's left stops being useful, so the skip goes straight to the start/end instead
 */
private const val MIN_SHORTENED_SKIP_MS = 1_000L

/**
 * Skip by the configured increment, unless that would leave too little of the video. Then skip half of what's
 * left, so short videos can still be stepped through. Once half would be under [MIN_SHORTENED_SKIP_MS], skip
 * straight to the start/end.
 *
 * Going forward, a skip never covers more than half of what's left, so it can't land just before the end (e.g. a
 * 30s skip with 31s left). Going back, the full increment is used until it would pass the start, since landing on
 * the start is useful.
 *
 * @param stopBeforeEnd don't make a forward skip that would land on the end (and so advance the playlist)
 * @return the signed distance skipped, or null if nothing was skipped
 */
fun Player.skipWithinVideo(
    forward: Boolean,
    stopBeforeEnd: Boolean = false,
): Long? {
    val increment = if (forward) seekForwardIncrement else seekBackIncrement
    val position = currentPosition
    val remaining =
        if (forward) {
            if (duration == C.TIME_UNSET) {
                seekForward()
                return increment
            }
            (duration - position).coerceAtLeast(0L)
        } else {
            position
        }
    val step =
        when {
            forward && remaining >= increment * 2 -> increment
            !forward && remaining > increment -> increment
            remaining / 2 >= MIN_SHORTENED_SKIP_MS -> remaining / 2
            forward && stopBeforeEnd -> return null
            else -> remaining
        }
    if (step <= 0L) return null
    val offset = if (forward) step else -step
    seekTo(position + offset)
    return offset
}
