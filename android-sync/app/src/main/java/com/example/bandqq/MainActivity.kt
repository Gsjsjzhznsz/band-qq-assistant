package com.example.bandqq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.bandqq.ui.BandQQApp
import com.example.bandqq.ui.BandQQTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BandQQTheme { BandQQApp() } }
    }
}