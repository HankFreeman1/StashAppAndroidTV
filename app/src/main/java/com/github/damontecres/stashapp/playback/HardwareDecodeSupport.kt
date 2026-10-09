package com.github.damontecres.stashapp.playback

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import com.github.damontecres.stashapp.data.Scene
import com.github.damontecres.stashapp.proto.StreamChoice

private const val TAG = "HardwareDecodeSupport"

/**
 * Decides forced transcoding from what this device's hardware video decoders declare they can play, instead of
 * fixed resolution & frame rate thresholds. For example, a decoder may handle HEVC at 4K60 but H.264 only at 4K30.
 */
object HardwareDecodeSupport {
    private val mimeTypes =
        buildMap {
            put("h264", MediaFormat.MIMETYPE_VIDEO_AVC)
            put("hevc", MediaFormat.MIMETYPE_VIDEO_HEVC)
            put("vp8", MediaFormat.MIMETYPE_VIDEO_VP8)
            put("vp9", MediaFormat.MIMETYPE_VIDEO_VP9)
            put("mpeg2video", MediaFormat.MIMETYPE_VIDEO_MPEG2)
            put("mpeg4", MediaFormat.MIMETYPE_VIDEO_MPEG4)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put("av1", MediaFormat.MIMETYPE_VIDEO_AV1)
            }
        }

    private val hardwareDecoders: List<MediaCodecInfo> by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .filter { !it.isEncoder && isHardware(it) }
            .also { decoders -> Log.v(TAG, "Hardware decoders: ${decoders.map { it.name }}") }
    }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val name = info.name.lowercase()
            !name.startsWith("omx.google.") && !name.startsWith("c2.android.")
        }

    /**
     * Whether a hardware decoder can play [mimeType] at this size and frame rate
     */
    private fun isSupported(
        mimeType: String,
        width: Int,
        height: Int,
        frameRate: Double?,
    ): Boolean =
        hardwareDecoders.any { info ->
            if (info.supportedTypes.none { it.equals(mimeType, ignoreCase = true) }) return@any false
            val caps = info.getCapabilitiesForType(mimeType).videoCapabilities ?: return@any false
            if (frameRate != null && frameRate > 0) {
                caps.areSizeAndRateSupported(width, height, frameRate)
            } else {
                caps.isSizeSupported(width, height)
            }
        }

    /**
     * If the scene's video can't be played by a hardware decoder, returns the largest transcoded stream for
     * [streamChoice] that can be, falling back to the smallest one. Returns null if the scene can be direct played
     * or there isn't enough information to tell.
     */
    fun checkIfTranscodeNeeded(
        scene: Scene,
        streamChoice: StreamChoice,
    ): String? {
        val mimeType = scene.videoCodec?.let { mimeTypes[it] } ?: return null
        val width = scene.videoWidth ?: return null
        val height = scene.videoHeight ?: return null
        if (isSupported(mimeType, width, height, scene.frameRate)) return null

        // Stash transcodes HLS & MP4 streams to H.264, DASH & WEBM to VP9
        val transcodeMimeType =
            when (streamChoice) {
                StreamChoice.DASH, StreamChoice.WEBM -> MediaFormat.MIMETYPE_VIDEO_VP9
                else -> MediaFormat.MIMETYPE_VIDEO_AVC
            }
        val regex = Regex("\\((\\d+)p?\\)")
        val candidates =
            scene.streams.keys
                .filter { it.lowercase().startsWith(streamChoice.label.lowercase()) }
                .mapNotNull { stream -> regex.find(stream)?.let { Pair(stream, it.groups[1]!!.value.toInt()) } }
                .sortedByDescending { it.second }
        // A stream's resolution is its short side, scaled with the same aspect ratio
        val supported =
            candidates.firstOrNull { (_, shortSide) ->
                val scale = shortSide.toDouble() / minOf(width, height)
                isSupported(transcodeMimeType, (width * scale).toInt(), (height * scale).toInt(), scene.frameRate)
            }
        val result = (supported ?: candidates.lastOrNull())?.first
        Log.d(
            TAG,
            "${scene.videoCodec} ${width}x$height@${scene.frameRate} not supported by hardware, transcoding with $result",
        )
        return result
    }
}
