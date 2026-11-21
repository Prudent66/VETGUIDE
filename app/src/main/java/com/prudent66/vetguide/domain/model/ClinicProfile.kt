package com.prudent66.vetguide.domain.model

/**
 * Clinic / practitioner identity persisted in the existing `app_settings`
 * key-value table (no schema change). Rendered in the exported SOAP PDF
 * header/footer when filled in.
 */
data class ClinicProfile(
    val clinicName: String = "",
    val vetName: String = "",
    val contact: String = "",
) {
    val isBlank: Boolean
        get() = clinicName.isBlank() && vetName.isBlank() && contact.isBlank()
}
