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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import kotlin.coroutines.coroutineContext

data class NetworkInfo(
  val hasNetwork: Boolean = false,
  val isValidated: Boolean = false,
  val connectionType: String = "No network",
  val localIp: String = "—",
  val wifiConnected: Boolean = false,
  val wifiInternet: Boolean = false,
  val ethernetConnected: Boolean = false,
  val ethernetInternet: Boolean = false,
  val cellularConnected: Boolean = false,
  val cellularInternet: Boolean = false
)

data class PublicIpInfo(
  val ip: String,
  val version: String?,
  val network: String?,
  val asn: String?,
  val organization: String?,
  val city: String?,
  val region: String?,
  val regionCode: String?,
  val postalCode: String?,
  val countryName: String?,
  val countryCode: String?,
  val continentCode: String?,
  val latitude: String?,
  val longitude: String?,
  val timezone: String?,
  val utcOffset: String?,
  val currency: String?,
  val currencyName: String?,
  val callingCode: String?,
  val languages: String?
)

data class ConnectivityResult(
  val isOnline: Boolean,
  val latencyMs: Long?,
  val networkInfo: NetworkInfo,
  val publicIpInfo: PublicIpInfo?
)

data class SpeedTestResult(
  val downloadMbps: Double,
  val uploadMbps: Double,
  val latencyMs: Long,
  val downloadedBytes: Long,
  val networkInfo: NetworkInfo,
  val publicIpInfo: PublicIpInfo?
)

class NetworkChecker(context: Context) {
  private val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  private var cachedPublicIpInfo: PublicIpInfo? = null
  private var cachedPublicIpNetwork: String? = null
  private var cachedPublicIpAt = 0L

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
      capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular data"
      capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet / LAN"
      capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
      capabilities == null -> "No network"
      else -> "Other network"
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
      return@withContext ConnectivityResult(false, null, initialInfo, null)
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
    val publicIpNetwork = activeNetwork?.takeIf { results[it]?.isOnline == true }
      ?: results.entries.firstOrNull { it.value.isOnline }?.key
    val publicIpInfo = publicIpNetwork?.let(::fetchPublicIpInfo)

    ConnectivityResult(
      isOnline = results.values.any { it.isOnline },
      latencyMs = latency?.takeUnless { it == Long.MAX_VALUE },
      networkInfo = info,
      publicIpInfo = publicIpInfo
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
        // Any response from a public server proves that the route and TLS work,
        // even if the server currently returns a 4xx or 5xx response.
        if (responseCode in 200..599) {
          return ProbeResult(true, latency)
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        // Continue to a fallback endpoint before marking this route offline.
      } finally {
        connection?.disconnect()
      }
    }

    // Android system validation is the final fallback when probe endpoints are
    // blocked by DNS, a firewall, or a managed network.
    return ProbeResult(systemValidated, null)
  }

  private fun fetchPublicIpInfo(network: Network): PublicIpInfo? {
    val networkKey = network.toString()
    val now = SystemClock.elapsedRealtime()
    if (
      cachedPublicIpNetwork == networkKey &&
      now - cachedPublicIpAt < PUBLIC_IP_CACHE_MS
    ) {
      return cachedPublicIpInfo
    }

    var connection: HttpURLConnection? = null
    return try {
      connection = (network.openConnection(URL("https://ipapi.co/json/")) as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 5_000
        readTimeout = 5_000
        useCaches = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("User-Agent", "NetCheck-Android/1.0")
      }
      if (connection.responseCode !in 200..299) return null

      val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
      if (json.optBoolean("error", false)) return null
      val ip = json.optionalText("ip") ?: return null
      val info = PublicIpInfo(
        ip = ip,
        version = json.optionalText("version"),
        network = json.optionalText("network"),
        asn = json.optionalText("asn"),
        organization = json.optionalText("org"),
        city = json.optionalText("city"),
        region = json.optionalText("region"),
        regionCode = json.optionalText("region_code"),
        postalCode = json.optionalText("postal"),
        countryName = json.optionalText("country_name"),
        countryCode = json.optionalText("country_code") ?: json.optionalText("country"),
        continentCode = json.optionalText("continent_code"),
        latitude = json.optionalText("latitude"),
        longitude = json.optionalText("longitude"),
        timezone = json.optionalText("timezone"),
        utcOffset = json.optionalText("utc_offset"),
        currency = json.optionalText("currency"),
        currencyName = json.optionalText("currency_name"),
        callingCode = json.optionalText("country_calling_code"),
        languages = json.optionalText("languages")
      )
      cachedPublicIpInfo = info
      cachedPublicIpNetwork = networkKey
      cachedPublicIpAt = now
      info
    } catch (error: Exception) {
      if (error is CancellationException) throw error
      null
    } finally {
      connection?.disconnect()
    }
  }

  suspend fun runSpeedTest(): SpeedTestResult = withContext(Dispatchers.IO) {
    val connectivity = checkConnectivity()
    if (!connectivity.isOnline) throw IllegalStateException("No internet access")

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
        throw IllegalStateException("The speed test server did not respond")
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
        throw IllegalStateException("No test data was received")
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
        throw IllegalStateException("The upload test server did not respond")
      }
      val uploadDurationSeconds =
        (SystemClock.elapsedRealtimeNanos() - uploadStartedAt) / 1_000_000_000.0

      SpeedTestResult(
        downloadMbps = downloadMbps,
        uploadMbps = calculateMbps(uploadBytes.toLong(), uploadDurationSeconds),
        latencyMs = connectivity.latencyMs ?: 0L,
        downloadedBytes = totalBytes,
        networkInfo = getNetworkInfo(),
        publicIpInfo = connectivity.publicIpInfo
      )
    } finally {
      connection?.disconnect()
    }
  }

  companion object {
    private const val PUBLIC_IP_CACHE_MS = 5 * 60 * 1_000L

    fun calculateMbps(bytes: Long, durationSeconds: Double): Double =
      (bytes * 8.0) / durationSeconds / 1_000_000.0
  }

  private data class ProbeResult(val isOnline: Boolean, val latencyMs: Long?)
}

private fun JSONObject.optionalText(name: String): String? {
  if (!has(name) || isNull(name)) return null
  return optString(name).trim().takeIf { it.isNotEmpty() && it != "null" }
}
