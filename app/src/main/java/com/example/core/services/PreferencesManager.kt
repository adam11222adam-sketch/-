package com.example.core.services

import android.content.Context
import android.content.SharedPreferences
import com.example.core.constants.AppConfig

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("shein_orders_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_NAME = "profile_name"
        private const val KEY_PHONE = "profile_phone"
        private const val KEY_CITY = "profile_city"
        private const val KEY_ADDRESS = "profile_address"
        private const val KEY_GENERAL_NOTES = "profile_general_notes"
        private const val KEY_WHATSAPP = "config_whatsapp"
        private const val KEY_SHEIN_URL = "config_shein_url"
    }

    var customerName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var phone: String
        get() = prefs.getString(KEY_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PHONE, value).apply()

    var city: String
        get() = prefs.getString(KEY_CITY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CITY, value).apply()

    var address: String
        get() = prefs.getString(KEY_ADDRESS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ADDRESS, value).apply()

    var generalNotes: String
        get() = prefs.getString(KEY_GENERAL_NOTES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GENERAL_NOTES, value).apply()

    var whatsappNumber: String
        get() = prefs.getString(KEY_WHATSAPP, AppConfig.DEFAULT_WHATSAPP_NUMBER) ?: AppConfig.DEFAULT_WHATSAPP_NUMBER
        set(value) = prefs.edit().putString(KEY_WHATSAPP, value).apply()

    var sheinHomeUrl: String
        get() = prefs.getString(KEY_SHEIN_URL, AppConfig.DEFAULT_SHEIN_URL) ?: AppConfig.DEFAULT_SHEIN_URL
        set(value) = prefs.edit().putString(KEY_SHEIN_URL, value).apply()

    fun isProfileComplete(): Boolean {
        return customerName.trim().isNotEmpty() &&
                phone.trim().isNotEmpty() &&
                city.trim().isNotEmpty() &&
                address.trim().isNotEmpty()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
