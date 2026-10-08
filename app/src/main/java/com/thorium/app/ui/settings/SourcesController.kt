package com.thorium.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thorium.app.R
import com.thorium.app.ui.main.Hint
import com.thorium.core.model.SourceConfig
import com.thorium.core.model.SourceType
import com.thorium.core.ui.input.GamepadAction
import com.thorium.core.ui.keyboard.KeyboardController
import com.thorium.core.ui.text.UiText

/** The form being edited. A null [password] means "keep the stored one"; an empty one clears it. */
data class SourceDraft(
    val id: Long = 0,
    val name: String = "",
    val type: SourceType = SourceType.Http,
    val location: String = "",
    val username: String = "",
    val password: String? = null,
    val hasPassword: Boolean = false,
    val allowInsecure: Boolean = false,
) {
    fun toConfig() = SourceConfig(
        id = id, name = name, type = type, location = location, username = username,
        hasPassword = hasPassword, allowInsecure = allowInsecure,
    )

    companion object {
        fun from(config: SourceConfig) = SourceDraft(
            id = config.id, name = config.name, type = config.type, location = config.location,
            username = config.username, hasPassword = config.hasPassword, allowInsecure = config.allowInsecure,
        )
    }
}

sealed interface TestResult {
    data object Idle : TestResult
    data object Running : TestResult
    data object Ok : TestResult
    data class Failed(val reason: UiText) : TestResult
}

/** Rows of the source form; which ones show depends on the source type. */
enum class FormRow { Name, Type, Location, Username, Password, Insecure, Test, Save, Delete }

/** What the sources screens need from the rest of the app. */
interface SourcesHost {
    val sources: List<SourceConfig>
    /** [verified] is true when the last test of this very draft succeeded. */
    fun saveSource(draft: SourceDraft, verified: Boolean)
    fun removeSource(id: Long)
    fun testSource(draft: SourceDraft, onResult: (TestResult) -> Unit)
    fun toast(message: UiText)
}

