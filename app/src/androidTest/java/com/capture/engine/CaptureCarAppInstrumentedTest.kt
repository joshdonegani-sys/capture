package com.capture.engine

import android.content.Context
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.capture.engine.car.CaptureCarAppService
import com.capture.engine.car.CaptureCarScreen
import com.capture.engine.car.CaptureCarSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureCarAppInstrumentedTest {

    @Test
    fun testCarAppServiceAndSession() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val service = CaptureCarAppService()
            val validator = service.createHostValidator()
            assertNotNull("HostValidator should be non-null", validator)

            val session = service.onCreateSession()
            assertNotNull("Session should be non-null", session)
            assertTrue("Session should be CaptureCarSession", session is CaptureCarSession)
        }
    }

    @Test
    fun testCaptureCarScreenTemplate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var screen: CaptureCarScreen? = null
        var template: MessageTemplate? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val testCarContext = TestCarContext.createCarContext(context)
            screen = CaptureCarScreen(testCarContext)
            template = screen?.onGetTemplate() as? MessageTemplate
        }

        assertNotNull("Screen should be created", screen)
        assertNotNull("Template should be MessageTemplate", template)
        assertNotNull("Header must be present", template?.header)
        assertEquals("Must have 1 action button (Record)", 1, template?.actions?.size)
        val action = template?.actions?.get(0)
        assertEquals("Record", action?.title?.toString())
        assertEquals(CarColor.PRIMARY, action?.backgroundColor)
    }
}
