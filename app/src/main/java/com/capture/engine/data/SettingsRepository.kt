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
    var isScreenGrabActive: Boolean
        get() = prefs.getBoolean(KEY_SRC_SCREEN_GRAB, true)
        set(value) = prefs.edit().putBoolean(KEY_SRC_SCREEN_GRAB, value).apply()

    var isAppTreeActive: Boolean
        get() = prefs.getBoolean(KEY_SRC_APP_TREE, true)
        set(value) = prefs.edit().putBoolean(KEY_SRC_APP_TREE, value).apply()

    var isHighlightedTextActive: Boolean
        get() = prefs.getBoolean(KEY_SRC_HIGHLIGHTED_TEXT, true)
        set(value) = prefs.edit().putBoolean(KEY_SRC_HIGHLIGHTED_TEXT, value).apply()

    var isMediaActive: Boolean
        get() = prefs.getBoolean(KEY_SRC_MEDIA, true)
        set(value) = prefs.edit().putBoolean(KEY_SRC_MEDIA, value).apply()

    // Toolbar Customisation Settings
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

    var lastUsedInputType: String
        get() = _lastUsedInputType.value
        set(value) {
            _lastUsedInputType.value = value
            prefs.edit().putString(KEY_LAST_USED_INPUT, value).apply()
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

    // 3. Visible Input Icons in "All" mode
    private val defaultInputIcons = setOf("speech_to_text", "mic_audio", "clipboard_paste", "text_snippet")
    private val _visibleInputIcons = MutableStateFlow(prefs.getStringSet(KEY_VISIBLE_INPUT_ICONS, defaultInputIcons) ?: defaultInputIcons)
    val visibleInputIconsFlow: StateFlow<Set<String>> = _visibleInputIcons

    var visibleInputIcons: Set<String>
        get() = _visibleInputIcons.value
        set(value) {
            _visibleInputIcons.value = value
            prefs.edit().putStringSet(KEY_VISIBLE_INPUT_ICONS, value).apply()
        }

    fun toggleInputIconVisibility(iconKey: String) {
        val current = _visibleInputIcons.value.toMutableSet()
        if (current.contains(iconKey)) {
            // Prevent removing the last remaining icon
            if (current.size > 1) {
                current.remove(iconKey)
                visibleInputIcons = current
            }
        } else {
            current.add(iconKey)
            visibleInputIcons = current
        }
    }

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

    // Sub Connection: "separate" or "joined"
    private val _toolbarSubConnection = MutableStateFlow(prefs.getString(KEY_TOOLBAR_SUB_CONNECTION, "separate") ?: "separate")
    val toolbarSubConnectionFlow: StateFlow<String> = _toolbarSubConnection

    var toolbarSubConnection: String
        get() = _toolbarSubConnection.value
        set(value) {
            _toolbarSubConnection.value = value
            prefs.edit().putString(KEY_TOOLBAR_SUB_CONNECTION, value).apply()
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

    var toolbarDormantPosX: Int
        get() = prefs.getInt(KEY_TOOLBAR_DORMANT_POS_X, 16)
        set(value) = prefs.edit().putInt(KEY_TOOLBAR_DORMANT_POS_X, value).apply()

    var toolbarDormantPosY: Int
        get() = prefs.getInt(KEY_TOOLBAR_DORMANT_POS_Y, 500)
        set(value) = prefs.edit().putInt(KEY_TOOLBAR_DORMANT_POS_Y, value).apply()

    // Awake State Config
    // Awake Style: "double icon", "single icon", "all icons"
    private val _toolbarAwakeStyle = MutableStateFlow(prefs.getString(KEY_TOOLBAR_AWAKE_STYLE, "double icon") ?: "double icon")
    val toolbarAwakeStyleFlow: StateFlow<String> = _toolbarAwakeStyle

    var toolbarAwakeStyle: String
        get() = _toolbarAwakeStyle.value
        set(value) {
            _toolbarAwakeStyle.value = value
            prefs.edit().putString(KEY_TOOLBAR_AWAKE_STYLE, value).apply()
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

        private const val KEY_TOOLBAR_ICON_SIZE = "toolbar_icon_size"
        private const val KEY_TOOLBAR_ALPHA = "toolbar_alpha"
        private const val KEY_INPUT_SELECTION_MODE = "input_selection_mode"
        private const val KEY_LAST_USED_INPUT = "last_used_input"

        private const val KEY_TOOLBAR_COLOR_ROLE = "toolbar_color_role"
        private const val KEY_TOOLBAR_CUSTOM_SEED = "toolbar_custom_seed"
        private const val KEY_CONTEXT_SOURCES_DISPLAY = "context_sources_display"
        private const val KEY_VISIBLE_INPUT_ICONS = "visible_input_icons"

        // Toolbar UI Layout Keys
        private const val KEY_TOOLBAR_BASE_LAYOUT = "toolbar_base_layout"
        private const val KEY_TOOLBAR_SUB_LAYOUT = "toolbar_sub_layout"
        private const val KEY_TOOLBAR_SUB_DIRECTION = "toolbar_sub_direction"
        private const val KEY_TOOLBAR_SUB_CONNECTION = "toolbar_sub_connection"
        private const val KEY_TOOLBAR_SUB_ALIGN = "toolbar_sub_align"
        private const val KEY_TOOLBAR_SUB_ALIGN_MODIFIER = "toolbar_sub_align_modifier"

        private const val KEY_TOOLBAR_DORMANT_STYLE = "toolbar_dormant_style"
        private const val KEY_TOOLBAR_DORMANT_LOCKED = "toolbar_dormant_locked"
        private const val KEY_TOOLBAR_DORMANT_POS_MODE = "toolbar_dormant_pos_mode"
        private const val KEY_TOOLBAR_DORMANT_POS_X = "toolbar_dormant_pos_x"
        private const val KEY_TOOLBAR_DORMANT_POS_Y = "toolbar_dormant_pos_y"

        private const val KEY_TOOLBAR_AWAKE_STYLE = "toolbar_awake_style"
        private const val KEY_TOOLBAR_AWAKE_LOCKED = "toolbar_awake_locked"
        private const val KEY_TOOLBAR_AWAKE_POSITION = "toolbar_awake_position"
        private const val KEY_TOOLBAR_ALL_ICONS_ORDER = "toolbar_all_icons_order"

        private const val KEY_TOOLBAR_WAKE_GESTURE = "toolbar_wake_gesture"
        private const val KEY_TOOLBAR_SLEEP_GESTURE = "toolbar_sleep_gesture"
    }
}
