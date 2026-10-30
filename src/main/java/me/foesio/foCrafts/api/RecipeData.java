package me.foesio.foCrafts.api;

import me.foesio.foCrafts.model.RecipeMatchMode;
import me.foesio.foCrafts.model.RecipeType;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A detached recipe definition. Item stacks are copied on construction and access. */
public record RecipeData(String id, String displayName, boolean enabled, RecipeType type,
                         ItemStack result, Map<Integer, ItemStack> ingredients, String permission,
                         String description, List<String> disabledWorlds, RecipeMatchMode matchMode,
                         int xpLevels, int xpPoints, double vaultMoney, List<String> craftCommands) {
    public RecipeData {
        result = result == null ? null : result.clone();
        ingredients = copyIngredients(ingredients);
        disabledWorlds = disabledWorlds == null ? List.of() : List.copyOf(disabledWorlds);
        craftCommands = craftCommands == null ? List.of() : List.copyOf(craftCommands);
    }

    @Override
    public ItemStack result() {
        return result == null ? null : result.clone();
    }

    @Override
    public Map<Integer, ItemStack> ingredients() {
        return copyIngredients(ingredients);
    }

    private static Map<Integer, ItemStack> copyIngredients(Map<Integer, ItemStack> source) {
        if (source == null) return Map.of();
        Map<Integer, ItemStack> copy = new LinkedHashMap<>();
        source.forEach((slot, item) -> copy.put(slot, item == null ? null : item.clone()));
        return Collections.unmodifiableMap(copy);
    }
}
