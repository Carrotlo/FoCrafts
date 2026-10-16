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

    private static final int GUI_SCHEMA_VERSION = 1;
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
    private static final Set<String> CUSTOM_CRAFTING_ADDED_PATHS = union(
            itemTemplatePaths("items.browse-recipes"),
            Set.of(
                    "result-item.unnamed-name",
                    "labels.no-permission",
                    "labels.type-shaped",
                    "labels.type-shapeless",
                    "labels.match-material-only",
                    "labels.match-material-meta",
                    "labels.match-strict-all",
                    "labels.cost-none",
                    "labels.cost-levels",
                    "labels.cost-points",
                    "labels.cost-money",
                    "labels.cost-separator"
            )
    );
    private static final Set<String> CUSTOM_RECIPES_ADDED_PATHS = union(
            itemTemplatePaths("items.search", "items.clear-search", "items.previous-page", "items.next-page", "items.back-to-craft"),
            Set.of(
                    "items.sort.name-ascending",
                    "items.sort.name-descending",
                    "items.search.none-label",
                    "items.clear-search.target-label",
                    "labels.no-permission",
                    "labels.type-shaped",
                    "labels.type-shapeless",
                    "labels.match-material-only",
                    "labels.match-material-meta",
                    "labels.match-strict-all",
                    "labels.cost-none",
                    "labels.cost-levels",
                    "labels.cost-points",
                    "labels.cost-money",
                    "labels.cost-separator"
            )
    );
    private static final Set<String> RECIPE_PREVIEW_ADDED_PATHS = union(
            itemTemplatePaths("items.back"),
            union(CUSTOM_CRAFTING_ADDED_PATHS, Set.of("result-item.unnamed-name"))
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
                GUI_SCHEMA_VERSION,
                CUSTOM_CRAFTING_ADDED_PATHS,
                Map.of(
                        "result-item.lore", OLD_CUSTOM_CRAFTING_RESULT_LORE,
                        "items.browse-recipes.name", ":book: {theme}&lBROWSE RECIPES",
                        "items.browse-recipes.lore", OLD_BROWSE_RECIPES_LORE
                )
        );
        int rows = readRows(cfg, "rows", 6);
        int size = rows * 9;
        List<Integer> gridSlots = readExactSlots(cfg, "grid-slots", size, DEFAULT_CRAFT_GRID_SLOTS, 9);
        Set<Integer> occupied = new LinkedHashSet<>(gridSlots);
        int resultSlot = resolveControlSlot("result-slot", readSlot(cfg, "result-slot", size, 24), 24, size, occupied);
        GuiItem browseRecipes = resolveItemSlot("items.browse-recipes",
                readItem(cfg, "items.browse-recipes", size, new GuiItem(49, Material.BOOK, 1, "{theme}&lBROWSE RECIPES", List.of(
                        "&8ʙᴜᴛᴛᴏɴ",
                        " ",
                        "&eⓘ Information ↓",
                        "&7&l | &fOpen the recipe browser.",
                        " ",
                        "{good}→ Click to Browse Recipes ←"
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))), 49, size, occupied);
        return new CustomCraftingGui(
                readText(cfg, "title", "Custom Crafting"),
                rows,
                gridSlots,
                resultSlot,
                readItem(cfg, "filler", size, new GuiItem(-1, Material.GRAY_STAINED_GLASS_PANE, 1, " ", List.of(), null, false, List.of())),
                browseRecipes,
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
                )),
                readText(cfg, "result-item.unnamed-name", ""),
                readPublicText(cfg)
        );
    }

    private CustomRecipesGui loadCustomRecipes() {
        YamlConfiguration cfg = YamlFileUpdater.update(plugin, "guis/custom-recipes.yml", GUI_SCHEMA_VERSION,
                CUSTOM_RECIPES_ADDED_PATHS, Map.of(
                        "items.sort.name", ":clock: {theme}&lSORT",
                        "items.sort.lore", List.of("{sort_options}")
                ));
        int rows = readRows(cfg, "rows", 6);
        int size = rows * 9;
        List<Integer> contentSlots = readSlots(cfg, "content-slots", size, DEFAULT_RECIPE_LIST_SLOTS);
        Set<Integer> occupied = new LinkedHashSet<>(contentSlots);
        GuiItem previousPage = resolveItemSlot("items.previous-page", readItem(cfg, "items.previous-page", size,
                pageButtonFallback(45, "PREVIOUS PAGE", "view the previous page")), 45, size, occupied);
        GuiItem nextPage = resolveItemSlot("items.next-page", readItem(cfg, "items.next-page", size,
                pageButtonFallback(53, "NEXT PAGE", "view the next page")), 53, size, occupied);
        GuiItem search = resolveItemSlot("items.search", readItem(cfg, "items.search", size,
                standardButtonFallback(51, Material.NAME_TAG, "{theme}&lSEARCH", List.of("&7&l | &fCurrent: {current}"), "set a filter")), 51, size, occupied);
        GuiItem clearSearch = resolveItemSlot("items.clear-search", readItem(cfg, "items.clear-search", size,
                standardButtonFallback(52, Material.MILK_BUCKET, "{theme}&lCLEAR SEARCH", List.of("&7&l | &fShow all {target}."), "clear the search")), 52, size, occupied);
        GuiItem backToCraft = resolveItemSlot("items.back-to-craft", readItem(cfg, "items.back-to-craft", size,
                standardButtonFallback(49, Material.IRON_DOOR, "&e&lBACK", List.of("&7&l | &fReturn to the crafting menu."), "go back")), 49, size, occupied);
        GuiItem sort = resolveItemSlot("items.sort", readItem(cfg, "items.sort", size, new GuiItem(48, Material.CLOCK, 1, "{theme}&lSORT", List.of(
                "&8ʙᴜᴛᴛᴏɴ",
                " ",
                "&eⓘ Information ↓",
                "{sort_options}",
                " ",
                "{good}→ Click to Cycle Sort ←"
        ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))), 48, size, occupied);
        return new CustomRecipesGui(
                readText(cfg, "title", "Custom Recipes"),
                rows,
                contentSlots,
                readItem(cfg, "filler", size, new GuiItem(-1, Material.GRAY_STAINED_GLASS_PANE, 1, " ", List.of(), null, false, List.of())),
                readItem(cfg, "empty-content", size, new GuiItem(-1, Material.LIGHT_GRAY_STAINED_GLASS_PANE, 1, " ", List.of(), null, false, List.of())),
                previousPage,
                nextPage,
                search,
                clearSearch,
                backToCraft,
                sort,
                readText(cfg, "items.sort.selected-line", "{theme}» {sort}"),
                readText(cfg, "items.sort.unselected-line", "{white}• {sort}"),
                readText(cfg, "items.sort.name-ascending", "Name A-Z"),
                readText(cfg, "items.sort.name-descending", "Name Z-A"),
                readText(cfg, "items.search.none-label", "{muted}None"),
                readText(cfg, "items.clear-search.target-label", "recipes"),
                readItem(cfg, "recipe-item", size, new GuiItem(-1, Material.BARRIER, 1, "{theme}{recipe_name}", List.of(
                        "{description}",
                        "{locked}",
                        "{white}Click to view this recipe."
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))),
                readText(cfg, "recipe-item.description-line", "{white}Desc: {theme}{description}"),
                readText(cfg, "recipe-item.locked-line", "{bad}Locked: missing permission"),
                readItem(cfg, "invalid-recipe-item", size, new GuiItem(-1, Material.BARRIER, 1, "{bad}Invalid result", List.of(
                        "{white}This recipe has no valid result item."
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))),
                readPublicText(cfg)
        );
    }

    private RecipePreviewGui loadRecipePreview() {
        YamlConfiguration cfg = YamlFileUpdater.update(plugin, "guis/recipe-preview.yml", GUI_SCHEMA_VERSION,
                RECIPE_PREVIEW_ADDED_PATHS, Map.of());
        int rows = readRows(cfg, "rows", 6);
        int size = rows * 9;
        List<Integer> gridSlots = readExactSlots(cfg, "grid-slots", size, DEFAULT_CRAFT_GRID_SLOTS, 9);
        Set<Integer> occupied = new LinkedHashSet<>(gridSlots);
        int resultSlot = resolveControlSlot("result-slot", readSlot(cfg, "result-slot", size, 24), 24, size, occupied);
        GuiItem details = resolveItemSlot("items.details", readItem(cfg, "items.details", size, new GuiItem(47, Material.BOOK, 1, "{theme}{recipe_name}", List.of(
                "{description}",
                "{white}Costs: {theme}{cost}",
                "{white}Status: {status}"
        ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))), 47, size, occupied);
        GuiItem back = resolveItemSlot("items.back", readItem(cfg, "items.back", size,
                standardButtonFallback(49, Material.IRON_DOOR, "&e&lBACK", List.of("&7&l | &fReturn to the recipe browser."), "go back")), 49, size, occupied);
        return new RecipePreviewGui(
                readText(cfg, "title", "Recipe Preview"),
                rows,
                gridSlots,
                resultSlot,
                readItem(cfg, "filler", size, new GuiItem(-1, Material.GRAY_STAINED_GLASS_PANE, 1, " ", List.of(), null, false, List.of())),
                details,
                back,
                readItem(cfg, "invalid-result-item", size, new GuiItem(-1, Material.BARRIER, 1, "{bad}Invalid result", List.of(
                        "{white}This recipe has no valid result item."
                ), null, false, List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP))),
                readStringList(cfg, "result-item.lore", List.of(
                        "{white}This is the output item."
                )),
                readText(cfg, "result-item.unnamed-name", ""),
                readText(cfg, "description-line", "{white}Description: {theme}{description}"),
                readText(cfg, "status.unlocked", "{good}Unlocked"),
                readText(cfg, "status.locked", "{bad}Locked"),
                readText(cfg, "status.world-blocked", "{bad}World restricted"),
                readPublicText(cfg)
        );
    }

    private static Set<String> itemTemplatePaths(String... roots) {
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

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> paths = new LinkedHashSet<>(first);
        paths.addAll(second);
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
            return isCurrentSchema(cfg)
                    ? new GuiItem(fallback.slot(), fallback.material(), fallback.amount(), "", List.of(),
                    fallback.customModelData(), fallback.glow(), fallback.flags())
                    : fallback;
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
                section.isString("name") ? section.getString("name", "") : isCurrentSchema(cfg) ? "" : fallback.name(),
                section.isList("lore") ? section.getStringList("lore") : isCurrentSchema(cfg) ? List.of() : fallback.lore(),
                customModelData,
                section.getBoolean("glow", fallback.glow()),
                readFlags(path, section, fallback.flags())
        );
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
        return cfg.isList(path) ? cfg.getStringList(path) : isCurrentSchema(cfg) ? List.of() : fallback;
    }

    private String readText(YamlConfiguration cfg, String path, String fallback) {
        return cfg.isString(path) ? cfg.getString(path, "") : isCurrentSchema(cfg) ? "" : fallback;
    }

    private boolean isCurrentSchema(YamlConfiguration cfg) {
        return cfg.getInt("schema-version", 0) >= GUI_SCHEMA_VERSION;
    }

    private List<ItemFlag> readFlags(String path, ConfigurationSection section, List<ItemFlag> fallback) {
        if (!section.isList("flags")) {
            return fallback;
        }
        List<String> rawFlags = section.getStringList("flags");
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

    private PublicText readPublicText(YamlConfiguration cfg) {
        return new PublicText(
                readText(cfg, "labels.no-permission", "none"),
                readText(cfg, "labels.type-shaped", "Shaped"),
                readText(cfg, "labels.type-shapeless", "Shapeless"),
                readText(cfg, "labels.match-material-only", "Material Only"),
                readText(cfg, "labels.match-material-meta", "Material + Meta"),
                readText(cfg, "labels.match-strict-all", "Strict All"),
                readText(cfg, "labels.cost-none", "none"),
                readText(cfg, "labels.cost-levels", "{amount} levels"),
                readText(cfg, "labels.cost-points", "{amount} xp"),
                readText(cfg, "labels.cost-money", "${amount}"),
                readText(cfg, "labels.cost-separator", ", ")
        );
    }

    private GuiItem pageButtonFallback(int slot, String label, String action) {
        return standardButtonFallback(slot, Material.ARROW, "{theme}&l" + label,
                List.of("&7&l | &fPage {page}/{max_page}"), action);
    }

    private GuiItem standardButtonFallback(int slot, Material material, String name, List<String> information, String action) {
        List<String> lore = new ArrayList<>();
        lore.add("&8ʙᴜᴛᴛᴏɴ");
        lore.add(" ");
        lore.add("&eⓘ Information ↓");
        lore.addAll(information);
        lore.add(" ");
        lore.add("{good}→ Click to " + action + " ←");
        return new GuiItem(slot, material, 1, name, List.copyOf(lore), null, false,
                List.of(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP));
    }

    private int resolveControlSlot(String path, int requested, int fallback, int inventorySize, Set<Integer> occupied) {
        int resolved = resolveAvailableSlot(requested, fallback, inventorySize, occupied);
        if (requested >= 0 && resolved != requested) {
            warn("GUI slot collision at " + path + ": " + requested + ". Using " + resolved + " instead.");
        }
        if (requested >= 0 && resolved < 0) {
            warn("No free GUI slot is available for " + path + ". The control will be disabled.");
        }
        if (resolved >= 0) {
            occupied.add(resolved);
        }
        return resolved;
    }

    private GuiItem resolveItemSlot(String path, GuiItem item, int fallback, int inventorySize, Set<Integer> occupied) {
        int resolved = resolveControlSlot(path + ".slot", item.slot(), fallback, inventorySize, occupied);
        if (resolved == item.slot()) {
            return item;
        }
        return new GuiItem(resolved, item.material(), item.amount(), item.name(), item.lore(),
                item.customModelData(), item.glow(), item.flags());
    }

    static int resolveAvailableSlot(int requested, int preferred, int inventorySize, Set<Integer> occupied) {
        if (requested == -1) {
            return -1;
        }
        if (requested >= 0 && requested < inventorySize && !occupied.contains(requested)) {
            return requested;
        }
        if (preferred >= 0 && preferred < inventorySize && !occupied.contains(preferred)) {
            return preferred;
        }
        for (int slot = 0; slot < inventorySize; slot++) {
            if (!occupied.contains(slot)) {
                return slot;
            }
        }
        return -1;
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
            List<String> resultLore,
            String unnamedResultName,
            PublicText text
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
            GuiItem previousPage,
            GuiItem nextPage,
            GuiItem search,
            GuiItem clearSearch,
            GuiItem backToCraft,
            GuiItem sort,
            String sortSelectedLine,
            String sortUnselectedLine,
            String sortNameAscending,
            String sortNameDescending,
            String searchNoneLabel,
            String clearSearchTargetLabel,
            GuiItem recipeItem,
            String descriptionLine,
            String lockedLine,
            GuiItem invalidRecipeItem,
            PublicText text
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
            GuiItem back,
            GuiItem invalidResult,
            List<String> resultLore,
            String unnamedResultName,
            String descriptionLine,
            String unlockedStatus,
            String lockedStatus,
            String worldBlockedStatus,
            PublicText text
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

    public record PublicText(
            String noPermission,
            String typeShaped,
            String typeShapeless,
            String matchMaterialOnly,
            String matchMaterialMeta,
            String matchStrictAll,
            String costNone,
            String costLevels,
            String costPoints,
            String costMoney,
            String costSeparator
    ) {
    }
}
