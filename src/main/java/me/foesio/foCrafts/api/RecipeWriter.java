package me.foesio.foCrafts.api;

/** Validated recipe writes. Invoke from a server event or scheduler thread, never an async worker. */
public interface RecipeWriter {
    RecipeWriteResult upsert(RecipeData recipe);

    RecipeWriteResult delete(String recipeId);

    /** Renames both the display name and generated ID, matching the admin editor. */
    RecipeWriteResult rename(String recipeId, String displayName);
}
