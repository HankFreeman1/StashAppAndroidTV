package com.github.damontecres.stashapp.ui.components.playback

import androidx.compose.ui.graphics.ImageBitmap

/**
 * The current scene's decoded sprite sheet, so seek previews can be drawn straight from it. Cropping each preview
 * through the image loader instead decodes the whole sheet again for every new frame, which is slow on TV devices.
 */
object SpriteSheetCache {
    @Volatile
    private var entry: Pair<String, ImageBitmap>? = null

    fun put(
        url: String,
        image: ImageBitmap,
    ) {
        entry = url to image
    }

    fun get(url: String): ImageBitmap? = entry?.takeIf { it.first == url }?.second
}
