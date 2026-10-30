package me.foesio.foCrafts.recipe;

import me.foesio.foCrafts.FoCrafts;
import me.foesio.foCrafts.api.RecipeAvailability;
import me.foesio.foCrafts.api.RecipeCatalog;
import me.foesio.foCrafts.api.RecipeData;
import me.foesio.foCrafts.api.RecipeWriteResult;
import me.foesio.foCrafts.api.RecipeWriter;
import me.foesio.foCrafts.model.CustomRecipe;
import me.foesio.foCrafts.model.RecipeCost;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Adapter between public API values and FoCrafts' recipe persistence path. */
public final class RecipeApiService implements RecipeCatalog, RecipeWriter {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private final FoCrafts plugin;
    private final RecipeManager manager;

    public RecipeApiService(FoCrafts plugin, RecipeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public List<RecipeData> recipes() {
        return plugin.isEnabled() ? manager.getAllRecipes().stream().map(RecipeApiService::snapshot).toList() : List.of();
    }

    @Override
    public Optional<RecipeData> recipe(String id) {
        return plugin.isEnabled() && id != null ? manager.getRecipe(id).map(RecipeApiService::snapshot) : Optional.empty();
    }

    @Override
    public RecipeAvailability availability(Player player, String recipeId) {
        Objects.requireNonNull(player, "player");
        if (!plugin.isEnabled()) return RecipeAvailability.UNAVAILABLE;
        CustomRecipe recipe = manager.getRecipe(recipeId).orElse(null);
        if (recipe == null) return RecipeAvailability.NOT_FOUND;
        if (!recipe.isEnabled()) return RecipeAvailability.DISABLED;
        if (recipe.getResult() == null) return RecipeAvailability.NO_RESULT;
        String permission = recipe.getPermission();
        if (!permission.isBlank() && !player.hasPermission(permission)
                && !player.hasPermission("focrafts.recipe.*")) return RecipeAvailability.NO_PERMISSION;
        String world = player.getWorld().getName();
        if (recipe.getDisabledWorlds().stream().anyMatch(name -> name.equalsIgnoreCase(world))) {
            return RecipeAvailability.WORLD_BLOCKED;
        }
        return RecipeAvailability.AVAILABLE;
    }

    @Override
    public RecipeWriteResult upsert(RecipeData data) {
        if (!plugin.isEnabled()) return result(RecipeWriteResult.Status.UNAVAILABLE, null, "FoCrafts is disabled");
        String problem = validate(data);
        if (problem != null) return result(RecipeWriteResult.Status.INVALID, data == null ? null : data.id(), problem);
        RecipeManager.Mutation mutation = manager.writeUpsert(model(data));
        return fromMutation(mutation, data.id());
    }

    @Override
    public RecipeWriteResult delete(String recipeId) {
        if (!plugin.isEnabled()) return result(RecipeWriteResult.Status.UNAVAILABLE, recipeId, "FoCrafts is disabled");
        if (recipeId == null || recipeId.isBlank()) return result(RecipeWriteResult.Status.INVALID, recipeId, "Recipe ID is required");
        return fromMutation(manager.writeDelete(recipeId), recipeId);
    }

    @Override
    public RecipeWriteResult rename(String recipeId, String displayName) {
        if (!plugin.isEnabled()) return result(RecipeWriteResult.Status.UNAVAILABLE, recipeId, "FoCrafts is disabled");
        if (recipeId == null || recipeId.isBlank() || displayName == null || displayName.isBlank()) {
            return result(RecipeWriteResult.Status.INVALID, recipeId, "Recipe ID and display name are required");
        }
        if (displayName.length() > 128) return result(RecipeWriteResult.Status.INVALID, recipeId, "Display name is too long");
        RecipeManager.RenameResult renamed = manager.writeRename(recipeId, displayName.trim());
        if (renamed.mutation() == RecipeManager.Mutation.INVALID) {
            return result(RecipeWriteResult.Status.INVALID, recipeId, "Generated recipe ID is too long");
        }
        return fromMutation(renamed.mutation(), renamed.recipeId());
    }

    private static RecipeWriteResult fromMutation(RecipeManager.Mutation mutation, String id) {
        return result(RecipeWriteResult.Status.valueOf(mutation.name()), id,
                mutation == RecipeManager.Mutation.SAVE_FAILED ? "Could not save recipes.yml" : "");
    }

    private static RecipeWriteResult result(RecipeWriteResult.Status status, String id, String message) {
        return new RecipeWriteResult(status, id, message);
    }

    private static String validate(RecipeData data) {
        if (data == null) return "Recipe data is required";
        if (data.id() == null || !ID.matcher(data.id()).matches()) return "ID must be 1-64 letters, numbers, _ or -";
        if (data.displayName() == null || data.displayName().isBlank() || data.displayName().length() > 128) {
            return "Display name must be 1-128 characters";
        }
        if (data.type() == null || data.matchMode() == null) return "Recipe type and match mode are required";
        if (data.enabled() && (data.result() == null || data.result().getType() == Material.AIR)) {
            return "An enabled recipe needs a result";
        }
        if (data.enabled() && data.ingredients().isEmpty()) return "An enabled recipe needs ingredients";
        if (!validItem(data.result())) return "Result item is invalid";
        for (Map.Entry<Integer, ItemStack> ingredient : data.ingredients().entrySet()) {
            if (ingredient.getKey() == null || ingredient.getKey() < 0 || ingredient.getKey() > 8
                    || !validItem(ingredient.getValue()) || ingredient.getValue() == null) {
                return "Ingredients require slots 0-8 and valid items";
            }
        }
        if (data.xpLevels() < 0 || data.xpPoints() < 0 || !Double.isFinite(data.vaultMoney()) || data.vaultMoney() < 0) {
            return "Costs must be nonnegative and finite";
        }
        if (data.disabledWorlds().stream().anyMatch(name -> name == null || name.isBlank())) return "World names cannot be blank";
        if (data.craftCommands().stream().anyMatch(command -> command == null || command.isBlank())) return "Commands cannot be blank";
        return null;
    }

    private static boolean validItem(ItemStack item) {
        return item == null || item.getType() != Material.AIR && item.getAmount() > 0
                && item.getAmount() <= item.getMaxStackSize();
    }

    private static RecipeData snapshot(CustomRecipe recipe) {
        RecipeCost cost = recipe.getCost();
        return new RecipeData(recipe.getId(), recipe.getDisplayName(), recipe.isEnabled(), recipe.getType(),
                recipe.getResult(), recipe.getIngredients(), recipe.getPermission(), recipe.getDescription(),
                recipe.getDisabledWorlds(), recipe.getMatchMode(), cost.getXpLevels(), cost.getXpPoints(),
                cost.getVaultMoney(), recipe.getCraftCommands());
    }

    private static CustomRecipe model(RecipeData data) {
        return new CustomRecipe(data.id(), data.displayName().trim(), data.enabled(), data.type(),
                data.result(), data.ingredients(), data.permission(), data.description(), data.disabledWorlds(),
                data.matchMode(), new RecipeCost(data.xpLevels(), data.xpPoints(), data.vaultMoney()),
                data.craftCommands());
    }
}
