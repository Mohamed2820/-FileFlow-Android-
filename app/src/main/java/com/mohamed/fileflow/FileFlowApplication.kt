package com.mohamed.fileflow

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.decode.SvgDecoder
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

/** Registers Coil's optional video decoder for AsyncImage requests. */
class FileFlowApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
}
