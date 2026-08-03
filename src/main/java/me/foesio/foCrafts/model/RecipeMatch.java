package me.foesio.foCrafts.model;

import java.util.Map;

public record RecipeMatch(CustomRecipe recipe, int maxCraftable, Map<Integer, Integer> shapelessMapping) {
}
