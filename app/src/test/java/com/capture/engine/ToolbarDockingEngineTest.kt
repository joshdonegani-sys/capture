package com.capture.engine

import com.capture.engine.overlay.DockEdge
import com.capture.engine.overlay.ToolbarDockingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToolbarDockingEngineTest {

    private val maxX = 1000
    private val maxY = 1800
    private val dockDistancePx = 70f

    @Test
    fun `FREE mode moves anywhere without docking`() {
        var state = ToolbarDockingEngine.onDragStart(
            initialVisualX = 500,
            initialVisualY = 500,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "free",
            dockDistancePx = dockDistancePx
        )
        assertNull(state.currentDockEdge)
        assertEquals(500, state.visualX)
        assertEquals(500, state.visualY)

        // Drag into the left edge zone (x = 20 < 70)
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = -480f,
            dy = 0f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "free",
            dockDistancePx = dockDistancePx
        )
        // In FREE mode, it should NOT dock to the edge
        assertNull(state.currentDockEdge)
        assertEquals(20, state.visualX)
        assertEquals(500, state.visualY)

        // Drag to screen bounds
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = -50f,
            dy = 0f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "free",
            dockDistancePx = dockDistancePx
        )
        assertNull(state.currentDockEdge)
        assertEquals(0, state.visualX)
    }

    @Test
    fun `DOCKABLE mode docks within distance, moves along edge, and undocks when dragged away`() {
        // 1. Starts freely in center
        var state = ToolbarDockingEngine.onDragStart(
            initialVisualX = 400,
            initialVisualY = 600,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "dockable",
            dockDistancePx = dockDistancePx
        )
        assertNull(state.currentDockEdge)

        // 2. Drag towards left edge, entering dock zone (targetX = 50 <= 70)
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = -350f,
            dy = 50f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "dockable",
            dockDistancePx = dockDistancePx
        )
        assertEquals(DockEdge.LEFT, state.currentDockEdge)
        assertEquals(0, state.visualX) // Snapped to edge!
        assertEquals(650, state.visualY) // Moved along edge

        // 3. Drag along the left edge (dy only)
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = 0f,
            dy = 100f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "dockable",
            dockDistancePx = dockDistancePx
        )
        assertEquals(DockEdge.LEFT, state.currentDockEdge)
        assertEquals(0, state.visualX) // Still snapped to left edge
        assertEquals(750, state.visualY) // Slid vertically along edge

        // 4. Drag target moves away slightly (dx = +15, targetX = 65 <= 70)
        // Visually it must remain on the edge!
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = 15f,
            dy = 0f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "dockable",
            dockDistancePx = dockDistancePx
        )
        assertEquals(DockEdge.LEFT, state.currentDockEdge)
        assertEquals(0, state.visualX) // Visually remains on edge
        assertEquals(65f, state.targetX, 0.01f) // Underlying target tracked

        // 5. Drag target moves away past threshold (dx = +20, targetX = 85 > 70)
        // Should undock and freely move anywhere again!
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = 20f,
            dy = 0f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "dockable",
            dockDistancePx = dockDistancePx
        )
        assertNull(state.currentDockEdge) // Undocked!
        assertEquals(85, state.visualX) // Follows target freely
        assertEquals(750, state.visualY)

        // 6. Drag back into dock zone (dx = -30, targetX = 55 <= 70)
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = -30f,
            dy = 0f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "dockable",
            dockDistancePx = dockDistancePx
        )
        assertEquals(DockEdge.LEFT, state.currentDockEdge) // Re-docks!
        assertEquals(0, state.visualX) // Snapped to edge again
    }

    @Test
    fun `DOCKABLE mode docks to top edge and moves horizontally`() {
        var state = ToolbarDockingEngine.onDragStart(
            initialVisualX = 300,
            initialVisualY = 300,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "dockable",
            dockDistancePx = dockDistancePx
        )

        // Drag within 40px of top edge (targetY = 40 <= 70)
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = 50f,
            dy = -260f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "dockable",
            dockDistancePx = dockDistancePx
        )
        assertEquals(DockEdge.TOP, state.currentDockEdge)
        assertEquals(0, state.visualY) // Snapped to top edge
        assertEquals(350, state.visualX) // Follows horizontally along top edge

        // Drag along top edge horizontally
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = 100f,
            dy = 0f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "dockable",
            dockDistancePx = dockDistancePx
        )
        assertEquals(DockEdge.TOP, state.currentDockEdge)
        assertEquals(0, state.visualY)
        assertEquals(450, state.visualX)
    }

    @Test
    fun `ALWAYS DOCKED mode stays on edge and snaps across when target reaches other edge`() {
        // Starts on left edge
        var state = ToolbarDockingEngine.onDragStart(
            initialVisualX = 0,
            initialVisualY = 500,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "always docked",
            dockDistancePx = dockDistancePx
        )
        assertEquals(DockEdge.LEFT, state.currentDockEdge)
        assertEquals(0, state.visualX)
        assertEquals(500, state.visualY)

        // 1. Drag along left edge
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = 0f,
            dy = 100f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "always docked",
            dockDistancePx = dockDistancePx
        )
        assertEquals(DockEdge.LEFT, state.currentDockEdge)
        assertEquals(0, state.visualX)
        assertEquals(600, state.visualY)

        // 2. Drag target moves across the screen towards the right (dx = +400, targetX = 400)
        // Far from right edge (distRight = 600 > 70), not close to top or bottom
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = 400f,
            dy = 50f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "always docked",
            dockDistancePx = dockDistancePx
        )
        // Visually remains on the left edge it is currently docked to!
        assertEquals(DockEdge.LEFT, state.currentDockEdge)
        assertEquals(0, state.visualX)
        assertEquals(650, state.visualY) // Parallel coordinate moves with target
        assertEquals(400f, state.targetX, 0.01f)

        // 3. Drag target reaches within dockDistance of right edge (targetX = 940, distRight = 60 <= 70)
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = 540f,
            dy = 0f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "always docked",
            dockDistancePx = dockDistancePx
        )
        // Docks to the right edge!
        assertEquals(DockEdge.RIGHT, state.currentDockEdge)
        assertEquals(maxX, state.visualX) // Snapped to right edge
        assertEquals(650, state.visualY) // Follows along right edge

        // 4. Drag along right edge
        state = ToolbarDockingEngine.onDrag(
            currentState = state,
            dx = 0f,
            dy = -150f,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "always docked",
            dockDistancePx = dockDistancePx
        )
        assertEquals(DockEdge.RIGHT, state.currentDockEdge)
        assertEquals(maxX, state.visualX)
        assertEquals(500, state.visualY)

        // 5. Drag end cleanly snaps to edge
        val endState = ToolbarDockingEngine.onDragEnd(
            currentState = state,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "always docked"
        )
        assertEquals(DockEdge.RIGHT, endState.currentDockEdge)
        assertEquals(maxX, endState.visualX)
        assertEquals(500, endState.visualY)
    }

    @Test
    fun `ALWAYS DOCKED snaps to nearest edge from unaligned starting point`() {
        // If toolbar was at (40, 500), closest edge is LEFT
        val state = ToolbarDockingEngine.onDragStart(
            initialVisualX = 40,
            initialVisualY = 500,
            maxX = maxX,
            maxY = maxY,
            positionBehavior = "always docked",
            dockDistancePx = dockDistancePx
        )
        assertEquals(DockEdge.LEFT, state.currentDockEdge)
        assertEquals(0, state.visualX)
        assertEquals(500, state.visualY)
    }

    @Test
    fun `snapToNearestEdge snaps to all 4 edges correctly`() {
        // Left
        val (leftX, leftY) = ToolbarDockingEngine.snapToNearestEdge(100, 500, maxX, maxY)
        assertEquals(0, leftX)
        assertEquals(500, leftY)

        // Right
        val (rightX, rightY) = ToolbarDockingEngine.snapToNearestEdge(950, 500, maxX, maxY)
        assertEquals(maxX, rightX)
        assertEquals(500, rightY)

        // Top
        val (topX, topY) = ToolbarDockingEngine.snapToNearestEdge(500, 80, maxX, maxY)
        assertEquals(500, topX)
        assertEquals(0, topY)

        // Bottom
        val (bottomX, bottomY) = ToolbarDockingEngine.snapToNearestEdge(500, 1750, maxX, maxY)
        assertEquals(500, bottomX)
        assertEquals(maxY, bottomY)
    }

    @Test
    fun `onScreenRotation ALWAYS DOCKED maintains edge and scales parallel coordinate`() {
        val portraitMaxX = 930
        val portraitMaxY = 2270
        val landscapeMaxX = 2250
        val landscapeMaxY = 950

        // 1. Docked to RIGHT edge in portrait, at 50% vertical height
        val rotRight = ToolbarDockingEngine.onScreenRotation(
            currentVisualX = portraitMaxX,
            currentVisualY = 1135,
            oldMaxX = portraitMaxX,
            oldMaxY = portraitMaxY,
            newMaxX = landscapeMaxX,
            newMaxY = landscapeMaxY,
            positionBehavior = "always docked"
        )
        assertEquals(DockEdge.RIGHT, rotRight.currentDockEdge)
        assertEquals(landscapeMaxX, rotRight.visualX) // Stays snapped to RIGHT
        assertEquals(475, rotRight.visualY) // 50% of landscapeMaxY (950)

        // Rotate back to portrait
        val backToPortrait = ToolbarDockingEngine.onScreenRotation(
            currentVisualX = rotRight.visualX,
            currentVisualY = rotRight.visualY,
            oldMaxX = landscapeMaxX,
            oldMaxY = landscapeMaxY,
            newMaxX = portraitMaxX,
            newMaxY = portraitMaxY,
            positionBehavior = "always docked",
            currentEdge = rotRight.currentDockEdge
        )
        assertEquals(DockEdge.RIGHT, backToPortrait.currentDockEdge)
        assertEquals(portraitMaxX, backToPortrait.visualX)
        assertEquals(1135, backToPortrait.visualY)

        // 2. Docked to LEFT edge in landscape, at 20% vertical height
        val rotLeft = ToolbarDockingEngine.onScreenRotation(
            currentVisualX = 0,
            currentVisualY = 190,
            oldMaxX = landscapeMaxX,
            oldMaxY = landscapeMaxY,
            newMaxX = portraitMaxX,
            newMaxY = portraitMaxY,
            positionBehavior = "always docked"
        )
        assertEquals(DockEdge.LEFT, rotLeft.currentDockEdge)
        assertEquals(0, rotLeft.visualX) // Stays snapped to LEFT
        assertEquals(454, rotLeft.visualY) // 20% of portraitMaxY (2270)

        // 3. Docked to TOP edge, at 50% horizontal width
        val rotTop = ToolbarDockingEngine.onScreenRotation(
            currentVisualX = 465,
            currentVisualY = 0,
            oldMaxX = portraitMaxX,
            oldMaxY = portraitMaxY,
            newMaxX = landscapeMaxX,
            newMaxY = landscapeMaxY,
            positionBehavior = "always docked"
        )
        assertEquals(DockEdge.TOP, rotTop.currentDockEdge)
        assertEquals(1125, rotTop.visualX) // 50% of landscapeMaxX (2250)
        assertEquals(0, rotTop.visualY) // Stays snapped to TOP

        // 4. Docked to BOTTOM edge, at 50% horizontal width
        val rotBottom = ToolbarDockingEngine.onScreenRotation(
            currentVisualX = 465,
            currentVisualY = portraitMaxY,
            oldMaxX = portraitMaxX,
            oldMaxY = portraitMaxY,
            newMaxX = landscapeMaxX,
            newMaxY = landscapeMaxY,
            positionBehavior = "always docked"
        )
        assertEquals(DockEdge.BOTTOM, rotBottom.currentDockEdge)
        assertEquals(1125, rotBottom.visualX) // 50% of landscapeMaxX (2250)
        assertEquals(landscapeMaxY, rotBottom.visualY) // Stays snapped to BOTTOM
    }

    @Test
    fun `onScreenRotation DOCKABLE preserves edge when docked, scales freely when undocked`() {
        val portraitMaxX = 930
        val portraitMaxY = 2270
        val landscapeMaxX = 2250
        val landscapeMaxY = 950

        // Docked to LEFT edge
        val dockedRot = ToolbarDockingEngine.onScreenRotation(
            currentVisualX = 0,
            currentVisualY = 1135,
            oldMaxX = portraitMaxX,
            oldMaxY = portraitMaxY,
            newMaxX = landscapeMaxX,
            newMaxY = landscapeMaxY,
            positionBehavior = "dockable",
            currentEdge = DockEdge.LEFT
        )
        assertEquals(DockEdge.LEFT, dockedRot.currentDockEdge)
        assertEquals(0, dockedRot.visualX)
        assertEquals(475, dockedRot.visualY)

        // Undocked in middle (30% X, 60% Y)
        val undockedRot = ToolbarDockingEngine.onScreenRotation(
            currentVisualX = 279, // 30% of 930
            currentVisualY = 1362, // 60% of 2270
            oldMaxX = portraitMaxX,
            oldMaxY = portraitMaxY,
            newMaxX = landscapeMaxX,
            newMaxY = landscapeMaxY,
            positionBehavior = "dockable",
            currentEdge = null
        )
        assertNull(undockedRot.currentDockEdge)
        assertEquals(675, undockedRot.visualX) // 30% of 2250
        assertEquals(570, undockedRot.visualY) // 60% of 950
    }

    @Test
    fun `onScreenRotation FREE scales both axes proportionally and clamps cleanly`() {
        val portraitMaxX = 1000
        val portraitMaxY = 2000
        val landscapeMaxX = 2000
        val landscapeMaxY = 1000

        val rot = ToolbarDockingEngine.onScreenRotation(
            currentVisualX = 500,
            currentVisualY = 1000,
            oldMaxX = portraitMaxX,
            oldMaxY = portraitMaxY,
            newMaxX = landscapeMaxX,
            newMaxY = landscapeMaxY,
            positionBehavior = "free"
        )
        assertNull(rot.currentDockEdge)
        assertEquals(1000, rot.visualX) // 50%
        assertEquals(500, rot.visualY) // 50%
    }
}
