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
    }
}
