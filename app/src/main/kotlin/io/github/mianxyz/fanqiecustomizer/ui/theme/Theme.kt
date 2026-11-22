package io.github.mianxyz.fanqiecustomizer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 液态玻璃档位随主题树向下传递，卡片/底栏按需读取。
 * 用 staticCompositionLocalOf：档位切换较少，读取方很多。
 */
val LocalGlassLevel = staticCompositionLocalOf { GlassLevel.None }

@Composable
fun FanqieCustomizerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    glassLevel: GlassLevel = GlassLevel.None,
    /** 跟随系统动态取色（Android 12+）。默认关闭，保持模块自身的鲜明强调色。 */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(LocalGlassLevel provides glassLevel) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

/** 让状态栏/导航栏图标随主题反色。 */
@Composable
fun ApplySystemBars(darkTheme: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val window = activity.window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = window.insetsController ?: return
        val light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        controller.setSystemBarsAppearance(if (darkTheme) 0 else light, light)
    }
}
