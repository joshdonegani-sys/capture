package com.capture.engine.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("capture_engine_prefs", Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "https://httpbin.org/post") ?: "https://httpbin.org/post"
        set(value) = prefs.edit().putString(KEY_WEBHOOK_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var retryIntervalMinutes: Int
        get() = prefs.getInt(KEY_RETRY_INTERVAL, 5)
        set(value) = prefs.edit().putInt(KEY_RETRY_INTERVAL, value).apply()

    var isFullAppTreeEnabled: Boolean
        get() = prefs.getBoolean(KEY_FULL_APP_TREE, false) // Default filtered (Option 1)
        set(value) = prefs.edit().putBoolean(KEY_FULL_APP_TREE, value).apply()

    var isOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()

    // Context Source Toggles
    private val _isScreenGrabActive = MutableStateFlow(prefs.getBoolean(KEY_SRC_SCREEN_GRAB, true))
    val isScreenGrabActiveFlow: StateFlow<Boolean> = _isScreenGrabActive

    var isScreenGrabActive: Boolean
        get() = _isScreenGrabActive.value
        set(value) {
            _isScreenGrabActive.value = value
            prefs.edit().putBoolean(KEY_SRC_SCREEN_GRAB, value).apply()
        }

    private val _isAppTreeActive = MutableStateFlow(prefs.getBoolean(KEY_SRC_APP_TREE, true))
    val isAppTreeActiveFlow: StateFlow<Boolean> = _isAppTreeActive

    var isAppTreeActive: Boolean
        get() = _isAppTreeActive.value
        set(value) {
            _isAppTreeActive.value = value
            prefs.edit().putBoolean(KEY_SRC_APP_TREE, value).apply()
        }

    private val _isHighlightedTextActive = MutableStateFlow(prefs.getBoolean(KEY_SRC_HIGHLIGHTED_TEXT, true))
    val isHighlightedTextActiveFlow: StateFlow<Boolean> = _isHighlightedTextActive

    var isHighlightedTextActive: Boolean
        get() = _isHighlightedTextActive.value
        set(value) {
            _isHighlightedTextActive.value = value
            prefs.edit().putBoolean(KEY_SRC_HIGHLIGHTED_TEXT, value).apply()
        }

    private val _isMediaActive = MutableStateFlow(prefs.getBoolean(KEY_SRC_MEDIA, true))
    val isMediaActiveFlow: StateFlow<Boolean> = _isMediaActive

    var isMediaActive: Boolean
        get() = _isMediaActive.value
        set(value) {
            _isMediaActive.value = value
            prefs.edit().putBoolean(KEY_SRC_MEDIA, value).apply()
        }

    // Toolbar Customisation Settings
    private val _toolbarForceOpenForPreview = MutableStateFlow(prefs.getBoolean(KEY_TOOLBAR_FORCE_OPEN_FOR_PREVIEW, false))
    val toolbarForceOpenForPreviewFlow: StateFlow<Boolean> = _toolbarForceOpenForPreview

    var toolbarForceOpenForPreview: Boolean
        get() = _toolbarForceOpenForPreview.value
        set(value) {
            _toolbarForceOpenForPreview.value = value
            prefs.edit().putBoolean(KEY_TOOLBAR_FORCE_OPEN_FOR_PREVIEW, value).apply()
        }

    private val _toolbarIconSize = MutableStateFlow(prefs.getFloat(KEY_TOOLBAR_ICON_SIZE, 24f))
    val toolbarIconSizeFlow: StateFlow<Float> = _toolbarIconSize

    var toolbarIconSize: Float
        get() = _toolbarIconSize.value
        set(value) {
            _toolbarIconSize.value = value
            prefs.edit().putFloat(KEY_TOOLBAR_ICON_SIZE, value).apply()
        }

    private val _toolbarAlpha = MutableStateFlow(prefs.getFloat(KEY_TOOLBAR_ALPHA, 0.94f))
    val toolbarAlphaFlow: StateFlow<Float> = _toolbarAlpha

    var toolbarAlpha: Float
        get() = _toolbarAlpha.value
        set(value) {
            _toolbarAlpha.value = value
            prefs.edit().putFloat(KEY_TOOLBAR_ALPHA, value).apply()
        }

    // Tonal & Shadow Elevation Settings
    private val _toolbarTonalElevation = MutableStateFlow(prefs.getFloat(KEY_TOOLBAR_TONAL_ELEVATION, 4f))
    val toolbarTonalElevationFlow: StateFlow<Float> = _toolbarTonalElevation

    var toolbarTonalElevation: Float
        get() = _toolbarTonalElevation.value
        set(value) {
            _toolbarTonalElevation.value = value
            prefs.edit().putFloat(KEY_TOOLBAR_TONAL_ELEVATION, value).apply()
        }

    private val _toolbarShadowElevation = MutableStateFlow(prefs.getFloat(KEY_TOOLBAR_SHADOW_ELEVATION, 4f))
    val toolbarShadowElevationFlow: StateFlow<Float> = _toolbarShadowElevation

    var toolbarShadowElevation: Float
        get() = _toolbarShadowElevation.value
        set(value) {
            _toolbarShadowElevation.value = value
            prefs.edit().putFloat(KEY_TOOLBAR_SHADOW_ELEVATION, value).apply()
        }

    private val _inputSelectionDisplayMode = MutableStateFlow(prefs.getString(KEY_INPUT_SELECTION_MODE, "all") ?: "all")
    val inputSelectionDisplayModeFlow: StateFlow<String> = _inputSelectionDisplayMode

    var inputSelectionDisplayMode: String
        get() = _inputSelectionDisplayMode.value
        set(value) {
            _inputSelectionDisplayMode.value = value
            prefs.edit().putString(KEY_INPUT_SELECTION_MODE, value).apply()
        }

    private val _lastUsedInputType = MutableStateFlow(prefs.getString(KEY_LAST_USED_INPUT, "speech_to_text") ?: "speech_to_text")
    val lastUsedInputTypeFlow: StateFlow<String> = _lastUsedInputType

    private val _lastUsedModalInputType = MutableStateFlow(prefs.getString(KEY_LAST_USED_MODAL_INPUT, "speech_to_text") ?: "speech_to_text")
    val lastUsedModalInputTypeFlow: StateFlow<String> = _lastUsedModalInputType

    var lastUsedModalInputType: String
        get() = _lastUsedModalInputType.value
        set(value) {
            _lastUsedModalInputType.value = value
            prefs.edit().putString(KEY_LAST_USED_MODAL_INPUT, value).apply()
        }

    var lastUsedInputType: String
        get() = _lastUsedInputType.value
        set(value) {
            _lastUsedInputType.value = value
            prefs.edit().putString(KEY_LAST_USED_INPUT, value).apply()
            if (value in setOf("speech_to_text", "mic_audio", "text_snippet", "clipboard_paste", "text")) {
                lastUsedModalInputType = value
            }
        }

    // 1. Toolbar Color Role & Custom Seed Color
    private val _toolbarColorRole = MutableStateFlow(prefs.getString(KEY_TOOLBAR_COLOR_ROLE, "surface_container") ?: "surface_container")
    val toolbarColorRoleFlow: StateFlow<String> = _toolbarColorRole

    var toolbarColorRole: String
        get() = _toolbarColorRole.value
        set(value) {
            _toolbarColorRole.value = value
            prefs.edit().putString(KEY_TOOLBAR_COLOR_ROLE, value).apply()
        }

    private val _toolbarCustomSeedColor = MutableStateFlow(prefs.getInt(KEY_TOOLBAR_CUSTOM_SEED, 0xFF6750A4.toInt()))
    val toolbarCustomSeedColorFlow: StateFlow<Int> = _toolbarCustomSeedColor

    var toolbarCustomSeedColor: Int
        get() = _toolbarCustomSeedColor.value
        set(value) {
            _toolbarCustomSeedColor.value = value
            prefs.edit().putInt(KEY_TOOLBAR_CUSTOM_SEED, value).apply()
        }

    // 2. Context Sources Display Mode ("menu", "above", "below")
    private val _contextSourcesDisplayMode = MutableStateFlow(prefs.getString(KEY_CONTEXT_SOURCES_DISPLAY, "menu") ?: "menu")
    val contextSourcesDisplayModeFlow: StateFlow<String> = _contextSourcesDisplayMode

    var contextSourcesDisplayMode: String
        get() = _contextSourcesDisplayMode.value
        set(value) {
            _contextSourcesDisplayMode.value = value
            prefs.edit().putString(KEY_CONTEXT_SOURCES_DISPLAY, value).apply()
        }

    // 3. Visible Icons in "Custom" mode (Ordered list of tools: speech_to_text, mic_audio, clipboard_paste, text_snippet, camera, point_mode, last_used, menu)
    val availableCustomIcons = listOf("speech_to_text", "mic_audio", "clipboard_paste", "text_snippet", "camera", "point_mode", "last_used", "menu")
    val defaultCustomIcons = listOf("last_used", "menu")

    private val _toolbarCustomIcons = MutableStateFlow<List<String>>(
        run {
            val raw = prefs.getString(KEY_TOOLBAR_CUSTOM_ICONS, null)
            if (!raw.isNullOrBlank()) {
                val list = raw.split(",").map { it.trim() }.filter { it in availableCustomIcons }
                if (list.isNotEmpty()) list else defaultCustomIcons
            } else {
                val savedSet = prefs.getStringSet(KEY_VISIBLE_INPUT_ICONS, null)
                if (savedSet != null && savedSet.isNotEmpty()) {
                    val list = availableCustomIcons.filter { it in savedSet }
                    if (list.isNotEmpty()) list else defaultCustomIcons
                } else {
                    defaultCustomIcons
                }
            }
        }
    )
    val toolbarCustomIconsFlow: StateFlow<List<String>> = _toolbarCustomIcons

    var toolbarCustomIcons: List<String>
        get() = _toolbarCustomIcons.value
        set(value) {
            val sanitized = value.filter { it in availableCustomIcons }
            val finalVal = if (sanitized.isNotEmpty()) sanitized else defaultCustomIcons
            _toolbarCustomIcons.value = finalVal
            prefs.edit().putString(KEY_TOOLBAR_CUSTOM_ICONS, finalVal.joinToString(",")).apply()
        }

    fun toggleCustomIcon(iconKey: String) {
        val current = _toolbarCustomIcons.value.toMutableList()
        if (current.contains(iconKey)) {
            if (current.size > 1) {
                current.remove(iconKey)
                toolbarCustomIcons = current
            }
        } else {
            current.add(iconKey)
            toolbarCustomIcons = current
        }
    }

    fun moveCustomIcon(iconKey: String, up: Boolean) {
        val current = _toolbarCustomIcons.value.toMutableList()
        val index = current.indexOf(iconKey)
        if (index == -1) return
        if (up && index > 0) {
            val item = current.removeAt(index)
            current.add(index - 1, item)
            toolbarCustomIcons = current
        } else if (!up && index in 0 until (current.size - 1)) {
            val item = current.removeAt(index)
            current.add(index + 1, item)
            toolbarCustomIcons = current
        }
    }

    // Backward compatibility for visibleInputIcons
    val visibleInputIconsFlow: StateFlow<Set<String>> = MutableStateFlow(setOf("speech_to_text", "mic_audio", "clipboard_paste", "text_snippet", "camera"))
    var visibleInputIcons: Set<String>
        get() = _toolbarCustomIcons.value.toSet()
        set(value) {
            toolbarCustomIcons = value.toList()
        }

    fun toggleInputIconVisibility(iconKey: String) = toggleCustomIcon(iconKey)

    // --- Toolbar Layout Engine Core Settings ---

    // Base Layout: "horiz" or "vert"
    private val _toolbarBaseLayout = MutableStateFlow(prefs.getString(KEY_TOOLBAR_BASE_LAYOUT, "horiz") ?: "horiz")
    val toolbarBaseLayoutFlow: StateFlow<String> = _toolbarBaseLayout

    var toolbarBaseLayout: String
        get() = _toolbarBaseLayout.value
        set(value) {
            _toolbarBaseLayout.value = value
            prefs.edit().putString(KEY_TOOLBAR_BASE_LAYOUT, value).apply()
        }

    // Sub Layout: "flow axis" or "cross axis"
    private val _toolbarSubLayout = MutableStateFlow(prefs.getString(KEY_TOOLBAR_SUB_LAYOUT, "flow axis") ?: "flow axis")
    val toolbarSubLayoutFlow: StateFlow<String> = _toolbarSubLayout

    var toolbarSubLayout: String
        get() = _toolbarSubLayout.value
        set(value) {
            _toolbarSubLayout.value = value
            prefs.edit().putString(KEY_TOOLBAR_SUB_LAYOUT, value).apply()
        }

    // Sub Direction: "down", "up", "left", "right"
    private val _toolbarSubDirection = MutableStateFlow(prefs.getString(KEY_TOOLBAR_SUB_DIRECTION, "down") ?: "down")
    val toolbarSubDirectionFlow: StateFlow<String> = _toolbarSubDirection

    var toolbarSubDirection: String
        get() = _toolbarSubDirection.value
        set(value) {
            _toolbarSubDirection.value = value
            prefs.edit().putString(KEY_TOOLBAR_SUB_DIRECTION, value).apply()
        }

    // Sub Align: "full base" or "base icon"
    private val _toolbarSubAlign = MutableStateFlow(prefs.getString(KEY_TOOLBAR_SUB_ALIGN, "full base") ?: "full base")
    val toolbarSubAlignFlow: StateFlow<String> = _toolbarSubAlign

    var toolbarSubAlign: String
        get() = _toolbarSubAlign.value
        set(value) {
            _toolbarSubAlign.value = value
            prefs.edit().putString(KEY_TOOLBAR_SUB_ALIGN, value).apply()
        }

    // Sub Align Modifier (for full base): "top", "middle", "bottom"
    private val _toolbarSubAlignModifier = MutableStateFlow(prefs.getString(KEY_TOOLBAR_SUB_ALIGN_MODIFIER, "middle") ?: "middle")
    val toolbarSubAlignModifierFlow: StateFlow<String> = _toolbarSubAlignModifier

    var toolbarSubAlignModifier: String
        get() = _toolbarSubAlignModifier.value
        set(value) {
            _toolbarSubAlignModifier.value = value
            prefs.edit().putString(KEY_TOOLBAR_SUB_ALIGN_MODIFIER, value).apply()
        }

    // Stay on screen (Smart Edge Avoidance)
    private val _toolbarStayOnScreen = MutableStateFlow(prefs.getBoolean(KEY_TOOLBAR_STAY_ON_SCREEN, true))
    val toolbarStayOnScreenFlow: StateFlow<Boolean> = _toolbarStayOnScreen

    var toolbarStayOnScreen: Boolean
        get() = _toolbarStayOnScreen.value
        set(value) {
            _toolbarStayOnScreen.value = value
            prefs.edit().putBoolean(KEY_TOOLBAR_STAY_ON_SCREEN, value).apply()
        }

    // Dormant State Config
    // Dormant Style: "small icon", "horizontal bar", "vertical bar"
    private val _toolbarDormantStyle = MutableStateFlow(prefs.getString(KEY_TOOLBAR_DORMANT_STYLE, "horizontal bar") ?: "horizontal bar")
    val toolbarDormantStyleFlow: StateFlow<String> = _toolbarDormantStyle

    var toolbarDormantStyle: String
        get() = _toolbarDormantStyle.value
        set(value) {
            _toolbarDormantStyle.value = value
            prefs.edit().putString(KEY_TOOLBAR_DORMANT_STYLE, value).apply()
        }

    private val _toolbarDormantLocked = MutableStateFlow(prefs.getBoolean(KEY_TOOLBAR_DORMANT_LOCKED, false))
    val toolbarDormantLockedFlow: StateFlow<Boolean> = _toolbarDormantLocked

    var toolbarDormantLocked: Boolean
        get() = _toolbarDormantLocked.value
        set(value) {
            _toolbarDormantLocked.value = value
            prefs.edit().putBoolean(KEY_TOOLBAR_DORMANT_LOCKED, value).apply()
        }

    // Dormant Position Mode: "off", "defined"
    private val _toolbarDormantPositionMode = MutableStateFlow(prefs.getString(KEY_TOOLBAR_DORMANT_POS_MODE, "off") ?: "off")
    val toolbarDormantPositionModeFlow: StateFlow<String> = _toolbarDormantPositionMode

    var toolbarDormantPositionMode: String
        get() = _toolbarDormantPositionMode.value
        set(value) {
            _toolbarDormantPositionMode.value = value
            prefs.edit().putString(KEY_TOOLBAR_DORMANT_POS_MODE, value).apply()
        }

    private val _toolbarDormantPosX = MutableStateFlow(prefs.getInt(KEY_TOOLBAR_DORMANT_POS_X, 16))
    val toolbarDormantPosXFlow: StateFlow<Int> = _toolbarDormantPosX

    var toolbarDormantPosX: Int
        get() = _toolbarDormantPosX.value
        set(value) {
            _toolbarDormantPosX.value = value
            prefs.edit().putInt(KEY_TOOLBAR_DORMANT_POS_X, value).apply()
        }

    private val _toolbarDormantPosY = MutableStateFlow(prefs.getInt(KEY_TOOLBAR_DORMANT_POS_Y, 500))
    val toolbarDormantPosYFlow: StateFlow<Int> = _toolbarDormantPosY

    var toolbarDormantPosY: Int
        get() = _toolbarDormantPosY.value
        set(value) {
            _toolbarDormantPosY.value = value
            prefs.edit().putInt(KEY_TOOLBAR_DORMANT_POS_Y, value).apply()
        }

    // Awake State Config
    // Awake Style: "all icons", "custom"
    private val _toolbarAwakeStyle = MutableStateFlow(
        when (prefs.getString(KEY_TOOLBAR_AWAKE_STYLE, "custom")) {
            "all icons" -> "all icons"
            else -> "custom"
        }
    )
    val toolbarAwakeStyleFlow: StateFlow<String> = _toolbarAwakeStyle

    var toolbarAwakeStyle: String
        get() = _toolbarAwakeStyle.value
        set(value) {
            val effective = if (value == "all icons") "all icons" else "custom"
            _toolbarAwakeStyle.value = effective
            prefs.edit().putString(KEY_TOOLBAR_AWAKE_STYLE, effective).apply()
        }

    private val _toolbarAwakeLocked = MutableStateFlow(prefs.getBoolean(KEY_TOOLBAR_AWAKE_LOCKED, false))
    val toolbarAwakeLockedFlow: StateFlow<Boolean> = _toolbarAwakeLocked

    var toolbarAwakeLocked: Boolean
        get() = _toolbarAwakeLocked.value
        set(value) {
            _toolbarAwakeLocked.value = value
            prefs.edit().putBoolean(KEY_TOOLBAR_AWAKE_LOCKED, value).apply()
        }

    // Awake Position: "free", "dockable", "always docked"
    private val _toolbarAwakePosition = MutableStateFlow(prefs.getString(KEY_TOOLBAR_AWAKE_POSITION, "dockable") ?: "dockable")
    val toolbarAwakePositionFlow: StateFlow<String> = _toolbarAwakePosition

    var toolbarAwakePosition: String
        get() = _toolbarAwakePosition.value
        set(value) {
            _toolbarAwakePosition.value = value
            prefs.edit().putString(KEY_TOOLBAR_AWAKE_POSITION, value).apply()
        }

    private val _toolbarAwakePosX = MutableStateFlow(prefs.getInt(KEY_TOOLBAR_AWAKE_POS_X, 16))
    val toolbarAwakePosXFlow: StateFlow<Int> = _toolbarAwakePosX

    var toolbarAwakePosX: Int
        get() = _toolbarAwakePosX.value
        set(value) {
            _toolbarAwakePosX.value = value
            prefs.edit().putInt(KEY_TOOLBAR_AWAKE_POS_X, value).apply()
        }

    private val _toolbarAwakePosY = MutableStateFlow(prefs.getInt(KEY_TOOLBAR_AWAKE_POS_Y, 500))
    val toolbarAwakePosYFlow: StateFlow<Int> = _toolbarAwakePosY

    var toolbarAwakePosY: Int
        get() = _toolbarAwakePosY.value
        set(value) {
            _toolbarAwakePosY.value = value
            prefs.edit().putInt(KEY_TOOLBAR_AWAKE_POS_Y, value).apply()
        }

    // All Icons Order: "inner", "outer", "left", "right", "up", "down"
    private val _toolbarAllIconsOrder = MutableStateFlow(prefs.getString(KEY_TOOLBAR_ALL_ICONS_ORDER, "inner") ?: "inner")
    val toolbarAllIconsOrderFlow: StateFlow<String> = _toolbarAllIconsOrder

    var toolbarAllIconsOrder: String
        get() = _toolbarAllIconsOrder.value
        set(value) {
            _toolbarAllIconsOrder.value = value
            prefs.edit().putString(KEY_TOOLBAR_ALL_ICONS_ORDER, value).apply()
        }

    // Wake Actions: "single tap", "double tap", "long tap", "slide horizontal", "slide vertical"
    private val _toolbarWakeGesture = MutableStateFlow(prefs.getString(KEY_TOOLBAR_WAKE_GESTURE, "single tap") ?: "single tap")
    val toolbarWakeGestureFlow: StateFlow<String> = _toolbarWakeGesture

    var toolbarWakeGesture: String
        get() = _toolbarWakeGesture.value
        set(value) {
            _toolbarWakeGesture.value = value
            prefs.edit().putString(KEY_TOOLBAR_WAKE_GESTURE, value).apply()
        }

    // Sleep Actions: "double tap", "flick", "drag to edge", "drag to dormant zone"
    private val _toolbarSleepGesture = MutableStateFlow(prefs.getString(KEY_TOOLBAR_SLEEP_GESTURE, "double tap") ?: "double tap")
    val toolbarSleepGestureFlow: StateFlow<String> = _toolbarSleepGesture

    var toolbarSleepGesture: String
        get() = _toolbarSleepGesture.value
        set(value) {
            _toolbarSleepGesture.value = value
            prefs.edit().putString(KEY_TOOLBAR_SLEEP_GESTURE, value).apply()
        }

    // Sleep After timeout in minutes (default 1 minute)
    private val _toolbarSleepAfterMinutes = MutableStateFlow(prefs.getInt(KEY_TOOLBAR_SLEEP_AFTER, 1))
    val toolbarSleepAfterMinutesFlow: StateFlow<Int> = _toolbarSleepAfterMinutes

    var toolbarSleepAfterMinutes: Int
        get() = _toolbarSleepAfterMinutes.value
        set(value) {
            _toolbarSleepAfterMinutes.value = value
            prefs.edit().putInt(KEY_TOOLBAR_SLEEP_AFTER, value).apply()
        }

    companion object {
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_RETRY_INTERVAL = "retry_interval_minutes"
        private const val KEY_FULL_APP_TREE = "full_app_tree"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_SRC_SCREEN_GRAB = "source_screen_grab"
        private const val KEY_SRC_APP_TREE = "source_app_tree"
        private const val KEY_SRC_HIGHLIGHTED_TEXT = "source_highlighted_text"
        private const val KEY_SRC_MEDIA = "source_media"

        private const val KEY_TOOLBAR_FORCE_OPEN_FOR_PREVIEW = "toolbar_force_open_for_preview"
        private const val KEY_TOOLBAR_ICON_SIZE = "toolbar_icon_size"
        private const val KEY_TOOLBAR_ALPHA = "toolbar_alpha"
        private const val KEY_TOOLBAR_TONAL_ELEVATION = "toolbar_tonal_elevation"
        private const val KEY_TOOLBAR_SHADOW_ELEVATION = "toolbar_shadow_elevation"
        private const val KEY_INPUT_SELECTION_MODE = "input_selection_mode"
        private const val KEY_LAST_USED_INPUT = "last_used_input"
        private const val KEY_LAST_USED_MODAL_INPUT = "last_used_modal_input"

        private const val KEY_TOOLBAR_COLOR_ROLE = "toolbar_color_role"
        private const val KEY_TOOLBAR_CUSTOM_SEED = "toolbar_custom_seed"
        private const val KEY_CONTEXT_SOURCES_DISPLAY = "context_sources_display"
        private const val KEY_VISIBLE_INPUT_ICONS = "visible_input_icons"
        private const val KEY_TOOLBAR_CUSTOM_ICONS = "toolbar_custom_icons"

        // Toolbar UI Layout Keys
        private const val KEY_TOOLBAR_BASE_LAYOUT = "toolbar_base_layout"
        private const val KEY_TOOLBAR_SUB_LAYOUT = "toolbar_sub_layout"
        private const val KEY_TOOLBAR_SUB_DIRECTION = "toolbar_sub_direction"
        private const val KEY_TOOLBAR_SUB_ALIGN = "toolbar_sub_align"
        private const val KEY_TOOLBAR_SUB_ALIGN_MODIFIER = "toolbar_sub_align_modifier"
        private const val KEY_TOOLBAR_STAY_ON_SCREEN = "toolbar_stay_on_screen"

        private const val KEY_TOOLBAR_DORMANT_STYLE = "toolbar_dormant_style"
        private const val KEY_TOOLBAR_DORMANT_LOCKED = "toolbar_dormant_locked"
        private const val KEY_TOOLBAR_DORMANT_POS_MODE = "toolbar_dormant_pos_mode"
        private const val KEY_TOOLBAR_DORMANT_POS_X = "toolbar_dormant_pos_x"
        private const val KEY_TOOLBAR_DORMANT_POS_Y = "toolbar_dormant_pos_y"

        private const val KEY_TOOLBAR_AWAKE_STYLE = "toolbar_awake_style"
        private const val KEY_TOOLBAR_AWAKE_LOCKED = "toolbar_awake_locked"
        private const val KEY_TOOLBAR_AWAKE_POSITION = "toolbar_awake_position"
        private const val KEY_TOOLBAR_AWAKE_POS_X = "toolbar_awake_pos_x"
        private const val KEY_TOOLBAR_AWAKE_POS_Y = "toolbar_awake_pos_y"
        private const val KEY_TOOLBAR_ALL_ICONS_ORDER = "toolbar_all_icons_order"

        private const val KEY_TOOLBAR_WAKE_GESTURE = "toolbar_wake_gesture"
        private const val KEY_TOOLBAR_SLEEP_GESTURE = "toolbar_sleep_gesture"
        private const val KEY_TOOLBAR_SLEEP_AFTER = "toolbar_sleep_after_minutes"
    }
}
