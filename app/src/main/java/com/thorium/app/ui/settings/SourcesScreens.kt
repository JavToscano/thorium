package com.thorium.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.thorium.app.R
import com.thorium.core.ui.text.resolve

@Composable
internal fun SourcesPage(c: SettingsController) {
    val s = c.sourcesUi
    PageFrame(stringResource(R.string.sources_title), stringResource(R.string.sources_subtitle)) {
        val rows = c.host.sources.map<com.thorium.core.model.SourceConfig, com.thorium.core.model.SourceConfig?> { it } + null
        FocusList(rows, s.listIndex) { _, source, focused ->
            if (source == null) {
                ListRow(stringResource(R.string.sources_add_title), stringResource(R.string.sources_add_desc), focused)
            } else {
                ListRow(
                    title = source.name,
                    detail = stringResource(SourcesController.typeLabel(source.type)) + " · " + source.location,
                    focused = focused,
                    trailing = when (source.healthy) {
                        true -> stringResource(R.string.sources_status_ok)
                        false -> stringResource(R.string.sources_status_failed)
                        null -> null
                    },
                )
            }
        }
    }
}

@Composable
internal fun SourceFormPage(c: SettingsController) {
    val s = c.sourcesUi
    val draft = s.draft
    PageFrame(stringResource(if (s.editing) R.string.form_title_edit else R.string.form_title_new), null) {
        FocusList(s.visibleRows, s.formIndex) { _, row, focused ->
            val notSet = stringResource(R.string.field_not_set)
            when (row) {
                FormRow.Name -> ListRow(stringResource(R.string.field_name), draft.name.ifBlank { notSet }, focused)
                FormRow.Type -> ListRow(stringResource(R.string.field_type), stringResource(SourcesController.typeLabel(draft.type)), focused)
                FormRow.Location -> ListRow(
                    stringResource(SourcesController.locationLabel(draft.type)), draft.location.ifBlank { notSet }, focused,
                )
                FormRow.Username -> ListRow(
                    stringResource(SourcesController.userLabel(draft.type)), draft.username.ifBlank { notSet }, focused,
                )
                FormRow.Password -> ListRow(
                    stringResource(SourcesController.passwordLabel(draft.type)),
                    if (draft.hasPassword) "••••••••" else notSet, focused,
                )
                FormRow.Insecure -> ListRow(
                    stringResource(R.string.field_insecure), stringResource(R.string.field_insecure_desc), focused,
                    trailing = stringResource(if (draft.allowInsecure) R.string.value_on else R.string.value_off),
                )
                FormRow.Console -> ListRow(
                    stringResource(R.string.field_default_console),
                    draft.defaultPlatformId?.let { id -> s.consoleChoices.firstOrNull { it?.id == id }?.name }
                        ?: stringResource(R.string.default_console_ask),
                    focused,
                )
                FormRow.Test -> ListRow(
                    stringResource(R.string.action_test),
                    when (val r = s.testResult) {
                        TestResult.Idle -> stringResource(R.string.test_idle)
                        TestResult.Running -> stringResource(R.string.test_running)
                        TestResult.Ok -> stringResource(R.string.test_ok)
                        is TestResult.Failed -> r.reason.resolve()
                    },
                    focused,
                )
                FormRow.Save -> ListRow(stringResource(R.string.action_save), null, focused)
                FormRow.Delete -> ListRow(stringResource(R.string.action_delete), null, focused)
            }
        }
    }
}

@Composable
internal fun SourceConsolePage(c: SettingsController) {
    val s = c.sourcesUi
    PageFrame(stringResource(R.string.field_default_console), stringResource(R.string.default_console_subtitle)) {
        FocusList(s.consoleChoices, s.consoleIndex) { _, system, focused ->
            if (system == null) {
                ListRow(stringResource(R.string.default_console_ask), stringResource(R.string.default_console_ask_desc), focused)
            } else {
                ListRow(system.name, stringResource(R.string.setup_folder_detail, system.id), focused)
            }
        }
    }
}
