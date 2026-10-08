package com.thorium.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.thorium.app.spike.SpikeActivity
import com.thorium.app.ui.input.InputMapper
import com.thorium.app.ui.theme.ThoriumTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.onOpenDiagnostics = { startActivity(Intent(this, SpikeActivity::class.java)) }
        setContent { ThoriumTheme { ThoriumApp(vm) } }
    }

    override fun onDestroy() {
        super.onDestroy()
        vm.onOpenDiagnostics = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val result = InputMapper.map(event)
        if (!result.consume) return super.dispatchKeyEvent(event)
        val action = result.action
        if (action != null && event.action == KeyEvent.ACTION_DOWN && (event.repeatCount == 0 || action.repeatable)) {
            vm.handle(action)
        }
        return true
    }
}
