package me.foesio.foCrafts.api;

/** Outcome of a recipe write; success means the new recipes.yml was saved. */
public record RecipeWriteResult(Status status, String recipeId, String message) {
    public enum Status { CREATED, UPDATED, UNCHANGED, DELETED, NOT_FOUND, INVALID, SAVE_FAILED, UNAVAILABLE }

    /** True only when this call changed and saved the catalog; UNCHANGED is a valid no-op. */
    public boolean successful() {
        return status == Status.CREATED || status == Status.UPDATED || status == Status.DELETED;
    }
}
