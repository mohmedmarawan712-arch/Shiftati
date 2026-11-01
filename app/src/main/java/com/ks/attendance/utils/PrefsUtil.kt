package com.ks.attendance.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ks_prefs")

/**
 * PrefsUtil — typed access to DataStore preferences.
 */
class PrefsUtil(private val context: Context) {

    companion object {
        val KEY_HOURLY_RATE       = doublePreferencesKey("hourly_rate")
        val KEY_PAYROLL_START     = intPreferencesKey("payroll_start_day")
        val KEY_PAYROLL_CYCLE     = stringPreferencesKey("payroll_cycle_type")
        val KEY_LANGUAGE          = stringPreferencesKey("language")   // "ar" | "en"
        val KEY_ESS_EMAIL         = stringPreferencesKey("ess_email")
        val KEY_ESS_PASSWORD      = stringPreferencesKey("ess_password")
        val KEY_ESS_SAVE_CREDS    = booleanPreferencesKey("ess_save_creds")
        val KEY_DARK_MODE         = booleanPreferencesKey("dark_mode")
        val KEY_THEME_MODE        = stringPreferencesKey("theme_mode") // "system" | "dark" | "light"
        val KEY_EMPLOYEE_NAME     = stringPreferencesKey("employee_name")
        val KEY_TIME_FORMAT       = stringPreferencesKey("time_format") // "12h" | "24h"
        val KEY_CUSTOM_START_DATE = stringPreferencesKey("custom_start_date")
        val KEY_CUSTOM_END_DATE   = stringPreferencesKey("custom_end_date")
        val KEY_FIXED_EMPLOYMENT  = booleanPreferencesKey("fixed_employment_enabled")
        val KEY_STANDARD_SHIFT_HOURS = doublePreferencesKey("standard_shift_hours")

        const val DEFAULT_HOURLY_RATE   = 30.0
        const val DEFAULT_PAYROLL_START = 16
        const val DEFAULT_LANGUAGE      = "ar"
        const val DEFAULT_TIME_FORMAT   = "12h"
        const val THEME_SYSTEM          = "system"
        const val THEME_DARK            = "dark"
        const val THEME_LIGHT           = "light"
        const val DEFAULT_THEME_MODE    = THEME_SYSTEM
        const val DEFAULT_FIXED_EMPLOYMENT = false
        const val DEFAULT_STANDARD_SHIFT_HOURS = 9.0
    }

    val allPreferences: Flow<Preferences> = context.dataStore.data

    val hourlyRate: Flow<Double> = context.dataStore.data.map {
        it[KEY_HOURLY_RATE] ?: DEFAULT_HOURLY_RATE
    }

    val payrollStartDay: Flow<Int> = context.dataStore.data.map {
        it[KEY_PAYROLL_START] ?: DEFAULT_PAYROLL_START
    }

    val payrollCycleType: Flow<String> = context.dataStore.data.map {
        it[KEY_PAYROLL_CYCLE] ?: PayrollUtil.CYCLE_16_TO_15
    }

