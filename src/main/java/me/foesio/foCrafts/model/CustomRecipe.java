package me.foesio.foCrafts.model;

import me.foesio.core.item.FoItemStacks;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomRecipe {
    private String id;
    private String displayName;
    private boolean enabled;
    private RecipeType type;
    private ItemStack result;
    private Map<Integer, ItemStack> ingredients;
    private String permission;
    private String description;
    private List<String> disabledWorlds;
    private RecipeMatchMode matchMode;
    private RecipeCost cost;
    private List<String> craftCommands;

    public CustomRecipe(String id, String displayName, boolean enabled, RecipeType type, ItemStack result, Map<Integer, ItemStack> ingredients) {
        this(
                id,
                displayName,
                enabled,
                type,
                result,
                ingredients,
                "",
                "",
                List.of(),
                RecipeMatchMode.MATERIAL_META,
                new RecipeCost(),
                List.of()
        );
    }

    public CustomRecipe(
            String id,
            String displayName,
            boolean enabled,
            RecipeType type,
            ItemStack result,
            Map<Integer, ItemStack> ingredients,
            String permission,
            String description,
            List<String> disabledWorlds,
            RecipeMatchMode matchMode,
            RecipeCost cost,
            List<String> craftCommands
    ) {
        this.id = id;
        this.displayName = displayName;
        this.enabled = enabled;
        this.type = type;
        this.result = result == null ? null : FoItemStacks.cloneItem(result);
        this.ingredients = cloneIngredients(ingredients);
        this.permission = permission == null ? "" : permission.trim();
        this.description = description == null ? "" : description;
        this.disabledWorlds = cloneStringList(disabledWorlds);
        this.matchMode = matchMode == null ? RecipeMatchMode.MATERIAL_META : matchMode;
        this.cost = cost == null ? new RecipeCost() : cost.copy();
        this.craftCommands = cloneStringList(craftCommands);
    }

    public CustomRecipe copy() {
        return new CustomRecipe(
                id,
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RecipeType getType() {
        return type;
    }

    public void setType(RecipeType type) {
        this.type = type;
    }

    public ItemStack getResult() {
        return result == null ? null : FoItemStacks.cloneItem(result);
    }

    public void setResult(ItemStack result) {
        this.result = result == null ? null : FoItemStacks.cloneItem(result);
    }

    public Map<Integer, ItemStack> getIngredients() {
        return cloneIngredients(ingredients);
    }

    public void setIngredients(Map<Integer, ItemStack> ingredients) {
        this.ingredients = cloneIngredients(ingredients);
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission == null ? "" : permission.trim();
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public List<String> getDisabledWorlds() {
        return cloneStringList(disabledWorlds);
    }

    public void setDisabledWorlds(List<String> disabledWorlds) {
        this.disabledWorlds = cloneStringList(disabledWorlds);
    }

    public RecipeMatchMode getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(RecipeMatchMode matchMode) {
        this.matchMode = matchMode == null ? RecipeMatchMode.MATERIAL_META : matchMode;
    }

    public RecipeCost getCost() {
        return cost == null ? new RecipeCost() : cost.copy();
    }

    public void setCost(RecipeCost cost) {
        this.cost = cost == null ? new RecipeCost() : cost.copy();
    }

    public List<String> getCraftCommands() {
        return cloneStringList(craftCommands);
    }

    public void setCraftCommands(List<String> craftCommands) {
        this.craftCommands = cloneStringList(craftCommands);
    }

    private Map<Integer, ItemStack> cloneIngredients(Map<Integer, ItemStack> input) {
        Map<Integer, ItemStack> clone = new LinkedHashMap<>();
        if (input == null) {
            return clone;
        }
        for (Map.Entry<Integer, ItemStack> entry : input.entrySet()) {
            if (entry.getValue() != null) {
                clone.put(entry.getKey(), FoItemStacks.cloneItem(entry.getValue()));
            }
        }
        return clone;
    }

    private List<String> cloneStringList(List<String> values) {
        List<String> output = new ArrayList<>();
        if (values == null) {
            return output;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                output.add(value);
            }
        }
        return output;
    }
}
