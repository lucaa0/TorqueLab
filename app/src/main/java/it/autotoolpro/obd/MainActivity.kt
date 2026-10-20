package it.autotoolpro.obd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.autotoolpro.obd.ui.AppRoot
import it.autotoolpro.obd.ui.AutoToolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoToolTheme {
                AppRoot()
            }
        }
    }
}
