package me.foesio.foCrafts.config;

import me.foesio.core.material.MaterialTypes;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GuiConfig {
    public static final List<Integer> DEFAULT_CRAFT_GRID_SLOTS = List.of(
            10, 11, 12,
            19, 20, 21,
            28, 29, 30
    );
    public static final List<Integer> DEFAULT_RECIPE_LIST_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );

    private static final Set<String> CUSTOM_CRAFTING_REMOVED_PATHS = Set.of(
            "items.clear-grid",
            "items.info",
            "items.craft-once",
            "items.craft-max"
    );
    private static final List<String> OLD_CUSTOM_CRAFTING_RESULT_LORE = List.of(
            "{white}Recipe: {theme}{recipe_name}",
            "{white}Max from grid: {theme}{max_craftable}",
            "{white}Costs: {theme}{cost}",
            "{white}Permission: {theme}{permission}",
            "{white}Click result or Craft buttons."
    );
    private static final List<String> OLD_BROWSE_RECIPES_LORE = List.of(
            "{white}Open the recipe browser."
    );
    private static final Set<String> CUSTOM_RECIPES_BUTTON_TEMPLATE_PATHS = sharedButtonTemplatePaths(
            "items.search",
            "items.clear-search",
            "items.previous-page",
            "items.next-page",
            "items.back-to-craft"
    );
    private static final Set<String> RECIPE_PREVIEW_BUTTON_TEMPLATE_PATHS = sharedButtonTemplatePaths(
            "items.back"
    );

    private final JavaPlugin plugin;
    private CustomCraftingGui customCrafting;
    private CustomRecipesGui customRecipes;
    private RecipePreviewGui recipePreview;

    public GuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        customCrafting = loadCustomCrafting();
        customRecipes = loadCustomRecipes();
        recipePreview = loadRecipePreview();
    }

    public CustomCraftingGui customCrafting() {
        if (customCrafting == null) {
            load();
        }
        return customCrafting;
    }

    public CustomRecipesGui customRecipes() {
        if (customRecipes == null) {
            load();
        }
        return customRecipes;
    }

    public RecipePreviewGui recipePreview() {
        if (recipePreview == null) {
            load();
        }
        return recipePreview;
    }

    private CustomCraftingGui loadCustomCrafting() {
        YamlConfiguration cfg = YamlFileUpdater.update(
                plugin,
                "guis/custom-crafting.yml",
                CUSTOM_CRAFTING_REMOVED_PATHS,
                Map.of(
                        "result-item.lore", OLD_CUSTOM_CRAFTING_RESULT_LORE,
                        "items.browse-recipes.name", ":book: {theme}&lBROWSE RECIPES",
                        "items.browse-recipes.lore", OLD_BROWSE_RECIPES_LORE
                )
        );
        int rows = readRows(cfg, "rows", 6);
        int size = rows * 9;
        return new CustomCraftingGui(
                cfg.getString("title", "Custom Crafting"),
                rows,
                readExactSlots(cfg, "grid-slots", size, DEFAULT_CRAFT_GRID_SLOTS, 9),
                readSlot(cfg, "result-slot", size, 24),
                readItem(cfg, "filler", size, new GuiItem(-1, Material.GRAY_STAINED_GLASS_PANE, 1, " ", List.of(), null, false, List.of())),
                readItem(cfg, "items.browse-recipes", size, new GuiItem(49, Material.BOOK, 1, "{theme}&lBROWSE RECIPES", List.of(
                        "&8ʙᴜᴛᴛᴏɴ",
                        " ",
                        "&eⓘ Information ↓",
                        "&7&l | &fOpen the recipe browser.",
                        " ",
                        "{good}→ Click to Browse Recipes ←"
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))),
                readItem(cfg, "result-items.no-match", size, new GuiItem(-1, Material.GRAY_DYE, 1, "{muted}No matching recipe", List.of(
                        "{white}Place items in the grid to craft."
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))),
                readItem(cfg, "result-items.locked", size, new GuiItem(-1, Material.BARRIER, 1, "{bad}Locked recipe", List.of(
                        "{white}Missing permission:",
                        "{theme}{permission}"
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))),
                readItem(cfg, "result-items.world-blocked", size, new GuiItem(-1, Material.BARRIER, 1, "{bad}World restricted", List.of(
                        "{white}Cannot craft this recipe in",
                        "{theme}{world}"
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))),
                readStringList(cfg, "result-item.lore", List.of(
                        "{white}Costs: {theme}{cost}",
                        "{white}Click to Craft",
                        "{white}Shift Click to Craft Multiple"
                ))
        );
    }

    private CustomRecipesGui loadCustomRecipes() {
        YamlConfiguration cfg = YamlFileUpdater.update(plugin, "guis/custom-recipes.yml", CUSTOM_RECIPES_BUTTON_TEMPLATE_PATHS,
                Map.of("items.sort.name", ":clock: {theme}&lSORT"));
        int rows = readRows(cfg, "rows", 6);
        int size = rows * 9;
        return new CustomRecipesGui(
                cfg.getString("title", "Custom Recipes"),
                rows,
                readSlots(cfg, "content-slots", size, DEFAULT_RECIPE_LIST_SLOTS),
                readItem(cfg, "filler", size, new GuiItem(-1, Material.GRAY_STAINED_GLASS_PANE, 1, " ", List.of(), null, false, List.of())),
                readItem(cfg, "empty-content", size, new GuiItem(-1, Material.LIGHT_GRAY_STAINED_GLASS_PANE, 1, " ", List.of(), null, false, List.of())),
                readButtonSlot(cfg, "items.previous-page", size, 45),
                readButtonSlot(cfg, "items.next-page", size, 53),
                readButtonSlot(cfg, "items.search", size, 51),
                readButtonSlot(cfg, "items.clear-search", size, 52),
                readButtonSlot(cfg, "items.back-to-craft", size, 49),
                readItem(cfg, "items.sort", size, new GuiItem(48, Material.CLOCK, 1, "{theme}&lSORT", List.of(
                        "{sort_options}"
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))),
                cfg.getString("items.sort.selected-line", "{theme}» {sort}"),
                cfg.getString("items.sort.unselected-line", "{white}• {sort}"),
                readItem(cfg, "recipe-item", size, new GuiItem(-1, Material.BARRIER, 1, "{theme}{recipe_name}", List.of(
                        "{description}",
                        "{locked}",
                        "{white}Click to view this recipe."
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))),
                cfg.getString("recipe-item.description-line", "{white}Desc: {theme}{description}"),
                cfg.getString("recipe-item.locked-line", "{bad}Locked: missing permission"),
                readItem(cfg, "invalid-recipe-item", size, new GuiItem(-1, Material.BARRIER, 1, "{bad}Invalid result", List.of(
                        "{white}This recipe has no valid result item."
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)))
        );
    }

    private RecipePreviewGui loadRecipePreview() {
        YamlConfiguration cfg = YamlFileUpdater.update(plugin, "guis/recipe-preview.yml", RECIPE_PREVIEW_BUTTON_TEMPLATE_PATHS, Map.of());
        int rows = readRows(cfg, "rows", 6);
        int size = rows * 9;
        return new RecipePreviewGui(
                cfg.getString("title", "Recipe Preview"),
                rows,
                readExactSlots(cfg, "grid-slots", size, DEFAULT_CRAFT_GRID_SLOTS, 9),
                readSlot(cfg, "result-slot", size, 24),
                readItem(cfg, "filler", size, new GuiItem(-1, Material.GRAY_STAINED_GLASS_PANE, 1, " ", List.of(), null, false, List.of())),
                readItem(cfg, "items.details", size, new GuiItem(47, Material.BOOK, 1, "{theme}{recipe_name}", List.of(
                        "{description}",
                        "{white}Costs: {theme}{cost}",
                        "{white}Status: {status}"
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))),
                readButtonSlot(cfg, "items.back", size, 49),
                readItem(cfg, "invalid-result-item", size, new GuiItem(-1, Material.BARRIER, 1, "{bad}Invalid result", List.of(
                        "{white}This recipe has no valid result item."
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))),
                readStringList(cfg, "result-item.lore", List.of(
                        "{white}This is the output item."
                )),
                cfg.getString("description-line", "{white}Description: {theme}{description}"),
                cfg.getString("status.unlocked", "{good}Unlocked"),
                cfg.getString("status.locked", "{bad}Locked"),
                cfg.getString("status.world-blocked", "{bad}World restricted")
        );
    }

    private static Set<String> sharedButtonTemplatePaths(String... roots) {
        Set<String> paths = new LinkedHashSet<>();
        for (String root : roots) {
            paths.add(root + ".material");
            paths.add(root + ".amount");
            paths.add(root + ".name");
            paths.add(root + ".lore");
            paths.add(root + ".custom-model-data");
            paths.add(root + ".glow");
            paths.add(root + ".flags");
        }
        return Set.copyOf(paths);
    }

    private int readRows(YamlConfiguration cfg, String path, int fallback) {
        int rows = cfg.getInt(path, fallback);
        if (rows < 1 || rows > 6) {
            warn("Invalid GUI rows at " + path + ": " + rows + ". Using " + fallback + ".");
            return fallback;
        }
        return rows;
    }

    private int readSlot(YamlConfiguration cfg, String path, int inventorySize, int fallback) {
        int slot = cfg.getInt(path, fallback);
        if (slot < 0 || slot >= inventorySize) {
            warn("Invalid GUI slot at " + path + ": " + slot + ". Using " + fallback + ".");
            return fallback >= 0 && fallback < inventorySize ? fallback : -1;
        }
        return slot;
    }

    private List<Integer> readExactSlots(YamlConfiguration cfg, String path, int inventorySize, List<Integer> fallback, int requiredCount) {
        List<Integer> slots = readSlots(cfg, path, inventorySize, fallback);
        if (slots.size() != requiredCount) {
            warn("Invalid GUI slot count at " + path + ": " + slots.size() + ". Using " + requiredCount + " fallback slots.");
            return validFallbackSlots(fallback, inventorySize);
        }
        return slots;
    }

    private List<Integer> readSlots(YamlConfiguration cfg, String path, int inventorySize, List<Integer> fallback) {
        List<Integer> slots = new ArrayList<>();
        for (int slot : cfg.getIntegerList(path)) {
            if (slot < 0 || slot >= inventorySize) {
                warn("Invalid GUI slot at " + path + ": " + slot + ". Slot ignored.");
                continue;
            }
            if (!slots.contains(slot)) {
                slots.add(slot);
            }
        }
        if (slots.isEmpty()) {
            return validFallbackSlots(fallback, inventorySize);
        }
        return List.copyOf(slots);
    }

    private List<Integer> validFallbackSlots(List<Integer> fallback, int inventorySize) {
        return fallback.stream()
                .filter(slot -> slot >= 0 && slot < inventorySize)
                .distinct()
                .toList();
    }

    private GuiItem readItem(YamlConfiguration cfg, String path, int inventorySize, GuiItem fallback) {
        ConfigurationSection section = cfg.getConfigurationSection(path);
        if (section == null) {
            return fallback;
        }

        int slot = section.getInt("slot", fallback.slot());
        if (slot < -1 || slot >= inventorySize) {
            warn("Invalid GUI slot at " + path + ".slot: " + slot + ". Using " + fallback.slot() + ".");
            slot = fallback.slot() >= -1 && fallback.slot() < inventorySize ? fallback.slot() : -1;
        }

        Material material = readMaterial(path, section.getString("material"), fallback.material());
        int amount = section.getInt("amount", fallback.amount());
        if (amount < 1 || amount > 64) {
            warn("Invalid GUI amount at " + path + ".amount: " + amount + ". Using " + fallback.amount() + ".");
            amount = fallback.amount();
        }

        Integer customModelData = fallback.customModelData();
        if (section.isInt("custom-model-data")) {
            customModelData = section.getInt("custom-model-data");
        }

        return new GuiItem(
                slot,
                material,
                amount,
                section.getString("name", fallback.name()),
                section.isList("lore") ? section.getStringList("lore") : fallback.lore(),
                customModelData,
                section.getBoolean("glow", fallback.glow()),
                readFlags(path, section.getStringList("flags"), fallback.flags())
        );
    }

    private GuiButtonSlot readButtonSlot(YamlConfiguration cfg, String path, int inventorySize, int fallbackSlot) {
        ConfigurationSection section = cfg.getConfigurationSection(path);
        int slot = section == null ? fallbackSlot : section.getInt("slot", fallbackSlot);
        if (slot < 0 || slot >= inventorySize) {
            warn("Invalid GUI slot at " + path + ".slot: " + slot + ". Using " + fallbackSlot + ".");
            slot = fallbackSlot >= 0 && fallbackSlot < inventorySize ? fallbackSlot : -1;
        }
        return new GuiButtonSlot(slot);
    }

    private Material readMaterial(String path, String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = MaterialTypes.match(raw);
        if (material == null || !material.isItem()) {
            warn("Invalid GUI material at " + path + ".material: " + raw + ". Using " + fallback.name() + ".");
            return fallback;
        }
        return material;
    }

    private List<String> readStringList(YamlConfiguration cfg, String path, List<String> fallback) {
        return cfg.isList(path) ? cfg.getStringList(path) : fallback;
    }

    private List<ItemFlag> readFlags(String path, List<String> rawFlags, List<ItemFlag> fallback) {
        if (rawFlags.isEmpty()) {
            return fallback;
        }
        List<ItemFlag> flags = new ArrayList<>();
        for (String raw : rawFlags) {
            try {
                flags.add(ItemFlag.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                warn("Invalid GUI item flag at " + path + ".flags: " + raw + ". Flag ignored.");
            }
        }
        return List.copyOf(flags);
    }

    private void warn(String message) {
        plugin.getLogger().warning(message);
    }

    public record CustomCraftingGui(
            String title,
            int rows,
            List<Integer> gridSlots,
            int resultSlot,
            GuiItem filler,
            GuiItem browseRecipes,
            GuiItem noMatch,
            GuiItem lockedRecipe,
            GuiItem worldBlocked,
            List<String> resultLore
    ) {
        public int size() {
            return rows * 9;
        }
    }

    public record CustomRecipesGui(
            String title,
            int rows,
            List<Integer> contentSlots,
            GuiItem filler,
            GuiItem emptyContent,
            GuiButtonSlot previousPage,
            GuiButtonSlot nextPage,
            GuiButtonSlot search,
            GuiButtonSlot clearSearch,
            GuiButtonSlot backToCraft,
            GuiItem sort,
            String sortSelectedLine,
            String sortUnselectedLine,
            GuiItem recipeItem,
            String descriptionLine,
            String lockedLine,
            GuiItem invalidRecipeItem
    ) {
        public int size() {
            return rows * 9;
        }
    }

    public record RecipePreviewGui(
            String title,
            int rows,
            List<Integer> gridSlots,
            int resultSlot,
            GuiItem filler,
            GuiItem details,
            GuiButtonSlot back,
            GuiItem invalidResult,
            List<String> resultLore,
            String descriptionLine,
            String unlockedStatus,
            String lockedStatus,
            String worldBlockedStatus
    ) {
        public int size() {
            return rows * 9;
        }
    }

    public record GuiItem(
            int slot,
            Material material,
            int amount,
            String name,
            List<String> lore,
            Integer customModelData,
            boolean glow,
            List<ItemFlag> flags
    ) {
    }

    public record GuiButtonSlot(int slot) {
    }
}
