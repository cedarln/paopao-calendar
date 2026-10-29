package com.paopao.calendar.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.paopao.calendar.domain.export.ExportFormat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导出内容的两条落点：用户选定的位置（SAF）与系统分享面板（FileProvider）。
 *
 * 做成两条而不是让用户二选一，是因为这两个动作回答的是不同的问题：
 * 「我要把它存进网盘/ U 盘」需要先挑位置，「我现在就发给别人」只想按一次分享。
 * 只给一个入口的话，另一个场景就只能绕路 —— 而绕路的方式通常是把文件发给自己再存下来。
 */
object ExportFiles {

    /** 分享用的临时目录；[pruneCache] 会把它压回最近几份。 */
    private const val CACHE_DIR = "exports"
    private const val CACHE_KEEP = 5

    /**
     * 写进 SAF 选中的位置。
     *
     * 用 "wt" 而不是默认的 "w"：CreateDocument 拿到的 Uri 已经指向一个新建的空文件，
     * 显式声明截断可以让「重复导出到同一个文件名」留下最后一次的内容，
     * 而不是把两段 JSON 拼在一个文件里 —— 那种文件谁也读不回去。
     */
    suspend fun writeTo(context: Context, target: Uri, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val stream = context.contentResolver.openOutputStream(target, "wt")
                    ?: error("无法写入所选位置")
                stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }
        }

    /**
     * 落到缓存目录并换成可分享的 content Uri。
     *
     * 不能直接分享 cacheDir 的文件路径：Android 7 之后传 `file://` 给别的应用会抛
     * FileUriExposedException，只能走 FileProvider —— 而它能看见哪些目录是
     * `res/xml/file_paths.xml` 决定的，两边必须对得上。
     */
    suspend fun prepareForShare(
        context: Context,
        format: ExportFormat,
        fileName: String,
        content: String,
    ): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        pruneCache(dir)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** 分享面板。Chooser 让系统自己列表可选应用，我们不做「哪个 App 能收」的判断。 */
    fun shareIntent(uri: Uri, format: ExportFormat, subject: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /**
     * 只保留最近几份。
     *
     * 导出文件动辄几百 KB，且用户完全可能连点五次「分享」试试不同范围；缓存不该无限长。
     * 留下几份是为了「刚分享失败、再点一次」不至于重新查一遍库。
     */
    private fun pruneCache(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(CACHE_KEEP).forEach { it.delete() }
    }
}
