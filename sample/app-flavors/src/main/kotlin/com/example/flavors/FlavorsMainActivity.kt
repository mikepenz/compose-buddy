package com.example.flavors

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class FlavorsMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("compose-buddy flavors sample")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PublicFlavorPreview() {
    Text(text = Greeting.message)
}

@Preview
@Composable
fun FlavorBoxPreview() {
    Box(modifier = Modifier.size(120.dp).background(Color.Cyan))
}

@Preview(name = "Hidden", showBackground = true)
@Composable
private fun HiddenPreview() {
    Box(modifier = Modifier.size(80.dp).background(Color.Magenta))
}
