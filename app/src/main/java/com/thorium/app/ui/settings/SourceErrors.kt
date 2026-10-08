package com.thorium.app.ui.settings

import com.thorium.app.R
import com.thorium.core.model.SourceException
import com.thorium.core.ui.text.UiText

/** Turns a failure from a source into a message the user can act on. */
object SourceErrors {
    fun explain(error: Throwable): UiText = when (error) {
        is SourceException.Unauthorized -> UiText.res(R.string.test_err_auth)
        is SourceException.NotFound -> UiText.res(R.string.test_err_not_found)
        is SourceException.InsecureConnection -> UiText.res(R.string.test_err_insecure)
        is SourceException.InvalidLocation -> UiText.res(R.string.test_err_invalid, error.message.orEmpty())
        is SourceException.Network -> UiText.res(R.string.test_err_network)
        else -> UiText.res(R.string.test_err_bad)
    }
}
