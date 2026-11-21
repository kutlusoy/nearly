package com.kutlusoy.nearly.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.ui.theme.NearlyColors

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun BrandMark(icon: ImageVector, size: Int = 96, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size.dp).background(Brush.linearGradient(listOf(NearlyColors.Rose, NearlyColors.RoseSoft)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size((size / 2).dp))
    }
}

// The Nearly logo: the pin on the rose circle, the same picture as the launcher icon and the splash screen
@Composable
fun NearlyLogo(size: Int = 96, modifier: Modifier = Modifier) =
    BrandMark(ImageVector.vectorResource(R.drawable.ic_nearly_pin), size, modifier)

@Composable
fun BusyOverlay(title: String, help: String) {
    Surface(modifier = Modifier.fillMaxSize().clickable(enabled = false) {}, color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(32.dp)) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Box(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Box(Modifier.height(8.dp))
            Text(help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun errorText(error: AppError?): String? = when (error) {
    AppError.SignInFailed -> stringResource(R.string.signin_wrong)
    AppError.AccountExists -> stringResource(R.string.reg_exists)
    AppError.Generic -> stringResource(R.string.error_generic)
    null -> null
}
