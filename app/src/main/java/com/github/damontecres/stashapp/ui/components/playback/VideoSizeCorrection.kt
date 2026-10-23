package com.github.damontecres.stashapp.ui.components.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import kotlin.math.abs

/**
 * Some hardware decoders squeeze frames taller than their output buffers into fewer rows and report that squeezed
 * size. For example, the Chromecast with Google TV's decoders output at most 4096x2304, so a 2160x3840 portrait
 * video is reported as 2160x2304 and drawn far too wide. When the decoded aspect ratio clearly disagrees with the
 * video track's, the track's aspect ratio is used instead.
 */
@Composable
fun rememberTrackCorrectedVideoSize(
    player: Player,
    decodedSizeDp: Size?,
): Size? {
    var trackFormat by remember(player) { mutableStateOf(selectedVideoFormat(player.currentTracks)) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    trackFormat = selectedVideoFormat(tracks)
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return remember(decodedSizeDp, trackFormat) { correctedVideoSize(decodedSizeDp, trackFormat) }
}

private fun selectedVideoFormat(tracks: Tracks): Format? =
    tracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) } }

private fun correctedVideoSize(
    decodedSizeDp: Size?,
    format: Format?,
): Size? {
    if (decodedSizeDp == null || format == null) return decodedSizeDp
    if (decodedSizeDp.width <= 0f || decodedSizeDp.height <= 0f) return decodedSizeDp
    if (format.width == Format.NO_VALUE || format.height == Format.NO_VALUE) return decodedSizeDp
    if (format.width <= 0 || format.height <= 0) return decodedSizeDp
    // The reported video size has rotation applied, the track's dimensions don't
    val rotated = format.rotationDegrees == 90 || format.rotationDegrees == 270
    val width = (if (rotated) format.height else format.width) * pixelRatio(format, rotated)
    val height = (if (rotated) format.width else format.height).toFloat()
    val trackAspect = width / height
    val decodedAspect = decodedSizeDp.width / decodedSizeDp.height
    if (abs(decodedAspect / trackAspect - 1f) < MAX_ASPECT_DIFFERENCE) return decodedSizeDp
    return Size(decodedSizeDp.width, decodedSizeDp.width / trackAspect)
}

private fun pixelRatio(
    format: Format,
    rotated: Boolean,
): Float {
    val ratio = format.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
    return if (rotated) 1f / ratio else ratio
}

private const val MAX_ASPECT_DIFFERENCE = 0.05f
