package com.paopao.calendar.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 应用内更新：查 GitHub Release → 比对 → 下载 → 拉起系统安装器，全程不跳浏览器。
 *
 * 零第三方依赖：`HttpURLConnection` 查 API，系统 `DownloadManager` 下载（自带通知栏
 * 进度与断点续传），`FileProvider` 把下好的 APK 单独授权给系统安装器。与 paopao-daily、
 * 正在备份 那一套同源，差别只写在下面的边界里。
 *
 * ── 这条网络通道在本作里的地位 ────────────────────────────────────────────────
 * 泡泡日历的数据层是纯本地的：日程、事项、习惯、训练全在设备自己的 Room 库里，
 * 没有任何「同步」。唯一的出网口就是这里，边界写死成三条，由 `NetworkBoundaryTest`
 * 守着而不是靠自觉：
 *   1. 全工程只有 `update/UpdateChecker.kt` 允许出现网络 API；
 *   2. 只有设置页可以引用它 —— 用户按下「检查更新」那一下才是这条链路的起点，
 *      没有启动时自动检查、没有后台轮询、不进 ViewModel 的任何一条常规路径；
 *   3. 查的是我们自己的发布页，读的是公开接口，不带任何 token，也不上传任何设备数据。
 *
 * 前提是仓库可见：私有仓库的 `releases/latest` 匿名访问是 404，所以这条链路要求
 * `cedarln/paopao-calendar` 是公开仓库（README 里也写明了）。
 */
object UpdateChecker {

    private const val OWNER = "cedarln"

    /** 检查更新查询的仓库（公开仓库，匿名可查；本包不含任何 token）。 */
    const val REPO = "paopao-calendar"

    private const val API_LATEST = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    private const val UPDATE_DIR = "update"

    /** 更新包目录：应用专属外部存储下的 `update/`，卸载时跟着一起消失。 */
    fun updateDir(ctx: Context): File = File(ctx.getExternalFilesDir(null), UPDATE_DIR)

    data class ReleaseInfo(
        val tag: String,
        val apkUrl: String,
        val apkSize: Long,
        val notes: String,
        /** 发布页给的 `sha256:...`（去掉前缀）。空 = 这个 API 版本没给，退化成只比大小。 */
        val apkSha256: String = "",
    ) {
        /** "v1.2.3" → "1.2.3" */
        val version: String get() = normalizeVersion(tag)
    }

    // ── 版本比较（纯逻辑，可单测）─────────────────────────────────────────────

    /** 从 tag 里提版本号：找到第一个数字后只保留数字和点，遇到别的字符就停。 */
    fun normalizeVersion(tag: String?): String {
        if (tag == null) return ""
        val v = tag.trim()
        val start = v.indexOfFirst { it in '0'..'9' }
        if (start < 0) return ""
        val sb = StringBuilder()
        var lastWasDot = false
        for (i in start until v.length) {
            val ch = v[i]
            when {
                ch in '0'..'9' -> { sb.append(ch); lastWasDot = false }
                ch == '.' && !lastWasDot -> { sb.append('.'); lastWasDot = true }
                else -> break
            }
        }
        return sb.toString().trim('.')
    }

