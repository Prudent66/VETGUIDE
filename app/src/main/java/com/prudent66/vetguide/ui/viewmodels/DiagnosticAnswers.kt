package com.prudent66.vetguide.ui.viewmodels

import com.prudent66.vetguide.domain.model.AnsweredQuestionPayload

/** Maps a wizard's selections to the QUESTIONNAIRE artifact rows (Phase 7). */
internal fun DiagnosticUiState.Wizard.answeredQuestions(): List<AnsweredQuestionPayload> =
    questions.map { question ->
        AnsweredQuestionPayload(
            question = question.text,
            answers = question.options
                .filter { it.id in selections[question.id].orEmpty() }
                .map { it.label },
        )
    }
