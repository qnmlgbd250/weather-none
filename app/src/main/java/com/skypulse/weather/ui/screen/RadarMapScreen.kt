package com.skypulse.weather.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.ui.theme.SetLightStatusBarEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// --- Radar station data ---

private data class RadarStation(
    val path: String,      // URL path: /publish/radar/{path}.htm
    val name: String,      // Display name
    val lat: Double,
    val lon: Double
)

private val RADAR_STATIONS = listOf(
    RadarStation("bei-jing/da-xing", "北京大兴", 39.74, 116.34),
    RadarStation("bei-jing/xi-la-shan", "北京西山", 39.95, 116.20),
    RadarStation("tian-jin/bin-hai", "天津滨海", 39.03, 117.70),
    RadarStation("he-bei/shi-jia-zhuang", "石家庄", 38.04, 114.51),
    RadarStation("he-bei/bao-ding", "保定", 38.87, 115.46),
    RadarStation("shan-xi/tai-yuan", "太原", 37.87, 112.55),
    RadarStation("nei-meng-gu/hu-he-hao-te", "呼和浩特", 40.84, 111.75),
    RadarStation("liao-ning/shen-yang", "沈阳", 41.80, 123.43),
    RadarStation("liao-ning/da-lian", "大连", 38.91, 121.60),
    RadarStation("ji-lin/chang-chun", "长春", 43.88, 125.32),
    RadarStation("hei-long-jiang/ha-er-bin", "哈尔滨", 45.75, 126.65),
    RadarStation("shang-hai/pu-dong", "上海浦东", 31.22, 121.54),
    RadarStation("jiang-su/nan-jing", "南京", 32.06, 118.80),
    RadarStation("jiang-su/su-zhou", "苏州", 31.30, 120.62),
    RadarStation("zhe-jiang/hang-zhou", "杭州", 30.27, 120.15),
    RadarStation("zhe-jiang/wen-zhou", "温州", 28.00, 120.67),
    RadarStation("an-hei/he-fei", "合肥", 31.82, 117.23),
    RadarStation("fu-jian/fu-zhou", "福州", 26.07, 119.30),
    RadarStation("fu-jian/xia-men", "厦门", 24.48, 118.09),
    RadarStation("jiang-xi/nan-chang", "南昌", 28.68, 115.86),
    RadarStation("shan-dong/ji-nan", "济南", 36.65, 116.99),
    RadarStation("shan-dong/qing-dao", "青岛", 36.07, 120.38),
    RadarStation("he-nan/zheng-zhou", "郑州", 34.75, 113.65),
    RadarStation("hu-bei/wu-han", "武汉", 30.59, 114.30),
    RadarStation("hu-nan/chang-sha", "长沙", 28.23, 112.94),
    RadarStation("guang-dong/guang-zhou", "广州", 23.13, 113.26),
    RadarStation("guang-dong/shen-zhen", "深圳", 22.54, 114.06),
    RadarStation("guang-xi/nan-ning", "南宁", 22.82, 108.32),
    RadarStation("hai-nan/hai-kou", "海口", 20.04, 110.35),
    RadarStation("si-chuan/cheng-du", "成都", 30.57, 104.07),
    RadarStation("gui-zhou/gui-yang", "贵阳", 26.65, 106.63),
    RadarStation("yun-nan/kun-ming", "昆明", 25.04, 102.68),
    RadarStation("xi-zang/la-sa", "拉萨", 29.65, 91.13),
    RadarStation("shan-xi/xi-an", "西安", 34.26, 108.94),
    RadarStation("gan-su/lan-zhou", "兰州", 36.06, 103.83),
    RadarStation("qing-hai/xi-ning", "西宁", 36.62, 101.78),
    RadarStation("ning-xia/yin-chuan", "银川", 38.49, 106.23),
    RadarStation("xin-jiang/wu-lu-mu-qi", "乌鲁木齐", 43.80, 87.60)
)

private data class RadarFrame(
    val imageUrl: String,
    val publishTime: String
)

private fun findNearestStation(lat: Double, lon: Double): RadarStation {
    return RADAR_STATIONS.minByOrNull { station ->
        haversine(lat, lon, station.lat, station.lon)
    } ?: RADAR_STATIONS[0]
}

