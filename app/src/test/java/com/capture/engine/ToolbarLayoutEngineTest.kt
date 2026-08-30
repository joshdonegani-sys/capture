package com.capture.engine

import com.capture.engine.overlay.ExpandedTarget
import com.capture.engine.overlay.calculateSubPlacement
import com.capture.engine.overlay.resolveAllIconsOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarLayoutEngineTest {

    @Test
    fun `calculateSubPlacement computes correct downward placement`() {
        val placement = calculateSubPlacement(
            baseAnchorX = 100,
            baseAnchorY = 200,
            baseW = 80,
            baseH = 40,
            subW = 120,
            subH = 40,
            screenWidth = 1080,
            screenHeight = 1920,
            baseLayout = "horiz",
            subLayout = "flow axis",
            subDirection = "down",
            subAlign = "full base",
            subAlignModifier = "middle",
            expandedTarget = ExpandedTarget.JOINED_BOTH,
            stayOnScreen = true,
            gapPx = 16
        )

        // y should be baseAnchorY + baseH + gapPx = 200 + 40 + 16 = 256
        assertEquals(256, placement.y)
        assertEquals("down", placement.effectiveDirection)
    }

    @Test
    fun `calculateSubPlacement auto-flips when close to top screen edge`() {
        val placement = calculateSubPlacement(
            baseAnchorX = 100,
            baseAnchorY = 20,
            baseW = 80,
            baseH = 40,
            subW = 120,
            subH = 40,
            screenWidth = 1080,
            screenHeight = 1920,
            baseLayout = "horiz",
            subLayout = "flow axis",
            subDirection = "up", // Requesting up, but idealY would be 20 - 40 - 16 = -36 (< margin)
            subAlign = "full base",
            subAlignModifier = "middle",
            expandedTarget = ExpandedTarget.JOINED_BOTH,
            stayOnScreen = true,
            gapPx = 16,
            screenMargin = 16
        )

        // Should flip to down
        assertEquals("down", placement.effectiveDirection)
        assertEquals(20 + 40 + 16, placement.y)
    }

    @Test
    fun `resolveAllIconsOrder correctly resolves directional and contextual ordering`() {
        // "left" -> always true (input first)
        assertTrue(resolveAllIconsOrder("left", true, 100, 100, 1080, 1920))
        // "right" -> always false (context first)
        assertFalse(resolveAllIconsOrder("right", true, 100, 100, 1080, 1920))

        // "inner" on horizontal layout:
        // If window on right half (x >= screenWidth / 2) -> true
        assertTrue(resolveAllIconsOrder("inner", true, 600, 100, 1080, 1920))
        // If window on left half (x < screenWidth / 2) -> false
        assertFalse(resolveAllIconsOrder("inner", true, 200, 100, 1080, 1920))
    }
}
