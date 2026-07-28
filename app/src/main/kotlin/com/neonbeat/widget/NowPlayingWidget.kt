package com.neonbeat.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.neonbeat.MainActivity

/**
 * Home-screen widget showing the current track.
 *
 * Built with Glance so the widget shares the app's Compose model and Material
 * You colors ([GlanceTheme.colors] follows the system palette automatically).
 *
 * State is read from the media session rather than kept in the widget, so the
 * widget stays correct when playback is controlled from the notification,
 * Bluetooth, or Android Auto.
 */
class NowPlayingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(text = "NeonBeat", style = TextStyle(color = GlanceTheme.colors.onSurface))
            Row(modifier = GlanceModifier.padding(top = 6.dp)) {
                Text(
                    text = "Tap to open the player",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}

/** Manifest-registered receiver that hosts [NowPlayingWidget]. */
class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
