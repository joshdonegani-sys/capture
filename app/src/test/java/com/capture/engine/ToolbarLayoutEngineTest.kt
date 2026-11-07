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
    fun `calculateSubPlacement computes correct rightward and leftward placement`() {
        val rightPlacement = calculateSubPlacement(
            baseAnchorX = 100,
            baseAnchorY = 200,
            baseW = 80,
            baseH = 40,
            subW = 120,
            subH = 40,
            screenWidth = 1080,
            screenHeight = 1920,
            baseLayout = "vert",
            subLayout = "flow axis",
            subDirection = "right",
            subAlign = "full base",
            subAlignModifier = "top",
            expandedTarget = ExpandedTarget.JOINED_BOTH,
            stayOnScreen = true,
            gapPx = 16
        )
        // x should be baseAnchorX + baseW + gapPx = 100 + 80 + 16 = 196
        assertEquals(196, rightPlacement.x)
        assertEquals(200, rightPlacement.y)
        assertEquals("right", rightPlacement.effectiveDirection)

        val leftPlacement = calculateSubPlacement(
            baseAnchorX = 300,
            baseAnchorY = 200,
            baseW = 80,
            baseH = 40,
            subW = 120,
            subH = 40,
            screenWidth = 1080,
            screenHeight = 1920,
            baseLayout = "vert",
            subLayout = "flow axis",
            subDirection = "left",
            subAlign = "full base",
            subAlignModifier = "top",
            expandedTarget = ExpandedTarget.JOINED_BOTH,
            stayOnScreen = true,
            gapPx = 16
        )
        // x should be baseAnchorX - subW - gapPx = 300 - 120 - 16 = 164
        assertEquals(164, leftPlacement.x)
        assertEquals(200, leftPlacement.y)
        assertEquals("left", leftPlacement.effectiveDirection)
    }

    @Test
    fun `calculateSubPlacement computes correct cross axis alignments`() {
        // Start alignment
        val startPlacement = calculateSubPlacement(
            baseAnchorX = 100,
            baseAnchorY = 200,
            baseW = 100,
            baseH = 40,
            subW = 60,
            subH = 40,
            screenWidth = 1080,
            screenHeight = 1920,
            baseLayout = "horiz",
            subLayout = "flow axis",
            subDirection = "down",
            subAlign = "full base",
            subAlignModifier = "start",
            expandedTarget = ExpandedTarget.JOINED_BOTH,
            stayOnScreen = true,
            gapPx = 16
        )
        assertEquals(100, startPlacement.x)

        // End alignment
        val endPlacement = calculateSubPlacement(
            baseAnchorX = 100,
            baseAnchorY = 200,
            baseW = 100,
            baseH = 40,
            subW = 60,
            subH = 40,
            screenWidth = 1080,
            screenHeight = 1920,
            baseLayout = "horiz",
            subLayout = "flow axis",
            subDirection = "down",
            subAlign = "full base",
            subAlignModifier = "end",
            expandedTarget = ExpandedTarget.JOINED_BOTH,
            stayOnScreen = true,
            gapPx = 16
        )
        // baseAnchorX + baseW - subW = 100 + 100 - 60 = 140
        assertEquals(140, endPlacement.x)
    }

    @Test
    fun `calculateSubPlacement computes correct base icon alignments`() {
        // Base icon alignment on horizontal layout (downward expansion)
        // baseW = 100, so iconW = 50, iconStartX = 100 + 100 - 50 = 150, iconCenterX = 175, iconEndX = 200
        val baseIconStart = calculateSubPlacement(
            baseAnchorX = 100,
            baseAnchorY = 200,
            baseW = 100,
            baseH = 40,
            subW = 60,
            subH = 40,
            screenWidth = 1080,
            screenHeight = 1920,
            baseLayout = "horiz",
            subLayout = "flow axis",
            subDirection = "down",
            subAlign = "base icon",
            subAlignModifier = "start",
            expandedTarget = ExpandedTarget.JOINED_BOTH,
            stayOnScreen = true,
            gapPx = 16
        )
        assertEquals(150, baseIconStart.x)

        val baseIconCenter = calculateSubPlacement(
            baseAnchorX = 100,
            baseAnchorY = 200,
            baseW = 100,
            baseH = 40,
            subW = 60,
            subH = 40,
            screenWidth = 1080,
            screenHeight = 1920,
            baseLayout = "horiz",
            subLayout = "flow axis",
            subDirection = "down",
            subAlign = "base icon",
            subAlignModifier = "middle",
            expandedTarget = ExpandedTarget.JOINED_BOTH,
            stayOnScreen = true,
            gapPx = 16
        )
        assertEquals(145, baseIconCenter.x)

        val baseIconEnd = calculateSubPlacement(
            baseAnchorX = 100,
            baseAnchorY = 200,
            baseW = 100,
            baseH = 40,
            subW = 60,
            subH = 40,
            screenWidth = 1080,
            screenHeight = 1920,
            baseLayout = "horiz",
            subLayout = "flow axis",
            subDirection = "down",
            subAlign = "base icon",
            subAlignModifier = "end",
            expandedTarget = ExpandedTarget.JOINED_BOTH,
            stayOnScreen = true,
            gapPx = 16
        )
        assertEquals(140, baseIconEnd.x)
    }

    @Test
    fun `calculateSubPlacement auto-flips when close to bottom screen edge`() {
        val placement = calculateSubPlacement(
            baseAnchorX = 100,
            baseAnchorY = 1860,
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
            gapPx = 16,
            screenMargin = 16
        )
        assertEquals("up", placement.effectiveDirection)
        assertEquals(1860 - 40 - 16, placement.y)
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
