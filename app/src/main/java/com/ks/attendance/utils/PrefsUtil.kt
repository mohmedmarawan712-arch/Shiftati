package com.ks.attendance.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
        val KEY_EMPLOYEE_NAME     = stringPreferencesKey("employee_name")
        val KEY_TIME_FORMAT       = stringPreferencesKey("time_format") // "12h" | "24h"

        const val DEFAULT_HOURLY_RATE   = 30.0
        const val DEFAULT_PAYROLL_START = 29
        const val DEFAULT_LANGUAGE      = "ar"
        const val DEFAULT_TIME_FORMAT   = "12h"
    }

    val allPreferences: Flow<Preferences> = context.dataStore.data

    val hourlyRate: Flow<Double> = context.dataStore.data.map {
        it[KEY_HOURLY_RATE] ?: DEFAULT_HOURLY_RATE
    }

    val payrollStartDay: Flow<Int> = context.dataStore.data.map {
        it[KEY_PAYROLL_START] ?: DEFAULT_PAYROLL_START
    }

    val payrollCycleType: Flow<String> = context.dataStore.data.map {
        it[KEY_PAYROLL_CYCLE] ?: PayrollUtil.CYCLE_29_TO_28
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

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DARK_MODE] = enabled }
    }

    suspend fun setEmployeeName(name: String) {
        val shortened = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.take(2).joinToString(" ")
        context.dataStore.edit { it[KEY_EMPLOYEE_NAME] = shortened }
    }

    suspend fun setTimeFormat(format: String) {
        context.dataStore.edit { it[KEY_TIME_FORMAT] = format }
    }
}
