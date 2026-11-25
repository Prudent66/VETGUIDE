package com.prudent66.vetguide.domain.model

/**
 * Domestic species vocabulary shared by entities, repositories, and UI.
 * The original eight species (dogs, cats and Nigerian livestock) are kept in
 * their original enum order for Room persistence; Build 23 adds global farmed
 * species. [OTHER] remains the legacy "Other Domestic" placeholder and is
 * never used for wildlife (wild patients keep their own category and group).
 */
enum class Species {
    DOG, CAT, CATTLE, GOAT, SHEEP, POULTRY, PIG, EQUINE,
    BUFFALO, CAMEL, RABBIT, FISH, OTHER,
}

enum class PatientSex { FEMALE, MALE, UNKNOWN }

enum class NeuterStatus { INTACT, NEUTERED, SPAYED, UNKNOWN }
