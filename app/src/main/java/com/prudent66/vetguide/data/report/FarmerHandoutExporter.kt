package com.prudent66.vetguide.data.report

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Plain-language owner handout with an exact meat/milk withdrawal safe date. */
object FarmerHandoutExporter {

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

    /** First calendar date on which meat/milk from the treated animal is safe. */
    fun withdrawalSafeDate(fromEpochMs: Long, withdrawalDays: Int): LocalDate =
        Instant.ofEpochMilli(fromEpochMs)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .plusDays(withdrawalDays.toLong())

    fun formatDate(date: LocalDate): String = date.format(DATE_FORMAT)

    /** Builds the shareable, plain-language care sheet. */
    fun buildHandout(
        patientName: String,
        drugName: String,
        doseInstructions: String,
        isolationSteps: String,
        fromEpochMs: Long,
        meatWithdrawalDays: Int,
        milkWithdrawalDays: Int,
    ): String {
        val meatDate = formatDate(withdrawalSafeDate(fromEpochMs, meatWithdrawalDays))
        val milkDate = formatDate(withdrawalSafeDate(fromEpochMs, milkWithdrawalDays))
        return buildString {
            append("VETGUIDE — Owner Care Sheet\n\n")
            append("Animal: $patientName\n")
            append("Treatment: $drugName — $doseInstructions\n\n")
            append("Isolation & care:\n$isolationSteps\n\n")
            append("Meat withdrawal safe date: $meatDate ($meatWithdrawalDays days)\n")
            append("Milk withdrawal safe date: $milkDate ($milkWithdrawalDays days)\n\n")
            append("Always follow the attending veterinarian's instructions.\n")
        }
    }
}
