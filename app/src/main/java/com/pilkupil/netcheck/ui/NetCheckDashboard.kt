package com.pilkupil.netcheck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilkupil.netcheck.network.NetworkChecker
import com.pilkupil.netcheck.network.NetworkInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF43E6A0)
private val Danger = Color(0xFFFF6B6B)
private val Muted = Color(0xFF91A0B8)
private val CardColor = Color(0xFF162033)
private val CardBorder = Color(0xFF25334A)

private enum class ConnectionStatus { CHECKING, ONLINE, OFFLINE }

@Composable
fun NetCheckDashboard(
  keepScreenOn: Boolean,
  onKeepScreenOnChange: (Boolean) -> Unit
) {
  val context = LocalContext.current
  val checker = remember { NetworkChecker(context.applicationContext) }
  var automaticCheck by rememberSaveable { mutableStateOf(true) }
  var intervalSeconds by rememberSaveable { mutableStateOf(5) }
  var status by remember { mutableStateOf(ConnectionStatus.CHECKING) }
  var networkInfo by remember { mutableStateOf(NetworkInfo()) }
  var latencyMs by rememberSaveable { mutableStateOf<Long?>(null) }
  var downloadMbps by rememberSaveable { mutableStateOf<Double?>(null) }
  var uploadMbps by rememberSaveable { mutableStateOf<Double?>(null) }
  var lastChecked by rememberSaveable { mutableStateOf("Belum diperiksa") }
  var isSpeedTesting by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  var history by remember { mutableStateOf<List<Boolean>>(emptyList()) }
  var networkDetailsExpanded by rememberSaveable { mutableStateOf(false) }
  var monitorSettingsExpanded by rememberSaveable { mutableStateOf(false) }

  suspend fun refreshConnection() {
    status = ConnectionStatus.CHECKING
    val result = checker.checkConnectivity()
    networkInfo = result.networkInfo
    latencyMs = result.latencyMs
    status = if (result.isOnline) ConnectionStatus.ONLINE else ConnectionStatus.OFFLINE
    lastChecked = currentTime()
    history = (history + result.isOnline).takeLast(12)
  }

  LaunchedEffect(automaticCheck, intervalSeconds) {
    if (automaticCheck) {
      while (true) {
        refreshConnection()
        delay(intervalSeconds * 1_000L)
      }
    }
  }

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("NETCHECK", color = Accent, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Internet health monitor", color = Muted, fontSize = 13.sp)
          }
          LiveBadge(automaticCheck)
        }
      }

      item {
        StatusHero(status = status, lastChecked = lastChecked)
      }

      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          MetricCard(
            modifier = Modifier.weight(1f),
            label = "DOWNLOAD",
            value = downloadMbps?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
            unit = "Mbps"
          )
          MetricCard(
            modifier = Modifier.weight(1f),
            label = "UPLOAD",
            value = uploadMbps?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
            unit = "Mbps"
          )
          MetricCard(
            modifier = Modifier.weight(1f),
            label = "LATENSI",
            value = latencyMs?.toString() ?: "—",
            unit = "ms"
          )
        }
      }

      item {
        DashboardCard {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { networkDetailsExpanded = !networkDetailsExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Detail jaringan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(
                "Aktif: ${networkInfo.connectionType}",
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1
              )
            }
            ExpandIndicator(networkDetailsExpanded)
          }

          if (networkDetailsExpanded) {
            Spacer(Modifier.height(16.dp))
            InfoRow("Jalur aktif", networkInfo.connectionType)
            Spacer(Modifier.height(10.dp))
            InfoRow("Alamat IP lokal", networkInfo.localIp)
            Spacer(Modifier.height(16.dp))
            TransportStatusRow(
              label = "Wi-Fi",
              connected = networkInfo.wifiConnected,
              internetAvailable = networkInfo.wifiInternet
            )
            Spacer(Modifier.height(10.dp))
            TransportStatusRow(
              label = "LAN / Ethernet",
              connected = networkInfo.ethernetConnected,
              internetAvailable = networkInfo.ethernetInternet
            )
            Spacer(Modifier.height(10.dp))
            TransportStatusRow(
              label = "Data seluler / SIM",
              connected = networkInfo.cellularConnected,
              internetAvailable = networkInfo.cellularInternet
            )
            Spacer(Modifier.height(14.dp))
            HistoryBar(history)
          }
        }
      }

      item {
        DashboardCard {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { monitorSettingsExpanded = !monitorSettingsExpanded },
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = automaticCheck,
              onCheckedChange = { automaticCheck = it },
              colors = CheckboxDefaults.colors(checkedColor = Accent, checkmarkColor = Color(0xFF07130E))
            )
            Column(modifier = Modifier.weight(1f)) {
              Text("Monitor otomatis", fontWeight = FontWeight.Bold)
              Text("Periksa akses internet berkala", color = Muted, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(
                if (automaticCheck) "AKTIF" else "MATI",
                color = if (automaticCheck) Accent else Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
              )
              ExpandIndicator(monitorSettingsExpanded)
            }
          }

          if (monitorSettingsExpanded) {
            Spacer(Modifier.height(14.dp))
            Text("Interval pengecekan", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(5, 10, 30).forEach { seconds ->
                IntervalButton(
                  seconds = seconds,
                  selected = intervalSeconds == seconds,
                  enabled = automaticCheck,
                  onClick = { intervalSeconds = seconds },
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }
        }
      }

      item {
        DashboardCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Layar selalu aktif", fontWeight = FontWeight.Bold)
              Text(
                if (keepScreenOn) "Layar tidak akan tidur selama aplikasi terbuka" else "Ikuti waktu tidur perangkat",
                color = Muted,
                fontSize = 13.sp
              )
            }
            Switch(
              checked = keepScreenOn,
              onCheckedChange = onKeepScreenOnChange,
              colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF07130E),
                checkedTrackColor = Accent,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = CardBorder
              )
            )
          }
        }
      }

      item {
        Button(
          onClick = {
            isSpeedTesting = true
            message = null
          },
          enabled = !isSpeedTesting,
          modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = Color(0xFF07130E),
            disabledContainerColor = Accent.copy(alpha = 0.45f)
          )
        ) {
          if (isSpeedTesting) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              strokeWidth = 2.dp,
              color = Color(0xFF07130E)
            )
            Text("  Mengukur kecepatan…", fontWeight = FontWeight.Bold)
          } else {
            Text("JALANKAN SPEED TEST", fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
          }
        }
        Text(
          text = message ?: "Tes manual menggunakan sekitar 12 MB data.",
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
          color = if (message != null) Danger else Muted,
          fontSize = 12.sp,
          textAlign = TextAlign.Center
        )
      }
    }
  }

  LaunchedEffect(isSpeedTesting) {
    if (!isSpeedTesting) return@LaunchedEffect
    try {
      val result = checker.runSpeedTest()
      downloadMbps = result.downloadMbps
      uploadMbps = result.uploadMbps
      latencyMs = result.latencyMs
      networkInfo = result.networkInfo
      status = ConnectionStatus.ONLINE
      lastChecked = currentTime()
      history = (history + true).takeLast(12)
    } catch (error: Exception) {
      if (error is CancellationException) throw error
      message = error.message ?: "Speed test gagal. Silakan coba lagi."
      refreshConnection()
    } finally {
      isSpeedTesting = false
    }
  }
}

