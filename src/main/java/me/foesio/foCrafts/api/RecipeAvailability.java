package me.foesio.foCrafts.api;

/** Current recipe restrictions, excluding ingredient, XP, and money affordability. */
public enum RecipeAvailability {
    AVAILABLE, UNAVAILABLE, NOT_FOUND, DISABLED, NO_RESULT, NO_PERMISSION, WORLD_BLOCKED
}
