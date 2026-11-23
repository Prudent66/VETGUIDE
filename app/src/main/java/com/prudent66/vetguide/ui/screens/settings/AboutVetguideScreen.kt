package com.prudent66.vetguide.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.BuildConfig
import com.prudent66.vetguide.R
import com.prudent66.vetguide.ui.components.LocalBottomBarClearance

/**
 * Dedicated "About VETGUIDE" guide screen for veterinarians, paravets,
 * veterinary students and livestock field officers. All copy is user-facing
 * and deliberately free of provider/product jargon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutVetguideScreen(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("About VETGUIDE") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.nav_back),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = LocalBottomBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OverviewSection()
            CapabilitiesSection()
            HowToSection()
            ResponsibilitySection()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun SectionBody(text: String) {
    Text(
        text = text.trimIndent(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun OverviewSection() {
    SectionTitle("Overview & Purpose")
    SectionBody(
        """
        VETGUIDE is a mobile-first veterinary clinical decision-support and
        case-management assistant developed by Veloxdigitalforge. It is built
        for both clinic-based practice and rural, low-connectivity livestock
        field work.

        The app helps veterinary professionals capture clinical observations,
        review structured findings and suspected conditions, and manage
        patient records — all while remaining functional offline and
        syncing automatically when connectivity returns.
        """,
    )
    Text(
        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CapabilitiesSection() {
    SectionTitle("Key Capabilities")
    SectionBody(
        """
        Visual & Clinical Triage: Capture or upload animal photos alongside
        species, breed, age, weight and clinical observations to review
        findings, suspected conditions with confidence scores, and structured
        field guidance.

        Separated Prescriptions & Dosages Sheet & Syringe Calculator: Drug and
        injection protocols are presented on their own dedicated sheet, kept
        distinct from general supportive care. Using the animal's recorded
        species and weight, VETGUIDE automatically calculates exact milligram
        doses, syringe draw volumes (mL) for the selected bottle concentration,
        meat and milk withdrawal periods, and species safety alerts, so the
        clinician can verify everything before administering treatment.

        Offline Resilience & Auto-Sync: Works reliably offline using built-in
        clinical questionnaires and an offline veterinary knowledge base, and
        automatically queues cases to sync when internet connectivity returns.

        Patient Records, SOAP Notes & PDF Export: Manage patient profiles,
        auto-populate structured SOAP clinical notes, export clean PDF case
        reports, and back up or restore all records locally via JSON.
        """,
    )
}

@Composable
private fun HowToSection() {
    SectionTitle("How to Use VETGUIDE")
    SectionBody(
        """
        Step 1 — Register or Select a Patient: Open Patients (+) to record
        species, breed, age, weight and sex, or capture a quick case directly
        from Diagnose.

        Step 2 — Capture Photo & Clinical Notes: Go to Diagnose, take or select
        a clear photo of the lesion or condition, and add brief clinical signs
        or observations to sharpen results.

        Step 3 — Review Analysis & Prescriptions: Inspect the findings and
        suspected conditions, then tap Open Prescriptions & Dosages. The sheet
        is pre-filled with the animal's species and recorded weight — adjust
        the weight or bottle concentration if needed, and verify the calculated
        mL syringe volume and withdrawal period before administering treatment.

        Step 4 — Save SOAP Note & Export PDF: Save the case to the patient's
        clinical record, review or edit the auto-populated SOAP plan, and
        export or share a PDF summary.

        Step 5 — Offline Queue & Backup: When working without signal, queue
        cases for automatic sync and periodically use Export Backup (JSON) in
        Settings to safeguard records.
        """,
    )
}

@Composable
private fun ResponsibilitySection() {
    SectionTitle("Clinical Responsibility & Developer Attribution")
    SectionBody(
        """
        Decision support — not a definitive diagnosis. The licensed
        veterinarian or animal health professional remains solely responsible
        for all clinical examinations, drug dosages, withdrawal periods, and
        treatment decisions.
        """,
    )
    Text(
        text = "© 2026 Veloxdigitalforge - All Rights Reserved",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
