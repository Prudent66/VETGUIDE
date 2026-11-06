package com.prudent66.vetguide.domain.model

/**
 * Signalment vocabulary shared by entities, repositories, and UI.
 * Clinical scope v1: dogs, cats, and Nigerian livestock — ruminants
 * (cattle, goats, sheep), poultry, swine, equines (+ generic "Other").
 */
enum class Species { DOG, CAT, CATTLE, GOAT, SHEEP, POULTRY, PIG, EQUINE, OTHER }

enum class PatientSex { FEMALE, MALE, UNKNOWN }

enum class NeuterStatus { INTACT, NEUTERED, SPAYED, UNKNOWN }
