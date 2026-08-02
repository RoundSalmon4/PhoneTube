package com.roundsalmon4.phonetube.player

import android.content.Context
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import java.util.ArrayList

@UnstableApi
class PlaybackRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    @Suppress("DEPRECATION")
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>
    ) {
        TextRenderer(output, outputLooper).apply {
            experimentalSetLegacyDecodingEnabled(true)
        }.let { out.add(it) }
    }
}
