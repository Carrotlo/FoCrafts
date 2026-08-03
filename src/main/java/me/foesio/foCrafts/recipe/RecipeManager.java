package me.foesio.foCrafts.recipe;

import me.foesio.core.item.FoItemStacks;
import me.foesio.foCrafts.model.CustomRecipe;
import me.foesio.foCrafts.model.RecipeCost;
import me.foesio.foCrafts.model.RecipeMatch;
import me.foesio.foCrafts.model.RecipeMatchMode;
import me.foesio.foCrafts.model.RecipeType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class RecipeManager {
    private final JavaPlugin plugin;
    private final Map<String, CustomRecipe> recipes = new LinkedHashMap<>();
    private File recipesFile;
    private FileConfiguration recipesConfig;

    public RecipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }

        recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
        if (!recipesFile.exists()) {
            plugin.saveResource("recipes.yml", false);
        }

        recipesConfig = YamlConfiguration.loadConfiguration(recipesFile);
        recipes.clear();

        ConfigurationSection root = recipesConfig.getConfigurationSection("recipes");
        if (root != null) {
            for (String recipeId : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(recipeId);
                if (section == null) {
                    continue;
                }
                CustomRecipe recipe = deserializeRecipe(recipeId, section);
                recipes.put(recipe.getId(), recipe);
            }
        }

        if (recipes.isEmpty()) {
            createDefaultRecipe();
        }

        // Always save after load to keep schema/default fields migrated forward.
        save();
    }

    public void save() {
        if (recipesConfig == null || recipesFile == null) {
            return;
        }

        recipesConfig.set("recipes", null);
        ConfigurationSection root = recipesConfig.createSection("recipes");
        for (CustomRecipe recipe : recipes.values()) {
            ConfigurationSection section = root.createSection(recipe.getId());
            serializeRecipeIntoSection(section, recipe);
        }

        try {
            recipesConfig.save(recipesFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save recipes.yml: " + exception.getMessage());
        }
    }

    public List<CustomRecipe> getEnabledRecipes() {
        List<CustomRecipe> list = new ArrayList<>();
        for (CustomRecipe recipe : recipes.values()) {
            if (recipe.isEnabled()) {
                list.add(recipe.copy());
            }
        }
        return list;
    }

    public List<CustomRecipe> getAllRecipes() {
        List<CustomRecipe> list = new ArrayList<>();
        for (CustomRecipe recipe : recipes.values()) {
            list.add(recipe.copy());
        }
        return list;
    }

    public Optional<CustomRecipe> getRecipe(String recipeId) {
        CustomRecipe recipe = recipes.get(recipeId);
        return Optional.ofNullable(recipe == null ? null : recipe.copy());
    }

    public void upsert(CustomRecipe recipe) {
        recipes.put(recipe.getId(), recipe.copy());
        save();
    }

    public boolean delete(String recipeId) {
        CustomRecipe removed = recipes.remove(recipeId);
        if (removed == null) {
            return false;
        }
        save();
        return true;
    }

    public String generateRecipeId() {
        int number = recipes.size() + 1;
        while (recipes.containsKey("recipe_" + number)) {
            number++;
        }
        return "recipe_" + number;
    }

    public String generateRecipeId(String displayName) {
        return uniqueRecipeId(baseRecipeId(displayName), null);
    }

    public Optional<CustomRecipe> renameRecipe(String currentRecipeId, String displayName) {
        CustomRecipe recipe = recipes.remove(currentRecipeId);
        if (recipe == null) {
            return Optional.empty();
        }

        recipe.setDisplayName(displayName);
        recipe.setId(uniqueRecipeId(baseRecipeId(displayName), currentRecipeId));
        recipes.put(recipe.getId(), recipe.copy());
        save();
        return Optional.of(recipe.copy());
    }

    private String uniqueRecipeId(String baseId, String allowedExistingId) {
        String safeBase = baseId == null || baseId.isBlank() ? "recipe" : baseId;
        String candidate = safeBase;
        int suffix = 2;
        while (recipes.containsKey(candidate) && !candidate.equals(allowedExistingId)) {
            candidate = safeBase + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String baseRecipeId(String displayName) {
        String normalized = Normalizer.normalize(displayName == null ? "" : displayName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "recipe" : normalized;
    }

    public RecipeMatch findFirstMatch(ItemStack[] grid) {
        return findFirstMatch(grid, recipe -> true);
    }

    public RecipeMatch findFirstMatch(ItemStack[] grid, Predicate<CustomRecipe> filter) {
        for (CustomRecipe recipe : recipes.values()) {
            if (!recipe.isEnabled() || !filter.test(recipe)) {
                continue;
            }
            RecipeMatch match = matchRecipe(recipe, grid);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    public RecipeMatch matchRecipe(CustomRecipe recipe, ItemStack[] grid) {
        if (recipe.getType() == RecipeType.SHAPED) {
            return matchShaped(recipe, grid);
        }
        return matchShapeless(recipe, grid);
    }

    private RecipeMatch matchShaped(CustomRecipe recipe, ItemStack[] grid) {
        Map<Integer, ItemStack> ingredients = recipe.getIngredients();
        int maxCrafts = Integer.MAX_VALUE;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack need = normalize(ingredients.get(slot));
            ItemStack have = normalize(grid[slot]);

            if (need == null) {
                if (have != null) {
                    return null;
                }
                continue;
            }

            if (have == null || !ingredientMatches(have, need, recipe.getMatchMode())) {
                return null;
            }
            if (have.getAmount() < need.getAmount()) {
                return null;
            }
            maxCrafts = Math.min(maxCrafts, have.getAmount() / need.getAmount());
        }

        if (maxCrafts == Integer.MAX_VALUE) {
            return null;
        }
        return new RecipeMatch(recipe.copy(), Math.max(1, maxCrafts), Collections.emptyMap());
    }

    private RecipeMatch matchShapeless(CustomRecipe recipe, ItemStack[] grid) {
        Map<Integer, ItemStack> ingredients = recipe.getIngredients();
        List<Map.Entry<Integer, ItemStack>> needs = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> entry : ingredients.entrySet()) {
            ItemStack normalized = normalize(entry.getValue());
            if (normalized != null) {
                needs.add(Map.entry(entry.getKey(), normalized));
            }
        }
        if (needs.isEmpty()) {
            return null;
        }

        List<Integer> occupiedSlots = new ArrayList<>();
        for (int i = 0; i < grid.length; i++) {
            if (normalize(grid[i]) != null) {
                occupiedSlots.add(i);
            }
        }
        if (occupiedSlots.size() != needs.size()) {
            return null;
        }

        Set<Integer> usedGridSlots = new LinkedHashSet<>();
        Map<Integer, Integer> mapping = new LinkedHashMap<>();
        int maxCrafts = Integer.MAX_VALUE;

        for (Map.Entry<Integer, ItemStack> needEntry : needs) {
            boolean matched = false;
            for (Integer gridSlot : occupiedSlots) {
                if (usedGridSlots.contains(gridSlot)) {
                    continue;
                }
                ItemStack have = normalize(grid[gridSlot]);
                ItemStack need = needEntry.getValue();
                if (have == null || !ingredientMatches(have, need, recipe.getMatchMode()) || have.getAmount() < need.getAmount()) {
                    continue;
                }

                usedGridSlots.add(gridSlot);
                mapping.put(needEntry.getKey(), gridSlot);
                maxCrafts = Math.min(maxCrafts, have.getAmount() / need.getAmount());
                matched = true;
                break;
            }
            if (!matched) {
                return null;
            }
        }

        if (usedGridSlots.size() != needs.size() || maxCrafts == Integer.MAX_VALUE) {
            return null;
        }
        return new RecipeMatch(recipe.copy(), Math.max(1, maxCrafts), mapping);
    }

    private boolean ingredientMatches(ItemStack have, ItemStack need, RecipeMatchMode mode) {
        if (have == null || need == null) {
            return false;
        }
        return switch (mode) {
            case MATERIAL_ONLY -> have.getType() == need.getType();
            case MATERIAL_META -> have.isSimilar(need);
            case STRICT_ALL -> {
                ItemStack left = FoItemStacks.cloneItem(have);
                ItemStack right = FoItemStacks.cloneItem(need);
                left.setAmount(1);
                right.setAmount(1);
                yield left.equals(right);
            }
        };
    }

    private CustomRecipe deserializeRecipe(String recipeId, ConfigurationSection section) {
        String displayName = section.getString("display-name", formatName(recipeId));
        boolean enabled = section.getBoolean("enabled", true);
        RecipeType type;
        try {
            type = RecipeType.valueOf(section.getString("type", "SHAPED").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            type = RecipeType.SHAPED;
        }

        ItemStack result = normalize(section.getItemStack("result"));

        Map<Integer, ItemStack> ingredients = new LinkedHashMap<>();
        ConfigurationSection ingredientSection = section.getConfigurationSection("ingredients");
        if (ingredientSection != null) {
            for (String key : ingredientSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot < 0 || slot > 8) {
                        continue;
                    }
                    ItemStack item = normalize(ingredientSection.getItemStack(key));
                    if (item != null) {
                        ingredients.put(slot, item);
                    }
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Invalid ingredient slot key '" + key + "' in recipe '" + recipeId + "'");
                }
            }
        }

        String permission = section.getString("permission", "");
        String description = section.getString("description", "");
        List<String> disabledWorlds = section.getStringList("disabled-worlds");

        RecipeMatchMode matchMode;
        try {
            matchMode = RecipeMatchMode.valueOf(section.getString("match-mode", "MATERIAL_META").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            matchMode = RecipeMatchMode.MATERIAL_META;
        }

        ConfigurationSection costSection = section.getConfigurationSection("cost");
        RecipeCost cost;
        if (costSection == null) {
            cost = new RecipeCost();
        } else {
            cost = new RecipeCost(
                    costSection.getInt("xp-levels", 0),
                    costSection.getInt("xp-points", 0),
                    costSection.getDouble("vault-money", 0.0D)
            );
        }

        List<String> craftCommands = section.getStringList("on-craft.commands");
        if (craftCommands.isEmpty()) {
            craftCommands = section.getStringList("craft-commands");
        }

        return new CustomRecipe(
                recipeId,
                displayName,
                enabled,
                type,
                result,
                ingredients,
                permission,
                description,
                disabledWorlds,
                matchMode,
                cost,
                craftCommands
        );
    }

    private void createDefaultRecipe() {
        Map<Integer, ItemStack> ingredients = new LinkedHashMap<>();
        ingredients.put(0, new ItemStack(Material.STICK, 1));
        ingredients.put(1, new ItemStack(Material.STICK, 1));
        ingredients.put(2, new ItemStack(Material.STICK, 1));
        ingredients.put(4, new ItemStack(Material.DIAMOND, 1));
        ingredients.put(7, new ItemStack(Material.STICK, 1));

        CustomRecipe recipe = new CustomRecipe(
                "starter_wand",
                "Starter Wand",
                true,
                RecipeType.SHAPED,
                new ItemStack(Material.BLAZE_ROD, 1),
                ingredients,
                "focrafts.recipe.starter_wand",
                "A starter recipe shipped with FoCrafts.",
                List.of(),
                RecipeMatchMode.MATERIAL_META,
                new RecipeCost(),
                List.of()
        );
        recipes.put(recipe.getId(), recipe);
    }

    private String formatName(String id) {
        String[] parts = id.split("[_\\-]");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase(Locale.ROOT))
                    .append(' ');
        }
        return builder.toString().trim();
    }

    private ItemStack normalize(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        ItemStack clone = FoItemStacks.cloneItem(item);
        clone.setAmount(Math.max(1, clone.getAmount()));
        return clone;
    }

    private void serializeRecipeIntoSection(ConfigurationSection section, CustomRecipe recipe) {
        section.set("display-name", recipe.getDisplayName());
        section.set("enabled", recipe.isEnabled());
        section.set("type", recipe.getType().name());
        section.set("result", recipe.getResult());
        section.set("permission", recipe.getPermission());
        section.set("description", recipe.getDescription());
        section.set("disabled-worlds", recipe.getDisabledWorlds());
        section.set("match-mode", recipe.getMatchMode().name());

        RecipeCost cost = recipe.getCost();
        ConfigurationSection costSection = section.createSection("cost");
        costSection.set("xp-levels", cost.getXpLevels());
        costSection.set("xp-points", cost.getXpPoints());
        costSection.set("vault-money", cost.getVaultMoney());

        ConfigurationSection onCraft = section.createSection("on-craft");
        onCraft.set("mode", "PER_CRAFT");
        onCraft.set("commands", recipe.getCraftCommands());

        ConfigurationSection ingredients = section.createSection("ingredients");
        for (Map.Entry<Integer, ItemStack> entry : recipe.getIngredients().entrySet()) {
            ingredients.set(String.valueOf(entry.getKey()), normalize(entry.getValue()));
        }
    }

}
