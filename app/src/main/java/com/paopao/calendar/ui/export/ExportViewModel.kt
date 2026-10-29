package com.paopao.calendar.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.export.ExportCollector
import com.paopao.calendar.data.export.ExportFiles
import com.paopao.calendar.domain.export.ExportFormat
import com.paopao.calendar.domain.export.ExportRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 导出页的视图模型。
 *
 * 生成内容这件事一定比用户以为的慢：整表读数加逐日展开在几年数据上是几百毫秒的量级，
 * 所以两条路径都先把界面置成 busy，而不是在主线程上赌它够快 —— 赌输了的表现是
 * 点一下按钮整页冻住，用户会以为没点上，然后连点五次。
 */
data class ExportUiState(
    val busy: Boolean = false,
    /** 已经落进缓存、可以分享的 Uri；界面消费一次即清空，避免转屏时重弹分享面板。 */
    val shareReady: Uri? = null,
    val notice: String? = null,
)

class ExportViewModel(private val container: AppContainer) : ViewModel() {

    private val collector = ExportCollector(container)
    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun fileNameFor(format: ExportFormat, range: ExportRange): String = collector.fileNameFor(format, range)

    fun saveTo(target: Uri, format: ExportFormat, range: ExportRange) = work(format, range, onSuccess = { content ->
        val written = ExportFiles.writeTo(context, target, content)
        written.fold(
            onSuccess = { _state.update { it.copy(notice = "已导出 ${format.label}：${sizeText(content)}") } },
            onFailure = { error ->
                _state.update { it.copy(notice = "导出失败：${error.message ?: "未知原因"}") }
            },
        )
    })

    fun prepareShare(format: ExportFormat, range: ExportRange) = work(format, range, onSuccess = { content ->
        val uri = ExportFiles.prepareForShare(context, format, collector.fileNameFor(format, range), content)
        _state.update { it.copy(shareReady = uri) }
    })

    fun shareIntent(uri: Uri, format: ExportFormat): Intent =
        ExportFiles.shareIntent(uri, format, "泡泡日历导出（${format.label}）")

    fun shareConsumed() = _state.update { it.copy(shareReady = null) }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    private val context: Context get() = container.appContext

    /** 生成内容并交给 [onSuccess]；异常统一收敛成一条提示，不让它冒到协程外层。 */
    private fun work(format: ExportFormat, range: ExportRange, onSuccess: suspend (String) -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch(Dispatchers.Default) {
            val result = runCatching {
                val content = collector.build(format, range)
                withContext(Dispatchers.IO) { onSuccess(content) }
            }
            result.exceptionOrNull()?.let { error ->
                _state.update { it.copy(notice = "导出失败：${error.message ?: error.javaClass.simpleName}") }
            }
            _state.update { it.copy(busy = false) }
        }
    }

    private fun sizeText(content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8).size
        return if (bytes < 1024) "$bytes B" else "%.1f KB".format(bytes / 1024.0)
    }
}