/** Focus and editing for the Sources list and the add/edit form. */
class SourcesController(
    private val host: SourcesHost,
    private val keyboard: KeyboardController,
    private val goTo: (SettingsPage) -> Unit,
    private val onBrowse: (SourceConfig) -> Unit,
) {
    var listIndex by mutableIntStateOf(0); private set
    var formIndex by mutableIntStateOf(0); private set
    var draft by mutableStateOf(SourceDraft()); private set
    var testResult by mutableStateOf<TestResult>(TestResult.Idle); private set

    val editing: Boolean get() = draft.id != 0L

    val visibleRows: List<FormRow>
        get() = buildList {
            add(FormRow.Name)
            add(FormRow.Type)
            add(FormRow.Location)
            if (draft.type != SourceType.Local) {
                add(FormRow.Username)
                add(FormRow.Password)
            }
            if (draft.type == SourceType.Http || draft.type == SourceType.Catalog) add(FormRow.Insecure)
            add(FormRow.Test)
            add(FormRow.Save)
            if (editing) add(FormRow.Delete)
        }

    val listHints: List<Hint>
        get() = listOf(
            Hint("A", R.string.hint_open), Hint("SELECT", R.string.hint_edit),
            Hint("Y", R.string.hint_remove), Hint("B", R.string.hint_back),
        )

    val formHints: List<Hint>
        get() = listOf(Hint("A", R.string.hint_select), Hint("B", R.string.hint_back))

    /** Number of rows on the list page: the sources plus the "Add source" row. */
    private val listSize: Int get() = host.sources.size + 1

    fun handleList(action: GamepadAction): Boolean {
        listIndex = listIndex.coerceIn(0, listSize - 1)
        when (action) {
            GamepadAction.Up -> listIndex = (listIndex - 1).coerceAtLeast(0)
            GamepadAction.Down -> listIndex = (listIndex + 1).coerceAtMost(listSize - 1)
            // A browses a source; the "Add source" row opens an empty form; SELECT edits.
            GamepadAction.Select -> {
                val source = host.sources.getOrNull(listIndex)
                if (source != null) onBrowse(source) else openForm(SourceDraft())
            }
            GamepadAction.Secondary -> host.sources.getOrNull(listIndex)?.let { openForm(SourceDraft.from(it)) }
            GamepadAction.Favorite -> host.sources.getOrNull(listIndex)?.let {
                host.removeSource(it.id)
                listIndex = (listIndex - 1).coerceAtLeast(0)
            }
            GamepadAction.Back -> goTo(SettingsPage.Main)
            else -> return false
        }
        return true
    }

    fun handleForm(action: GamepadAction): Boolean {
        val rows = visibleRows
        formIndex = formIndex.coerceIn(0, rows.size - 1)
        when (action) {
            GamepadAction.Up -> formIndex = (formIndex - 1).coerceAtLeast(0)
            GamepadAction.Down -> formIndex = (formIndex + 1).coerceAtMost(rows.size - 1)
            GamepadAction.Select -> activate(rows[formIndex])
            GamepadAction.Back -> goTo(SettingsPage.Sources)
            else -> return false
        }
        return true
    }

    private fun openForm(newDraft: SourceDraft) {
        draft = newDraft
        formIndex = 0
        testResult = TestResult.Idle
        goTo(SettingsPage.SourceForm)
    }

    private fun activate(row: FormRow) {
        when (row) {
            FormRow.Name -> edit(R.string.field_name, draft.name) { draft = draft.copy(name = it) }
            FormRow.Type -> {
                val types = SourceType.entries
                draft = draft.copy(type = types[(draft.type.ordinal + 1) % types.size])
                testResult = TestResult.Idle
            }
            FormRow.Location -> edit(locationLabel(draft.type), draft.location) {
                draft = draft.copy(location = it)
                testResult = TestResult.Idle
            }
            FormRow.Username -> edit(userLabel(draft.type), draft.username) { draft = draft.copy(username = it) }
            FormRow.Password -> edit(passwordLabel(draft.type), "", masked = true) {
                // Leaving it empty on an existing source keeps the stored password; use a new value to replace it.
                if (it.isNotEmpty()) draft = draft.copy(password = it, hasPassword = true)
            }
            FormRow.Insecure -> draft = draft.copy(allowInsecure = !draft.allowInsecure)
            FormRow.Test -> runTest()
            FormRow.Save -> save()
            FormRow.Delete -> {
                host.removeSource(draft.id)
                goTo(SettingsPage.Sources)
            }
        }
    }

    private fun edit(@androidx.annotation.StringRes label: Int, initial: String, masked: Boolean = false, onDone: (String) -> Unit) {
        keyboard.open(UiText.res(label), initial, masked, onDone)
    }

    private fun runTest() {
        if (draft.location.isBlank()) {
            host.toast(UiText.res(R.string.toast_source_needs_location))
            return
        }
        testResult = TestResult.Running
        host.testSource(draft) { testResult = it }
    }

    private fun save() {
        if (draft.location.isBlank()) {
            host.toast(UiText.res(R.string.toast_source_needs_location))
            return
        }
        host.saveSource(draft.copy(name = draft.name.ifBlank { draft.location }), verified = testResult == TestResult.Ok)
        goTo(SettingsPage.Sources)
    }

    companion object {
        @androidx.annotation.StringRes
        fun locationLabel(type: SourceType) = when (type) {
            SourceType.Http -> R.string.field_location_http
            SourceType.Catalog -> R.string.field_location_catalog
            SourceType.InternetArchive -> R.string.field_location_ia
            SourceType.Local -> R.string.field_location_local
        }

        @androidx.annotation.StringRes
        fun userLabel(type: SourceType) =
            if (type == SourceType.InternetArchive) R.string.field_access_key else R.string.field_user

        @androidx.annotation.StringRes
        fun passwordLabel(type: SourceType) =
            if (type == SourceType.InternetArchive) R.string.field_secret_key else R.string.field_password

        @androidx.annotation.StringRes
        fun typeLabel(type: SourceType) = when (type) {
            SourceType.Http -> R.string.type_http
            SourceType.Catalog -> R.string.type_catalog
            SourceType.InternetArchive -> R.string.type_internet_archive
            SourceType.Local -> R.string.type_local
        }
    }
}
