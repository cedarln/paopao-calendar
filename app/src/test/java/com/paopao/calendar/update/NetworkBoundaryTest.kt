package com.paopao.calendar.update

import android.app.DownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 网络边界与版本比较。
 *
 * 泡泡日历的数据层是纯本地的，v0.1.x 因此整包不申请 `INTERNET`。加了应用内更新之后
 * 「没有任何网络调用」这句话不能再说了 —— 那就把还能说的部分变成可执行的检查，
 * 而不是注释里的自我表扬。
 *
 * 三条：
 *   1. 全工程只有 `update/UpdateChecker.kt` 允许出现网络 API；
 *   2. 只有设置页那两行 UI 可以引用它（用户按下去的那一下才是这条链路的起点）；
 *   3. 版本号比较与选包规则是纯算术，钉死在这儿 —— 它们决定用户会不会被拉去装一个新包，
 *      错一次的代价是「明明最新还弹更新」或者「有新版却说自己最新」。
 */
class NetworkBoundaryTest {

    /** 主源码根目录。Gradle 跑单测时工作目录就是模块目录（app）。 */
    private val mainSrc: File
        get() = File("src/main/java").absoluteFile.also {
            assertTrue("找不到主源码目录：$it（本测试必须在 app 模块下跑）", it.isDirectory)
        }

    private fun kotlinFiles() = mainSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** 出现即算「碰了网络」的记号。宁可宽一点：漏一个记号就等于这条边界没守。 */
    private val networkMarks = listOf(
        "java.net.",
        "HttpURLConnection",
        "DownloadManager",
        "OkHttp",
        "Retrofit",
        "Socket(",
        "URL(",
        "WebView",
    )

