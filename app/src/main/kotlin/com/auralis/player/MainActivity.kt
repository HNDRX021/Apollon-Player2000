package com.auralis.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.player.playback.PlayerController
import com.auralis.player.settings.AppSettings
import com.auralis.player.ui.navigation.AuralisNavGraph
import com.auralis.player.ui.theme.AuralisTheme
import com.auralis.player.ui.theme.LocalCompactMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Fresh launch (not a rotation): put the last queue back, paused.
        if (savedInstanceState == null) playerController.restoreLastSession()

        setContent {
            var showSplash by remember { mutableStateOf(savedInstanceState == null) }
            LaunchedEffect(Unit) {
                delay(1900)
                showSplash = false
            }

            val settingsViewModel: SettingsBridgeViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsState(initial = AppSettings())

            RequestNotificationPermissionIfNeeded()

            AuralisTheme(settings = settings) {
                CompositionLocalProvider(LocalCompactMode provides settings.compactMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val backgroundUri = settings.backgroundImageUri
                        val backgroundBitmap by rememberBackgroundBitmap(backgroundUri)
                        Box(Modifier.fillMaxSize()) {
                            backgroundBitmap?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                val isDark = settings.themeMode == com.auralis.player.settings.ThemeMode.DARK ||
                                    (settings.themeMode == com.auralis.player.settings.ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())
                                Box(
                                    Modifier.fillMaxSize().background(
                                        if (isDark) Color.Black.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.58f),
                                    ),
                                )
                            }
                            Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                                AuralisNavGraph()
                            }
                            AnimatedVisibility(
                                visible = showSplash,
                                enter = fadeIn(animationSpec = tween(350)),
                                exit = fadeOut(animationSpec = tween(500)),
                            ) {
                                ApollonLoadingScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApollonLoadingScreen() {
    val coral = Color(0xFFFF5364)
    val backdrop = Brush.verticalGradient(
        colors = listOf(Color(0xFF17121D), Color(0xFF0D0C12), Color(0xFF09090D)),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(coral.copy(alpha = 0.15f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .shadow(28.dp, RoundedCornerShape(42.dp), ambientColor = coral.copy(alpha = 0.28f))
                    .clip(RoundedCornerShape(42.dp))
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        RoundedCornerShape(42.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_art),
                    contentDescription = "Apollon Player logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(30.dp))
            androidx.compose.material3.Text(
                text = "Apollon Player",
                color = Color(0xFFF8F5FA),
                fontSize = 29.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.Text(
                text = "Your Music, My Way",
                color = Color(0xFFB8AEBE),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.4.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            LoadingEqualizer(color = coral)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.2f)),
            )
            androidx.compose.material3.Text(
                text = "Made by HNDRX",
                color = Color(0xFFB8AEBE),
                fontSize = 12.sp,
                letterSpacing = 1.1.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.2f)),
            )
        }
    }
}

@Composable
private fun LoadingEqualizer(color: Color) {
    val transition = rememberInfiniteTransition(label = "splash-equalizer")
    Row(
        modifier = Modifier.height(34.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { index ->
            val level by transition.animateFloat(
                initialValue = 0.22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 420 + index * 75, delayMillis = index * 65),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "equalizer-bar-$index",
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((8 + level * 24).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFB06A), color),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun rememberBackgroundBitmap(uriString: String?): androidx.compose.runtime.State<Bitmap?> {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, uriString) {
        value = if (uriString.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(uriString)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    val metrics = context.resources.displayMetrics
                    var sampleSize = 1
                    while (bounds.outWidth / (sampleSize * 2) >= metrics.widthPixels &&
                        bounds.outHeight / (sampleSize * 2) >= metrics.heightPixels
                    ) {
                        sampleSize *= 2
                    }
                    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                }.getOrNull()
            }
        }
    }
}

/**
 * On Android 13+, the playback foreground service's notification stays
 * invisible without this runtime grant — asked for once, up front, rather
 * than only at the moment playback first starts, since that would mean
 * the very first song played has no visible transport notification.
 */
@Composable
private fun RequestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* no UI change either way — Settings will surface this later */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
