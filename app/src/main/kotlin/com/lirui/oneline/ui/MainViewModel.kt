package com.lirui.oneline.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lirui.oneline.data.TodoRepository
import com.lirui.oneline.data.model.Todo
import com.lirui.oneline.parser.Parser
import com.lirui.oneline.parser.ParsedTodo
import com.lirui.oneline.util.AppTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 圆圈填充动画时长。UI 的 animateFloatAsState 与这里的 delay 必须同值，否则会出现"提前消失"。 */
const val CIRCLE_FILL_MILLIS = 650L

data class UiState(
    val active: List<Todo> = emptyList(),
    val archived: List<Todo> = emptyList()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TodoRepository(app)
    private val parser = Parser()

    val state: StateFlow<UiState> =
        combine(repo.active, repo.archived) { act, arch ->
            UiState(active = act, archived = arch)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    /** Latest parse result for the add sheet preview/error. */
    var lastParse by mutableStateOf<ParsedTodo?>(null)
        private set

    /** All parsed todos for the current input (one per mentioned date). */
    var lastParses by mutableStateOf<List<ParsedTodo>>(emptyList())
        private set

    fun parse(text: String) {
        val all = parser.parseAll(text)
        lastParses = all
        lastParse = all.firstOrNull()
    }

    /** Prefill the parse preview for an existing todo (for the edit sheet). */
    fun previewExisting(todo: Todo) {
        // Pinned zone: a device-timezone change must not shift what the stored epoch means.
        val zoned = todo.dueAtMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(AppTime.ZONE) }
        val parsed = ParsedTodo(
            title = todo.title,
            dueDate = zoned?.toLocalDate(),
            dueTime = zoned?.toLocalTime(),
            repeatDays = todo.repeatDays,
            detail = todo.detail.ifBlank { null }
        )
        lastParse = parsed
        lastParses = listOf(parsed)
    }

    /** @return a sealed outcome describing whether to submit or show an error. */
    fun add(text: String): AddResult {
        val parsedList = parser.parseAll(text)
        lastParses = parsedList
        lastParse = parsedList.firstOrNull()
        if (parsedList.isEmpty() || parsedList.all { it.cleanTitle.isBlank() }) return AddResult.Blank
        if (parsedList.any { it.dateError }) return AddResult.DateError
        if (parsedList.any { it.timeError }) return AddResult.TimeError
        // One todo per parsed date ("明天后天晚上开会" -> two reminders).
        viewModelScope.launch { parsedList.forEach { repo.addFromParsed(it) } }
        return AddResult.Success
    }

    /** Edit an existing todo's content from a re-parse. */
    fun edit(id: Long, text: String, keepRepeat: Boolean = true): AddResult {
        val raw = parser.parse(text)
        // Turning the repeat switch off drops the "每天/每周/每月" plan even if the sentence
        // still contains the repeat word.
        val parsed = if (keepRepeat) raw else raw.copy(repeatDays = 0)
        lastParse = parsed
        lastParses = listOf(parsed)
        if (parsed.cleanTitle.isBlank()) return AddResult.Blank
        if (parsed.dateError) return AddResult.DateError
        if (parsed.timeError) return AddResult.TimeError
        viewModelScope.launch { repo.editFromParsed(id, parsed) }
        return AddResult.Success
    }

    /** Create a "测试通知" todo due at the next whole minute (settings page test button). */
    fun addTestInOneMinute() {
        viewModelScope.launch { repo.addTestInOneMinute() }
    }

    fun archive(id: Long) {
        requestArchive(id)
    }

    // ---------- 归档：点击圆圈 / 右滑 共用的一套状态机 ----------
    //
    // ★ 为什么不能像 v0.3 那样写在 TodoRow 里：
    //     `val scope = rememberCoroutineScope()` 绑在行的组合上，行被 LazyColumn 回收
    //     （快速滑动、切 tab）时 scope 连同里面的 `delay(650)` 一起被取消，
    //     `onCircleTap()` 永远不执行 —— 这就是"点了圆圈再快速滑动，待办没归档"的根因。
    //
    // 现在的分工：
    //   - 状态：[_pendingArchive] 是"正在归档"的**唯一真相源**，行被回收再滑回来，
    //     勾选态照样在（`TodoRow` 用 `id in pendingArchive` 判断，不再自己 remember）。
    //   - 计时：挂在 `viewModelScope` 上，任何 UI 销毁都取消不了它。
    //   - 写库：仍然走 [TodoRepository.archive]，重复任务顺延的语义不变。

    private val _pendingArchive = MutableStateFlow<Set<Long>>(emptySet())

    /** 正在播归档动画的待办 id 集合。UI 用它渲染圆圈勾选态。 */
    val pendingArchive: StateFlow<Set<Long>> = _pendingArchive.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** 一次性提示（归档失败等）。UI 收集后用 Toast 呈现。 */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * 归档请求的唯一入口。
     *
     * @param animate true = 圆圈点击：先播完 650ms 填充动画再写库，视觉上"填满 -> 消失"；
     *                false = 右滑：行已经滑出屏幕，立即写库，不再多等。
     */
    fun requestArchive(id: Long, animate: Boolean = true) {
        // 防重复：动画期间再点一次（或点击后立刻右滑）不会排第二个任务。
        if (id in _pendingArchive.value) return
        _pendingArchive.update { it + id }
        viewModelScope.launch {
            try {
                if (animate) delay(CIRCLE_FILL_MILLIS)
                // ★ 动画期间被撤销（CompletedBar 的「撤销」）：pending 已被 unarchive 清掉，
                // 这里必须收手，否则 650ms 一到照样写库，撤销就成了假动作。
                if (id !in _pendingArchive.value) return@launch
                if (!repo.archive(id)) {
                    _messages.tryEmit("这条待办已经不在了")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _messages.tryEmit("归档失败：${error.message ?: "未知错误"}")
            } finally {
                // finally 而不是正常路径：失败 / 取消都要把勾选态收回来，
                // 否则那一行会永远停在"填满但没消失"。
                _pendingArchive.update { it - id }
            }
        }
    }

    fun unarchive(id: Long) {
        _pendingArchive.update { it - id }
        viewModelScope.launch { repo.unarchive(id) }
    }

    fun delete(id: Long) {
        _pendingArchive.update { it - id }
        viewModelScope.launch { repo.delete(id) }
    }
}

sealed class AddResult {
    object Success : AddResult()
    object Blank : AddResult()
    object DateError : AddResult()
    object TimeError : AddResult()
}