    @Test
    fun `网络 API 只许出现在 UpdateChecker 里`() {
        val allowed = "update/UpdateChecker.kt"
        val offenders = kotlinFiles().flatMap { f ->
            if (f.invariantSeparatorsPath.endsWith(allowed)) return@flatMap emptyList()
            f.readLines().withIndex().mapNotNull { (i, line) ->
                val hit = networkMarks.firstOrNull { line.contains(it) } ?: return@mapNotNull null
                // 注释里提一句「不联网」不算碰网络
                if (line.trimStart().startsWith("*") || line.trimStart().startsWith("//")) null
                else "${f.name}:${i + 1}  ${line.trim()}   ← 命中 `$hit`"
            }
        }
        assertTrue(
            "网络调用越界了。这个包唯一的网络通道是 update/UpdateChecker.kt，" +
                "而且只在用户按下设置页「检查更新」时动一次：\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `更新链路只接在设置页上`() {
        fun codeLines(f: File) = f.readLines().filterNot {
            val t = it.trimStart()
            t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
        }
        val allowed = setOf("UpdateChecker.kt", "UpdateCard.kt", "SettingsScreen.kt")
        val offenders = kotlinFiles()
            .filterNot { it.name in allowed }
            .filter { f -> codeLines(f).any { it.contains("UpdateChecker") } }
            .map { it.name }
        assertTrue(
            "更新不许被接到插件、提醒、启动或任何后台路径上（那等于让应用自己悄悄联网）：" +
                "引用方=${offenders}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `tag 里的版本号提取扛得住发布页的写法`() {
        assertEquals("0.1.0", UpdateChecker.normalizeVersion("v0.1.0"))
        assertEquals("0.1.0", UpdateChecker.normalizeVersion("泡泡日历 v0.1.0"))
        assertEquals("1.0.0", UpdateChecker.normalizeVersion("v1.0.0-rc2"))
        assertEquals("", UpdateChecker.normalizeVersion("no-number-here"))
        assertEquals("", UpdateChecker.normalizeVersion(null))
    }

    @Test
    fun `版本比较按数字段走，不当字符串比`() {
        // 字符串比会得出 "0.9.0" > "0.10.0"，那种 bug 的表现为「永远提示有新版」
        assertTrue(UpdateChecker.compareVersions("0.10.0", "0.9.0") > 0)
        assertEquals(0, UpdateChecker.compareVersions("0.1.1", "v0.1.1"))
        assertTrue(UpdateChecker.compareVersions("0.1.1", "0.1.0") > 0)
        assertTrue(UpdateChecker.compareVersions("0.1.0", "0.1.1") < 0)
        // 段数不齐：缺的按 0 算
        assertTrue(UpdateChecker.compareVersions("0.2", "0.1.9") > 0)
    }

    @Test
    fun `hasNewer 只在发布页真的更新时为真`() {
        assertTrue(UpdateChecker.hasNewer("v0.2.0", "0.1.0"))
        assertTrue(!UpdateChecker.hasNewer("v0.2.0", "0.2.0"))
        assertTrue(!UpdateChecker.hasNewer("v0.1.0", "0.2.0"))
        // 读不到当前版本时不弹更新——宁可不动，也不要在用户身上试错
        assertTrue(!UpdateChecker.hasNewer("v0.2.0", ""))
        assertTrue(!UpdateChecker.hasNewer("v0.2.0", null))
    }

    @Test
    fun `更新只装 release 那个包，不装 debug`() {
        val assets = listOf(
            UpdateChecker.Asset(
                "paopao-calendar-0.2.0-debug.apk",
                "https://github.com/cedarln/paopao-calendar/releases/download/v0.2.0/paopao-calendar-0.2.0-debug.apk",
                15_000_000L,
                "aa",
            ),
            UpdateChecker.Asset(
                "paopao-calendar-0.2.0-release.apk",
                "https://github.com/cedarln/paopao-calendar/releases/download/v0.2.0/paopao-calendar-0.2.0-release.apk",
                11_000_000L,
                "bb",
            ),
            UpdateChecker.Asset("changelog.txt", "https://example/changelog.txt", 10L, ""),
        )
        // 照「取第一个 .apk」的写法，会把包名带 .debug 的开发包推给用户
        val picked = UpdateChecker.pickApk(assets)
        assertEquals("paopao-calendar-0.2.0-release.apk", picked?.name)
        assertEquals("bb", picked?.sha256)
        assertEquals("paopao-calendar-0.2.0-release.apk", UpdateChecker.archiveName(picked!!.toInfo("v0.2.0")))
        // 没有 release 命名的包时链路不断，至少退回第一个 APK
        assertEquals("paopao-calendar-0.2.0-debug.apk", UpdateChecker.pickApk(assets.take(1))?.name)
        assertTrue(UpdateChecker.pickApk(emptyList()) == null)
    }

    @Test
    fun `release JSON 里没有 APK 时说的是「没有包」而不是解析失败`() {
        val json = """{"tag_name":"v0.2.0","body":"x","assets":[{"name":"notes.txt","size":1}]}"""
        val error = runCatching { UpdateChecker.parseLatest(json) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("最新 Release 里没有 APK 文件", error?.message)
    }

    @Test
    fun `解析发布页只取需要的那几个字段`() {
        val json = """
            {
              "tag_name": "v0.3.0",
              "body": "修了两处",
              "assets": [
                {"name":"app-release.apk","browser_download_url":"https://d/a.apk","size":2048,"digest":"sha256:deadbeef"},
                {"name":"app-debug.apk","browser_download_url":"https://d/b.apk","size":4096}
              ]
            }
        """.trimIndent()
        val info = UpdateChecker.parseLatest(json)
        assertEquals("v0.3.0", info.tag)
        assertEquals("0.3.0", info.version)
        assertEquals("修了两处", info.notes)
        assertEquals("https://d/a.apk", info.apkUrl)
        assertEquals(2048L, info.apkSize)
        assertEquals("deadbeef", info.apkSha256)
    }

    @Test
    fun `系统挂起下载时不许伪装成在下载`() {
        // 实测过：网络没过系统校验时任务挂在「还没开始」，界面停在一个不动的 0% 好几分钟。
        // 那种表现只能被叫做坏了，所以它是「等待」，不是一个百分比。
        val total = 11_966_395L
        assertTrue(
            UpdateChecker.DownloadProgress(DownloadManager.STATUS_PAUSED, 0L, total).isWaiting,
        )
        // 连总大小都没拿到 = 还没开始传，百分比没有意义
        assertTrue(
            UpdateChecker.DownloadProgress(DownloadManager.STATUS_RUNNING, 0L, -1L).isWaiting,
        )
        val running = UpdateChecker.DownloadProgress(DownloadManager.STATUS_RUNNING, 5_000_000L, total)
        assertTrue(!running.isWaiting)
        assertEquals(41, running.percent)
        val done = UpdateChecker.DownloadProgress(DownloadManager.STATUS_SUCCESSFUL, total, total)
        assertTrue(done.isSuccessful)
        assertTrue(!done.isWaiting)
    }

    private fun UpdateChecker.Asset.toInfo(tag: String) =
        UpdateChecker.ReleaseInfo(tag = tag, apkUrl = url, apkSize = size, notes = "", apkSha256 = sha256)
}
