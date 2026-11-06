package com.prudent66.vetguide.database

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** Room aggregate for the condition detail screen. */
data class ConditionWithRelations(
    @Embedded val condition: ConditionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "conditionId",
    )
    val species: List<ConditionSpeciesJunction>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ConditionSymptomJunction::class,
            parentColumn = "conditionId",
            entityColumn = "symptomId",
        ),
    )
    val symptoms: List<SymptomEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ConditionReferenceJunction::class,
            parentColumn = "conditionId",
            entityColumn = "referenceId",
        ),
    )
    val references: List<ReferenceEntity>,
)

/** Room aggregate for the list/search screen (species tags included). */
data class ConditionWithSpecies(
    @Embedded val condition: ConditionEntity,
    @Relation(parentColumn = "id", entityColumn = "conditionId")
    val species: List<ConditionSpeciesJunction>,
)
