package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.Species
import kotlin.math.ceil

/** Field-entered summary of a herd, pen or flock unit. */
data class HerdSnapshot(
    val unitName: String = "",
    val species: Species = Species.CATTLE,
    val headcount: Int = 0,
    val sickCount: Int = 0,
    val deadCount: Int = 0,
    val averageWeightKg: Double = 0.0,
)

/** Morbidity and case-fatality summaries. */
data class HerdMetrics(
    val morbidityPercent: Double,
    val caseFatalityPercent: Double,
    val affectedCount: Int,
)

/** Whole-group medication and logistics for one formulary drug. */
data class BatchTreatment(
    val drugName: String,
    val headcount: Int,
    val doseMgPerKg: Double,
    val totalMg: Double,
    val totalMl: Double,
    val totalLiters: Double,
    val bottleCount: Int,
    val bottleSizeMl: Double,
    val waterVolumeLiters: Double?,
    val meatWithdrawalDays: Int,
    val milkWithdrawalDays: Int,
)

/** Field mixing assumption: 100 mL of stock solution per litre of drinking water. */
const val WATER_ML_PER_LITRE = 100.0

/**
 * Morbidity = sick / headcount. Case fatality = dead / (sick + dead), i.e. the
 * share of affected animals that died. Returns null for an invalid headcount.
 */
fun herdMetrics(snapshot: HerdSnapshot): HerdMetrics? {
    if (snapshot.headcount <= 0) return null
    val affected = snapshot.sickCount + snapshot.deadCount
    val morbidity = snapshot.sickCount.toDouble() / snapshot.headcount * 100.0
    val fatality = if (affected == 0) {
        0.0
    } else {
        snapshot.deadCount.toDouble() / affected * 100.0
    }
    return HerdMetrics(morbidity, fatality, affected)
}

/**
 * Whole-group dose using the formulary midpoint for this species. Total mL is
 * converted from mg via the selected bottle concentration; vial count rounds
 * up. Water-medication routes also get a drinking-water mixing volume.
 */
fun batchTreatment(
    drug: FormularyDrug,
    snapshot: HerdSnapshot,
    concentrationMgPerMl: Double = drug.defaultConcentrationMgPerMl,
    bottleSizeMl: Double = 100.0,
): BatchTreatment? {
    val range = drug.doseBySpecies[snapshot.species]
    if (
        range == null ||
        snapshot.headcount <= 0 ||
        snapshot.averageWeightKg <= 0.0 ||
        concentrationMgPerMl <= 0.0 ||
        bottleSizeMl <= 0.0
    ) {
        return null
    }
    val perKg = range.recommended()
    val totalMg = perKg * snapshot.averageWeightKg * snapshot.headcount
    val totalMl = totalMg / concentrationMgPerMl
    return BatchTreatment(
        drugName = drug.name,
        headcount = snapshot.headcount,
        doseMgPerKg = perKg,
        totalMg = totalMg,
        totalMl = totalMl,
        totalLiters = totalMl / 1000.0,
        bottleCount = ceil(totalMl / bottleSizeMl).toInt(),
        bottleSizeMl = bottleSizeMl,
        waterVolumeLiters = if (drug.route == DoseRoute.WATER) {
            totalMl / WATER_ML_PER_LITRE
        } else {
            null
        },
        meatWithdrawalDays = drug.meatWithdrawalDays,
        milkWithdrawalDays = drug.milkWithdrawalDays,
    )
}

private val baseChecklist = listOf(
    "Isolate affected animals immediately.",
    "Restrict movement of animals, people and vehicles between pens.",
    "Use dedicated boots and equipment for the affected group.",
    "Disinfect housing, drinkers and equipment daily.",
    "Record clinical signs, temperatures and deaths twice daily.",
    "Notify the animal-health authority about suspected contagious disease.",
)

private val diseaseSteps = mapOf(
    "contagious-bovine-pleuropneumonia" to "Test and movement-restrict the whole herd for CBPP.",
    "foot-and-mouth-disease" to "Stop all animal movements and report suspected FMD immediately.",
    "peste-des-petits-ruminants" to "Isolate the flock and report suspected PPR immediately.",
    "newcastle-disease" to "Quarantine the flock and report suspected Newcastle disease.",
    "fowl-pox" to "Isolate affected birds and control biting insects and shared equipment.",
)

/** Quarantine checklist: universal steps plus disease-specific actions. */
fun quarantineChecklist(suspectedConditionIds: List<String>): List<String> =
    baseChecklist + diseaseSteps.filterKeys { it in suspectedConditionIds }.values.toList()
