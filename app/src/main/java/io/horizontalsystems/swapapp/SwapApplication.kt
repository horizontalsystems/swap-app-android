package io.horizontalsystems.swapapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import io.horizontalsystems.swapapp.settings.DebugSettings

/**
 * Supplies Coil's app-wide [ImageLoader] with an SVG decoder, so token logos from `/tokens/all`
 * (many `logoURI`s are `.svg`) render through the shared loader/cache. Coil's singleton picks this
 * up automatically, so every `rememberAsyncImagePainter` call gets SVG support without per-call
 * configuration.
 */
class SwapApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        DebugSettings.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .build()
}
