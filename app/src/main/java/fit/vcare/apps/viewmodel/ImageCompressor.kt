package fit.vcare.apps.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

//ImageCompressor.kt
object ImageCompressor {
    private const val DEFAULT_MAX_DIMENSION_PX = 1280
    private const val DEFAULT_JPEG_QUALITY = 70

    fun compress(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int = DEFAULT_MAX_DIMENSION_PX,
        jpegQuality: Int = DEFAULT_JPEG_QUALITY
    ): ByteArray {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("فایل قابل خواندن نیست")
        boundsStream.use { input -> BitmapFactory.decodeStream(input, null, boundsOptions) }

        val actualWidth = boundsOptions.outWidth
        val actualHeight = boundsOptions.outHeight
        if (actualWidth <= 0 || actualHeight <= 0) {
            throw IllegalStateException("فرمت تصویر پشتیبانی نمی‌شود")
        }

        val sampleSize = calculateInSampleSize(actualWidth, actualHeight, maxDimensionPx)

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decodeStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("فایل قابل خواندن نیست")
        val sampled = decodeStream.use { input -> BitmapFactory.decodeStream(input, null, decodeOptions) }
            ?: throw IllegalStateException("خواندن تصویر ناموفق بود")

        val finalBitmap = scaleDown(sampled, maxDimensionPx)
        val output = java.io.ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)

        if (finalBitmap !== sampled) sampled.recycle()
        finalBitmap.recycle()
        return output.toByteArray()
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetMaxDimension: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= targetMaxDimension || currentHeight / 2 >= targetMaxDimension) {
            currentWidth /= 2
            currentHeight /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val (newWidth, newHeight) = if (width >= height) {
            maxDimension to (maxDimension / ratio).toInt()
        } else {
            (maxDimension * ratio).toInt() to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}