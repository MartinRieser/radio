package com.pulsecast.radio.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.IOException

/**
 * Loads station favicons and artwork asynchronously using Coil and delivers software ARGB_8888
 * bitmaps to Media3 / MediaLibrarySession for Bluetooth AVRCP and system media notifications.
 */
@OptIn(UnstableApi::class)
class CoilBitmapLoader(
    private val context: Context,
    private val imageLoader: ImageLoader = ImageLoader(context)
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val bitmap = runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }.getOrNull()
        return if (bitmap != null) {
            Futures.immediateFuture(bitmap)
        } else {
            Futures.immediateFailedFuture(IOException("Failed to decode bitmap from byte array"))
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false) // MediaSession and Bluetooth require software bitmaps
            .target(
                onSuccess = { drawable ->
                    val bitmap = if (drawable is BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        runCatching { drawable.toBitmap() }.getOrNull()
                    }
                    if (bitmap != null) {
                        future.set(bitmap)
                    } else {
                        future.setException(IOException("Could not convert drawable to Bitmap"))
                    }
                },
                onError = {
                    future.setException(IOException("Failed to load bitmap from URI: $uri"))
                }
            )
            .build()

        imageLoader.enqueue(request)
        return future
    }
}

