package com.linguabridge.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.linguabridge.app.R

/** Top-level bottom-bar destinations. Detail screens (review session,
 *  reader, quiz session) are pushed on top and defined per phase. */
enum class TopDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Today("today", R.string.nav_today, Icons.Filled.Today),
    Library("library", R.string.nav_library, Icons.Filled.Book),
    Practice("practice", R.string.nav_practice, Icons.Filled.Headphones),
    Stats("stats", R.string.nav_stats, Icons.Filled.Insights),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}