    val fixedEmploymentEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_FIXED_EMPLOYMENT] ?: DEFAULT_FIXED_EMPLOYMENT
    }

    val standardShiftHours: Flow<Double> = context.dataStore.data.map {
        it[KEY_STANDARD_SHIFT_HOURS] ?: DEFAULT_STANDARD_SHIFT_HOURS
    }

    val timeFormat: Flow<String> = context.dataStore.data.map {
        it[KEY_TIME_FORMAT] ?: DEFAULT_TIME_FORMAT
    }

    val language: Flow<String> = context.dataStore.data.map {
        it[KEY_LANGUAGE] ?: DEFAULT_LANGUAGE
    }

    val essEmail: Flow<String> = context.dataStore.data.map {
        it[KEY_ESS_EMAIL] ?: ""
    }

    val essPassword: Flow<String> = context.dataStore.data.map {
        it[KEY_ESS_PASSWORD] ?: ""
    }

    val essSaveCredentials: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_ESS_SAVE_CREDS] ?: false
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_DARK_MODE] ?: true // Default to true (AMOLED looks gorgeous)
    }

    val themeMode: Flow<String> = context.dataStore.data.map {
        it[KEY_THEME_MODE] ?: DEFAULT_THEME_MODE
    }

    val employeeName: Flow<String> = context.dataStore.data.map {
        it[KEY_EMPLOYEE_NAME] ?: ""
    }

    suspend fun setHourlyRate(value: Double) {
        context.dataStore.edit { it[KEY_HOURLY_RATE] = value }
    }

    suspend fun setPayrollStartDay(day: Int) {
        context.dataStore.edit { it[KEY_PAYROLL_START] = day }
    }

    suspend fun setPayrollCycleType(type: String) {
        context.dataStore.edit { it[KEY_PAYROLL_CYCLE] = type }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun setEssCredentials(email: String, pass: String, save: Boolean) {
        context.dataStore.edit {
            it[KEY_ESS_SAVE_CREDS] = save
            if (save) {
                it[KEY_ESS_EMAIL] = email
                it[KEY_ESS_PASSWORD] = pass
            } else {
                it[KEY_ESS_PASSWORD] = ""
            }
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit {
            it[KEY_THEME_MODE] = mode
            it[KEY_DARK_MODE] = (mode == THEME_DARK)
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        setThemeMode(if (enabled) THEME_DARK else THEME_LIGHT)
    }

    suspend fun setEmployeeName(name: String) {
        val shortened = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.take(2).joinToString(" ")
        context.dataStore.edit { it[KEY_EMPLOYEE_NAME] = shortened }
    }

    val customStartDate: Flow<String> = context.dataStore.data.map {
        it[KEY_CUSTOM_START_DATE] ?: ""
    }

    val customEndDate: Flow<String> = context.dataStore.data.map {
        it[KEY_CUSTOM_END_DATE] ?: ""
    }

    suspend fun setCustomPeriodRange(start: String, end: String) {
        context.dataStore.edit {
            it[KEY_CUSTOM_START_DATE] = start
            it[KEY_CUSTOM_END_DATE] = end
            it[KEY_PAYROLL_CYCLE] = PayrollUtil.CYCLE_CUSTOM_RANGE
        }
    }

    suspend fun setTimeFormat(format: String) {
        context.dataStore.edit { it[KEY_TIME_FORMAT] = format }
    }

    suspend fun setFixedEmployment(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FIXED_EMPLOYMENT] = enabled }
    }

    suspend fun setStandardShiftHours(hours: Double) {
        context.dataStore.edit { it[KEY_STANDARD_SHIFT_HOURS] = hours }
    }

    suspend fun getAllPrefsMap(): Map<String, Any?> {
        val prefs = context.dataStore.data.first()
        return mapOf(
            "hourly_rate" to (prefs[KEY_HOURLY_RATE] ?: DEFAULT_HOURLY_RATE),
            "payroll_start_day" to (prefs[KEY_PAYROLL_START] ?: DEFAULT_PAYROLL_START),
            "payroll_cycle_type" to (prefs[KEY_PAYROLL_CYCLE] ?: PayrollUtil.CYCLE_16_TO_15),
            "time_format" to (prefs[KEY_TIME_FORMAT] ?: DEFAULT_TIME_FORMAT),
            "language" to (prefs[KEY_LANGUAGE] ?: DEFAULT_LANGUAGE),
            "ess_email" to (prefs[KEY_ESS_EMAIL] ?: ""),
            "ess_password" to (prefs[KEY_ESS_PASSWORD] ?: ""),
            "ess_save_creds" to (prefs[KEY_ESS_SAVE_CREDS] ?: false),
            "dark_mode" to (prefs[KEY_DARK_MODE] ?: true),
            "theme_mode" to (prefs[KEY_THEME_MODE] ?: DEFAULT_THEME_MODE),
            "employee_name" to (prefs[KEY_EMPLOYEE_NAME] ?: ""),
            "custom_start_date" to (prefs[KEY_CUSTOM_START_DATE] ?: ""),
            "custom_end_date" to (prefs[KEY_CUSTOM_END_DATE] ?: ""),
            "fixed_employment_enabled" to (prefs[KEY_FIXED_EMPLOYMENT] ?: DEFAULT_FIXED_EMPLOYMENT),
            "standard_shift_hours" to (prefs[KEY_STANDARD_SHIFT_HOURS] ?: DEFAULT_STANDARD_SHIFT_HOURS)
        )
    }

    suspend fun restorePrefsFromMap(map: Map<String, Any?>) {
        context.dataStore.edit { p ->
            (map["hourly_rate"] as? Number)?.toDouble()?.let { p[KEY_HOURLY_RATE] = it }
            (map["payroll_start_day"] as? Number)?.toInt()?.let { p[KEY_PAYROLL_START] = it }
            (map["payroll_cycle_type"] as? String)?.let { p[KEY_PAYROLL_CYCLE] = it }
            (map["time_format"] as? String)?.let { p[KEY_TIME_FORMAT] = it }
            (map["language"] as? String)?.let { p[KEY_LANGUAGE] = it }
            (map["ess_email"] as? String)?.let { p[KEY_ESS_EMAIL] = it }
            (map["ess_password"] as? String)?.let { p[KEY_ESS_PASSWORD] = it }
            (map["ess_save_creds"] as? Boolean)?.let { p[KEY_ESS_SAVE_CREDS] = it }
            (map["dark_mode"] as? Boolean)?.let { p[KEY_DARK_MODE] = it }
            (map["theme_mode"] as? String)?.let { p[KEY_THEME_MODE] = it }
            (map["employee_name"] as? String)?.let { p[KEY_EMPLOYEE_NAME] = it }
            (map["custom_start_date"] as? String)?.let { p[KEY_CUSTOM_START_DATE] = it }
            (map["custom_end_date"] as? String)?.let { p[KEY_CUSTOM_END_DATE] = it }
            (map["fixed_employment_enabled"] as? Boolean)?.let { p[KEY_FIXED_EMPLOYMENT] = it }
            (map["standard_shift_hours"] as? Number)?.toDouble()?.let { p[KEY_STANDARD_SHIFT_HOURS] = it }
        }
    }
}
