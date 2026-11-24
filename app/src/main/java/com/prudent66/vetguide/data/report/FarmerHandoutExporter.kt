package com.prudent66.vetguide.data.report

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Inputs for a plain-language owner handout. */
data class HandoutInput(
    val patientName: String,
    val drugName: String,
    val doseInstructions: String,
    val isolationSteps: String,
    val fromEpochMs: Long,
    val withdrawal: HandoutWithdrawal,
)

/** Meat and milk withdrawal periods in days. */
data class HandoutWithdrawal(
    val meatDays: Int,
    val milkDays: Int,
)

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
    fun buildHandout(input: HandoutInput): String {
        val meatDate = formatDate(withdrawalSafeDate(input.fromEpochMs, input.withdrawal.meatDays))
        val milkDate = formatDate(withdrawalSafeDate(input.fromEpochMs, input.withdrawal.milkDays))
        return buildString {
            append("VETGUIDE — Owner Care Sheet\n\n")
            append("Animal: ${input.patientName}\n")
            append("Treatment: ${input.drugName} — ${input.doseInstructions}\n\n")
            append("Isolation & care:\n${input.isolationSteps}\n\n")
            append("Meat withdrawal safe date: $meatDate (${input.withdrawal.meatDays} days)\n")
            append("Milk withdrawal safe date: $milkDate (${input.withdrawal.milkDays} days)\n\n")
            append("Always follow the attending veterinarian's instructions.\n")
        }
    }
}
