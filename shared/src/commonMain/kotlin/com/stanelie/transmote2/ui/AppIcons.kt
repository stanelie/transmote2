package com.stanelie.transmote2.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The Material icon libraries are frozen and have no current multiplatform
 * build: org.jetbrains.compose.material:material-icons-core stops at 1.7.3,
 * androidx's at 1.7.8, and material-icons-extended is an
 * androidx.compose.material artifact that clashes with Compose Multiplatform.
 *
 * This app needs six glyphs, so they are declared here from the standard
 * Material 24dp path data. PathParser is part of compose-ui, so this works
 * unchanged on Android and iOS.
 */
private fun materialIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()

object AppIcons {
    val ArrowBack: ImageVector by lazy {
        materialIcon("ArrowBack", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    }
    val Check: ImageVector by lazy {
        materialIcon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    }
    val KeyboardArrowDown: ImageVector by lazy {
        materialIcon("KeyboardArrowDown", "M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6 1.41,-1.41z")
    }
    val KeyboardArrowRight: ImageVector by lazy {
        materialIcon("KeyboardArrowRight", "M8.59,16.59L13.17,12 8.59,7.41 10,6l6,6 -6,6 -1.41,-1.41z")
    }
    val RssFeed: ImageVector by lazy {
        materialIcon(
            "RssFeed",
            "M6.18,17.82m-2.18,0a2.18,2.18 0,1 1,4.36 0a2.18,2.18 0,1 1,-4.36 0" +
                "M4,4.44v2.83c7.03,0 12.73,5.7 12.73,12.73h2.83c0,-8.59 -6.97,-15.56 -15.56,-15.56z" +
                "M4,10.1v2.83c3.9,0 7.07,3.17 7.07,7.07h2.83c0,-5.47 -4.43,-9.9 -9.9,-9.9z"
        )
    }
    val Settings: ImageVector by lazy {
        materialIcon(
            "Settings",
            "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58" +
                "c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96" +
                "c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84" +
                "c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33" +
                "c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58" +
                "C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61" +
                "l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54" +
                "c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54" +
                "c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32" +
                "c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6" +
                "s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"
        )
    }
}
