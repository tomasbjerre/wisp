package com.github.tomasbjerre.wisp.data

/**
 * See specs/calories.md#activity-type. [id] is what a session stores
 * (specs/data-model.md#session), so it must never change once shipped.
 */
enum class ActivityType(
    val id: String,
    val label: String,
) {
    WALKING("walking", "Walking"),
    RUNNING("running", "Running"),
    CYCLING("cycling", "Cycling"),
    ;

    companion object {
        /** Null for a session recorded before activity types existed, or an id this version doesn't know. */
        fun fromId(id: String?): ActivityType? = entries.firstOrNull { it.id == id }
    }
}
