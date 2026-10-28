package com.paopao.calendar.ui.settings

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paopao.calendar.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页里的「版本更新」。
 *
 * 这是本作唯一一处会自己发起网络请求的 UI，所以入口做成一个必须按下才动的按钮：
 * 不放进 `LaunchedEffect(Unit)` 自动检查 —— 用户没问的时候偷偷联网，是对一个
 * 「数据不出设备」的应用最廉价的背叛。同理，这里也不出现任何下载框架的类名：
 * 网络记号只允许存在于 `update/UpdateChecker.kt`，由 `NetworkBoundaryTest` 钉住。
 *
 * 进度用副作用轮询而不是把循环塞进点击回调：转屏或组合重建时，后者会留下一个
 * 无人认领的协程继续去改一个已经被丢弃的状态。
 */
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember { UpdateChecker.currentVersion(context) }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var downloadId by rememberSaveable { mutableLongStateOf(0L) }
    var fileName by rememberSaveable { mutableStateOf("") }
    var expectedSize by rememberSaveable { mutableLongStateOf(0L) }
    var expectedSha by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(downloadId) {
        if (downloadId == 0L) return@LaunchedEffect
        while (true) {
            val progress = withContext(Dispatchers.IO) { UpdateChecker.queryDownload(context, downloadId) }
            if (progress == null) {
                downloadId = 0L
                state = UpdateState.Failed("下载任务查不到了，重新点一次检查更新")
                break
            } else if (progress.isSuccessful) {
                downloadId = 0L
                val ok = withContext(Dispatchers.IO) {
                    UpdateChecker.verifyFile(context, fileName, expectedSize, expectedSha)
                }
                state = if (ok) UpdateState.Ready(fileName)
                else UpdateState.Failed("校验没通过，坏包已删掉，请重新下载")
                break
            } else if (progress.isFailed) {
                downloadId = 0L
                state = UpdateState.Failed("下载失败，检查网络后再试一次")
                break
            } else {
                state = UpdateState.Downloading(fileName, progress.percent, progress.isWaiting)
                delay(800)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("版本更新", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("当前版本 v$currentVersion", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = when (val s = state) {
                            UpdateState.Idle -> "查 ${UpdateChecker.REPO} 在 GitHub 上的最新发布"
                            UpdateState.Checking -> "正在检查更新…"
                            is UpdateState.UpToDate -> "已经是最新版了"
                            is UpdateState.Available -> "发现新版本 v${s.info.version}（${humanSize(s.info.apkSize)}）"
                            is UpdateState.Downloading ->
                                if (s.waiting) "等待网络…" else "下载中 ${s.percent}%"
                            is UpdateState.Ready -> "更新包已就绪，点「安装」完成更新"
                            is UpdateState.Failed -> s.message
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (val s = state) {
                    is UpdateState.Available -> Button(
                        onClick = {
                            val name = UpdateChecker.archiveName(s.info)
                            if (UpdateChecker.cachedArchiveReady(context, s.info)) {
                                // 目录里已经有一个「就是这一版而且没坏」的包，就别再花用户一次流量
                                fileName = name
                                state = UpdateState.Ready(name)
                            } else {
                                val id = UpdateChecker.download(context, s.info.apkUrl, name)
                                if (id == -1L) {
                                    state = UpdateState.Failed("下载没被系统接受，稍后再试一次")
                                } else {
                                    fileName = name
                                    expectedSize = s.info.apkSize
                                    expectedSha = s.info.apkSha256
                                    downloadId = id
                                    state = UpdateState.Downloading(name, 0, waiting = true)
                                }
                            }
                        },
                    ) { Text("下载更新") }

                    is UpdateState.Downloading -> CircularProgressIndicator(
                        modifier = Modifier
                            .height(24.dp)
                            .padding(2.dp),
                        strokeWidth = 2.dp,
                    )

                    is UpdateState.Ready -> Button(
                        onClick = { install(context, s.fileName) { state = it } },
                    ) { Text("安装") }

                    else -> OutlinedButton(
                        enabled = state != UpdateState.Checking,
                        onClick = {
                            scope.launch {
                                state = UpdateState.Checking
                                state = runCatching { UpdateChecker.fetchLatest() }.fold(
                                    onSuccess = { info ->
                                        if (UpdateChecker.hasNewer(info.tag, currentVersion)) {
                                            UpdateState.Available(info, currentVersion)
                                        } else {
                                            UpdateState.UpToDate(currentVersion)
                                        }
                                    },
                                    onFailure = { e ->
                                        UpdateState.Failed(
                                            "查不到更新（${e.message ?: "网络异常"}）。" +
                                                "这条路要求仓库是公开的，且最新 Release 上挂着 APK。",
                                        )
                                    },
                                )
                            }
                        },
                    ) { Text(if (state == UpdateState.Checking) "检查中…" else "检查更新") }
                }
            }
            (state as? UpdateState.Downloading)
                ?.takeIf { !it.waiting }
                ?.let { s ->
                    LinearProgressIndicator(
                        progress = { s.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            (state as? UpdateState.Available)
                ?.info
                ?.notes
                ?.takeIf { it.isNotBlank() }
                ?.let { notes ->
                    Text(
                        text = notes.take(400),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
    }
}

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data class Available(val info: UpdateChecker.ReleaseInfo, val current: String) : UpdateState
    data class Downloading(val fileName: String, val percent: Int, val waiting: Boolean) : UpdateState
    data class Ready(val fileName: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * 把下好的 APK 交给系统安装器。
 *
 * 没有「安装未知应用」授权时先跳授权页并停在这儿：直接 startActivity 会被系统拒绝，
 * 而用户看到的只是一个没反应的按钮。回来再按一次就装上。
 */
private fun install(context: Context, fileName: String, onUpdate: (UpdateState) -> Unit) {
    val activity = context as? Activity ?: return
    val apk = UpdateChecker.downloadedFile(context, fileName)
    if (!apk.isFile || apk.length() == 0L) {
        onUpdate(UpdateState.Failed("更新包不见了，重新下载一次"))
        return
    }
    if (!UpdateChecker.canInstallApk(activity)) {
        UpdateChecker.requestInstallPermission(activity)
        onUpdate(UpdateState.Failed("已跳到授权页：允许「安装未知应用」后回来再按一次安装"))
        return
    }
    UpdateChecker.promptInstall(activity, apk)
}

private fun humanSize(bytes: Long): String = when {
    bytes <= 0L -> "大小未知"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
