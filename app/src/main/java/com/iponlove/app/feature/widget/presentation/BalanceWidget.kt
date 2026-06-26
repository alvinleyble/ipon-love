package com.iponlove.app.feature.widget.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.iponlove.app.R
import com.iponlove.app.MainActivity
import com.iponlove.app.feature.widget.di.WidgetEntryPoint
import com.iponlove.app.feature.widget.domain.model.WidgetData
import dagger.hilt.android.EntryPointAccessors
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

class BalanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val data = runCatching { entryPoint.getWidgetDataUseCase()() }.getOrNull()

        provideContent {
            GlanceTheme {
                BalanceWidgetContent(data)
            }
        }
    }
}

@Composable
private fun BalanceWidgetContent(data: WidgetData?) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFFFFD9E3))
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = LocalContext.current.getString(R.string.app_name),
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(Color(0xFF9F3758)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.height(6.dp))
            if (data == null) {
                Text(
                    text = "Sign in to view balance",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFF3C0016)),
                        fontSize = 13.sp,
                    ),
                )
            } else {
                Text(
                    text = formatPeso(data.totalBalance),
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFF3C0016)),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = "Spent today  ${formatPeso(data.todaySpend)}",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFF9F3758)),
                        fontSize = 12.sp,
                    ),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(Intent(context, QuickAddActivity::class.java))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+ Quick add",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFF9F3758)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

private fun formatPeso(amount: BigDecimal): String {
    val fmt = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "₱ ${fmt.format(amount)}"
}
