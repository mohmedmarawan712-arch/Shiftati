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
 * Supports account-scoped preferences for employment mode, rates, shift hours, and payroll cycles.
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
        val KEY_HAS_CHOSEN_EMPLOYMENT = booleanPreferencesKey("has_chosen_employment")

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

        fun keyHourlyRate(accId: Long) = if (accId == 0L) KEY_HOURLY_RATE else doublePreferencesKey("hourly_rate_$accId")
        fun keyPayrollStart(accId: Long) = if (accId == 0L) KEY_PAYROLL_START else intPreferencesKey("payroll_start_day_$accId")
        fun keyPayrollCycle(accId: Long) = if (accId == 0L) KEY_PAYROLL_CYCLE else stringPreferencesKey("payroll_cycle_type_$accId")
        fun keyCustomStartDate(accId: Long) = if (accId == 0L) KEY_CUSTOM_START_DATE else stringPreferencesKey("custom_start_date_$accId")
        fun keyCustomEndDate(accId: Long) = if (accId == 0L) KEY_CUSTOM_END_DATE else stringPreferencesKey("custom_end_date_$accId")
        fun keyFixedEmployment(accId: Long) = if (accId == 0L) KEY_FIXED_EMPLOYMENT else booleanPreferencesKey("fixed_employment_enabled_$accId")
        fun keyStandardShiftHours(accId: Long) = if (accId == 0L) KEY_STANDARD_SHIFT_HOURS else doublePreferencesKey("standard_shift_hours_$accId")
        fun keyHasChosenEmployment(accId: Long) = if (accId == 0L) KEY_HAS_CHOSEN_EMPLOYMENT else booleanPreferencesKey("has_chosen_employment_$accId")
    }

    val allPreferences: Flow<Preferences> = context.dataStore.data

    // ─── Scoped Getters (from snapshot Preferences) ──────────────────────────

    fun getHourlyRate(prefs: Preferences, accId: Long = 0L): Double {
        if (accId != 0L) {
            val v = prefs[keyHourlyRate(accId)]
            if (v != null) return v
        }
        return prefs[KEY_HOURLY_RATE] ?: DEFAULT_HOURLY_RATE
    }

    fun getPayrollStartDay(prefs: Preferences, accId: Long = 0L): Int {
        if (accId != 0L) {
            val v = prefs[keyPayrollStart(accId)]
            if (v != null) return v
        }
        return prefs[KEY_PAYROLL_START] ?: DEFAULT_PAYROLL_START
    }

    fun getPayrollCycleType(prefs: Preferences, accId: Long = 0L): String {
        if (accId != 0L) {
            val v = prefs[keyPayrollCycle(accId)]
            if (v != null) return v
        }
        return prefs[KEY_PAYROLL_CYCLE] ?: PayrollUtil.CYCLE_16_TO_15
    }

    fun getCustomStartDate(prefs: Preferences, accId: Long = 0L): String {
        if (accId != 0L) {
            val v = prefs[keyCustomStartDate(accId)]
            if (v != null) return v
        }
        return prefs[KEY_CUSTOM_START_DATE] ?: ""
    }

    fun getCustomEndDate(prefs: Preferences, accId: Long = 0L): String {
        if (accId != 0L) {
            val v = prefs[keyCustomEndDate(accId)]
            if (v != null) return v
        }
        return prefs[KEY_CUSTOM_END_DATE] ?: ""
    }

    fun getFixedEmployment(prefs: Preferences, accId: Long = 0L): Boolean {
        if (accId != 0L) {
            val v = prefs[keyFixedEmployment(accId)]
            if (v != null) return v
        }
        return prefs[KEY_FIXED_EMPLOYMENT] ?: DEFAULT_FIXED_EMPLOYMENT
    }

    fun getStandardShiftHours(prefs: Preferences, accId: Long = 0L): Double {
        if (accId != 0L) {
            val v = prefs[keyStandardShiftHours(accId)]
            if (v != null) return v
        }
        return prefs[KEY_STANDARD_SHIFT_HOURS] ?: DEFAULT_STANDARD_SHIFT_HOURS
    }

    fun getHasChosenEmployment(prefs: Preferences, accId: Long = 0L): Boolean {
        if (accId != 0L) {
            val v = prefs[keyHasChosenEmployment(accId)]
            if (v != null) return v
        }
        return prefs[KEY_HAS_CHOSEN_EMPLOYMENT] ?: false
    }

    // ─── Flow Getters ────────────────────────────────────────────────────────

    fun hourlyRate(accId: Long = 0L): Flow<Double> = context.dataStore.data.map { getHourlyRate(it, accId) }
    val hourlyRate: Flow<Double> get() = hourlyRate(0L)

    fun payrollStartDay(accId: Long = 0L): Flow<Int> = context.dataStore.data.map { getPayrollStartDay(it, accId) }
    val payrollStartDay: Flow<Int> get() = payrollStartDay(0L)

    fun payrollCycleType(accId: Long = 0L): Flow<String> = context.dataStore.data.map { getPayrollCycleType(it, accId) }
    val payrollCycleType: Flow<String> get() = payrollCycleType(0L)

    fun fixedEmploymentEnabled(accId: Long = 0L): Flow<Boolean> = context.dataStore.data.map { getFixedEmployment(it, accId) }
    val fixedEmploymentEnabled: Flow<Boolean> get() = fixedEmploymentEnabled(0L)

    fun standardShiftHours(accId: Long = 0L): Flow<Double> = context.dataStore.data.map { getStandardShiftHours(it, accId) }
    val standardShiftHours: Flow<Double> get() = standardShiftHours(0L)

    fun hasChosenEmployment(accId: Long = 0L): Flow<Boolean> = context.dataStore.data.map { getHasChosenEmployment(it, accId) }
    val hasChosenEmployment: Flow<Boolean> get() = hasChosenEmployment(0L)

    fun customStartDate(accId: Long = 0L): Flow<String> = context.dataStore.data.map { getCustomStartDate(it, accId) }
    val customStartDate: Flow<String> get() = customStartDate(0L)

    fun customEndDate(accId: Long = 0L): Flow<String> = context.dataStore.data.map { getCustomEndDate(it, accId) }
    val customEndDate: Flow<String> get() = customEndDate(0L)

    // ─── Global App Preferences ──────────────────────────────────────────────

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
        it[KEY_DARK_MODE] ?: true // Default to true (AMOLED)
    }

    val themeMode: Flow<String> = context.dataStore.data.map {
        it[KEY_THEME_MODE] ?: DEFAULT_THEME_MODE
    }

    val employeeName: Flow<String> = context.dataStore.data.map {
        it[KEY_EMPLOYEE_NAME] ?: ""
    }

    // ─── Setters with Optional Account Scope ─────────────────────────────────

    suspend fun setHourlyRate(value: Double, accId: Long = 0L) {
        context.dataStore.edit {
            it[keyHourlyRate(accId)] = value
            if (accId == 0L) it[KEY_HOURLY_RATE] = value
        }
    }

    suspend fun setPayrollStartDay(day: Int, accId: Long = 0L) {
        context.dataStore.edit {
            it[keyPayrollStart(accId)] = day
            if (accId == 0L) it[KEY_PAYROLL_START] = day
        }
    }

    suspend fun setPayrollCycleType(type: String, accId: Long = 0L) {
        context.dataStore.edit {
            it[keyPayrollCycle(accId)] = type
            if (accId == 0L) it[KEY_PAYROLL_CYCLE] = type
        }
    }

    suspend fun setCustomPeriodRange(start: String, end: String, accId: Long = 0L) {
        context.dataStore.edit {
            it[keyCustomStartDate(accId)] = start
            it[keyCustomEndDate(accId)] = end
            it[keyPayrollCycle(accId)] = PayrollUtil.CYCLE_CUSTOM_RANGE
            if (accId == 0L) {
                it[KEY_CUSTOM_START_DATE] = start
                it[KEY_CUSTOM_END_DATE] = end
                it[KEY_PAYROLL_CYCLE] = PayrollUtil.CYCLE_CUSTOM_RANGE
            }
        }
    }

    suspend fun setFixedEmployment(enabled: Boolean, accId: Long = 0L) {
        context.dataStore.edit {
            it[keyFixedEmployment(accId)] = enabled
            if (accId == 0L) it[KEY_FIXED_EMPLOYMENT] = enabled
        }
    }

    suspend fun setStandardShiftHours(hours: Double, accId: Long = 0L) {
        context.dataStore.edit {
            it[keyStandardShiftHours(accId)] = hours
            if (accId == 0L) it[KEY_STANDARD_SHIFT_HOURS] = hours
        }
    }

    suspend fun setHasChosenEmployment(chosen: Boolean, accId: Long = 0L) {
        context.dataStore.edit {
            it[keyHasChosenEmployment(accId)] = chosen
            if (accId == 0L) it[KEY_HAS_CHOSEN_EMPLOYMENT] = chosen
        }
    }

    suspend fun clearAccountPrefs(accId: Long) {
        if (accId == 0L) return
        context.dataStore.edit {
            it.remove(keyHourlyRate(accId))
            it.remove(keyPayrollStart(accId))
            it.remove(keyPayrollCycle(accId))
            it.remove(keyCustomStartDate(accId))
            it.remove(keyCustomEndDate(accId))
            it.remove(keyFixedEmployment(accId))
            it.remove(keyStandardShiftHours(accId))
            it.remove(keyHasChosenEmployment(accId))
        }
    }

    // ─── Global Setters ──────────────────────────────────────────────────────

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

    suspend fun setTimeFormat(format: String) {
        context.dataStore.edit { it[KEY_TIME_FORMAT] = format }
    }

    // ─── JSON Backup & Restore Support ───────────────────────────────────────

    suspend fun getAllPrefsMap(): Map<String, Any?> {
        val prefs = context.dataStore.data.first()
        val map = mutableMapOf<String, Any?>()
        for ((key, value) in prefs.asMap()) {
            map[key.name] = value
        }
        return map
    }

    suspend fun restorePrefsFromMap(map: Map<String, Any?>) {
        context.dataStore.edit { p ->
            for ((keyName, value) in map) {
                when (value) {
                    is Boolean -> p[booleanPreferencesKey(keyName)] = value
                    is Double -> p[doublePreferencesKey(keyName)] = value
                    is Float -> p[floatPreferencesKey(keyName)] = value
                    is Int -> p[intPreferencesKey(keyName)] = value
                    is Long -> p[longPreferencesKey(keyName)] = value
                    is String -> p[stringPreferencesKey(keyName)] = value
                    is Number -> p[doublePreferencesKey(keyName)] = value.toDouble()
                }
            }
        }
    }
}
