package com.ipvio.stream.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

const val VERSION = "0.1.0"

fun main(args: Array<String>) {
    if ("--smoke" in args) {
        println("IPVIO Stream desktop $VERSION")
        return
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "IPVIO Stream",
            state = rememberWindowState(size = DpSize(1200.dp, 760.dp))
        ) {
            App()
        }
    }
}
