package com.prudent66.vetguide.domain.model

/**
 * Signalment vocabulary shared by entities, repositories, and UI.
 * v1 scope is dog/cat only (roadmap §6.1/§2.1).
 */
enum class Species { DOG, CAT }

enum class PatientSex { FEMALE, MALE, UNKNOWN }

enum class NeuterStatus { INTACT, NEUTERED, SPAYED, UNKNOWN }
