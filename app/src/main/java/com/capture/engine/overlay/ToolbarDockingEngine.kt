package com.capture.engine.overlay

enum class DockEdge {
    LEFT, TOP, RIGHT, BOTTOM
}

data class DockState(
    val visualX: Int,
    val visualY: Int,
    val targetX: Float,
    val targetY: Float,
    val currentDockEdge: DockEdge?
)

object ToolbarDockingEngine {

    fun getClosestEdge(x: Float, y: Float, maxX: Int, maxY: Int): DockEdge {
        val dLeft = x
        val dRight = (maxX - x).coerceAtLeast(0f)
        val dTop = y
        val dBottom = (maxY - y).coerceAtLeast(0f)
        val minD = minOf(dLeft, dRight, dTop, dBottom)
        return when (minD) {
            dLeft -> DockEdge.LEFT
            dRight -> DockEdge.RIGHT
            dTop -> DockEdge.TOP
            else -> DockEdge.BOTTOM
        }
    }

    /**
     * Resolves the visual position when docked to an edge.
     * Snapped to the edge, dragging moves it along that edge.
     */
    fun resolveDockedPosition(
        edge: DockEdge,
        targetX: Float,
        targetY: Float,
        maxX: Int,
        maxY: Int
    ): Pair<Int, Int> {
        return when (edge) {
            DockEdge.LEFT -> Pair(0, targetY.toInt().coerceIn(0, maxY))
            DockEdge.RIGHT -> Pair(maxX, targetY.toInt().coerceIn(0, maxY))
            DockEdge.TOP -> Pair(targetX.toInt().coerceIn(0, maxX), 0)
            DockEdge.BOTTOM -> Pair(targetX.toInt().coerceIn(0, maxX), maxY)
        }
    }

    /**
     * Snap a given coordinate pair to the nearest edge.
     */
    fun snapToNearestEdge(
        currentX: Int,
        currentY: Int,
        maxX: Int,
        maxY: Int
    ): Pair<Int, Int> {
        val edge = getClosestEdge(currentX.toFloat(), currentY.toFloat(), maxX, maxY)
        return resolveDockedPosition(edge, currentX.toFloat(), currentY.toFloat(), maxX, maxY)
    }

    /**
     * Called when a drag starts.
     */
    fun onDragStart(
        initialVisualX: Int,
        initialVisualY: Int,
        maxX: Int,
        maxY: Int,
        positionBehavior: String, // "free", "dockable", "always docked"
        dockDistancePx: Float,
        edgeTolerancePx: Float = 20f
    ): DockState {
        return when (positionBehavior) {
            "always docked" -> {
                val edge = getClosestEdge(initialVisualX.toFloat(), initialVisualY.toFloat(), maxX, maxY)
                val (visX, visY) = resolveDockedPosition(edge, initialVisualX.toFloat(), initialVisualY.toFloat(), maxX, maxY)
                DockState(
                    visualX = visX,
                    visualY = visY,
                    targetX = visX.toFloat(),
                    targetY = visY.toFloat(),
                    currentDockEdge = edge
                )
            }
            "dockable" -> {
                val dLeft = initialVisualX.toFloat()
                val dRight = (maxX - initialVisualX).toFloat().coerceAtLeast(0f)
                val dTop = initialVisualY.toFloat()
                val dBottom = (maxY - initialVisualY).toFloat().coerceAtLeast(0f)
                val minD = minOf(dLeft, dRight, dTop, dBottom)

                val initialEdge = if (minD <= edgeTolerancePx) {
                    when (minD) {
                        dLeft -> DockEdge.LEFT
                        dRight -> DockEdge.RIGHT
                        dTop -> DockEdge.TOP
                        else -> DockEdge.BOTTOM
                    }
                } else null

                if (initialEdge != null) {
                    val (visX, visY) = resolveDockedPosition(initialEdge, initialVisualX.toFloat(), initialVisualY.toFloat(), maxX, maxY)
                    DockState(
                        visualX = visX,
                        visualY = visY,
                        targetX = visX.toFloat(),
                        targetY = visY.toFloat(),
                        currentDockEdge = initialEdge
                    )
                } else {
                    DockState(
                        visualX = initialVisualX,
                        visualY = initialVisualY,
                        targetX = initialVisualX.toFloat(),
                        targetY = initialVisualY.toFloat(),
                        currentDockEdge = null
                    )
                }
            }
            else -> {
                // "free"
                DockState(
                    visualX = initialVisualX,
                    visualY = initialVisualY,
                    targetX = initialVisualX.toFloat(),
                    targetY = initialVisualY.toFloat(),
                    currentDockEdge = null
                )
            }
        }
    }

