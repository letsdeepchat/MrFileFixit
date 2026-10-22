package com.mrfilefixit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mrfilefixit.app.navigation.MffApp
import com.mrfilefixit.core.ui.ads.AdSlots
import com.mrfilefixit.core.ui.ads.LocalAdSlots
import com.mrfilefixit.core.ui.theme.MffTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var adSlots: AdSlots

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()
            MffTheme(themeMode = prefs.themeMode, dynamicColor = prefs.dynamicColor) {
                CompositionLocalProvider(LocalAdSlots provides adSlots) {
                    MffApp()
                }
            }
        }
        // Consent first, ads SDK second. Runs after the first frame so it never delays cold start.
        if (savedInstanceState == null) {
            lifecycleScope.launch { adSlots.initialize(this@MainActivity) }
        }
    }
}
