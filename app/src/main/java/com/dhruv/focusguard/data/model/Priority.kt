package com.dhruv.focusguard.data.model

enum class Priority(val label: String, val weight: Int) {
    LOW("Low", 0),
    MEDIUM("Medium", 1),
    HIGH("High", 2),
    URGENT("Urgent", 3);

    companion object {
        fun fromOrdinal(ordinal: Int): Priority = entries.getOrElse(ordinal) { MEDIUM }
    }
}