    /**
     * Called on each drag delta (dx, dy).
     */
    fun onDrag(
        currentState: DockState,
        dx: Float,
        dy: Float,
        maxX: Int,
        maxY: Int,
        positionBehavior: String,
        dockDistancePx: Float
    ): DockState {
        val newTargetX = (currentState.targetX + dx).coerceIn(0f, maxX.toFloat())
        val newTargetY = (currentState.targetY + dy).coerceIn(0f, maxY.toFloat())

        val dLeft = newTargetX
        val dRight = (maxX - newTargetX).coerceAtLeast(0f)
        val dTop = newTargetY
        val dBottom = (maxY - newTargetY).coerceAtLeast(0f)

        when (positionBehavior) {
            "free" -> {
                return DockState(
                    visualX = newTargetX.toInt(),
                    visualY = newTargetY.toInt(),
                    targetX = newTargetX,
                    targetY = newTargetY,
                    currentDockEdge = null
                )
            }

            "dockable" -> {
                val currentEdge = currentState.currentDockEdge
                if (currentEdge == null) {
                    // Undocked state: check if dragged within dockDistancePx of any edge
                    val minD = minOf(dLeft, dRight, dTop, dBottom)
                    if (minD <= dockDistancePx) {
                        val dockedEdge = when (minD) {
                            dLeft -> DockEdge.LEFT
                            dRight -> DockEdge.RIGHT
                            dTop -> DockEdge.TOP
                            else -> DockEdge.BOTTOM
                        }
                        val (visX, visY) = resolveDockedPosition(dockedEdge, newTargetX, newTargetY, maxX, maxY)
                        return DockState(
                            visualX = visX,
                            visualY = visY,
                            targetX = newTargetX,
                            targetY = newTargetY,
                            currentDockEdge = dockedEdge
                        )
                    } else {
                        return DockState(
                            visualX = newTargetX.toInt(),
                            visualY = newTargetY.toInt(),
                            targetX = newTargetX,
                            targetY = newTargetY,
                            currentDockEdge = null
                        )
                    }
                } else {
                    // Docked state:
                    val distFromCurrentEdge = when (currentEdge) {
                        DockEdge.LEFT -> dLeft
                        DockEdge.RIGHT -> dRight
                        DockEdge.TOP -> dTop
                        DockEdge.BOTTOM -> dBottom
                    }

                    // Check if dragged into another edge directly
                    val minD = minOf(dLeft, dRight, dTop, dBottom)
                    val closestEdge = when (minD) {
                        dLeft -> DockEdge.LEFT
                        dRight -> DockEdge.RIGHT
                        dTop -> DockEdge.TOP
                        else -> DockEdge.BOTTOM
                    }

                    if (distFromCurrentEdge > dockDistancePx) {
                        // Drag target pulled away from current edge!
                        if (minD <= dockDistancePx) {
                            // Hit another edge within dockDistancePx
                            val (visX, visY) = resolveDockedPosition(closestEdge, newTargetX, newTargetY, maxX, maxY)
                            return DockState(
                                visualX = visX,
                                visualY = visY,
                                targetX = newTargetX,
                                targetY = newTargetY,
                                currentDockEdge = closestEdge
                            )
                        } else {
                            // Undocked! Freely moves anywhere again
                            return DockState(
                                visualX = newTargetX.toInt(),
                                visualY = newTargetY.toInt(),
                                targetX = newTargetX,
                                targetY = newTargetY,
                                currentDockEdge = null
                            )
                        }
                    } else {
                        // Still within dockDistancePx of current edge!
                        // If near corner and closer to perpendicular edge, switch
                        val activeEdge = if (minD < distFromCurrentEdge && minD <= dockDistancePx) closestEdge else currentEdge
                        val (visX, visY) = resolveDockedPosition(activeEdge, newTargetX, newTargetY, maxX, maxY)
                        return DockState(
                            visualX = visX,
                            visualY = visY,
                            targetX = newTargetX,
                            targetY = newTargetY,
                            currentDockEdge = activeEdge
                        )
                    }
                }
            }

            "always docked" -> {
                val currentEdge = currentState.currentDockEdge ?: getClosestEdge(newTargetX, newTargetY, maxX, maxY)
                val distFromCurrentEdge = when (currentEdge) {
                    DockEdge.LEFT -> dLeft
                    DockEdge.RIGHT -> dRight
                    DockEdge.TOP -> dTop
                    DockEdge.BOTTOM -> dBottom
                }

                val otherEdges = listOf(DockEdge.LEFT, DockEdge.RIGHT, DockEdge.TOP, DockEdge.BOTTOM).filter { it != currentEdge }
                val minOtherDist = otherEdges.minOf { edge ->
                    when (edge) {
                        DockEdge.LEFT -> dLeft
                        DockEdge.RIGHT -> dRight
                        DockEdge.TOP -> dTop
                        DockEdge.BOTTOM -> dBottom
                    }
                }
                val candidateEdge = otherEdges.first { edge ->
                    val d = when (edge) {
                        DockEdge.LEFT -> dLeft
                        DockEdge.RIGHT -> dRight
                        DockEdge.TOP -> dTop
                        DockEdge.BOTTOM -> dBottom
                    }
                    d == minOtherDist
                }

                val activeEdge = if (minOtherDist <= dockDistancePx && (distFromCurrentEdge > dockDistancePx || minOtherDist < distFromCurrentEdge)) {
                    candidateEdge
                } else {
                    currentEdge
                }

                val (visX, visY) = resolveDockedPosition(activeEdge, newTargetX, newTargetY, maxX, maxY)
                return DockState(
                    visualX = visX,
                    visualY = visY,
                    targetX = newTargetX,
                    targetY = newTargetY,
                    currentDockEdge = activeEdge
                )
            }

            else -> {
                return DockState(
                    visualX = newTargetX.toInt(),
                    visualY = newTargetY.toInt(),
                    targetX = newTargetX,
                    targetY = newTargetY,
                    currentDockEdge = null
                )
            }
        }
    }

