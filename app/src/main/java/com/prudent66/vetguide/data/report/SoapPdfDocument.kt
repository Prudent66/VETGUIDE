package com.prudent66.vetguide.data.report

import android.graphics.pdf.PdfDocument
import com.prudent66.vetguide.domain.model.SoapPayload

/** All data the PDF renderer needs — assembled by the case detail ViewModel. */
data class SoapReportData(
    val patientLine: String,
    val ownerLine: String?,
    val caseId: String,
    val complaint: String,
    val status: String,
    val generatedAt: String,
    val soap: SoapPayload,
    val aiNarrative: String?,
    val imageBase64: String?,
    val clinicName: String? = null,
    val vetName: String? = null,
    val clinicContact: String? = null,
    val animalCategory: String = "Domestic",
    val differentials: List<Pair<String, Double>> = emptyList(),
    val prescriptions: List<String> = emptyList(),
    val supportiveCare: List<String> = emptyList(),
)

/**
 * Backwards-compatible entry point. The clinical report layout now lives in
 * [PdfReportExporter], which renders the hospital-grade branded document.
 */
object SoapPdfDocument {

    fun build(data: SoapReportData): PdfDocument = PdfReportExporter.build(
        HospitalReportData(
            patientLine = data.patientLine,
            ownerLine = data.ownerLine,
            caseId = data.caseId,
            complaint = data.complaint,
            status = data.status,
            generatedAt = data.generatedAt,
            soap = data.soap,
            narrative = data.aiNarrative,
            imageBase64 = data.imageBase64,
            clinicName = data.clinicName,
            vetName = data.vetName,
            clinicContact = data.clinicContact,
            animalCategory = data.animalCategory,
            differentials = data.differentials,
            prescriptions = data.prescriptions,
            supportiveCare = data.supportiveCare,
        ),
    )
}
