package io.github.mianxyz.fanqiecustomizer.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.DrawableRes
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * 把赞赏收款码写进系统公共相册 Pictures/Pulp。
 *
 * 两个 API 世代各走一条真实可用的路径，不是同一个调用加判断：
 *  - API 29+：MediaStore 的 RELATIVE_PATH + IS_PENDING，先占位再写数据，
 *    写完清 IS_PENDING 才对相册可见；重名直接覆盖（自己是所有者）。
 *  - API 28：还没有 RELATIVE_PATH/IS_PENDING，必须先在
 *    Environment.DIRECTORY_PICTURES 下建目录、用 DATA 列注册，
 *    再插入 MediaStore，最后通知扫描器。
 *
 * 整个过程只读模块自带资源，不申请任何权限：API 28 写公共相册需要
 * WRITE_EXTERNAL_STORAGE，调用方负责在缺权限时给出提示。
 */
internal object QrSaver {
    private const val ALBUM = "Pulp"
    private const val MIME = "image/png"

    /** 保存结果：成功返回写入后的 Uri，失败返回原因。 */
    sealed interface Result {
        data class Saved(val uri: Uri, val displayName: String) : Result
        data class Failed(val message: String) : Result
    }

    fun save(context: Context, @DrawableRes resId: Int, displayName: String): Result {
        val bitmap = try {
            BitmapFactory.decodeResource(context.resources, resId)
        } catch (_: Throwable) {
            null
        } ?: return Result.Failed("收款码读取失败")

        return try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveModern(context, bitmap, displayName)
            } else {
                saveLegacy(context, bitmap, displayName)
            }
            if (uri == null) Result.Failed("保存失败") else Result.Saved(uri, displayName)
        } catch (error: Throwable) {
            Result.Failed(error.javaClass.simpleName)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** API 29+：MediaStore 直写，无需任何存储权限。 */
    private fun saveModern(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = Environment.DIRECTORY_PICTURES + File.separator + ALBUM

        // 同名覆盖：只删自己这个相册下同名且同 MIME 的条目，不碰别的应用文件。
        resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH}=? AND ${MediaStore.Images.Media.DISPLAY_NAME}=?",
            arrayOf("$relativePath/", displayName),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                resolver.delete(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Images.Media._ID}=?",
                    arrayOf(id.toString()),
                )
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { writeBitmap(bitmap, it) }
                ?: throw IllegalStateException("openOutputStream returned null")
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri
        } catch (error: Throwable) {
            // 半成品不能留在相册里，否则用户会看到一张打不开的图。
            resolver.delete(uri, null, null)
            throw error
        }
    }

    /** API 28：先落到 Pictures/Pulp 实体目录，再登记进 MediaStore。 */
    private fun saveLegacy(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        @Suppress("DEPRECATION")
        val album = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        )
        if (!album.isDirectory && !album.mkdirs()) return null
        val file = File(album, displayName)
        FileOutputStream(file).use { writeBitmap(bitmap, it) }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME)
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, file.absolutePath)
        }
        // 只登记一次：DATA 列已经指向刚落盘的文件，
        // 再调 insertImage 会复制出第二条相册记录。
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
        )

        // 让相册索引立刻看到这张图，否则要等下一次系统扫描。
        @Suppress("DEPRECATION")
        android.media.MediaScannerConnection.scanFile(
            context, arrayOf(file.absolutePath), arrayOf(MIME), null,
        )
        return uri ?: Uri.fromFile(file)
    }

    private fun writeBitmap(bitmap: Bitmap, out: OutputStream) {
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            throw IllegalStateException("Bitmap.compress failed")
        }
        out.flush()
    }

    /** 统一的反馈文案。 */
    fun toast(context: Context, result: Result) {
        val message = when (result) {
            is Result.Saved -> "已保存到相册 Pictures/$ALBUM/${result.displayName}"
            is Result.Failed -> "保存失败：${result.message}"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