    /** 按数字段比较：负 = a 更旧，0 = 相同，正 = a 更新。 */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".")
        val pb = b.split(".")
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = if (i < pa.size) pa[i].trim().toIntOrNull() ?: 0 else 0
            val y = if (i < pb.size) pb[i].trim().toIntOrNull() ?: 0 else 0
            if (x != y) return x - y
        }
        return 0
    }

    /** 最新 Release 的 tag 是否比当前安装的版本新。 */
    fun hasNewer(latestTag: String, currentVersion: String?): Boolean =
        !currentVersion.isNullOrEmpty() &&
            compareVersions(normalizeVersion(latestTag), currentVersion) > 0

    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    // ── GitHub API ───────────────────────────────────────────────────────────

    /** 查最新 Release。阻塞网络调用，本函数已自行切到 IO。 */
    suspend fun fetchLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val conn = URL(API_LATEST).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            if (code != 200) {
                throw IllegalStateException(
                    if (code == 404) "仓库不可见，或还没有发布任何版本" else "HTTP $code",
                )
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseLatest(body)
        } finally {
            conn.disconnect()
        }
    }

    /** 发布页上的一个可下载文件（已经把 JSON 拆成纯数据，好单独测选择规则）。 */
    data class Asset(val name: String, val url: String, val size: Long, val sha256: String)

    /**
     * 从资产列表里挑要下的那个 APK。
     *
     * **优先 `-release.apk`**：发布页同时挂正式包和开发包时，照「取第一个 .apk」的写法
     * 有相当概率推给用户一个包名带 `.debug` 的开发包 —— 那是另一个应用，装上去既不能
     * 覆盖正式版的数据库，又让人以为更新失败。没有 release 命名的包时退回第一个 APK，
     * 至少更新链路不断。
     */
    fun pickApk(assets: List<Asset>): Asset? {
        val apks = assets.filter { it.name.lowercase().endsWith(".apk") }
        return apks.firstOrNull { it.name.lowercase().endsWith("-release.apk") } ?: apks.firstOrNull()
    }

    /** 解析 Release JSON；挑包的权利在 [pickApk]，这里只做拆解。 */
    fun parseLatest(json: String): ReleaseInfo {
        val root = JSONObject(json)
        val tag = root.optString("tag_name", "")
        if (tag.isEmpty()) throw IllegalStateException("Release 数据里没有 tag_name")
        val assets = mutableListOf<Asset>()
        root.optJSONArray("assets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                val name = a.optString("name", "")
                if (!name.lowercase().endsWith(".apk")) continue
                assets += Asset(name, a.optString("browser_download_url", ""), a.optLong("size", 0L), digestOf(a))
            }
        }
        val picked = pickApk(assets) ?: throw IllegalStateException("最新 Release 里没有 APK 文件")
        return ReleaseInfo(tag, picked.url, picked.size, root.optString("body", ""), picked.sha256)
    }

    /** GitHub 的 asset 带 `digest: "sha256:<hex>"`；老版本或别的托管方可能没有。 */
    private fun digestOf(asset: JSONObject): String {
        val raw = asset.optString("digest", "")
        return if (raw.startsWith("sha256:")) raw.removePrefix("sha256:") else ""
    }

    // ── 下载（系统 DownloadManager）────────────────────────────────────────────

    /** 下到 `getExternalFilesDir(null)/update/<fileName>`，返回下载 id（-1 = 无法入队）。 */
    fun download(ctx: Context, url: String, fileName: String): Long {
        cleanInstalledArchives(ctx)
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("正在下载 泡泡日历 更新包")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(ctx, null, "$UPDATE_DIR/$fileName")
        }
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        return dm?.enqueue(req) ?: -1L
    }

    fun downloadedFile(ctx: Context, fileName: String): File = File(updateDir(ctx), fileName)

    /** 用发布页给的资产名落盘，避免我们自己再编一套命名。 */
    fun archiveName(info: ReleaseInfo): String {
        val fromUrl = info.apkUrl.substringAfterLast('/').substringBefore('?')
        return fromUrl.ifEmpty { "$REPO-${info.version}-release.apk" }
    }

    /**
     * 更新目录里是不是已经有一个「就是这一版、而且没坏」的包。有就别再花用户一次流量。
     *
     * 先比大小（免费），再比发布页给的 sha256。只比大小不够：同名同尺寸的资产可以是
     * 另一个构建（我们重打过一次包），那时装完用户会以为更新没用。
     */
    fun cachedArchiveReady(ctx: Context, info: ReleaseInfo): Boolean {
        if (info.apkSize <= 0L) return false
        val f = downloadedFile(ctx, archiveName(info))
        if (!f.isFile || f.length() != info.apkSize) return false
        return info.apkSha256.isEmpty() || sha256(f) == info.apkSha256
    }

    /**
     * 下载完成后校验文件；不匹配就删掉它 —— 留着一个坏包只会骗到下一次点击。
     * 返回 false 时不要弹「安装」。
     */
    fun verifyArchive(ctx: Context, info: ReleaseInfo): Boolean =
        verifyFile(ctx, archiveName(info), info.apkSize, info.apkSha256)

    /**
     * [verifyArchive] 的裸参数版。
     *
     * 界面上只留得下「文件名 + 大小 + 摘要」三个值（组合重建时 `ReleaseInfo` 整个对象
     * 不在 SaveableStateRegistry 里），所以校验入口也要能用这三个值调。
     */
    fun verifyFile(ctx: Context, fileName: String, expectedSize: Long, expectedSha256: String): Boolean {
        val f = downloadedFile(ctx, fileName)
        val ok = f.isFile && (expectedSize <= 0L || f.length() == expectedSize) &&
            (expectedSha256.isEmpty() || sha256(f) == expectedSha256)
        if (!ok) f.delete()
        return ok
    }

    /** 几 MB 读完约几十毫秒；调用方请在 IO 线程上用它。 */
    private fun sha256(file: File): String = try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        ""
    }

    data class DownloadProgress(val status: Int, val downloaded: Long, val total: Long) {
        val percent: Int get() = if (total > 0) (downloaded * 100 / total).toInt() else 0

        val isSuccessful: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
        val isFailed: Boolean get() = status == DownloadManager.STATUS_FAILED

        /**
         * 系统还没在传字节：任务被挂起（网络没通过系统校验、离线、排队等 Wi-Fi），
         * 或者它连总大小都还没拿到。
         *
         * 这不是失败，也不该显示成一个卡死的百分比 —— 停在 0% 的进度条只能被叫做坏了。
         */
        val isWaiting: Boolean
            get() = !isSuccessful && !isFailed &&
                (status != DownloadManager.STATUS_RUNNING || total <= 0L)
    }

    /** 查下载状态与进度；null = 任务查不到（系统重启、被清理）。 */
    fun queryDownload(ctx: Context, id: Long): DownloadProgress? {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null
        val cur = dm.query(DownloadManager.Query().setFilterById(id)) ?: return null
        return cur.use {
            if (!it.moveToFirst()) return null
            DownloadProgress(
                status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }
    }

    /** 读一个已下载 APK 的版本号（解析失败返回空串）。 */
    fun archiveVersion(ctx: Context, apk: File): String = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    /** 清掉更新目录里「已装完或解析失败」的旧包，返回删除数。别让几 MB 一个的包越攒越多。 */
    fun cleanInstalledArchives(ctx: Context): Int {
        val dir = updateDir(ctx)
        if (!dir.isDirectory) return 0
        val current = currentVersion(ctx)
        var deleted = 0
        dir.listFiles()?.forEach { f ->
            if (!f.name.lowercase().endsWith(".apk")) return@forEach
            val ver = archiveVersion(ctx, f)
            if (ver.isEmpty() || compareVersions(normalizeVersion(ver), current) <= 0) {
                if (f.delete()) deleted++
            }
        }
        return deleted
    }

    // ── 安装（Android 8+ 需要「安装未知应用」授权）──────────────────────────────

    fun canInstallApk(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ctx.packageManager.canRequestPackageInstalls()

    /** 引导用户去开「安装未知应用」；回来再按一次「安装」即可。 */
    fun requestInstallPermission(activity: Activity) {
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            ),
        )
    }

    /** 拉起系统安装器；没授权时跳设置页并返回 false。 */
    fun promptInstall(activity: Activity, apk: File): Boolean {
        if (!canInstallApk(activity)) {
            requestInstallPermission(activity)
            return false
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        return true
    }
}
