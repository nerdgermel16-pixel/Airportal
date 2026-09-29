package com.example.ui.web

import android.content.Context

object DashboardHtml {

  @Volatile
  private var cachedHtml: String? = null

  fun getHtml(context: Context? = null): String {
    cachedHtml?.let { return it }
    val loaded = if (context != null) {
      try {
        context.assets.open("web/index.html").bufferedReader().use { it.readText() }
      } catch (_: Exception) {
        null
      }
    } else {
      null
    }

    val html = loaded ?: DEFAULT_FALLBACK_HTML
    if (loaded != null) {
      cachedHtml = html
    }
    return html
  }

  const val DEFAULT_FALLBACK_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>AirPortal | Wireless Device Manager</title>
  <style>body { font-family: sans-serif; background: #0f172a; color: #f8fafc; padding: 40px; text-align: center; }</style>
</head>
<body>
  <h1>AirPortal Web Server Active</h1>
  <p>Messages, Contacts, and Files API endpoints are ready.</p>
</body>
</html>"""
}
