package com.huolala.mockgps.utils // 请确认这里的包名和你文件顶部的包名一致

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Environment
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureManager(private val context: Context, private val mediaProjection: MediaProjection) {

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var density = 0

    init {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // 获取屏幕真实的宽高
        wm.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi

        // 【关键修复1】：使用 @SuppressLint 忽略格式检查，并确保 width/height 大于0
        if (width > 0 && height > 0) {
            initImageReader()
            createVirtualDisplay()
        }
    }

    @SuppressLint("WrongConstant") // 忽略关于 PixelFormat 的报错
    private fun initImageReader() {
        // 防止 Android Studio 报 ImageReader 格式错误
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    }

    private fun createVirtualDisplay() {
        // 【关键修复2】：使用 !! 强制解包，因为上面已经初始化了，这里一定不为空
        // 解决了 imageReader?.surface 报错的问题
        if (imageReader != null) {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "MockGpsCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, // 这里改成了 !!
                null, null
            )
        }
    }

    fun captureAndSave(lat: Double, lon: Double, onComplete: (() -> Unit)? = null) {
        // 如果 reader 为空，直接回调并返回
        if (imageReader == null) {
            onComplete?.invoke()
            return
        }

        // 尝试获取最新图片
        val image = try {
            imageReader?.acquireLatestImage()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        // 如果获取不到图片（比如屏幕没刷新），必须回调让导航继续！
        if (image == null) {
            onComplete?.invoke()
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                // ... 原有的保存图片逻辑不变 ...
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)

                saveBitmapToDisk(finalBitmap, lat, lon)

                bitmap.recycle()
                finalBitmap.recycle()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // 关闭 image
                try { image.close() } catch (e: Exception) {}

                // 无论成功还是报错，最后必须通知 Service 继续走！
                onComplete?.invoke()
            }
        }
    }

    private fun saveBitmapToDisk(bitmap: Bitmap, lat: Double, lon: Double) {
        // 【修改点】改为 App 私有图片目录
        // 旧代码：val dir = File(Environment.getExternalStorageDirectory(), "MockGpsScreenshots")
        // 新代码如下：
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MockGpsScreenshots")

        if (!dir.exists()) {
            val success = dir.mkdirs()
            if (!success) {
                // 如果创建失败，尝试直接用 pictures 根目录
                // log: 文件夹创建失败
            }
        }

        val fileName = String.format("%.6f_%.6f.png", lat, lon)
        val file = File(dir, fileName)

        try {
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
            // 打印日志方便调试，或者在 Logcat 里搜索 "Saved"
            // Log.d("ScreenCapture", "Saved to: " + file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection.stop()
    }
}