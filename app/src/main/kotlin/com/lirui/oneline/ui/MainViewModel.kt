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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        viewModelScope.launch { repo.archive(id) }
    }

    fun unarchive(id: Long) {
        viewModelScope.launch { repo.unarchive(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}

sealed class AddResult {
    object Success : AddResult()
    object Blank : AddResult()
    object DateError : AddResult()
    object TimeError : AddResult()
}