@Composable
private fun StatusHero(status: ConnectionStatus, lastChecked: String) {
  val color = when (status) {
    ConnectionStatus.ONLINE -> Accent
    ConnectionStatus.OFFLINE -> Danger
    ConnectionStatus.CHECKING -> Muted
  }
  val label = when (status) {
    ConnectionStatus.ONLINE -> "INTERNET ONLINE"
    ConnectionStatus.OFFLINE -> "INTERNET OFFLINE"
    ConnectionStatus.CHECKING -> "MEMERIKSA…"
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
    shape = RoundedCornerShape(28.dp),
    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 27.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
        Canvas(Modifier.fillMaxSize()) {
          drawCircle(color = color.copy(alpha = 0.15f), style = Stroke(9.dp.toPx()))
          drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = if (status == ConnectionStatus.CHECKING) 100f else 360f,
            useCenter = false,
            style = Stroke(9.dp.toPx(), cap = StrokeCap.Round)
          )
        }
        Box(Modifier.size(25.dp).background(color, CircleShape))
      }
      Spacer(Modifier.height(16.dp))
      Text(label, color = color, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
      Text("Terakhir diperiksa: $lastChecked", color = Muted, fontSize = 12.sp)
    }
  }
}

@Composable
private fun MetricCard(modifier: Modifier, label: String, value: String, unit: String) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = CardColor),
    shape = RoundedCornerShape(18.dp)
  ) {
    Column(Modifier.padding(horizontal = 11.dp, vertical = 14.dp)) {
      Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 0.5.sp, maxLines = 1)
      Spacer(Modifier.height(8.dp))
      Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
      Text(unit, color = Muted, fontSize = 10.sp, maxLines = 1)
    }
  }
}

