package com.pilkupil.netcheck.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import kotlin.coroutines.coroutineContext

data class NetworkInfo(
  val hasNetwork: Boolean = false,
  val isValidated: Boolean = false,
  val connectionType: String = "Tidak ada jaringan",
  val localIp: String = "—",
  val wifiConnected: Boolean = false,
  val wifiInternet: Boolean = false,
  val ethernetConnected: Boolean = false,
  val ethernetInternet: Boolean = false,
  val cellularConnected: Boolean = false,
  val cellularInternet: Boolean = false
)

data class ConnectivityResult(
  val isOnline: Boolean,
  val latencyMs: Long?,
  val networkInfo: NetworkInfo
)

data class SpeedTestResult(
  val downloadMbps: Double,
  val uploadMbps: Double,
  val latencyMs: Long,
  val downloadedBytes: Long,
  val networkInfo: NetworkInfo
)

class NetworkChecker(context: Context) {
  private val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  fun getNetworkInfo(): NetworkInfo {
    val network = connectivityManager.activeNetwork
    val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
    val linkProperties = network?.let(connectivityManager::getLinkProperties)
    val ip = linkProperties?.linkAddresses
      ?.map { it.address }
      ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
      ?.hostAddress
      ?: "—"

    val type = when {
      capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
      capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Data seluler"
      capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet / LAN"
      capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
      else -> "Jaringan lainnya"
    }

    val allCapabilities = connectivityManager.allNetworks.mapNotNull { candidate ->
      connectivityManager.getNetworkCapabilities(candidate)
    }
    val wifiCapabilities = allCapabilities.filter {
      it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    val ethernetCapabilities = allCapabilities.filter {
      it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
    val cellularCapabilities = allCapabilities.filter {
      it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    return NetworkInfo(
      hasNetwork = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
      isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
      connectionType = type,
      localIp = ip,
      wifiConnected = wifiCapabilities.isNotEmpty(),
      wifiInternet = wifiCapabilities.any {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
      },
      ethernetConnected = ethernetCapabilities.isNotEmpty(),
      ethernetInternet = ethernetCapabilities.any {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
      },
      cellularConnected = cellularCapabilities.isNotEmpty(),
      cellularInternet = cellularCapabilities.any {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
      }
    )
  }

  suspend fun checkConnectivity(): ConnectivityResult = withContext(Dispatchers.IO) {
    val initialInfo = getNetworkInfo()
    val activeNetwork = connectivityManager.activeNetwork
    val wifiNetwork = findNetwork(NetworkCapabilities.TRANSPORT_WIFI)
    val ethernetNetwork = findNetwork(NetworkCapabilities.TRANSPORT_ETHERNET)
    val cellularNetwork = findNetwork(NetworkCapabilities.TRANSPORT_CELLULAR)
    val candidates = listOfNotNull(
      activeNetwork,
      wifiNetwork,
      ethernetNetwork,
      cellularNetwork
    ).distinct()

    if (candidates.isEmpty()) {
      return@withContext ConnectivityResult(false, null, initialInfo)
    }

    val results = candidates.map { network ->
      async { network to probeNetwork(network) }
    }.awaitAll().toMap()
    val activeResult = activeNetwork?.let(results::get)
    val wifiResult = wifiNetwork?.let(results::get)
    val ethernetResult = ethernetNetwork?.let(results::get)
    val cellularResult = cellularNetwork?.let(results::get)
    val info = getNetworkInfo().copy(
      wifiConnected = wifiNetwork != null,
      wifiInternet = wifiResult?.isOnline == true,
      ethernetConnected = ethernetNetwork != null,
      ethernetInternet = ethernetResult?.isOnline == true,
      cellularConnected = cellularNetwork != null,
      cellularInternet = cellularResult?.isOnline == true
    )
    val latency = activeResult?.latencyMs
      ?: results.values.filter { it.isOnline }.minOfOrNull { it.latencyMs ?: Long.MAX_VALUE }

    ConnectivityResult(
      isOnline = results.values.any { it.isOnline },
      latencyMs = latency?.takeUnless { it == Long.MAX_VALUE },
      networkInfo = info
    )
  }

  private fun findNetwork(transport: Int): Network? {
    val matchingNetworks = connectivityManager.allNetworks.filter { network ->
      connectivityManager.getNetworkCapabilities(network)?.hasTransport(transport) == true
    }
    return matchingNetworks.firstOrNull { network ->
      connectivityManager.getNetworkCapabilities(network)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } ?: matchingNetworks.firstOrNull()
  }

  private suspend fun probeNetwork(network: Network): ProbeResult {
    val systemValidated = connectivityManager.getNetworkCapabilities(network)
      ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    val endpoints = listOf(
      "https://connectivitycheck.gstatic.com/generate_204",
      "https://clients3.google.com/generate_204",
      "https://www.cloudflare.com/cdn-cgi/trace"
    )

    for (endpoint in endpoints) {
      coroutineContext.ensureActive()
      var connection: HttpURLConnection? = null
      try {
        val startedAt = SystemClock.elapsedRealtime()
        connection = (network.openConnection(URL(endpoint)) as HttpURLConnection).apply {
          requestMethod = "GET"
          connectTimeout = 4_000
          readTimeout = 4_000
          useCaches = false
          setRequestProperty("Cache-Control", "no-cache")
          setRequestProperty("User-Agent", "NetCheck-Android/1.0")
        }
        val responseCode = connection.responseCode
        val latency = SystemClock.elapsedRealtime() - startedAt
        // Respons HTTP apa pun dari server publik membuktikan jalur internet
        // dan TLS berhasil, walaupun server sedang mengembalikan 4xx/5xx.
        if (responseCode in 200..599) {
          return ProbeResult(true, latency)
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        // Endpoint cadangan mencegah satu penyedia yang gagal dianggap offline.
      } finally {
        connection?.disconnect()
      }
    }

    // Android juga memvalidasi internet di tingkat sistem. Ini menjadi fallback
    // bila semua server probe diblokir oleh DNS, firewall, atau jaringan kantor.
    return ProbeResult(systemValidated, null)
  }

  suspend fun runSpeedTest(): SpeedTestResult = withContext(Dispatchers.IO) {
    val connectivity = checkConnectivity()
    if (!connectivity.isOnline) throw IllegalStateException("Tidak ada akses internet")

    val targetBytes = 10_000_000L
    val url = URL("https://speed.cloudflare.com/__down?bytes=$targetBytes&t=${System.currentTimeMillis()}")
    var connection: HttpURLConnection? = null
    try {
      connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 15_000
        useCaches = false
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("User-Agent", "NetCheck-Android/1.0")
      }

      if (connection.responseCode !in 200..299) {
        throw IllegalStateException("Server tes tidak merespons")
      }

      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      var totalBytes = 0L
      val startedAt = SystemClock.elapsedRealtimeNanos()
      connection.inputStream.buffered().use { input ->
        while (true) {
          coroutineContext.ensureActive()
          val count = input.read(buffer)
          if (count < 0) break
          totalBytes += count
        }
      }
      val durationSeconds = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000_000.0
      if (totalBytes == 0L || durationSeconds <= 0.0) {
        throw IllegalStateException("Data tes tidak diterima")
      }

      val downloadMbps = calculateMbps(totalBytes, durationSeconds)
      connection.disconnect()

      val uploadBytes = 2_000_000
      connection = (URL("https://speed.cloudflare.com/__up?t=${System.currentTimeMillis()}")
        .openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 8_000
        readTimeout = 15_000
        useCaches = false
        setFixedLengthStreamingMode(uploadBytes)
        setRequestProperty("Content-Type", "application/octet-stream")
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("User-Agent", "NetCheck-Android/1.0")
      }
      val uploadBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
      var remaining = uploadBytes
      val uploadStartedAt = SystemClock.elapsedRealtimeNanos()
      connection.outputStream.buffered().use { output ->
        while (remaining > 0) {
          coroutineContext.ensureActive()
          val count = minOf(uploadBuffer.size, remaining)
          output.write(uploadBuffer, 0, count)
          remaining -= count
        }
      }
      if (connection.responseCode !in 200..299) {
        throw IllegalStateException("Server tes upload tidak merespons")
      }
      val uploadDurationSeconds =
        (SystemClock.elapsedRealtimeNanos() - uploadStartedAt) / 1_000_000_000.0

      SpeedTestResult(
        downloadMbps = downloadMbps,
        uploadMbps = calculateMbps(uploadBytes.toLong(), uploadDurationSeconds),
        latencyMs = connectivity.latencyMs ?: 0L,
        downloadedBytes = totalBytes,
        networkInfo = getNetworkInfo()
      )
    } finally {
      connection?.disconnect()
    }
  }

  companion object {
    fun calculateMbps(bytes: Long, durationSeconds: Double): Double =
      (bytes * 8.0) / durationSeconds / 1_000_000.0
  }

  private data class ProbeResult(val isOnline: Boolean, val latencyMs: Long?)
}