private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarMapScreen(
    cityName: String,
    longitude: Double,
    latitude: Double,
    onBack: () -> Unit
) {
    SetLightStatusBarEffect(lightStatusBar = true)

    val station = remember { findNearestStation(latitude, longitude) }
    var frames by remember { mutableStateOf<List<RadarFrame>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    // Preload all images into memory for smooth crossfade
    val context = LocalContext.current
    val imageLoader = remember { coil.ImageLoader.Builder(context).build() }

    // Fetch radar data
    LaunchedEffect(station) {
        try {
            val result = withContext(Dispatchers.IO) { fetchRadarFrames(station) }
            if (result.isNotEmpty()) {
                frames = result
                currentIndex = 0
                // Preload all images
                frames.forEach { frame ->
                    val req = ImageRequest.Builder(context).data(frame.imageUrl).build()
                    imageLoader.enqueue(req)
                }
                // Wait a moment for first images to cache
                delay(500)
                isPlaying = true
            } else {
                errorMessage = "暂无雷达数据"
            }
        } catch (e: Exception) {
            errorMessage = "加载失败: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Auto-play with smooth crossfade timing
    LaunchedEffect(isPlaying, frames.size) {
        if (isPlaying && frames.size > 1) {
            while (isActive) {
                delay(600)
                currentIndex = (currentIndex + 1) % frames.size
            }
        }
    }

    // Crossfade alpha: animate between 0 and 1 for smooth transition
    val crossfadeAlpha by animateFloatAsState(
        targetValue = if (currentIndex % 2 == 0) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "crossfade"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SecondaryScreenGradient[0],
                        SecondaryScreenGradient[1],
                        SecondaryScreenGradient[2]
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "天气地图",
                            color = SecondaryTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "雷达站: ${station.name}",
                            color = SecondaryTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = SecondaryTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            // Time display
            if (frames.isNotEmpty() && currentIndex in frames.indices) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cityName,
                        color = SecondaryTextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = frames[currentIndex].publishTime,
                        color = SecondaryAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Radar image area with crossfade
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SecondaryPanel),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = SecondaryAccent,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "正在加载 ${station.name} 雷达数据...",
                                color = SecondaryTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    errorMessage != null -> {
                        Text(
                            text = errorMessage!!,
                            color = SecondaryTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                    frames.isNotEmpty() && currentIndex in frames.indices -> {
                        // Crossfade between two layers for smooth animation
                        if (frames.size > 1) {
                            val prevIndex = if (currentIndex > 0) currentIndex - 1 else frames.lastIndex
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(frames[prevIndex].imageUrl)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().alpha(1f - crossfadeAlpha)
                            )
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(frames[currentIndex].imageUrl)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = "雷达图",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().alpha(crossfadeAlpha)
                            )
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(frames[currentIndex].imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "雷达图",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Frame counter overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.45f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${currentIndex + 1}/${frames.size}",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Controls
            if (frames.size > 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .padding(bottom = 24.dp)
                ) {
                    // Timeline slider
                    Slider(
                        value = currentIndex.toFloat(),
                        onValueChange = {
                            currentIndex = it.toInt()
                            isPlaying = false
                        },
                        valueRange = 0f..(frames.size - 1).toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = SecondaryAccent,
                            activeTrackColor = SecondaryAccent,
                            inactiveTrackColor = SecondaryPanel
                        )
                    )

                    // Play/Pause button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier
                                .size(48.dp)
                                .background(SecondaryPanel, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = SecondaryTextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun fetchRadarFrames(station: RadarStation): List<RadarFrame> {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val url = "https://www.nmc.cn/publish/radar/${station.path}.htm"
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
        .build()

    val response = client.newCall(request).execute()
    val html = response.body?.string() ?: return emptyList()

    val imgPattern = Regex("""data-img="([^"]+)"""")
    val timePattern = Regex("""data-time="([^"]+)"""")
    val imgMatches = imgPattern.findAll(html).toList()
    val timeMatches = timePattern.findAll(html).toList()

    val result = mutableListOf<RadarFrame>()
    val count = minOf(imgMatches.size, timeMatches.size)
    for (i in 0 until count) {
        val imageUrl = imgMatches[i].groupValues[1]
        val publishTime = timeMatches[i].groupValues[1]
        if (imageUrl.isNotBlank()) {
            result.add(RadarFrame(imageUrl = imageUrl, publishTime = publishTime))
        }
    }

    // Reverse so oldest frame is first
    return result.reversed()
}
