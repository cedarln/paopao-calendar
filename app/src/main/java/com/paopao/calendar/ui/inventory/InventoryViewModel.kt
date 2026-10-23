package com.paopao.calendar.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paopao.calendar.AppContainer
import com.paopao.calendar.data.local.entity.InventoryEntity
import com.paopao.calendar.domain.model.Quadrant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class InventoryMode {
    BOARD, LIST, TRASH, SEARCH
}

data class InventoryUiState(
    val mode: InventoryMode = InventoryMode.BOARD,
    val showDone: Boolean = false,
    val focusedQuadrant: Quadrant? = null,
    val query: String = "",
    val roots: List<InventoryEntity> = emptyList(),
    val trash: List<InventoryEntity> = emptyList(),
    val searchResults: List<InventoryEntity> = emptyList(),
    val activeCount: Int = 0,
) {
    val byQuadrant: Map<Quadrant, List<InventoryEntity>> get() = roots.groupBy { Quadrant.fromIndex(it.quadrantIndex) }
}

/**
 * 事项清单状态。
 *
 * 象限分组在内存里做而不是发四次查询：矩阵视图本来就要同时看到四格，
 * 一条 SQL 取回全量再 group 比四个订阅便宜，也让「拖到另一格」有唯一的写入入口。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val mode = MutableStateFlow(InventoryMode.BOARD)
    private val showDone = MutableStateFlow(false)
    private val focusedQuadrant = MutableStateFlow<Quadrant?>(null)
    private val query = MutableStateFlow("")

    val state: StateFlow<InventoryUiState> = combine(
        mode,
        showDone,
        focusedQuadrant,
        query,
    ) { m, done, quadrant, text -> Quad(m, done, quadrant, text) }
        .flatMapLatest { (m, done, quadrant, text) ->
            combine(
                container.inventoryRepository.observeActiveRoots(showDone = done),
                container.inventoryRepository.observeTrash(),
                container.inventoryRepository.observeActiveCount(),
                if (text.isBlank()) flowOf(emptyList()) else container.inventoryRepository.search(text),
            ) { roots, trash, activeCount, results ->
                InventoryUiState(
                    mode = m,
                    showDone = done,
                    focusedQuadrant = quadrant,
                    query = text,
                    roots = roots.filter { quadrant == null || Quadrant.fromIndex(it.quadrantIndex) == quadrant },
                    trash = trash,
                    searchResults = results,
                    activeCount = activeCount,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    init {
        viewModelScope.launch { container.inventoryRepository.purgeExpiredTrash() }
    }

    fun setMode(value: InventoryMode) {
        mode.value = value
    }

    fun setShowDone(value: Boolean) {
        showDone.value = value
    }

    fun focusQuadrant(quadrant: Quadrant?) {
        focusedQuadrant.value = quadrant
        if (quadrant != null) mode.value = InventoryMode.LIST
    }

    fun setQuery(value: String) {
        query.value = value
        if (value.isNotBlank()) mode.value = InventoryMode.SEARCH
    }

    fun toggleDone(item: InventoryEntity) {
        viewModelScope.launch { container.inventoryRepository.setDone(item.id, !item.done) }
    }

    /** 删除是软删除，所以撤销靠恢复而不是重录。 */
    fun moveToTrash(item: InventoryEntity) {
        viewModelScope.launch { container.inventoryRepository.softDelete(item.id) }
    }

    fun restore(item: InventoryEntity) {
        viewModelScope.launch { container.inventoryRepository.restore(item.id) }
    }

    fun emptyTrash() {
        viewModelScope.launch { container.inventoryRepository.purgeAllTrash() }
    }

    /** 快速新增：只填标题，双轴给默认值，细节留给编辑页。 */
    fun quickAdd(title: String, dueDate: LocalDate?) {
        viewModelScope.launch {
            container.inventoryRepository.upsert(
                InventoryEntity(
                    title = title.ifBlank { "未命名事项" },
                    dueEpochDay = dueDate?.toEpochDay(),
                ),
            )
        }
    }

    fun observeChildren(parentId: Long) = container.inventoryRepository.observeChildren(parentId)

    private data class Quad(
        val mode: InventoryMode,
        val showDone: Boolean,
        val quadrant: Quadrant?,
        val query: String,
    )
}
