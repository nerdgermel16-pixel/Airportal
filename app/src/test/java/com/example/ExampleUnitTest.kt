package com.example

import com.example.data.model.ContactItem
import com.example.data.model.FileItem
import com.example.data.model.SmsMessageItem
import com.example.server.ServerLogManager
import com.example.ui.web.DashboardHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testDashboardHtmlContainsRequiredTabs() {
    val html = DashboardHtml.getHtml()
    assertTrue(html.contains("AirPortal"))
  }

  @Test
  fun testServerLogManager() {
    ServerLogManager.clearLogs()
    assertEquals(0, ServerLogManager.logs.value.size)

    ServerLogManager.addLog("GET", "/api/status", 200, "192.168.1.100", "test")
    assertEquals(1, ServerLogManager.logs.value.size)
    assertEquals("GET", ServerLogManager.logs.value[0].method)
    assertEquals("/api/status", ServerLogManager.logs.value[0].uri)
    assertEquals(200, ServerLogManager.logs.value[0].statusCode)

    ServerLogManager.clearLogs()
    assertEquals(0, ServerLogManager.logs.value.size)
  }

  @Test
  fun testModelCreation() {
    val contact = ContactItem("1", "John Doe", "+1234567890", listOf("+1234567890"))
    assertEquals("John Doe", contact.displayName)

    val sms = SmsMessageItem(1L, 1L, "+1234567890", "John Doe", "Hello World", 1000L, 1)
    assertEquals("Hello World", sms.body)

    val file = FileItem("test.png", "/storage/test.png", false, 1024L, 1000L, "image/png", true)
    assertTrue(file.isImage)
  }
}
