/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import android.annotation.SuppressLint
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import org.mozilla.fenix.FeatureFlags
import org.mozilla.fenix.R
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.components.toolbar.ToolbarPosition
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.ext.showToolbar
import org.mozilla.fenix.utils.view.addToRadioGroup

/**
 * Lets the user customize the UI.
 */

@Suppress("LargeClass", "TooManyFunctions")
class CustomizationFragment : PreferenceFragmentCompat() {
    private lateinit var radioLightTheme: RadioButtonPreference
    private lateinit var radioDarkTheme: RadioButtonPreference
    private lateinit var radioAutoBatteryTheme: RadioButtonPreference
    private lateinit var radioFollowDeviceTheme: RadioButtonPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.customization_preferences, rootKey)

        setupPreferences()
    }

    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.preferences_customize))
    }

    private fun setupPreferences() {
        bindFollowDeviceTheme()
        bindDarkTheme()
        bindLightTheme()
        bindAutoBatteryTheme()
        setupRadioGroups()
        setupToolbarCategory()
        setupGesturesCategory()
        setupAddonsCustomizationCategory()
        setupSystemBehaviorCategory()

        requirePreference<SwitchPreference>(R.string.pref_key_strip_url).apply {
            isChecked = context.settings().shouldStripUrl

            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
    }

    private fun setupRadioGroups() {
        addToRadioGroup(
            radioLightTheme,
            radioDarkTheme,
            if (SDK_INT >= Build.VERSION_CODES.P) {
                radioFollowDeviceTheme
            } else {
                radioAutoBatteryTheme
            }
        )
    }

    private fun bindLightTheme() {
        radioLightTheme = requirePreference(R.string.pref_key_light_theme)
        radioLightTheme.onClickListener {
            setNewTheme(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    @SuppressLint("WrongConstant")
    // Suppressing erroneous lint warning about using MODE_NIGHT_AUTO_BATTERY, a likely library bug
    private fun bindAutoBatteryTheme() {
        radioAutoBatteryTheme = requirePreference(R.string.pref_key_auto_battery_theme)
        radioAutoBatteryTheme.onClickListener {
            setNewTheme(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
        }
    }

    private fun bindDarkTheme() {
        radioDarkTheme = requirePreference(R.string.pref_key_dark_theme)
        radioDarkTheme.onClickListener {
            requireContext().components.analytics.metrics.track(
                Event.DarkThemeSelected(
                    Event.DarkThemeSelected.Source.SETTINGS
                )
            )
            setNewTheme(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun bindFollowDeviceTheme() {
        radioFollowDeviceTheme = requirePreference(R.string.pref_key_follow_device_theme)
        if (SDK_INT >= Build.VERSION_CODES.P) {
            radioFollowDeviceTheme.onClickListener {
                setNewTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    private fun setNewTheme(mode: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == mode) return
        AppCompatDelegate.setDefaultNightMode(mode)
        activity?.recreate()
        with(requireComponents.core) {
            engine.settings.preferredColorScheme = getPreferredColorScheme()
        }
        requireComponents.useCases.sessionUseCases.reload.invoke()
    }

    private fun setupToolbarCategory() {
        val topPreference = requirePreference<RadioButtonPreference>(R.string.pref_key_toolbar_top)
        topPreference.onClickListener {
            requireContext().components.analytics.metrics.track(
                Event.ToolbarPositionChanged(
                    Event.ToolbarPositionChanged.Position.TOP
                )
            )
        }

        val bottomPreference = requirePreference<RadioButtonPreference>(R.string.pref_key_toolbar_bottom)
        bottomPreference.onClickListener {
            requireContext().components.analytics.metrics.track(
                Event.ToolbarPositionChanged(
                    Event.ToolbarPositionChanged.Position.BOTTOM
                )
            )
        }

        val toolbarPosition = requireContext().settings().toolbarPosition
        topPreference.setCheckedWithoutClickListener(toolbarPosition == ToolbarPosition.TOP)
        bottomPreference.setCheckedWithoutClickListener(toolbarPosition == ToolbarPosition.BOTTOM)

        addToRadioGroup(topPreference, bottomPreference)
    }

    private fun setupGesturesCategory() {
        requirePreference<SwitchPreference>(R.string.pref_key_website_pull_to_refresh).apply {
            isVisible = FeatureFlags.pullToRefreshEnabled
            isChecked = context.settings().isPullToRefreshEnabledInBrowser
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
        requirePreference<SwitchPreference>(R.string.pref_key_dynamic_toolbar).apply {
            isChecked = context.settings().isDynamicToolbarEnabled
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
        requirePreference<SwitchPreference>(R.string.pref_key_swipe_toolbar_switch_tabs).apply {
            isChecked = context.settings().isSwipeToolbarToSwitchTabsEnabled
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
    }

    private fun setupAddonsCustomizationCategory() {
        requirePreference<EditTextPreference>(R.string.pref_key_addons_custom_account).apply {
            text = context.settings().customAddonsAccount
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }

        requirePreference<EditTextPreference>(R.string.pref_key_addons_custom_collection).apply {
            text = context.settings().customAddonsCollection
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
    }

    private fun setupSystemBehaviorCategory() {
        requirePreference<SwitchPreference>(R.string.pref_key_relinquish_memory_under_pressure).apply {
            isChecked = context.settings().shouldRelinquishMemoryUnderPressure
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
    }
    
    class CustomizeHomeMetricsUpdater : SharedPreferenceUpdater() {
        override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
            try {
                val context = preference.context
                context.components.analytics.metrics.track(
                    Event.CustomizeHomePreferenceToggled(
                        preference.key,
                        newValue as Boolean,
                        context
                    )
                )
            } catch (e: IllegalArgumentException) {
                // The event is not tracked
            }
            return super.onPreferenceChange(preference, newValue)
        }
    }
}
