package com.paranoidx.keyboard

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager

class ImeSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            
            // Haptic feedback preference
            findPreference<SwitchPreferenceCompat>("pref_haptic")?.let { pref ->
                pref.isChecked = prefs.getBoolean("pref_haptic", true)
                pref.setOnPreferenceChangeListener { _, newValue ->
                    prefs.edit().putBoolean("pref_haptic", newValue as Boolean).apply()
                    true
                }
            }
            
            // Sound preference
            findPreference<SwitchPreferenceCompat>("pref_sound")?.let { pref ->
                pref.isChecked = prefs.getBoolean("pref_sound", false)
                pref.setOnPreferenceChangeListener { _, newValue ->
                    prefs.edit().putBoolean("pref_sound", newValue as Boolean).apply()
                    true
                }
            }
            
            // No logging preference
            findPreference<SwitchPreferenceCompat>("pref_no_logging")?.let { pref ->
                pref.isChecked = prefs.getBoolean("pref_no_logging", true)
                pref.setOnPreferenceChangeListener { _, newValue ->
                    prefs.edit().putBoolean("pref_no_logging", newValue as Boolean).apply()
                    true
                }
            }
            
            // Incognito mode preference
            findPreference<SwitchPreferenceCompat>("pref_incognito")?.let { pref ->
                pref.isChecked = prefs.getBoolean("pref_incognito", true)
                pref.setOnPreferenceChangeListener { _, newValue ->
                    prefs.edit().putBoolean("pref_incognito", newValue as Boolean).apply()
                    true
                }
            }
        }
    }
}