    /**
     * Called when drag ends. Ensures coordinates are cleanly snapped.
     */
    fun onDragEnd(
        currentState: DockState,
        maxX: Int,
        maxY: Int,
        positionBehavior: String
    ): DockState {
        if (positionBehavior == "always docked") {
            val edge = currentState.currentDockEdge ?: getClosestEdge(currentState.targetX, currentState.targetY, maxX, maxY)
            val (visX, visY) = resolveDockedPosition(edge, currentState.targetX, currentState.targetY, maxX, maxY)
            return currentState.copy(
                visualX = visX,
                visualY = visY,
                targetX = visX.toFloat(),
                targetY = visY.toFloat(),
                currentDockEdge = edge
            )
        } else if (positionBehavior == "dockable" && currentState.currentDockEdge != null) {
            val edge = currentState.currentDockEdge
            val (visX, visY) = resolveDockedPosition(edge, currentState.targetX, currentState.targetY, maxX, maxY)
            return currentState.copy(
                visualX = visX,
                visualY = visY,
                targetX = visX.toFloat(),
                targetY = visY.toFloat()
            )
        }
        return currentState
    }

    /**
     * Handles coordinate transformation when the device rotates (e.g. Portrait <-> Landscape).
     * Maintains edge docking and preserves proportional position along the docked or free axes.
     */
    fun onScreenRotation(
        currentVisualX: Int,
        currentVisualY: Int,
        oldMaxX: Int,
        oldMaxY: Int,
        newMaxX: Int,
        newMaxY: Int,
        positionBehavior: String,
        currentEdge: DockEdge? = null,
        edgeTolerancePx: Float = 20f
    ): DockState {
        val ratioX = if (oldMaxX > 0) (currentVisualX.toFloat() / oldMaxX).coerceIn(0f, 1f) else 0f
        val ratioY = if (oldMaxY > 0) (currentVisualY.toFloat() / oldMaxY).coerceIn(0f, 1f) else 0f

        return when (positionBehavior) {
            "always docked" -> {
                val edge = currentEdge ?: getClosestEdge(currentVisualX.toFloat(), currentVisualY.toFloat(), oldMaxX, oldMaxY)
                val (visX, visY) = when (edge) {
                    DockEdge.LEFT -> Pair(0, (ratioY * newMaxY).toInt().coerceIn(0, newMaxY))
                    DockEdge.RIGHT -> Pair(newMaxX, (ratioY * newMaxY).toInt().coerceIn(0, newMaxY))
                    DockEdge.TOP -> Pair((ratioX * newMaxX).toInt().coerceIn(0, newMaxX), 0)
                    DockEdge.BOTTOM -> Pair((ratioX * newMaxX).toInt().coerceIn(0, newMaxX), newMaxY)
                }
                DockState(
                    visualX = visX,
                    visualY = visY,
                    targetX = visX.toFloat(),
                    targetY = visY.toFloat(),
                    currentDockEdge = edge
                )
            }

            "dockable" -> {
                val resolvedEdge = if (currentEdge != null) {
                    currentEdge
                } else {
                    val dLeft = currentVisualX.toFloat()
                    val dRight = (oldMaxX - currentVisualX).toFloat().coerceAtLeast(0f)
                    val dTop = currentVisualY.toFloat()
                    val dBottom = (oldMaxY - currentVisualY).toFloat().coerceAtLeast(0f)
                    val minD = minOf(dLeft, dRight, dTop, dBottom)
                    if (minD <= edgeTolerancePx) {
                        when (minD) {
                            dLeft -> DockEdge.LEFT
                            dRight -> DockEdge.RIGHT
                            dTop -> DockEdge.TOP
                            else -> DockEdge.BOTTOM
                        }
                    } else null
                }

                if (resolvedEdge != null) {
                    val (visX, visY) = when (resolvedEdge) {
                        DockEdge.LEFT -> Pair(0, (ratioY * newMaxY).toInt().coerceIn(0, newMaxY))
                        DockEdge.RIGHT -> Pair(newMaxX, (ratioY * newMaxY).toInt().coerceIn(0, newMaxY))
                        DockEdge.TOP -> Pair((ratioX * newMaxX).toInt().coerceIn(0, newMaxX), 0)
                        DockEdge.BOTTOM -> Pair((ratioX * newMaxX).toInt().coerceIn(0, newMaxX), newMaxY)
                    }
                    DockState(
                        visualX = visX,
                        visualY = visY,
                        targetX = visX.toFloat(),
                        targetY = visY.toFloat(),
                        currentDockEdge = resolvedEdge
                    )
                } else {
                    val visX = (ratioX * newMaxX).toInt().coerceIn(0, newMaxX)
                    val visY = (ratioY * newMaxY).toInt().coerceIn(0, newMaxY)
                    DockState(
                        visualX = visX,
                        visualY = visY,
                        targetX = visX.toFloat(),
                        targetY = visY.toFloat(),
                        currentDockEdge = null
                    )
                }
            }

            else -> {
                // "free"
                val visX = (ratioX * newMaxX).toInt().coerceIn(0, newMaxX)
                val visY = (ratioY * newMaxY).toInt().coerceIn(0, newMaxY)
                DockState(
                    visualX = visX,
                    visualY = visY,
                    targetX = visX.toFloat(),
                    targetY = visY.toFloat(),
                    currentDockEdge = null
                )
            }
        }
    }
}
