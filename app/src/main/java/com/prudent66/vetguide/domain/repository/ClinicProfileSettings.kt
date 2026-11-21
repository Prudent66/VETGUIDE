package com.prudent66.vetguide.domain.repository

import com.prudent66.vetguide.domain.model.ClinicProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Clinic-profile keys and persistence helpers over the generic
 * [SettingsRepository] (`app_settings` table). Keeping the keys here prevents
 * UI/PDF code from drifting apart. No schema change: every value is one row.
 */
object ClinicProfileSettings {

    const val KEY_CLINIC_NAME = "clinic_name"
    const val KEY_VET_NAME = "vet_name"
    const val KEY_CONTACT = "clinic_contact"

    fun observe(repository: SettingsRepository): Flow<ClinicProfile> = combine(
        repository.observeString(KEY_CLINIC_NAME),
        repository.observeString(KEY_VET_NAME),
        repository.observeString(KEY_CONTACT),
    ) { clinicName, vetName, contact ->
        ClinicProfile(clinicName = clinicName, vetName = vetName, contact = contact)
    }

    suspend fun load(repository: SettingsRepository): ClinicProfile = ClinicProfile(
        clinicName = repository.getString(KEY_CLINIC_NAME),
        vetName = repository.getString(KEY_VET_NAME),
        contact = repository.getString(KEY_CONTACT),
    )

    suspend fun save(repository: SettingsRepository, profile: ClinicProfile) {
        repository.putString(KEY_CLINIC_NAME, profile.clinicName.trim())
        repository.putString(KEY_VET_NAME, profile.vetName.trim())
        repository.putString(KEY_CONTACT, profile.contact.trim())
    }
}
