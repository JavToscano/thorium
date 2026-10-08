package com.thorium.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.thorium.app.ui.main.MainActivity
import com.thorium.feature.display.DisplayCoordinator

/**
 * The activity behind the app icon. It shows nothing: it starts [MainActivity] on the top
 * screen and finishes. Without it, opening the app from the bottom screen's launcher would put the
 * main UI on the bottom screen and the companion on the top one.
 */
class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DisplayCoordinator(this).launchOnDefaultDisplay(this, MainActivity::class.java)) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
