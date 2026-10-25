package dev.localvoice.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary=Color(0xFF006B5D),onPrimary=Color.White,primaryContainer=Color(0xFFD8EFE8),onPrimaryContainer=Color(0xFF07382F),
    background=Color(0xFFF7FAF9),surface=Color(0xFFF7FAF9),surfaceContainer=Color(0xFFEEF4F1),surfaceContainerLow=Color.White,
    onSurface=Color(0xFF182724),onSurfaceVariant=Color(0xFF4B605B),outline=Color(0xFF697F77),outlineVariant=Color(0xFFD4DFD9),error=Color(0xFFB3261E),
)
private val Dark = darkColorScheme(
    primary=Color(0xFF82D8C0),onPrimary=Color(0xFF00382D),primaryContainer=Color(0xFF174E41),onPrimaryContainer=Color(0xFFB9F2DF),
    background=Color(0xFF101916),surface=Color(0xFF101916),surfaceContainer=Color(0xFF213029),surfaceContainerLow=Color(0xFF18231F),
    onSurface=Color(0xFFE4EFE9),onSurfaceVariant=Color(0xFFB2C7BE),outline=Color(0xFF81978D),outlineVariant=Color(0xFF3B4C43),error=Color(0xFFFFB4AB),
)
private val Type=Typography(
    headlineMedium=TextStyle(fontSize=28.sp,lineHeight=36.sp,fontWeight=FontWeight.SemiBold),
    titleLarge=TextStyle(fontSize=22.sp,lineHeight=28.sp,fontWeight=FontWeight.Medium),
    titleMedium=TextStyle(fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=26.sp),bodyMedium=TextStyle(fontSize=14.sp,lineHeight=22.sp),
    labelLarge=TextStyle(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold),
)
@Composable fun LocalVoiceTheme(theme: String="light",content: @Composable () -> Unit) {
    val dark=theme=="dark" || theme=="system" && isSystemInDarkTheme()
    MaterialTheme(colorScheme=if(dark) Dark else Light,typography=Type,content=content)
}
