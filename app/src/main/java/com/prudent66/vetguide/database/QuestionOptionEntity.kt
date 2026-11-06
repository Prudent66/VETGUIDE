package com.prudent66.vetguide.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One multiple-choice answer mapped to a signed-in [SymptomEntity] id (or null). */
@Entity(
    tableName = "kb_question_options",
    indices = [Index("questionId")],
)
data class QuestionOptionEntity(
    @PrimaryKey val id: String,
    val questionId: String,
    val label: String,
    val symptomId: String?,
    val orderIndex: Int,
)
