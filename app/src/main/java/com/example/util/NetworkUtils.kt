package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

  fun getLocalIpAddress(context: Context): String {
    // Try via NetworkInterface first (most accurate for both Wi-Fi, Ethernet, and Hotspot/AP mode)
    try {
      val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
      // Check Wi-Fi and Ethernet interfaces first
      val priorityInterfaces = listOf("wlan", "eth", "ap", "rndis")
      
      for (prefix in priorityInterfaces) {
        val target = interfaces.firstOrNull { it.name.startsWith(prefix, ignoreCase = true) && it.isUp }
        if (target != null) {
          for (addr in Collections.list(target.inetAddresses)) {
            if (!addr.isLoopbackAddress && addr is Inet4Address) {
              val ip = addr.hostAddress
              if (!ip.isNullOrEmpty() && ip != "0.0.0.0") {
                return ip
              }
            }
          }
        }
      }

      // Fallback: any active IPv4 non-loopback interface
      for (intf in interfaces) {
        if (!intf.isUp || intf.isLoopback) continue
        for (addr in Collections.list(intf.inetAddresses)) {
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            val ip = addr.hostAddress
            if (!ip.isNullOrEmpty() && ip != "0.0.0.0") {
              return ip
            }
          }
        }
      }
    } catch (_: Exception) {
    }

    // Secondary fallback: WifiManager
    try {
      val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
      if (ipInt != 0) {
        val ip = String.format(
          "%d.%d.%d.%d",
          ipInt and 0xff,
          (ipInt shr 8) and 0xff,
          (ipInt shr 16) and 0xff,
          (ipInt shr 24) and 0xff
        )
        return ip
      }
    } catch (_: Exception) {
    }

    return "127.0.0.1"
  }

  fun getWifiSsid(context: Context): String {
    try {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      val network = cm?.activeNetwork
      val caps = cm?.getNetworkCapabilities(network)
      if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val info = wifiManager?.connectionInfo
        val ssid = info?.ssid?.replace("\"", "")
        if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
          return ssid
        }
        return "Wi-Fi Connected"
      } else if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
        return "Ethernet"
      }
    } catch (_: Exception) {
    }
    return "Local Network"
  }
}
