package com.kutlusoy.nearly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kutlusoy.nearly.ui.AppRoot
import com.kutlusoy.nearly.ui.theme.NearlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NearlyTheme {
                AppRoot()
            }
        }
    }
}
