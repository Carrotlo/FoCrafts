package me.foesio.foCrafts.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/** Read-only access to FoCrafts' currently loaded recipe definitions. */
public interface RecipeCatalog {
    List<RecipeData> recipes();

    Optional<RecipeData> recipe(String id);

    /** Call on the player's owning server thread; does not check costs or ingredients. */
    RecipeAvailability availability(Player player, String recipeId);
}
