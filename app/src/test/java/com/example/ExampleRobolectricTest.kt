package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.web.DashboardHtml
import com.example.util.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AirPortal", appName)
  }

  @Test
  fun `generate qr code with robolectric android graphics`() {
    val qr = QrCodeGenerator.generateQrBitmap("http://192.168.1.15:8080", 256)
    assertNotNull(qr)
    assertEquals(256, qr?.width)
    assertEquals(256, qr?.height)
  }

  @Test
  fun `load web dashboard asset from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val html = DashboardHtml.getHtml(context)
    assertTrue(html.contains("AirPortal"))
    assertTrue(html.contains("Messages"))
    assertTrue(html.contains("Contacts"))
    assertTrue(html.contains("Files"))
  }
}
