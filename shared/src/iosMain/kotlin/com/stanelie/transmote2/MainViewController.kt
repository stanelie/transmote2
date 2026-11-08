package com.stanelie.transmote2

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS entry point. The Swift app wraps this UIViewController in a
 * UIViewControllerRepresentable, so iOS renders the same Compose UI
 * Android does.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
