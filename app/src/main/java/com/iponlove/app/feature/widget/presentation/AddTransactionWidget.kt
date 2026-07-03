package com.iponlove.app.feature.widget.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.iponlove.app.R

class AddTransactionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                AddTransactionWidgetContent()
            }
        }
    }
}

@Composable
private fun AddTransactionWidgetContent() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFFFFD9E3))
            .clickable(actionStartActivity(Intent(context, QuickAddActivity::class.java)))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Love, Ipon",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF9F3758)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_heart),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF9F3758))),
                    modifier = GlanceModifier.width(16.dp).height(16.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = "+ Add Txn",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF9F3758)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}
