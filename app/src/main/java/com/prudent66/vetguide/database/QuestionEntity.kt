package com.prudent66.vetguide.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Guided questionnaire — one question per step (roadmap Phase 3).
 * `species` stores the Species enum name; a question belongs to one flow.
 * All question/option text is placeholder pending veterinary review.
 */
@Entity(tableName = "kb_questions")
data class QuestionEntity(
    @PrimaryKey val id: String,
    val species: String,
    val text: String,
    val orderIndex: Int,
)
