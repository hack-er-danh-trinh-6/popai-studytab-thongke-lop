package com.health.pop.domain.model

data class AssessmentResult(
    val id: String = "",
    val completedAt: String = "",
    val phase: AssessmentPhase = AssessmentPhase.PRE,
    val vnScore: Float = 0f,
    val ptScore: Float = 0f,
    val pbScore: Float = 0f,
    val ttScore: Float = 0f,
    val dgScore: Float = 0f,
    val profile: Map<String, String> = emptyMap(),
    val openAnswers: Map<String, String> = emptyMap(),
    val note: String = "Phục vụ nghiên cứu, không phải chẩn đoán"
)