@Composable
private fun DashboardCard(content: @Composable ColumnScope.() -> Unit) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, CardBorder, RoundedCornerShape(22.dp)),
    colors = CardDefaults.cardColors(containerColor = CardColor),
    shape = RoundedCornerShape(22.dp)
  ) {
    Column(modifier = Modifier.padding(18.dp), content = content)
  }
}

@Composable
private fun ExpandIndicator(expanded: Boolean) {
  Box(
    modifier = Modifier
      .size(30.dp)
      .background(CardBorder, CircleShape),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = if (expanded) "▲" else "▼",
      color = Muted,
      fontSize = 9.sp,
      fontWeight = FontWeight.Black
    )
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = Muted, fontSize = 13.sp)
    Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
  }
}

@Composable
private fun TransportStatusRow(
  label: String,
  connected: Boolean,
  internetAvailable: Boolean
) {
  val status = when {
    internetAvailable -> "ONLINE"
    connected -> "TANPA INTERNET"
    else -> "TIDAK TERHUBUNG"
  }
  val statusColor = when {
    internetAvailable -> Accent
    connected -> Danger
    else -> Muted
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(CardBorder.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
      .padding(horizontal = 12.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(8.dp).background(statusColor, CircleShape))
      Text("  $label", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
    Text(status, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
  }
}

@Composable
private fun HistoryBar(history: List<Boolean>) {
  Column {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("12 pemeriksaan terakhir", color = Muted, fontSize = 11.sp)
      Text("${history.count { it }}/${history.size} online", color = Muted, fontSize = 11.sp)
    }
    Spacer(Modifier.height(8.dp))
    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
      val slots = 12
      val gap = 5.dp.toPx()
      val width = (size.width - gap * (slots - 1)) / slots
      repeat(slots) { index ->
        val historyIndex = history.size - slots + index
        val color = when {
          historyIndex < 0 -> CardBorder
          history[historyIndex] -> Accent
          else -> Danger
        }
        drawLine(
          color = color,
          start = Offset(index * (width + gap), size.height / 2),
          end = Offset(index * (width + gap) + width, size.height / 2),
          strokeWidth = size.height,
          cap = StrokeCap.Round
        )
      }
    }
  }
}

@Composable
private fun IntervalButton(
  seconds: Int,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.height(40.dp),
    shape = RoundedCornerShape(12.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = if (selected) Accent.copy(alpha = 0.16f) else CardBorder.copy(alpha = 0.65f),
      contentColor = if (selected) Accent else Muted,
      disabledContainerColor = CardBorder.copy(alpha = 0.3f),
      disabledContentColor = Muted.copy(alpha = 0.4f)
    ),
    contentPadding = PaddingValues(0.dp)
  ) {
    Text("$seconds detik", fontSize = 12.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun LiveBadge(enabled: Boolean) {
  Row(
    modifier = Modifier
      .background(if (enabled) Accent.copy(alpha = 0.12f) else CardColor, RoundedCornerShape(50))
      .padding(horizontal = 12.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.size(7.dp).background(if (enabled) Accent else Muted, CircleShape))
    Text(
      if (enabled) "  LIVE" else "  PAUSED",
      color = if (enabled) Accent else Muted,
      fontSize = 10.sp,
      fontWeight = FontWeight.Black
    )
  }
}

private fun currentTime(): String =
  SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

