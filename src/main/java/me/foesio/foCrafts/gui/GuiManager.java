package me.foesio.foCrafts.gui;

import me.foesio.core.FoCoreContext;
import me.foesio.core.command.CommandPlaceholders;
import me.foesio.core.dialog.ConfirmationDialogRequest;
import me.foesio.core.dialog.ConfiguredTextDialogs;
import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.dialog.FallbackDialogService;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.gui.FoButtonStyle;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.gui.GuiTitles;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.item.FoItemStacks;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.message.FoStyle;
import me.foesio.core.text.FoText;
import me.foesio.core.selector.TriStateSelectionActionType;
import me.foesio.core.selector.TriStateSelectionClick;
import me.foesio.core.selector.TriStateSelectionHolder;
import me.foesio.core.selector.TriStateSelectionMenus;
import me.foesio.core.selector.TriStateSelectionRequest;
import me.foesio.core.selector.TriStateSelectionState;
import me.foesio.core.selector.TriStateSelections;
import me.foesio.core.selector.WorldSelectionEntries;
import me.foesio.foCrafts.config.GuiConfig;
import me.foesio.foCrafts.FoCrafts;
import me.foesio.foCrafts.config.GuiConfig.CustomCraftingGui;
import me.foesio.foCrafts.config.GuiConfig.CustomRecipesGui;
import me.foesio.foCrafts.config.GuiConfig.GuiButtonSlot;
import me.foesio.foCrafts.config.GuiConfig.GuiItem;
import me.foesio.foCrafts.config.GuiConfig.RecipePreviewGui;
import me.foesio.foCrafts.input.FoCraftsTextInputFallback;
import me.foesio.foCrafts.model.CustomRecipe;
import me.foesio.foCrafts.model.RecipeCost;
import me.foesio.foCrafts.model.RecipeMatch;
import me.foesio.foCrafts.model.RecipeMatchMode;
import me.foesio.foCrafts.model.RecipeType;
import me.foesio.foCrafts.recipe.RecipeManager;
import me.foesio.core.economy.VaultEconomyBridge;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class GuiManager implements Listener {
    private static final int[] GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int[] RECIPE_LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final int RESULT_SLOT = 24;

    private static final int PAGE_PREVIOUS_SLOT = GuiSlots.bottomRowStart(6);
    private static final int PAGE_NEXT_SLOT = 53;

    private static final int ADMIN_SEARCH_SLOT = 51;
    private static final int ADMIN_SORT_SLOT = 47;
    private static final int ADMIN_CLEAR_SEARCH_SLOT = 52;
    private static final int ADMIN_CREATE_SLOT = 49;

    private static final int ADMIN_EDITOR_ROWS = 4;
    private static final int ADMIN_EDITOR_SIZE = ADMIN_EDITOR_ROWS * 9;
    private static final int ADMIN_EDITOR_ENABLED_SLOT = 10;
    private static final int ADMIN_EDITOR_RENAME_SLOT = 11;
    private static final int ADMIN_EDITOR_DESCRIPTION_SLOT = 12;
    private static final int ADMIN_EDITOR_ITEMS_SLOT = 13;
    private static final int ADMIN_EDITOR_TYPE_SLOT = 14;
    private static final int ADMIN_EDITOR_MATCH_MODE_SLOT = 15;
    private static final int ADMIN_EDITOR_COMMANDS_SLOT = 16;
    private static final int ADMIN_EDITOR_PERMISSION_SLOT = 19;
    private static final int ADMIN_EDITOR_COST_SLOT = 20;
    private static final int ADMIN_EDITOR_WORLDS_SLOT = 21;
    private static final int ADMIN_EDITOR_DELETE_SLOT = 22;
    private static final int ADMIN_EDITOR_BACK_SLOT = GuiSlots.bottomMiddleSlot(ADMIN_EDITOR_ROWS);
    private static final int ADMIN_RECIPE_ITEMS_BACK_SLOT = GuiSlots.bottomMiddleSlot(6);
    private static final int ADMIN_RECIPE_ITEMS_RESULT_SLOT = RESULT_SLOT;

    private static final int DELETE_CONFIRM_CANCEL_SLOT = 11;
    private static final int DELETE_CONFIRM_CONFIRM_SLOT = 15;

    private static final Set<Integer> ADMIN_RECIPE_ITEMS_EDITABLE_SLOTS = new HashSet<>();

    static {
        for (int gridSlot : GRID_SLOTS) {
            ADMIN_RECIPE_ITEMS_EDITABLE_SLOTS.add(gridSlot);
        }
        ADMIN_RECIPE_ITEMS_EDITABLE_SLOTS.add(ADMIN_RECIPE_ITEMS_RESULT_SLOT);
    }

    private final FoCrafts plugin;
    private final RecipeManager recipeManager;
    private final FoMessageService messages;
    private final GuiConfig guiConfig;
    private final VaultEconomyBridge vaultHook;
    private final Supplier<FoCoreContext> coreSupplier;
    private final ConfiguredTextDialogs textDialogs;
    private final FoCraftsTextInputFallback textFallback;
    private final GuiButtonConfig buttons = GuiButtonConfig.defaults();
    private final DecimalFormat moneyFormat = new DecimalFormat("0.##");

    private final Map<UUID, PlayerRecipeState> playerRecipeStates = new HashMap<>();
    private final Map<UUID, AdminListState> adminListStates = new HashMap<>();
    private final Map<UUID, WorldSelectionSession> worldSelectionSessions = new HashMap<>();
    private DialogService dialogService;

    public GuiManager(FoCrafts plugin, RecipeManager recipeManager, FoMessageService messages, GuiConfig guiConfig, VaultEconomyBridge vaultHook, Supplier<FoCoreContext> coreSupplier) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.messages = messages;
        this.guiConfig = guiConfig;
        this.vaultHook = vaultHook;
        this.coreSupplier = coreSupplier;
        this.textDialogs = ConfiguredTextDialogs.create(plugin)
                .register("search", searchDialogRequest("", "Any search text"));
        this.textDialogs.load();
        this.textFallback = new FoCraftsTextInputFallback(messages, () -> currentCore().nativeDialogs());
        refreshDialogService();
    }

    public void clearRuntimeState() {
        playerRecipeStates.clear();
        adminListStates.clear();
        worldSelectionSessions.clear();
        textFallback.reload();
        textDialogs.reload();
        refreshDialogService();
    }

    private FoCoreContext currentCore() {
        FoCoreContext core = coreSupplier.get();
        if (core == null) {
            throw new IllegalStateException("FoPluginCore context is not initialized.");
        }
        return core;
    }

    private DialogService dialogService() {
        if (dialogService == null) {
            refreshDialogService();
        }
        return dialogService;
    }

    private void refreshDialogService() {
        FoCoreContext core = currentCore();
        FallbackDialogService fallback = new FallbackDialogService(
                core.nativeDialogs(),
                (player, request, onClose) -> run(onClose),
                (player, request, onConfirm, onCancel) -> run(onCancel),
                textFallback
        );
        this.dialogService = core.createDialogService(fallback);
    }

    private static void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    public void openCraftGui(Player player) {
        plugin.getGuiSounds().open(player);
        openCraftGui(player, null, false);
    }

    public void openRecipeListGui(Player player, int requestedPage) {
        plugin.getGuiSounds().open(player);
        openRecipeListGui(player, requestedPage, false);
    }

    private void openRecipeListGui(Player player, int requestedPage, boolean returnToCraft) {
        CustomRecipesGui gui = guiConfig.customRecipes();
        PlayerRecipeState state = getPlayerRecipeState(player);
        List<CustomRecipe> recipes = getFilteredPlayerRecipes(player, state);
        int pageSize = Math.max(1, gui.contentSlots().size());
        int maxPage = Math.max(0, (recipes.size() - 1) / pageSize);
        int page = clampPage(requestedPage, maxPage);

        RecipeListHolder holder = new RecipeListHolder(page, state.search, state.sort, returnToCraft, gui);
        Inventory inventory = Bukkit.createInventory(holder, gui.size(), styleTitle(gui.title()));
        holder.setInventory(inventory);

        fillInventory(inventory, gui.filler());
        fillSlots(inventory, gui.contentSlots(), gui.emptyContent());

        int start = page * pageSize;
        int end = Math.min(start + pageSize, recipes.size());
        for (int i = start; i < end; i++) {
            int slot = gui.contentSlots().get(i - start);
            CustomRecipe recipe = recipes.get(i);
            inventory.setItem(slot, recipeIcon(gui, recipe, !hasRecipePermission(player, recipe)));
        }

        if (page > 0) {
            placeButton(inventory, gui.previousPage(), buttons.previousPage(player, page, maxPage));
        }
        if (page < maxPage) {
            placeButton(inventory, gui.nextPage(), buttons.nextPage(player, page, maxPage));
        }

        placeButton(inventory, gui.search(), buttons.search(player, state.search));
        if (!state.search.isBlank()) {
            placeButton(inventory, gui.clearSearch(), buttons.clearSearch(player, "recipes"));
        }
        if (returnToCraft) {
            placeButton(inventory, gui.backToCraft(), buttons.back(player));
        }
        placeItem(inventory, gui.sort(), Map.of(
                "sort", state.sort.displayName,
                "search", state.search,
                "page", String.valueOf(page + 1),
                "max_page", String.valueOf(maxPage + 1)
        ), Map.of("sort_options", sortOptionLines(gui, state.sort)));

        openForViewer(player, inventory);
    }

    public void openAdminListGui(Player player, int requestedPage) {
        AdminListState state = getAdminListState(player);
        List<CustomRecipe> recipes = getFilteredAdminRecipes(state);
        List<EntryBrowserRequest.Entry> entries = new ArrayList<>();
        entries.addAll(recipes.stream()
                .map(recipe -> EntryBrowserRequest.Entry.of(recipe.getId(), adminRecipeIcon(recipe)))
                .toList());

        EntryBrowserMenus.open(player, EntryBrowserRequest.builder()
                .title("Recipe Editor")
                .entries(entries)
                .page(requestedPage)
                .filter(state.search)
                .buttons(buttons)
                .extraButton(button(player, Material.CLOCK, "{theme}Sort", sortLore(state.sort), "cycle recipe sort"))
                .addButton(button(player, Material.ANVIL, "{good}Create Recipe", List.of(
                        "{white}Create a new custom recipe.",
                        "{white}You will name it first."
                ), "create a recipe"))
                .build());
    }

    private void openRecipePreviewGui(Player player, String recipeId, int returnPage) {
        openRecipePreviewGui(player, recipeId, returnPage, false);
    }

    private void openRecipePreviewGui(Player player, String recipeId, int returnPage, boolean returnToCraft) {
        Optional<CustomRecipe> optional = recipeManager.getRecipe(recipeId);
        if (optional.isEmpty()) {
            messages.send(player, "recipe-missing");
            openRecipeListGui(player, returnPage, returnToCraft);
            return;
        }

        CustomRecipe recipe = optional.get();
        RecipePreviewGui gui = guiConfig.recipePreview();
        RecipePreviewHolder holder = new RecipePreviewHolder(returnPage, returnToCraft, gui);
        Inventory inventory = Bukkit.createInventory(holder, gui.size(), styleTitle(gui.title()));
        holder.setInventory(inventory);

        fillInventory(inventory, gui.filler());
        clearSlots(inventory, gui.gridSlots());
        clearSlot(inventory, gui.resultSlot());
        placeRecipeInGrid(inventory, recipe, gui.gridSlots());
        inventory.setItem(gui.resultSlot(), recipeResultPreview(gui, player, recipe));

        placeItem(inventory, gui.details(), recipePreviewPlaceholders(gui, player, recipe),
                Map.of("description", descriptionExpansion(gui.descriptionLine(), recipe)));
        placeButton(inventory, gui.back(), buttons.back(player));

        openForViewer(player, inventory);
    }

    private void openAdminEditorGui(Player player, String recipeId, int returnPage) {
        Optional<CustomRecipe> optional = recipeManager.getRecipe(recipeId);
        if (optional.isEmpty()) {
            messages.send(player, "recipe-missing");
            openAdminListGui(player, returnPage);
            return;
        }
        CustomRecipe recipe = optional.get();

        AdminEditorHolder holder = new AdminEditorHolder(recipe.getId(), returnPage);
        Inventory inventory = Bukkit.createInventory(holder, ADMIN_EDITOR_SIZE, styleTitle("&8Edit Recipe"));
        holder.setInventory(inventory);

        fillBackground(inventory, false);
        refreshAdminEditorMeta(player, inventory, recipe);

        openForViewer(player, inventory);
    }

    private void openAdminRecipeItemsGui(Player player, String recipeId, int returnPage) {
        Optional<CustomRecipe> optional = recipeManager.getRecipe(recipeId);
        if (optional.isEmpty()) {
            messages.send(player, "recipe-missing");
            openAdminListGui(player, returnPage);
            return;
        }
        CustomRecipe recipe = optional.get();

        AdminRecipeItemsHolder holder = new AdminRecipeItemsHolder(recipe.getId(), returnPage);
        Inventory inventory = Bukkit.createInventory(holder, 54, styleTitle("&8Recipe Items"));
        holder.setInventory(inventory);

        fillBackground(inventory);
        placeRecipeInGrid(inventory, recipe);
        inventory.setItem(ADMIN_RECIPE_ITEMS_RESULT_SLOT, recipe.getResult());
        inventory.setItem(ADMIN_RECIPE_ITEMS_BACK_SLOT, buttons.back(player));

        openForViewer(player, inventory);
    }

    private void openDeleteConfirmGui(Player player, String recipeId, int returnPage, Inventory editorInventory) {
        DeleteConfirmHolder holder = new DeleteConfirmHolder(recipeId, returnPage, editorInventory);
        Inventory inventory = Bukkit.createInventory(holder, 27, styleTitle("&8Confirm Delete"));
        holder.setInventory(inventory);

        fillBackground(inventory, false);
        ItemStack warningPane = named(Material.RED_STAINED_GLASS_PANE, " ", List.of());
        inventory.setItem(10, warningPane.clone());
        inventory.setItem(12, warningPane.clone());
        inventory.setItem(14, warningPane.clone());
        inventory.setItem(16, warningPane.clone());
        inventory.setItem(13, named(Material.LAVA_BUCKET, "{bad}Delete Recipe?", List.of(
                "{white}Recipe: {theme}" + recipeId,
                "",
                "{bad}This permanently deletes the recipe.",
                "{muted}You cannot undo this action."
        )));
        inventory.setItem(DELETE_CONFIRM_CANCEL_SLOT, EditorItemFactory.cancel(player));
        inventory.setItem(DELETE_CONFIRM_CONFIRM_SLOT, button(player, Material.RED_CONCRETE, "{bad}Confirm Delete", List.of(
                "{white}Delete this recipe now.",
                "{bad}This cannot be undone."
        ), "confirm deletion"));

        openForViewer(player, inventory);
    }

    private void openDeleteConfirmation(Player player, String recipeId, int returnPage, Inventory editorInventory) {
        if (currentCore().nativeDialogs().canUseNativeDialogs(player)) {
            ConfirmationDialogRequest request = new ConfirmationDialogRequest(
                    FoStyle.BAD + GuiTitles.smallCaps("Delete Recipe"),
                    List.of(
                            FoStyle.WHITE + "Recipe: " + FoStyle.THEME + recipeId,
                            "",
                            FoStyle.BAD + "This permanently deletes the recipe.",
                            FoStyle.MUTED + "You cannot undo this action."
                    ),
                    DialogButton.icon("lava_bucket", FoStyle.BAD + "Delete Recipe", "Permanently delete this recipe.", 150),
                    DialogButton.cancel("Cancel", "Return to the recipe editor.", 120),
                    340,
                    true,
                    false
            );
            boolean opened = EditorDialogInputs.openConfirmationFromInventory(
                    plugin,
                    currentCore().inventoryCloseSuppressor(),
                    dialogService(),
                    player,
                    request,
                    () -> deleteRecipeAndReturn(player, recipeId, returnPage),
                    () -> openAdminEditorGui(player, recipeId, returnPage)
            );
            if (opened) {
                return;
            }
        }
        openDeleteConfirmGui(player, recipeId, returnPage, editorInventory);
    }

    private void refreshAdminEditorMeta(Player player, Inventory inventory, CustomRecipe recipe) {
        String permission = recipe.getPermission().isBlank() ? "(none)" : recipe.getPermission();
        inventory.setItem(ADMIN_EDITOR_RENAME_SLOT, button(player, Material.NAME_TAG, "{theme}Name", List.of(
                "{white}Current: {theme}" + recipe.getDisplayName(),
                "{white}ID: {theme}" + recipe.getId(),
                "{white}Click to edit in chat."
        ), "edit recipe name"));
        inventory.setItem(ADMIN_EDITOR_PERMISSION_SLOT, button(player, Material.TRIPWIRE_HOOK, "{theme}Permission", List.of(
                "{white}Current: {theme}" + permission,
                "{white}Click to edit in chat."
        ), "edit recipe permission"));
        inventory.setItem(ADMIN_EDITOR_COST_SLOT, button(player, Material.GOLD_INGOT, "{theme}Costs", List.of(
                "{white}Current: {theme}" + formatCost(recipe.getCost()),
                "{white}Click to edit in chat.",
                "{white}Format: levels points money"
        ), "edit recipe costs"));
        List<String> commandLore = new ArrayList<>();
        commandLore.add("{white}Commands: {theme}" + recipe.getCraftCommands().size());
        commandLore.add("{white}Click to edit in chat.");
        commandLore.add("{white}Format: console:cmd | player:cmd");
        commandLore.add("{white}Type {theme}none {white}to clear.");
        commandLore.add("{white}Configured:");
        if (recipe.getCraftCommands().isEmpty()) {
            commandLore.add("{muted}- (none)");
        } else {
            for (String command : cropList(recipe.getCraftCommands(), 6)) {
                commandLore.add("{white}- {theme}" + command);
            }
            if (recipe.getCraftCommands().size() > 6) {
                commandLore.add("{muted}- ...");
            }
        }
        inventory.setItem(ADMIN_EDITOR_COMMANDS_SLOT, button(player, Material.COMMAND_BLOCK, "{theme}On-Craft Commands", commandLore,
                "edit on-craft commands"));

        inventory.setItem(ADMIN_EDITOR_WORLDS_SLOT, EditorItemFactory.worlds(player, 0, recipe.getDisabledWorlds().size(), "Allowed"));
        inventory.setItem(ADMIN_EDITOR_DESCRIPTION_SLOT, button(player, Material.PAPER, "{theme}Description", List.of(
                "{white}Current: {theme}" + (recipe.getDescription().isBlank() ? "(none)" : recipe.getDescription()),
                "{white}Click to edit in chat."
        ), "edit recipe description"));
        inventory.setItem(ADMIN_EDITOR_ITEMS_SLOT, button(player, Material.BARREL, "{theme}Recipe Items", List.of(
                "{white}Edit the 3x3 recipe grid and output item.",
                "{white}Click to open the item editor."
        ), "edit recipe items"));

        refreshAdminEditorButtons(player, inventory, recipe);
    }

    private void openCraftGui(Player player, CustomRecipe insertRecipe, boolean pullFromPlayerInventory) {
        CustomCraftingGui gui = guiConfig.customCrafting();
        CraftHolder holder = new CraftHolder(gui);
        Inventory inventory = Bukkit.createInventory(holder, gui.size(), styleTitle(gui.title()));
        holder.setInventory(inventory);

        fillInventory(inventory, gui.filler());
        clearSlots(inventory, gui.gridSlots());
        clearSlot(inventory, gui.resultSlot());
        placeItem(inventory, gui.browseRecipes(), Map.of());

        if (insertRecipe != null && pullFromPlayerInventory) {
            prefillCraftGridFromInventory(player, inventory, insertRecipe, gui.gridSlots());
        }

        refreshCraftPreview(player, inventory);
        openForViewer(player, inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (holder instanceof CraftHolder craftHolder) {
            handleCraftClick(event, player, top, craftHolder);
            return;
        }
        if (holder instanceof RecipeListHolder recipeListHolder) {
            handleRecipeListClick(event, player, recipeListHolder);
            return;
        }
        if (holder instanceof RecipePreviewHolder previewHolder) {
            handleRecipePreviewClick(event, player, previewHolder);
            return;
        }
        if (holder instanceof EntryBrowserHolder entryBrowserHolder) {
            handleAdminEntryBrowserClick(event, player, entryBrowserHolder);
            return;
        }
        if (holder instanceof AdminListHolder adminListHolder) {
            handleAdminListClick(event, player, adminListHolder);
            return;
        }
        if (holder instanceof DeleteConfirmHolder deleteConfirmHolder) {
            handleDeleteConfirmClick(event, player, deleteConfirmHolder);
            return;
        }
        if (holder instanceof AdminRecipeItemsHolder recipeItemsHolder) {
            handleAdminRecipeItemsClick(event, player, top, recipeItemsHolder);
            return;
        }
        if (holder instanceof AdminEditorHolder adminEditorHolder) {
            handleAdminEditorClick(event, player, top, adminEditorHolder);
            return;
        }
        if (holder instanceof TriStateSelectionHolder selectionHolder) {
            handleWorldSelectionClick(event, player, selectionHolder);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (holder instanceof EntryBrowserHolder) {
            event.setCancelled(true);
            return;
        }

        if (holder instanceof CraftHolder craftHolder) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= top.getSize()) {
                    continue;
                }
                if (!containsSlot(craftHolder.gui.gridSlots(), rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (event.getWhoClicked() instanceof Player player) {
                currentCore().scheduler().runForPlayer(player, () -> refreshCraftPreview(player, top));
            }
            return;
        }

        if (holder instanceof AdminRecipeItemsHolder recipeItemsHolder) {
            boolean touchedEditableTopSlot = false;
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= top.getSize()) {
                    continue;
                }
                if (!ADMIN_RECIPE_ITEMS_EDITABLE_SLOTS.contains(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
                touchedEditableTopSlot = true;
            }
            if (touchedEditableTopSlot && event.getWhoClicked() instanceof Player player) {
                scheduleRecipeItemsAutoSave(player, top, recipeItemsHolder.recipeId);
            }
            return;
        }

        if (holder instanceof RecipeListHolder
                || holder instanceof RecipePreviewHolder
                || holder instanceof AdminListHolder
                || holder instanceof AdminEditorHolder
                || holder instanceof DeleteConfirmHolder
                || holder instanceof TriStateSelectionHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        HumanEntity human = event.getPlayer();
        if (!(human instanceof Player player)) {
            return;
        }

        if (currentCore().inventoryCloseSuppressor().consumeSuppressedClose(player)) {
            return;
        }

        if (holder instanceof CraftHolder craftHolder) {
            returnItems(player, inventory, craftHolder.gui.gridSlots());
            return;
        }
        if (holder instanceof AdminRecipeItemsHolder recipeItemsHolder) {
            autoSaveRecipeItems(inventory, recipeItemsHolder.recipeId);
        }
    }

    @EventHandler
    public void onPromptChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!textFallback.hasPendingChatInput(player)) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage();
        currentCore().scheduler().runForPlayer(player, () -> textFallback.handleChatInput(player, input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerRecipeStates.remove(playerId);
        adminListStates.remove(playerId);
        worldSelectionSessions.remove(playerId);
        currentCore().inventoryCloseSuppressor().clear(event.getPlayer());
        textFallback.clear(event.getPlayer());
    }

    private void handleCraftClick(InventoryClickEvent event, Player player, Inventory top, CraftHolder holder) {
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();
        CustomCraftingGui gui = holder.gui;
        boolean gridSlot = containsSlot(gui.gridSlots(), raw);

        if (inTop && !gridSlot) {
            event.setCancelled(true);
        }

        if (!inTop && event.isShiftClick()) {
            scheduleCraftPreviewUpdate(player, top);
            return;
        }

        if (!inTop) {
            return;
        }

        if (raw == gui.resultSlot()) {
            int amount = event.isShiftClick() ? Integer.MAX_VALUE : 1;
            CraftAttempt attempt = attemptCraft(player, top, amount);
            if (attempt.crafted > 0) {
                plugin.getSounds().play(player, "craft.success");
                messages.send(player, "craft-success", Map.of("amount", String.valueOf(attempt.crafted)));
            } else {
                plugin.getSounds().play(player, "craft.error");
                sendCraftFailure(player, attempt);
            }
            refreshCraftPreview(player, top);
            return;
        }

        if (raw == gui.browseRecipes().slot()) {
            plugin.getGuiSounds().open(player);
            openRecipeListGui(player, 0, true);
            return;
        }

        if (gridSlot) {
            scheduleCraftPreviewUpdate(player, top);
        }
    }

    private void handleRecipeListClick(InventoryClickEvent event, Player player, RecipeListHolder holder) {
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }

        PlayerRecipeState state = getPlayerRecipeState(player);
        List<CustomRecipe> recipes = getFilteredPlayerRecipes(player, state);
        CustomRecipesGui gui = holder.gui;
        int pageSize = Math.max(1, gui.contentSlots().size());
        int maxPage = Math.max(0, (recipes.size() - 1) / pageSize);

        int recipeSlotIndex = recipeListSlotIndex(raw, gui.contentSlots());
        if (recipeSlotIndex >= 0) {
            int index = holder.page * pageSize + recipeSlotIndex;
            if (index < recipes.size()) {
                plugin.getGuiSounds().open(player);
                openRecipePreviewGui(player, recipes.get(index).getId(), holder.page, holder.returnToCraft);
            }
            return;
        }

        if (raw == gui.previousPage().slot() && holder.page > 0) {
            plugin.getGuiSounds().previousPage(player);
            openRecipeListGui(player, holder.page - 1, holder.returnToCraft);
            return;
        }
        if (raw == gui.nextPage().slot() && holder.page < maxPage) {
            plugin.getGuiSounds().nextPage(player);
            openRecipeListGui(player, holder.page + 1, holder.returnToCraft);
            return;
        }
        if (raw == gui.search().slot()) {
            if (event.getClick().isRightClick()) {
                state.search = "";
                plugin.getGuiSounds().clearSearch(player);
                messages.send(player, "search-cleared");
                openRecipeListGui(player, 0, holder.returnToCraft);
                return;
            }
            plugin.getGuiSounds().search(player);
            startPrompt(player, PromptType.PLAYER_SEARCH, null, holder.page, "{theme}Type recipe search query in chat. Type {bad}cancel {theme}to cancel.", holder.returnToCraft);
            return;
        }
        if (raw == gui.clearSearch().slot() && !state.search.isBlank()) {
            state.search = "";
            plugin.getGuiSounds().clearSearch(player);
            messages.send(player, "search-cleared");
            openRecipeListGui(player, 0, holder.returnToCraft);
            return;
        }
        if (raw == gui.backToCraft().slot() && holder.returnToCraft) {
            plugin.getGuiSounds().back(player);
            openCraftGui(player, null, false);
            return;
        }
        if (raw == gui.sort().slot()) {
            state.sort = state.sort.next();
            plugin.getGuiSounds().sort(player);
            openRecipeListGui(player, 0, holder.returnToCraft);
            return;
        }
    }

    private void handleRecipePreviewClick(InventoryClickEvent event, Player player, RecipePreviewHolder holder) {
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (raw == holder.gui.back().slot()) {
            plugin.getGuiSounds().back(player);
            openRecipeListGui(player, holder.returnPage, holder.returnToCraft);
            return;
        }
    }

    private void handleAdminEntryBrowserClick(InventoryClickEvent event, Player player, EntryBrowserHolder holder) {
        event.setCancelled(true);
        EntryBrowserClick click = EntryBrowserMenus.handleClick(event.getRawSlot(), holder, event.getClick());
        AdminListState state = getAdminListState(player);
        switch (click.action()) {
            case ENTRY -> {
                plugin.getEditorSounds().open(player);
                openAdminEditorGui(player, click.entryId(), holder.request().page());
            }
            case ADD -> startPrompt(player, PromptType.CREATE_RECIPE_NAME, null, holder.request().page(),
                    "{theme}Type the new recipe name. Type {bad}cancel {theme}to cancel.");
            case EXTRA -> {
                state.sort = state.sort.next();
                plugin.getEditorSounds().cycle(player);
                openAdminListGui(player, 0);
            }
            case SEARCH -> {
                plugin.getEditorSounds().search(player);
                startPrompt(player, PromptType.ADMIN_SEARCH, null, holder.request().page(),
                        "{theme}Type admin recipe search query in chat. Type {bad}cancel {theme}to cancel.");
            }
            case CLEAR_SEARCH -> {
                state.search = "";
                plugin.getEditorSounds().clearSearch(player);
                messages.send(player, "search-cleared");
                openAdminListGui(player, 0);
            }
            case PREVIOUS_PAGE -> {
                plugin.getEditorSounds().previousPage(player);
                openAdminListGui(player, holder.request().page() - 1);
            }
            case NEXT_PAGE -> {
                plugin.getEditorSounds().nextPage(player);
                openAdminListGui(player, holder.request().page() + 1);
            }
            case BACK, NONE -> {
            }
        }
    }

    private void handleAdminListClick(InventoryClickEvent event, Player player, AdminListHolder holder) {
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }

        AdminListState state = getAdminListState(player);
        List<CustomRecipe> recipes = getFilteredAdminRecipes(state);
        int maxPage = Math.max(0, (recipes.size() - 1) / RECIPE_LIST_SLOTS.length);

        int recipeSlotIndex = recipeListSlotIndex(raw);
        if (recipeSlotIndex >= 0) {
            int index = holder.page * RECIPE_LIST_SLOTS.length + recipeSlotIndex;
            if (index < recipes.size()) {
                plugin.getEditorSounds().open(player);
                openAdminEditorGui(player, recipes.get(index).getId(), holder.page);
            }
            return;
        }

        if (raw == PAGE_PREVIOUS_SLOT && holder.page > 0) {
            plugin.getEditorSounds().previousPage(player);
            openAdminListGui(player, holder.page - 1);
            return;
        }
        if (raw == PAGE_NEXT_SLOT && holder.page < maxPage) {
            plugin.getEditorSounds().nextPage(player);
            openAdminListGui(player, holder.page + 1);
            return;
        }
        if (raw == ADMIN_SEARCH_SLOT) {
            if (event.getClick().isRightClick()) {
                state.search = "";
                plugin.getEditorSounds().clearSearch(player);
                messages.send(player, "search-cleared");
                openAdminListGui(player, 0);
                return;
            }
            plugin.getEditorSounds().search(player);
            startPrompt(player, PromptType.ADMIN_SEARCH, null, holder.page, "{theme}Type admin recipe search query in chat. Type {bad}cancel {theme}to cancel.");
            return;
        }
        if (raw == ADMIN_CLEAR_SEARCH_SLOT && !state.search.isBlank()) {
            state.search = "";
            plugin.getEditorSounds().clearSearch(player);
            messages.send(player, "search-cleared");
            openAdminListGui(player, 0);
            return;
        }
        if (raw == ADMIN_SORT_SLOT) {
            state.sort = state.sort.next();
            plugin.getEditorSounds().cycle(player);
            openAdminListGui(player, 0);
            return;
        }
        if (raw == ADMIN_CREATE_SLOT) {
            startPrompt(player, PromptType.CREATE_RECIPE_NAME, null, holder.page, "{theme}Type the new recipe name. Type {bad}cancel {theme}to cancel.");
            return;
        }
    }

    private void handleDeleteConfirmClick(InventoryClickEvent event, Player player, DeleteConfirmHolder holder) {
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (raw == DELETE_CONFIRM_CANCEL_SLOT) {
            plugin.getEditorSounds().back(player);
            if (holder.editorInventory != null) {
                openForViewer(player, holder.editorInventory);
            } else {
                openAdminEditorGui(player, holder.recipeId, holder.returnPage);
            }
            return;
        }
        if (raw == DELETE_CONFIRM_CONFIRM_SLOT) {
            deleteRecipeAndReturn(player, holder.recipeId, holder.returnPage);
        }
    }

    private void deleteRecipeAndReturn(Player player, String recipeId, int returnPage) {
        boolean deleted = recipeManager.delete(recipeId);
        if (deleted) {
            plugin.getEditorSounds().delete(player);
            messages.send(player, "admin-deleted", Map.of("id", recipeId));
        } else {
            plugin.getEditorSounds().error(player);
            messages.send(player, "recipe-missing");
        }
        openAdminListGui(player, returnPage);
    }

    private void handleAdminEditorClick(InventoryClickEvent event, Player player, Inventory top, AdminEditorHolder holder) {
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        if (inTop) {
            event.setCancelled(true);
        }

        if (!inTop) {
            return;
        }

        Optional<CustomRecipe> optional = recipeManager.getRecipe(holder.recipeId);
        if (optional.isEmpty()) {
            messages.send(player, "recipe-missing");
            openAdminListGui(player, holder.returnPage);
            return;
        }
        CustomRecipe recipe = optional.get();

        if (raw == ADMIN_EDITOR_RENAME_SLOT) {
            event.setCancelled(true);
            startPrompt(player, PromptType.EDIT_NAME, recipe.getId(), holder.returnPage, "{theme}Type new recipe name. Type {bad}cancel {theme}to cancel.");
            return;
        }
        if (raw == ADMIN_EDITOR_PERMISSION_SLOT) {
            event.setCancelled(true);
            startPrompt(player, PromptType.EDIT_PERMISSION, recipe.getId(), holder.returnPage, "{theme}Type permission node. Use {white}none {theme}for empty or type {bad}cancel {theme}to cancel.");
            return;
        }
        if (raw == ADMIN_EDITOR_DESCRIPTION_SLOT) {
            event.setCancelled(true);
            startPrompt(player, PromptType.EDIT_DESCRIPTION, recipe.getId(), holder.returnPage, "{theme}Type recipe description. Use {white}none {theme}to clear or type {bad}cancel {theme}to cancel.");
            return;
        }
        if (raw == ADMIN_EDITOR_COST_SLOT) {
            event.setCancelled(true);
            startPrompt(
                    player,
                    PromptType.EDIT_COST,
                    recipe.getId(),
                    holder.returnPage,
                    "{theme}Type costs: <xp_levels> <xp_points> <vault_money>. Example: 1 25 100. Type {bad}cancel {theme}to cancel."
            );
            return;
        }
        if (raw == ADMIN_EDITOR_COMMANDS_SLOT) {
            event.setCancelled(true);
            startPrompt(
                    player,
                    PromptType.EDIT_COMMANDS,
                    recipe.getId(),
                    holder.returnPage,
                    "{theme}Type commands split by {white}|{theme}. Prefix with {white}console:{theme} or {white}player:{theme}. Type {white}none {theme}to clear or {bad}cancel {theme}to cancel."
            );
            return;
        }
        if (raw == ADMIN_EDITOR_ITEMS_SLOT) {
            event.setCancelled(true);
            plugin.getEditorSounds().open(player);
            openAdminRecipeItemsGui(player, recipe.getId(), holder.returnPage);
            return;
        }
        if (raw == ADMIN_EDITOR_WORLDS_SLOT) {
            event.setCancelled(true);
            plugin.getEditorSounds().open(player);
            openDisabledWorldSelector(player, recipe, holder.returnPage);
            return;
        }

        if (raw == ADMIN_EDITOR_TYPE_SLOT) {
            event.setCancelled(true);
            recipe.setType(recipe.getType() == RecipeType.SHAPED ? RecipeType.SHAPELESS : RecipeType.SHAPED);
            recipeManager.upsert(recipe);
            plugin.getEditorSounds().cycle(player);
            refreshAdminEditorMeta(player, top, recipe);
            messages.send(player, "admin-type-changed", Map.of("type", recipe.getType().name()));
            return;
        }

        if (raw == ADMIN_EDITOR_MATCH_MODE_SLOT) {
            event.setCancelled(true);
            recipe.setMatchMode(nextMatchMode(recipe.getMatchMode()));
            recipeManager.upsert(recipe);
            plugin.getEditorSounds().cycle(player);
            refreshAdminEditorMeta(player, top, recipe);
            messages.send(player, "admin-match-mode-changed", Map.of("mode", recipe.getMatchMode().name()));
            return;
        }

        if (raw == ADMIN_EDITOR_ENABLED_SLOT) {
            event.setCancelled(true);
            recipe.setEnabled(!recipe.isEnabled());
            recipeManager.upsert(recipe);
            plugin.getEditorSounds().toggle(player, recipe.isEnabled());
            refreshAdminEditorMeta(player, top, recipe);
            messages.send(player, recipe.isEnabled() ? "admin-enabled" : "admin-disabled");
            return;
        }

        if (raw == ADMIN_EDITOR_DELETE_SLOT) {
            event.setCancelled(true);
            plugin.getEditorSounds().open(player);
            openDeleteConfirmation(player, holder.recipeId, holder.returnPage, top);
            return;
        }

        if (raw == ADMIN_EDITOR_BACK_SLOT) {
            event.setCancelled(true);
            plugin.getEditorSounds().back(player);
            openAdminListGui(player, holder.returnPage);
            return;
        }

    }

    private void handleAdminRecipeItemsClick(InventoryClickEvent event, Player player, Inventory top, AdminRecipeItemsHolder holder) {
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        if (inTop && !ADMIN_RECIPE_ITEMS_EDITABLE_SLOTS.contains(raw)) {
            event.setCancelled(true);
        }

        if (!inTop) {
            if (event.isShiftClick()) {
                scheduleRecipeItemsAutoSave(player, top, holder.recipeId);
            }
            return;
        }

        if (raw == ADMIN_RECIPE_ITEMS_BACK_SLOT) {
            event.setCancelled(true);
            autoSaveRecipeItems(top, holder.recipeId);
            plugin.getEditorSounds().back(player);
            openAdminEditorGui(player, holder.recipeId, holder.returnPage);
            return;
        }

        if (ADMIN_RECIPE_ITEMS_EDITABLE_SLOTS.contains(raw)) {
            scheduleRecipeItemsAutoSave(player, top, holder.recipeId);
        }
    }

    private void handleWorldSelectionClick(InventoryClickEvent event, Player player, TriStateSelectionHolder holder) {
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }

        WorldSelectionSession session = worldSelectionSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        TriStateSelectionClick click = TriStateSelectionMenus.handleClick(raw, holder);
        TriStateSelectionActionType action = click.action();
        if (action == TriStateSelectionActionType.NONE) {
            return;
        }
        if (action == TriStateSelectionActionType.BACK) {
            worldSelectionSessions.remove(player.getUniqueId());
            plugin.getEditorSounds().back(player);
            openAdminEditorGui(player, session.recipeId(), session.returnPage());
            return;
        }
        if (action == TriStateSelectionActionType.SEARCH) {
            startWorldSelectionSearch(player, session, holder.request());
            return;
        }
        if (action == TriStateSelectionActionType.CLEAR_SEARCH) {
            plugin.getEditorSounds().clearSearch(player);
            openWorldSelection(player, session, click.nextRequest());
            return;
        }
        if (action == TriStateSelectionActionType.PREVIOUS_PAGE) {
            plugin.getEditorSounds().previousPage(player);
            openWorldSelection(player, session, click.nextRequest());
            return;
        }
        if (action == TriStateSelectionActionType.NEXT_PAGE) {
            plugin.getEditorSounds().nextPage(player);
            openWorldSelection(player, session, click.nextRequest());
            return;
        }
        if (action == TriStateSelectionActionType.TOGGLE) {
            saveWorldSelection(player, session, click.nextRequest());
        }
    }

    private void openDisabledWorldSelector(Player player, CustomRecipe recipe, int returnPage) {
        WorldSelectionSession session = new WorldSelectionSession(recipe.getId(), returnPage);
        openWorldSelection(player, session, disabledWorldSelectionRequest(recipe));
    }

    private void openWorldSelection(Player player, WorldSelectionSession session, TriStateSelectionRequest request) {
        worldSelectionSessions.put(player.getUniqueId(), session);
        TriStateSelectionMenus.open(player, request);
    }

    private TriStateSelectionRequest disabledWorldSelectionRequest(CustomRecipe recipe) {
        List<String> disabledWorlds = recipe.getDisabledWorlds();
        return TriStateSelectionRequest.builder()
                .worldSelection()
                .entries(WorldSelectionEntries.loadedAndConfigured(plugin, disabledWorlds))
                .states(TriStateSelections.fromEnabledDisabled(List.of(), disabledWorlds))
                .cycleOrder(List.of(TriStateSelectionState.NEUTRAL, TriStateSelectionState.DISABLED))
                .buttons(buttons)
                .showBack(true)
                .showSearch(true)
                .disabledLabel("Disabled")
                .neutralLabel("Allowed")
                .clickHint("Click to allow or disable this world.")
                .emptyTitle("No Worlds")
                .emptyLore(List.of("No loaded or configured worlds are available."))
                .build();
    }

    private void saveWorldSelection(Player player, WorldSelectionSession session, TriStateSelectionRequest request) {
        Optional<CustomRecipe> optional = recipeManager.getRecipe(session.recipeId());
        if (optional.isEmpty()) {
            plugin.getEditorSounds().error(player);
            messages.send(player, "recipe-missing");
            worldSelectionSessions.remove(player.getUniqueId());
            openAdminListGui(player, session.returnPage());
            return;
        }

        List<String> disabledWorlds = TriStateSelections.disabledKeys(request);
        CustomRecipe recipe = optional.get();
        recipe.setDisabledWorlds(disabledWorlds);
        recipeManager.upsert(recipe);
        plugin.getEditorSounds().cycle(player);
        messages.send(player, "admin-disabled-worlds-updated", Map.of("amount", String.valueOf(disabledWorlds.size())));
        openWorldSelection(player, session, request);
    }

    private void startWorldSelectionSearch(Player player, WorldSelectionSession session, TriStateSelectionRequest request) {
        textFallback.rememberHint(player, "{theme}Type world search query. Type {bad}cancel {theme}to cancel.");
        Map<String, String> replacements = Map.of(
                "current", request.filter(),
                "format", "world name"
        );
        boolean openedNative = EditorDialogInputs.openTextFromInventory(
                plugin,
                currentCore().inventoryCloseSuppressor(),
                dialogService(),
                player,
                formatRequest(searchDialogRequest("{current}", "{format}"), replacements),
                input -> {
                    if (input.equalsIgnoreCase("cancel")) {
                        plugin.getEditorSounds().back(player);
                        openWorldSelection(player, session, request);
                        return;
                    }
                    openWorldSelection(player, session, request.withFilter(input.trim()));
                },
                () -> {
                    plugin.getEditorSounds().back(player);
                    openWorldSelection(player, session, request);
                }
        );
        if (openedNative) {
            textFallback.clearHint(player);
            plugin.getEditorSounds().open(player);
        }
    }

    private void autoSaveRecipeItems(Inventory top, String recipeId) {
        Optional<CustomRecipe> optional = recipeManager.getRecipe(recipeId);
        if (optional.isEmpty()) {
            return;
        }
        CustomRecipe recipe = optional.get();
        applyRecipeItemsInventoryToRecipe(top, recipe);
        recipeManager.upsert(recipe);
    }

    private void scheduleRecipeItemsAutoSave(Player player, Inventory expectedTop, String recipeId) {
        currentCore().scheduler().runLaterForPlayer(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            Inventory currentTop = player.getOpenInventory().getTopInventory();
            if (currentTop.equals(expectedTop) && currentTop.getHolder() instanceof AdminRecipeItemsHolder) {
                autoSaveRecipeItems(currentTop, recipeId);
            }
        }, 1L);
    }

    private void applyRecipeItemsInventoryToRecipe(Inventory top, CustomRecipe recipe) {
        ItemStack result = normalize(top.getItem(ADMIN_RECIPE_ITEMS_RESULT_SLOT));
        Map<Integer, ItemStack> ingredients = new LinkedHashMap<>();
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            ItemStack ingredient = normalize(top.getItem(GRID_SLOTS[i]));
            if (ingredient != null) {
                ingredients.put(i, ingredient);
            }
        }

        recipe.setResult(result);
        recipe.setIngredients(ingredients);
        if ((recipe.getDisplayName() == null || recipe.getDisplayName().isBlank() || recipe.getDisplayName().equalsIgnoreCase("New Recipe")) && result != null) {
            recipe.setDisplayName(formatMaterial(result.getType()));
        }
    }

    private CraftAttempt attemptCraft(Player player, Inventory inventory, int desiredCrafts) {
        CustomCraftingGui gui = craftGui(inventory);
        List<Integer> gridSlots = gui.gridSlots();
        ItemStack[] grid = readGrid(inventory, gridSlots);
        RecipeMatch match = recipeManager.findFirstMatch(grid, recipe -> canCraftRecipe(player, recipe));

        if (match == null) {
            RecipeMatch restricted = recipeManager.findFirstMatch(grid);
            if (restricted != null) {
                CustomRecipe blocked = restricted.recipe();
                if (!hasRecipePermission(player, blocked)) {
                    return new CraftAttempt(0, "craft-no-recipe-permission", Map.of("permission", blocked.getPermission()));
                }
                if (!isWorldAllowed(player, blocked)) {
                    return new CraftAttempt(0, "craft-world-blocked", Map.of("world", player.getWorld().getName()));
                }
            }
            return new CraftAttempt(0, "craft-no-match", Map.of());
        }

        CustomRecipe recipe = match.recipe();

        int crafts = Math.min(desiredCrafts, Math.max(0, match.maxCraftable()));
        if (crafts <= 0) {
            return new CraftAttempt(0, "craft-no-match", Map.of());
        }

        int crafted = 0;
        for (int i = 0; i < crafts; i++) {
            CostCheck costCheck = checkCost(player, recipe.getCost());
            if (!costCheck.ok) {
                if (crafted == 0) {
                    return new CraftAttempt(0, costCheck.messageKey, costCheck.placeholders);
                }
                break;
            }

            if (!consumeOneCraft(inventory, match, gridSlots)) {
                break;
            }

            if (!chargeCost(player, recipe.getCost())) {
                restoreOneCraftInputs(inventory, match, gridSlots);
                if (crafted == 0) {
                    return new CraftAttempt(0, "craft-cost-failed", Map.of());
                }
                break;
            }

            ItemStack result = recipe.getResult();
            giveResult(player, result);
            executeCraftCommands(player, recipe, result.getAmount());
            crafted++;
        }

        return new CraftAttempt(crafted, crafted > 0 ? null : "craft-no-match", Map.of());
    }

    private void executeCraftCommands(Player player, CustomRecipe recipe, int amount) {
        for (String rawCommand : recipe.getCraftCommands()) {
            if (rawCommand == null || rawCommand.isBlank()) {
                continue;
            }
            String command = rawCommand.trim();
            boolean asPlayer = false;

            if (command.regionMatches(true, 0, "player:", 0, "player:".length())) {
                asPlayer = true;
                command = command.substring("player:".length()).trim();
            } else if (command.regionMatches(true, 0, "console:", 0, "console:".length())) {
                command = command.substring("console:".length()).trim();
            }

            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (command.isBlank()) {
                continue;
            }

            String parsed = CommandPlaceholders.applyPlayer(command, player);
            parsed = CommandPlaceholders.apply(parsed, Map.of(
                    "recipe_id", recipe.getId(),
                    "recipe_name", recipe.getDisplayName(),
                    "amount", String.valueOf(amount)
            ));

            if (asPlayer) {
                Bukkit.dispatchCommand(player, parsed);
            } else {
                CommandSender console = Bukkit.getConsoleSender();
                Bukkit.dispatchCommand(console, parsed);
            }
        }
    }

    private boolean canCraftRecipe(Player player, CustomRecipe recipe) {
        return recipe.isEnabled() && recipe.getResult() != null && hasRecipePermission(player, recipe) && isWorldAllowed(player, recipe);
    }

    private boolean hasRecipePermission(Player player, CustomRecipe recipe) {
        String permission = recipe.getPermission();
        if (permission == null || permission.isBlank()) {
            return true;
        }
        return player.hasPermission(permission) || player.hasPermission("focrafts.recipe.*");
    }

    private boolean isWorldAllowed(Player player, CustomRecipe recipe) {
        String world = player.getWorld().getName();
        List<String> disabledWorlds = recipe.getDisabledWorlds();
        return !containsIgnoreCase(disabledWorlds, world);
    }

    private boolean consumeOneCraft(Inventory inventory, RecipeMatch match, List<Integer> gridSlots) {
        Map<Integer, ItemStack> ingredients = match.recipe().getIngredients();
        RecipeMatchMode mode = match.recipe().getMatchMode();

        if (match.recipe().getType() == RecipeType.SHAPED) {
            for (Map.Entry<Integer, ItemStack> entry : ingredients.entrySet()) {
                int gridIndex = entry.getKey();
                int rawSlot = slotAt(gridSlots, gridIndex);
                if (rawSlot < 0) {
                    return false;
                }
                ItemStack current = normalize(inventory.getItem(rawSlot));
                if (current == null || !matchesIngredient(current, entry.getValue(), mode) || current.getAmount() < entry.getValue().getAmount()) {
                    return false;
                }
            }

            for (Map.Entry<Integer, ItemStack> entry : ingredients.entrySet()) {
                int rawSlot = slotAt(gridSlots, entry.getKey());
                if (rawSlot < 0) {
                    return false;
                }
                decreaseSlot(inventory, rawSlot, entry.getValue().getAmount());
            }
            return true;
        }

        for (Map.Entry<Integer, ItemStack> entry : ingredients.entrySet()) {
            Integer mappedGrid = match.shapelessMapping().get(entry.getKey());
            if (mappedGrid == null) {
                return false;
            }
            int rawSlot = slotAt(gridSlots, mappedGrid);
            if (rawSlot < 0) {
                return false;
            }
            ItemStack current = normalize(inventory.getItem(rawSlot));
            if (current == null || !matchesIngredient(current, entry.getValue(), mode) || current.getAmount() < entry.getValue().getAmount()) {
                return false;
            }
        }

        for (Map.Entry<Integer, ItemStack> entry : ingredients.entrySet()) {
            int rawSlot = slotAt(gridSlots, match.shapelessMapping().get(entry.getKey()));
            if (rawSlot < 0) {
                return false;
            }
            decreaseSlot(inventory, rawSlot, entry.getValue().getAmount());
        }
        return true;
    }

    private void restoreOneCraftInputs(Inventory inventory, RecipeMatch match, List<Integer> gridSlots) {
        Map<Integer, ItemStack> ingredients = match.recipe().getIngredients();
        if (match.recipe().getType() == RecipeType.SHAPED) {
            for (Map.Entry<Integer, ItemStack> entry : ingredients.entrySet()) {
                int rawSlot = slotAt(gridSlots, entry.getKey());
                if (rawSlot >= 0) {
                    addToSlot(inventory, rawSlot, entry.getValue().getAmount());
                }
            }
            return;
        }
        for (Map.Entry<Integer, ItemStack> entry : ingredients.entrySet()) {
            Integer gridIndex = match.shapelessMapping().get(entry.getKey());
            if (gridIndex == null) {
                continue;
            }
            int rawSlot = slotAt(gridSlots, gridIndex);
            if (rawSlot >= 0) {
                addToSlot(inventory, rawSlot, entry.getValue().getAmount());
            }
        }
    }

    private void addToSlot(Inventory inventory, int rawSlot, int amount) {
        ItemStack existing = normalize(inventory.getItem(rawSlot));
        if (existing == null) {
            return;
        }
        existing.setAmount(existing.getAmount() + amount);
        inventory.setItem(rawSlot, existing);
    }

    private CostCheck checkCost(Player player, RecipeCost cost) {
        if (cost == null || !cost.hasAnyCost()) {
            return CostCheck.success();
        }

        int levels = cost.getXpLevels();
        int points = cost.getXpPoints();
        double money = cost.getVaultMoney();

        if (levels > 0 && player.getLevel() < levels) {
            return new CostCheck(false, "craft-cost-xp", Map.of(
                    "levels", String.valueOf(levels),
                    "points", String.valueOf(points)
            ));
        }

        int currentTotalExperience = getTotalExperience(player);
        int levelCostInPoints = expCostForLevelDrop(player.getLevel(), levels);
        int requiredTotalPoints = Math.max(0, points + levelCostInPoints);
        if (requiredTotalPoints > 0 && currentTotalExperience < requiredTotalPoints) {
            return new CostCheck(false, "craft-cost-xp", Map.of(
                    "levels", String.valueOf(levels),
                    "points", String.valueOf(points)
            ));
        }

        if (money > 0.0D) {
            if (!vaultHook.isAvailable()) {
                return new CostCheck(false, "craft-cost-vault-missing", Map.of());
            }
            if (!vaultHook.has(player, money)) {
                return new CostCheck(false, "craft-cost-money", Map.of(
                        "money", moneyFormat.format(money)
                ));
            }
        }

        return CostCheck.success();
    }

    private boolean chargeCost(Player player, RecipeCost cost) {
        if (cost == null || !cost.hasAnyCost()) {
            return true;
        }

        double money = cost.getVaultMoney();
        if (money > 0.0D && !vaultHook.withdraw(player, money)) {
            return false;
        }

        if (cost.getXpLevels() > 0) {
            player.giveExpLevels(-cost.getXpLevels());
        }
        if (cost.getXpPoints() > 0) {
            player.giveExp(-cost.getXpPoints());
        }

        return true;
    }

    private int getTotalExperience(Player player) {
        int level = player.getLevel();
        float progress = player.getExp();
        int toLevel = xpToNextLevel(level);
        return totalExperienceAtLevel(level) + Math.round(progress * toLevel);
    }

    private int expCostForLevelDrop(int currentLevel, int levelsToDrop) {
        if (levelsToDrop <= 0 || currentLevel <= 0) {
            return 0;
        }
        int targetLevel = Math.max(0, currentLevel - levelsToDrop);
        return totalExperienceAtLevel(currentLevel) - totalExperienceAtLevel(targetLevel);
    }

    private int totalExperienceAtLevel(int level) {
        if (level <= 16) {
            return level * level + (6 * level);
        }
        if (level <= 31) {
            return (int) Math.floor(2.5 * level * level - 40.5 * level + 360);
        }
        return (int) Math.floor(4.5 * level * level - 162.5 * level + 2220);
    }

    private int xpToNextLevel(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        }
        if (level <= 30) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    private void sendCraftFailure(Player player, CraftAttempt attempt) {
        if (attempt.messageKey == null) {
            messages.send(player, "craft-no-match");
            return;
        }
        messages.send(player, attempt.messageKey, attempt.placeholders);
    }

    private void prefillCraftGridFromInventory(Player player, Inventory inventory, CustomRecipe recipe, List<Integer> gridSlots) {
        if (recipe.getType() == RecipeType.SHAPED) {
            for (Map.Entry<Integer, ItemStack> entry : recipe.getIngredients().entrySet()) {
                int gridIndex = entry.getKey();
                int rawSlot = slotAt(gridSlots, gridIndex);
                if (rawSlot < 0) {
                    continue;
                }
                ItemStack moved = pullFromInventory(player, entry.getValue(), entry.getValue().getAmount(), recipe.getMatchMode());
                if (moved != null) {
                    inventory.setItem(rawSlot, moved);
                }
            }
            return;
        }

        int index = 0;
        for (ItemStack ingredient : recipe.getIngredients().values()) {
            int rawSlot = slotAt(gridSlots, index);
            if (rawSlot < 0) {
                break;
            }
            ItemStack moved = pullFromInventory(player, ingredient, ingredient.getAmount(), recipe.getMatchMode());
            if (moved != null) {
                inventory.setItem(rawSlot, moved);
            }
            index++;
        }
    }

    private ItemStack pullFromInventory(Player player, ItemStack template, int amount, RecipeMatchMode mode) {
        if (template == null || template.getType() == Material.AIR || amount <= 0) {
            return null;
        }

        if (player.getGameMode().name().equalsIgnoreCase("CREATIVE")) {
            ItemStack stack = FoItemStacks.cloneItem(template);
            stack.setAmount(amount);
            return stack;
        }

        PlayerInventory inventory = player.getInventory();
        int remaining = amount;
        ItemStack output = FoItemStacks.cloneItem(template);
        output.setAmount(0);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR || !matchesIngredient(current, template, mode)) {
                continue;
            }

            int toTake = Math.min(remaining, current.getAmount());
            remaining -= toTake;
            output.setAmount(output.getAmount() + toTake);

            if (current.getAmount() == toTake) {
                inventory.setItem(slot, null);
            } else {
                current.setAmount(current.getAmount() - toTake);
                inventory.setItem(slot, current);
            }

            if (remaining <= 0) {
                break;
            }
        }

        if (output.getAmount() <= 0) {
            return null;
        }
        return output;
    }

    private void placeRecipeInGrid(Inventory inventory, CustomRecipe recipe) {
        placeRecipeInGrid(inventory, recipe, GuiConfig.DEFAULT_CRAFT_GRID_SLOTS);
    }

    private void placeRecipeInGrid(Inventory inventory, CustomRecipe recipe, List<Integer> gridSlots) {
        if (recipe.getType() == RecipeType.SHAPED) {
            for (Map.Entry<Integer, ItemStack> entry : recipe.getIngredients().entrySet()) {
                int gridIndex = entry.getKey();
                int rawSlot = slotAt(gridSlots, gridIndex);
                if (rawSlot < 0) {
                    continue;
                }
                ItemStack ingredient = normalize(entry.getValue());
                if (ingredient != null) {
                    inventory.setItem(rawSlot, ingredient);
                }
            }
            return;
        }

        int index = 0;
        for (ItemStack ingredient : recipe.getIngredients().values()) {
            int rawSlot = slotAt(gridSlots, index);
            if (rawSlot < 0) {
                break;
            }
            ItemStack cloned = normalize(ingredient);
            if (cloned != null) {
                inventory.setItem(rawSlot, cloned);
            }
            index++;
        }
    }

    private void refreshCraftPreview(Player player, Inventory inventory) {
        CustomCraftingGui gui = craftGui(inventory);
        ItemStack[] grid = readGrid(inventory, gui.gridSlots());
        RecipeMatch match = recipeManager.findFirstMatch(grid, recipe -> canCraftRecipe(player, recipe));
        if (match == null) {
            RecipeMatch restricted = recipeManager.findFirstMatch(grid);
            if (restricted != null) {
                if (!hasRecipePermission(player, restricted.recipe())) {
                    setCraftResult(player, inventory, gui.resultSlot(), createConfiguredItem(gui.lockedRecipe(), Map.of(
                            "permission", configuredPermission(restricted.recipe())
                    )));
                    return;
                }
                if (!isWorldAllowed(player, restricted.recipe())) {
                    setCraftResult(player, inventory, gui.resultSlot(), createConfiguredItem(gui.worldBlocked(), Map.of(
                            "world", player.getWorld().getName()
                    )));
                    return;
                }
            }
            setCraftResult(player, inventory, gui.resultSlot(), createConfiguredItem(gui.noMatch(), Map.of()));
            return;
        }

        ItemStack result = normalize(match.recipe().getResult());
        if (result == null) {
            setCraftResult(player, inventory, gui.resultSlot(), createConfiguredItem(gui.noMatch(), Map.of()));
            return;
        }
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            ensureOutputName(result, meta);
            meta.setLore(renderLore(gui.resultLore(), craftPlaceholders(match.recipe(), match.maxCraftable()), Map.of()));
            result.setItemMeta(meta);
        }
        setCraftResult(player, inventory, gui.resultSlot(), result);
    }

    /**
     * Preview items are inserted after the inventory was opened. Resolve the
     * raw template for this viewer at that point as well, otherwise the
     * no-match icon (and result lore) can remain as literal :material: text.
     */
    private void setCraftResult(Player player, Inventory inventory, int resultSlot, ItemStack item) {
        inventory.setItem(resultSlot, item == null ? null : DialogIcons.forViewer(player, item));
    }

    private void refreshAdminEditorButtons(Player player, Inventory inventory, CustomRecipe recipe) {
        inventory.setItem(ADMIN_EDITOR_TYPE_SLOT, button(player,
                recipe.getType() == RecipeType.SHAPED ? Material.OAK_SIGN : Material.MAGMA_CREAM,
                "{theme}Type", List.of("{white}Current: {theme}" + recipeTypeLabel(recipe.getType())),
                "cycle recipe type"
        ));

        inventory.setItem(ADMIN_EDITOR_MATCH_MODE_SLOT, button(player,
                Material.LODESTONE,
                "{theme}Match Mode", List.of("{white}Current: {theme}" + matchModeLabel(recipe.getMatchMode())),
                "cycle match mode"
        ));

        inventory.setItem(ADMIN_EDITOR_ENABLED_SLOT, button(player,
                recipe.isEnabled() ? Material.LIME_DYE : Material.RED_DYE,
                recipe.isEnabled() ? "{good}Enabled" : "{bad}Disabled",
                List.of("{white}Click to toggle enabled state.",
                        "{white}State: " + (recipe.isEnabled() ? "{good}ON" : "{bad}OFF")),
                "toggle recipe"
        ));

        inventory.setItem(ADMIN_EDITOR_DELETE_SLOT, button(player, Material.LAVA_BUCKET, "{bad}Delete Recipe", List.of(
                "{white}Open confirmation first.",
                "{bad}This permanently deletes the recipe."
        ), "delete recipe"));
        inventory.setItem(ADMIN_EDITOR_BACK_SLOT, buttons.back(player));
    }

    private ItemStack adminRecipeIcon(CustomRecipe recipe) {
        ItemStack icon = normalize(recipe.getResult());
        if (icon == null) {
            icon = new ItemStack(Material.BARRIER);
        }

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("{theme}" + recipe.getDisplayName()));
            List<String> lore = new ArrayList<>();
            if (!recipe.getDescription().isBlank()) {
                lore.add(color("{white}Desc: {theme}" + recipe.getDescription()));
            }
            lore.add(color("{white}ID: {theme}" + recipe.getId()));
            lore.add(color("{white}Type: {theme}" + recipe.getType().name()));
            lore.add(color("{white}Match: {theme}" + recipe.getMatchMode().name()));
            lore.add(color(recipe.isEnabled() ? "{good}Enabled" : "{bad}Disabled"));
            lore.add(color("{white}Click to edit this recipe."));
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack recipeIcon(CustomRecipesGui gui, CustomRecipe recipe, boolean lockedForPlayer) {
        Map<String, String> placeholders = recipePlaceholders(recipe, lockedForPlayer);
        Map<String, List<String>> expansions = Map.of(
                "description", descriptionExpansion(gui.descriptionLine(), recipe),
                "locked", lockedExpansion(gui, lockedForPlayer)
        );
        ItemStack icon = normalize(recipe.getResult());
        if (icon == null) {
            return createConfiguredItem(gui.invalidRecipeItem(), placeholders, Map.of());
        }
        return createConfiguredItemFromStack(icon, gui.recipeItem(), placeholders, expansions);
    }

    private ItemStack recipeResultPreview(RecipePreviewGui gui, Player player, CustomRecipe recipe) {
        ItemStack result = normalize(recipe.getResult());
        if (result == null) {
            return createConfiguredItem(gui.invalidResult(), recipePreviewPlaceholders(gui, player, recipe), Map.of());
        }

        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            ensureOutputName(result, meta);
            meta.setLore(renderLore(gui.resultLore(), recipePreviewPlaceholders(gui, player, recipe), Map.of()));
            result.setItemMeta(meta);
        }
        return result;
    }

    private void ensureOutputName(ItemStack item, ItemMeta meta) {
        if (hasVisibleItemName(meta.hasDisplayName() ? meta.getDisplayName() : null)
                || hasVisibleItemName(meta.hasItemName() ? meta.getItemName() : null)) {
            return;
        }
        meta.setDisplayName(color("{white}" + formatMaterial(item.getType())));
    }

    private boolean hasVisibleItemName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return DialogIcons.containsToken(name)
                || !DialogIcons.fallbackText(DialogIcons.render(name)).isBlank();
    }

    private ItemStack createConfiguredItem(GuiItem item, Map<String, String> placeholders) {
        return createConfiguredItem(item, placeholders, Map.of());
    }

    private ItemStack createConfiguredItem(GuiItem item, Map<String, String> placeholders, Map<String, List<String>> expansions) {
        ItemStack stack = new ItemStack(item.material(), item.amount());
        return applyConfiguredItem(stack, item, placeholders, expansions, false);
    }

    private ItemStack createConfiguredItemFromStack(ItemStack base, GuiItem item, Map<String, String> placeholders, Map<String, List<String>> expansions) {
        ItemStack stack = normalize(base);
        if (stack == null) {
            stack = new ItemStack(item.material(), item.amount());
            return applyConfiguredItem(stack, item, placeholders, expansions, false);
        }
        return applyConfiguredItem(stack, item, placeholders, expansions, true);
    }

    @SuppressWarnings("deprecation")
    private ItemStack applyConfiguredItem(ItemStack stack, GuiItem item, Map<String, String> placeholders, Map<String, List<String>> expansions, boolean preserveAmount) {
        if (!preserveAmount) {
            stack.setAmount(item.amount());
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        String renderedName = renderLine(item.name(), placeholders);
        List<String> renderedLore = renderLore(item.lore(), placeholders, expansions);
        String iconizedName = DialogIcons.withMaterialIcon(renderedName, item.material());
        if (!DialogIcons.applyItemMetaTemplate(meta, iconizedName, renderedLore)) {
            meta.setDisplayName(DialogIcons.fallbackText(DialogIcons.render(iconizedName)));
            meta.setLore(renderedLore.stream()
                    .map(line -> DialogIcons.fallbackText(DialogIcons.render(line)))
                    .toList());
        }
        if (item.customModelData() != null) {
            meta.setCustomModelData(item.customModelData());
        }
        if (!item.flags().isEmpty()) {
            meta.addItemFlags(item.flags().toArray(ItemFlag[]::new));
        }
        if (item.glow()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private void fillInventory(Inventory inventory, GuiItem filler) {
        ItemStack item = createConfiguredItem(filler, Map.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, item.clone());
        }
    }

    private void fillSlots(Inventory inventory, List<Integer> slots, GuiItem filler) {
        ItemStack item = createConfiguredItem(filler, Map.of());
        for (int slot : slots) {
            if (isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item.clone());
            }
        }
    }

    private void clearSlots(Inventory inventory, List<Integer> slots) {
        for (int slot : slots) {
            clearSlot(inventory, slot);
        }
    }

    private void clearSlot(Inventory inventory, int slot) {
        if (isValidSlot(inventory, slot)) {
            inventory.setItem(slot, null);
        }
    }

    private void placeButton(Inventory inventory, GuiButtonSlot button, ItemStack item) {
        if (button == null || item == null || !isValidSlot(inventory, button.slot())) {
            return;
        }
        inventory.setItem(button.slot(), FoItemStacks.cloneItem(item));
    }

    private void placeItem(Inventory inventory, GuiItem item, Map<String, String> placeholders) {
        placeItem(inventory, item, placeholders, Map.of());
    }

    private void placeItem(Inventory inventory, GuiItem item, Map<String, String> placeholders, Map<String, List<String>> expansions) {
        if (item == null || !isValidSlot(inventory, item.slot())) {
            return;
        }
        inventory.setItem(item.slot(), createConfiguredItem(item, placeholders, expansions));
    }

    private boolean isValidSlot(Inventory inventory, int slot) {
        return slot >= 0 && slot < inventory.getSize();
    }

    private boolean containsSlot(List<Integer> slots, int rawSlot) {
        return rawSlot >= 0 && slots.contains(rawSlot);
    }

    private int slotAt(List<Integer> slots, Integer index) {
        if (index == null || index < 0 || index >= slots.size()) {
            return -1;
        }
        return slots.get(index);
    }

    private int recipeListSlotIndex(int rawSlot, List<Integer> slots) {
        for (int index = 0; index < slots.size(); index++) {
            if (slots.get(index) == rawSlot) {
                return index;
            }
        }
        return -1;
    }

    private CustomCraftingGui craftGui(Inventory inventory) {
        if (inventory.getHolder() instanceof CraftHolder holder) {
            return holder.gui;
        }
        return guiConfig.customCrafting();
    }

    private ItemStack[] readGrid(Inventory inventory, List<Integer> gridSlots) {
        ItemStack[] grid = new ItemStack[9];
        for (int i = 0; i < grid.length; i++) {
            int rawSlot = slotAt(gridSlots, i);
            if (rawSlot >= 0 && isValidSlot(inventory, rawSlot)) {
                grid[i] = normalize(inventory.getItem(rawSlot));
            }
        }
        return grid;
    }

    private void returnItems(Player player, Inventory inventory, List<Integer> rawSlots) {
        for (int rawSlot : rawSlots) {
            if (!isValidSlot(inventory, rawSlot)) {
                continue;
            }
            ItemStack item = normalize(inventory.getItem(rawSlot));
            if (item == null) {
                continue;
            }
            inventory.setItem(rawSlot, null);
            currentCore().inventoryDeposits().deposit(player, item, player.getLocation(), OverflowPolicy.DROP_OVERFLOW);
        }
    }

    private List<String> sortOptionLines(CustomRecipesGui gui, SortMode selected) {
        List<String> information = new ArrayList<>();
        for (SortMode mode : SortMode.values()) {
            String template = mode == selected ? gui.sortSelectedLine() : gui.sortUnselectedLine();
            information.add(renderLine(template, Map.of("sort", mode.displayName)));
        }
        return FoButtonStyle.buttonLore(information, "cycle");
    }

    private List<String> descriptionExpansion(String template, CustomRecipe recipe) {
        if (template == null || template.isBlank() || safe(recipe.getDescription()).isBlank()) {
            return List.of();
        }
        return List.of(template);
    }

    private List<String> lockedExpansion(CustomRecipesGui gui, boolean lockedForPlayer) {
        if (!lockedForPlayer || gui.lockedLine() == null || gui.lockedLine().isBlank()) {
            return List.of();
        }
        return List.of(gui.lockedLine());
    }

    private Map<String, String> recipePlaceholders(CustomRecipe recipe, boolean lockedForPlayer) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("recipe_id", safe(recipe.getId()));
        placeholders.put("recipe_name", recipeName(recipe));
        placeholders.put("description", safe(recipe.getDescription()));
        placeholders.put("permission", configuredPermission(recipe));
        placeholders.put("type", recipe.getType() == null ? "" : recipe.getType().name());
        placeholders.put("match_mode", recipe.getMatchMode() == null ? "" : recipe.getMatchMode().name());
        placeholders.put("cost", formatCost(recipe.getCost()));
        placeholders.put("locked", lockedForPlayer ? "true" : "false");
        return placeholders;
    }

    private Map<String, String> recipePreviewPlaceholders(RecipePreviewGui gui, Player player, CustomRecipe recipe) {
        Map<String, String> placeholders = new HashMap<>(recipePlaceholders(recipe, !hasRecipePermission(player, recipe)));
        placeholders.put("status", previewStatus(gui, player, recipe));
        return placeholders;
    }

    private Map<String, String> craftPlaceholders(CustomRecipe recipe, int maxCraftable) {
        Map<String, String> placeholders = new HashMap<>(recipePlaceholders(recipe, false));
        placeholders.put("max_craftable", String.valueOf(maxCraftable));
        return placeholders;
    }

    private String configuredPermission(CustomRecipe recipe) {
        String permission = recipe.getPermission();
        return permission == null || permission.isBlank() ? "none" : permission;
    }

    private String recipeName(CustomRecipe recipe) {
        String name = recipe.getDisplayName();
        return name == null || name.isBlank() ? safe(recipe.getId()) : name;
    }

    private String previewStatus(RecipePreviewGui gui, Player player, CustomRecipe recipe) {
        if (!hasRecipePermission(player, recipe)) {
            return gui.lockedStatus();
        }
        if (!isWorldAllowed(player, recipe)) {
            return gui.worldBlockedStatus();
        }
        return gui.unlockedStatus();
    }

    private List<String> renderLore(List<String> lore, Map<String, String> placeholders, Map<String, List<String>> expansions) {
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        List<String> rendered = new ArrayList<>();
        for (String line : lore) {
            String expansionKey = expansionKey(line);
            if (expansionKey != null && expansions.containsKey(expansionKey)) {
                for (String expandedLine : expansions.get(expansionKey)) {
                    rendered.add(renderLine(expandedLine, placeholders));
                }
                continue;
            }
            rendered.add(renderLine(line, placeholders));
        }
        return rendered;
    }

    private String expansionKey(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}") || trimmed.length() < 3) {
            return null;
        }
        String key = trimmed.substring(1, trimmed.length() - 1);
        return key.matches("[A-Za-z0-9_-]+") ? key : null;
    }

    private String renderLine(String input, Map<String, String> placeholders) {
        return messages.renderTemplateForItem(
                CommandPlaceholders.apply(input == null ? "" : input, placeholders), Map.of());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void openForViewer(Player player, Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null) {
                inventory.setItem(slot, DialogIcons.forViewer(player, item));
            }
        }
        player.openInventory(inventory);
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.setDisplayName(color(name));
        if (lore != null && !lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(color(line));
            }
            meta.setLore(coloredLore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack button(Player viewer, Material material, String name, List<String> lore, String action) {
        String renderedName = color(name);
        String nameColor = renderedName.contains(FoStyle.BAD) ? FoStyle.BAD
                : renderedName.contains(FoStyle.GOOD) ? FoStyle.GOOD : FoStyle.THEME;
        List<String> renderedLore = lore == null ? List.of() : lore.stream().map(this::color).toList();
        return EditorItemFactory.button(viewer, material, nameColor, FoText.plain(renderedName), renderedLore, action);
    }

    private ItemStack emptyRecipeSlotFiller() {
        return named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private void fillBackground(Inventory inventory) {
        fillBackground(inventory, true);
    }

    private void fillBackground(Inventory inventory, boolean clearRecipeSlots) {
        ItemStack filler = EditorItemFactory.filler();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        if (clearRecipeSlots) {
            for (int gridSlot : GRID_SLOTS) {
                if (gridSlot < inventory.getSize()) {
                    inventory.setItem(gridSlot, null);
                }
            }
            if (RESULT_SLOT < inventory.getSize()) {
                inventory.setItem(RESULT_SLOT, null);
            }
        }
    }

    private void giveResult(Player player, ItemStack result) {
        if (result == null || result.getType() == Material.AIR) {
            return;
        }
        currentCore().inventoryDeposits().deposit(player, result, player.getLocation(), OverflowPolicy.DROP_OVERFLOW);
    }

    private void decreaseSlot(Inventory inventory, int rawSlot, int amount) {
        ItemStack item = normalize(inventory.getItem(rawSlot));
        if (item == null) {
            return;
        }
        int nextAmount = item.getAmount() - amount;
        if (nextAmount <= 0) {
            inventory.setItem(rawSlot, null);
            return;
        }
        item.setAmount(nextAmount);
        inventory.setItem(rawSlot, item);
    }

    private ItemStack normalize(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        ItemStack clone = FoItemStacks.cloneItem(item);
        clone.setAmount(Math.max(1, clone.getAmount()));
        return clone;
    }

    private String formatMaterial(Material material) {
        String[] parts = material.name().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase(Locale.ROOT))
                    .append(' ');
        }
        return builder.toString().trim();
    }

    private boolean matchesIngredient(ItemStack have, ItemStack need, RecipeMatchMode mode) {
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

    private void scheduleCraftPreviewUpdate(Player player, Inventory expectedTop) {
        currentCore().scheduler().runForPlayer(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            Inventory currentTop = player.getOpenInventory().getTopInventory();
            if (currentTop.equals(expectedTop) && currentTop.getHolder() instanceof CraftHolder) {
                refreshCraftPreview(player, currentTop);
            }
        });
    }

    private int clampPage(int requested, int maxPage) {
        if (requested < 0) {
            return 0;
        }
        return Math.min(requested, maxPage);
    }

    private int recipeListSlotIndex(int rawSlot) {
        for (int index = 0; index < RECIPE_LIST_SLOTS.length; index++) {
            if (RECIPE_LIST_SLOTS[index] == rawSlot) {
                return index;
            }
        }
        return -1;
    }

    private void startPrompt(Player player, PromptType type, String recipeId, int returnPage, String hint) {
        startPrompt(player, type, recipeId, returnPage, hint, false);
    }

    private void startPrompt(Player player, PromptType type, String recipeId, int returnPage, String hint, boolean returnToCraft) {
        saveOpenEditorBeforePrompt(player, recipeId);
        ChatPrompt prompt = new ChatPrompt(type, recipeId, returnPage, returnToCraft);
        textFallback.rememberHint(player, hint);
        boolean openedNative = EditorDialogInputs.openTextFromInventory(
                plugin,
                currentCore().inventoryCloseSuppressor(),
                dialogService(),
                player,
                inputRequestFor(player, prompt),
                input -> handleChatPrompt(player, prompt, input),
                () -> cancelPrompt(player, prompt)
        );
        if (openedNative) {
            textFallback.clearHint(player);
            plugin.getEditorSounds().open(player);
        }
    }

    private void saveOpenEditorBeforePrompt(Player player, String recipeId) {
        if (recipeId == null) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof AdminRecipeItemsHolder holder && holder.recipeId.equals(recipeId)) {
            autoSaveRecipeItems(top, holder.recipeId);
        }
    }

    private TextDialogRequest inputRequestFor(Player player, ChatPrompt prompt) {
        CustomRecipe recipe = prompt.recipeId == null ? null : recipeManager.getRecipe(prompt.recipeId).orElse(null);
        String current = currentPromptValue(player, prompt.type, recipe);
        if (current == null) {
            current = "";
        }
        String title = promptTitle(prompt.type);
        String format = promptFormat(prompt.type);
        Map<String, String> replacements = Map.of(
                "setting", title,
                "current", current,
                "format", format
        );

        if (prompt.type == PromptType.PLAYER_SEARCH || prompt.type == PromptType.ADMIN_SEARCH) {
            TextDialogRequest request = textDialogs.request("search", searchDialogRequest("{current}", "{format}"), replacements);
            return formatRequest(request, Map.of());
        }
        return formatRequest(editorDialogRequest(prompt.type), replacements);
    }

    private TextDialogRequest searchDialogRequest(String current, String format) {
        return new TextDialogRequest(
                "Search",
                List.of(
                        "{muted}Current query: {theme}{current}",
                        "{muted}Expected input: {white}{format}"
                ),
                "{white}Search query",
                current,
                format,
                DialogButton.search(),
                DialogButton.cancel(),
                300,
                300,
                128,
                true,
                true,
                false
        );
    }

    private TextDialogRequest editorDialogRequest(PromptType type) {
        List<String> body = List.of(
                "{theme}{setting}",
                "{muted}Current value: {theme}{current}",
                "{muted}Expected input: {white}{format}"
        );
        return switch (type) {
            case EDIT_COST -> new TextDialogRequest(
                    "{theme}{setting}",
                    body,
                    "{white}XP levels, XP points, money",
                    "{current}",
                    "1 25 100",
                    DialogButton.save(),
                    DialogButton.cancel(),
                    320,
                    300,
                    64,
                    true,
                    true,
                    false
            );
            case CREATE_RECIPE_NAME -> new TextDialogRequest(
                    "{theme}{setting}",
                    List.of(
                            "{muted}Expected input: {white}{format}",
                            "{muted}The recipe ID is generated from this name."
                    ),
                    "{white}Recipe name",
                    "",
                    "Emerald Hammer",
                    DialogButton.confirm("Create Recipe"),
                    DialogButton.cancel(),
                    320,
                    300,
                    128,
                    true,
                    true,
                    false
            );
            case EDIT_COMMANDS -> TextDialogRequest.command(body, "{current}", "console:give {player} diamond 1 | player:say Thanks");
            case EDIT_NAME -> TextDialogRequest.text(body, "{current}", "Emerald Hammer");
            case EDIT_PERMISSION -> TextDialogRequest.text(body, "{current}", "focrafts.recipe.example");
            case EDIT_DESCRIPTION -> TextDialogRequest.text(body, "{current}", "Crafted from rare materials");
            case PLAYER_SEARCH, ADMIN_SEARCH -> searchDialogRequest("{current}", "{format}");
        };
    }

    private TextDialogRequest formatRequest(TextDialogRequest request, Map<String, String> replacements) {
        return new TextDialogRequest(
                messages.renderTemplate(request.title(), replacements),
                request.body().stream().map(line -> messages.renderTemplate(line, replacements)).toList(),
                messages.renderTemplate(request.fieldLabel(), replacements),
                applyDialogPlaceholders(request.initialValue(), replacements),
                applyDialogPlaceholders(request.placeholder(), replacements),
                formatButton(request.submitButton(), replacements),
                formatButton(request.cancelButton(), replacements),
                request.bodyWidth(),
                request.inputWidth(),
                request.maxLength(),
                request.labelVisible(),
                request.canCloseWithEscape(),
                request.pause()
        );
    }

    private DialogButton formatButton(DialogButton button, Map<String, String> replacements) {
        return new DialogButton(
                messages.renderTemplate(button.label(), replacements),
                messages.renderTemplate(button.tooltip(), replacements),
                button.width(),
                button.icon()
        );
    }

    private String applyDialogPlaceholders(String value, Map<String, String> replacements) {
        return CommandPlaceholders.apply(value, replacements);
    }

    private String promptTitle(PromptType type) {
        return switch (type) {
            case CREATE_RECIPE_NAME -> "Create Recipe";
            case PLAYER_SEARCH -> "Recipe Search";
            case ADMIN_SEARCH -> "Admin Recipe Search";
            case EDIT_NAME -> "Recipe Name";
            case EDIT_PERMISSION -> "Recipe Permission";
            case EDIT_DESCRIPTION -> "Recipe Description";
            case EDIT_COST -> "Recipe Costs";
            case EDIT_COMMANDS -> "On-Craft Commands";
        };
    }

    private String promptFormat(PromptType type) {
        return switch (type) {
            case CREATE_RECIPE_NAME -> "Non-empty recipe display name";
            case PLAYER_SEARCH, ADMIN_SEARCH -> "Any search text";
            case EDIT_NAME -> "Non-empty recipe display name";
            case EDIT_PERMISSION -> "Permission node or none";
            case EDIT_DESCRIPTION -> "Description text or none";
            case EDIT_COST -> "<xp_levels> <xp_points> <vault_money>";
            case EDIT_COMMANDS -> "Commands split by |, or none";
        };
    }

    private String currentPromptValue(Player player, PromptType type, CustomRecipe recipe) {
        return switch (type) {
            case CREATE_RECIPE_NAME -> "";
            case PLAYER_SEARCH -> getPlayerRecipeState(player).search;
            case ADMIN_SEARCH -> getAdminListState(player).search;
            case EDIT_NAME -> recipe == null ? "" : recipe.getDisplayName();
            case EDIT_PERMISSION -> emptyAsNone(recipe == null ? "" : recipe.getPermission());
            case EDIT_DESCRIPTION -> emptyAsNone(recipe == null ? "" : recipe.getDescription());
            case EDIT_COST -> recipe == null ? "" : formatCostPromptValue(recipe.getCost());
            case EDIT_COMMANDS -> listAsPromptValue(recipe == null ? List.of() : recipe.getCraftCommands(), " | ");
        };
    }

    private String formatCostPromptValue(RecipeCost cost) {
        double money = cost.getVaultMoney();
        String moneyValue = money == Math.rint(money) ? String.valueOf((long) money) : Double.toString(money);
        return cost.getXpLevels() + " " + cost.getXpPoints() + " " + moneyValue;
    }

    private String emptyAsNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private String listAsPromptValue(List<String> values, String delimiter) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return String.join(delimiter, values);
    }

    private void cancelPrompt(Player player, ChatPrompt prompt) {
        plugin.getEditorSounds().back(player);
        messages.send(player, "chat-prompt-cancelled");
        reopenPromptOrigin(player, prompt);
    }

    private void handleChatPrompt(Player player, ChatPrompt prompt, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            cancelPrompt(player, prompt);
            return;
        }

        switch (prompt.type) {
            case PLAYER_SEARCH -> {
                PlayerRecipeState state = getPlayerRecipeState(player);
                state.search = input.trim();
                openRecipeListGui(player, 0, prompt.returnToCraft);
            }
            case ADMIN_SEARCH -> {
                AdminListState state = getAdminListState(player);
                state.search = input.trim();
                openAdminListGui(player, 0);
            }
            case CREATE_RECIPE_NAME -> createRecipeFromPrompt(player, prompt, input);
            case EDIT_NAME -> handleNamePrompt(player, prompt, input);
            case EDIT_PERMISSION -> handlePermissionPrompt(player, prompt, input);
            case EDIT_DESCRIPTION -> handleDescriptionPrompt(player, prompt, input);
            case EDIT_COST -> handleCostPrompt(player, prompt, input);
            case EDIT_COMMANDS -> handleCommandsPrompt(player, prompt, input);
        }
    }

    private void createRecipeFromPrompt(Player player, ChatPrompt prompt, String input) {
        String name = input.trim();
        if (name.isBlank()) {
            plugin.getEditorSounds().error(player);
            messages.send(player, "chat-prompt-invalid");
            reopenPromptOrigin(player, prompt);
            return;
        }

        String recipeId = recipeManager.generateRecipeId(name);
        CustomRecipe recipe = new CustomRecipe(
                recipeId,
                name,
                true,
                RecipeType.SHAPED,
                null,
                new LinkedHashMap<>()
        );
        recipeManager.upsert(recipe);
        plugin.getEditorSounds().add(player);
        messages.send(player, "admin-created", Map.of("id", recipeId));
        openAdminEditorGui(player, recipeId, prompt.returnPage);
    }

    private void handleNamePrompt(Player player, ChatPrompt prompt, String input) {
        String name = input.trim();
        if (name.isBlank()) {
            plugin.getEditorSounds().error(player);
            messages.send(player, "chat-prompt-invalid");
            reopenPromptOrigin(player, prompt);
            return;
        }

        Optional<CustomRecipe> renamed = recipeManager.renameRecipe(prompt.recipeId, name);
        if (renamed.isEmpty()) {
            plugin.getEditorSounds().error(player);
            messages.send(player, "recipe-missing");
            return;
        }

        CustomRecipe recipe = renamed.get();
        plugin.getEditorSounds().save(player);
        messages.send(player, "admin-name-updated", Map.of("id", recipe.getId()));
        refreshOpenEditorIfMatching(player, recipe, prompt);
    }

    private void updateRecipeTextField(Player player, ChatPrompt prompt, String input, RecipeTextUpdater updater, String successKey) {
        Optional<CustomRecipe> optional = recipeManager.getRecipe(prompt.recipeId);
        if (optional.isEmpty()) {
            plugin.getEditorSounds().error(player);
            messages.send(player, "recipe-missing");
            return;
        }
        String value = input.trim();
        if (value.isBlank()) {
            plugin.getEditorSounds().error(player);
            messages.send(player, "chat-prompt-invalid");
            reopenPromptOrigin(player, prompt);
            return;
        }
        CustomRecipe recipe = optional.get();
        updater.update(recipe, value);
        recipeManager.upsert(recipe);
        plugin.getEditorSounds().save(player);
        messages.send(player, successKey);
        refreshOpenEditorIfMatching(player, recipe, prompt);
    }

    private void handlePermissionPrompt(Player player, ChatPrompt prompt, String input) {
        Optional<CustomRecipe> optional = recipeManager.getRecipe(prompt.recipeId);
        if (optional.isEmpty()) {
            plugin.getEditorSounds().error(player);
            messages.send(player, "recipe-missing");
            return;
        }
        String value = input.trim();
        if (value.equalsIgnoreCase("none")) {
            value = "";
        }
        CustomRecipe recipe = optional.get();
        recipe.setPermission(value);
        recipeManager.upsert(recipe);
        plugin.getEditorSounds().save(player);
        messages.send(player, "admin-permission-updated");
        refreshOpenEditorIfMatching(player, recipe, prompt);
    }

    private void handleDescriptionPrompt(Player player, ChatPrompt prompt, String input) {
        Optional<CustomRecipe> optional = recipeManager.getRecipe(prompt.recipeId);
        if (optional.isEmpty()) {
            plugin.getEditorSounds().error(player);
            messages.send(player, "recipe-missing");
            return;
        }
        String value = input.trim();
        if (value.equalsIgnoreCase("none")) {
            value = "";
        }
        CustomRecipe recipe = optional.get();
        recipe.setDescription(value);
        recipeManager.upsert(recipe);
        plugin.getEditorSounds().save(player);
        messages.send(player, "admin-description-updated");
        refreshOpenEditorIfMatching(player, recipe, prompt);
    }

    private void handleCostPrompt(Player player, ChatPrompt prompt, String input) {
        Optional<CustomRecipe> optional = recipeManager.getRecipe(prompt.recipeId);
        if (optional.isEmpty()) {
            plugin.getEditorSounds().error(player);
            messages.send(player, "recipe-missing");
            return;
        }

        String[] split = input.trim().split("\\s+");
        if (split.length != 3) {
            plugin.getEditorSounds().error(player);
            messages.send(player, "chat-prompt-invalid");
            reopenPromptOrigin(player, prompt);
            return;
        }

        try {
            int levels = Integer.parseInt(split[0]);
            int points = Integer.parseInt(split[1]);
            double money = Double.parseDouble(split[2]);
            if (levels < 0 || points < 0 || money < 0) {
                plugin.getEditorSounds().error(player);
                messages.send(player, "chat-prompt-invalid");
                reopenPromptOrigin(player, prompt);
                return;
            }

            CustomRecipe recipe = optional.get();
            recipe.setCost(new RecipeCost(levels, points, money));
            recipeManager.upsert(recipe);
            plugin.getEditorSounds().save(player);
            messages.send(player, "admin-cost-updated");
            refreshOpenEditorIfMatching(player, recipe, prompt);
        } catch (NumberFormatException ignored) {
            plugin.getEditorSounds().error(player);
            messages.send(player, "chat-prompt-invalid");
            reopenPromptOrigin(player, prompt);
        }
    }

    private void handleCommandsPrompt(Player player, ChatPrompt prompt, String input) {
        Optional<CustomRecipe> optional = recipeManager.getRecipe(prompt.recipeId);
        if (optional.isEmpty()) {
            plugin.getEditorSounds().error(player);
            messages.send(player, "recipe-missing");
            return;
        }

        List<String> commands = new ArrayList<>();
        String trimmed = input.trim();
        if (!trimmed.equalsIgnoreCase("none")) {
            String[] split = trimmed.split("\\|");
            for (String piece : split) {
                String command = piece.trim();
                if (command.isBlank()) {
                    continue;
                }
                if (!command.regionMatches(true, 0, "console:", 0, "console:".length())
                        && !command.regionMatches(true, 0, "player:", 0, "player:".length())) {
                    command = "console:" + command;
                }
                commands.add(command);
            }
        }

        CustomRecipe recipe = optional.get();
        recipe.setCraftCommands(commands);
        recipeManager.upsert(recipe);
        plugin.getEditorSounds().save(player);
        messages.send(player, "admin-commands-updated", Map.of("amount", String.valueOf(commands.size())));
        refreshOpenEditorIfMatching(player, recipe, prompt);
    }

    private void reopenPromptOrigin(Player player, ChatPrompt prompt) {
        switch (prompt.type) {
            case CREATE_RECIPE_NAME -> openAdminListGui(player, Math.max(0, prompt.returnPage));
            case PLAYER_SEARCH -> openRecipeListGui(player, Math.max(0, prompt.returnPage), prompt.returnToCraft);
            case ADMIN_SEARCH -> openAdminListGui(player, Math.max(0, prompt.returnPage));
            default -> {
                if (prompt.recipeId != null) {
                    openAdminEditorGui(player, prompt.recipeId, prompt.returnPage);
                }
            }
        }
    }

    private void refreshOpenEditorIfMatching(Player player, CustomRecipe recipe, ChatPrompt prompt) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof AdminEditorHolder holder && holder.recipeId.equals(recipe.getId())) {
            refreshAdminEditorMeta(player, top, recipe);
            return;
        }
        openAdminEditorGui(player, recipe.getId(), prompt.returnPage);
    }

    private List<String> sortLore(SortMode selected) {
        List<String> options = new ArrayList<>();
        for (SortMode mode : SortMode.values()) {
            options.add(mode.displayName);
        }
        return optionLore(selected == null ? "" : selected.displayName, options);
    }

    private List<String> recipeTypeLore(RecipeType selected) {
        return optionLore(recipeTypeLabel(selected), List.of(
                recipeTypeLabel(RecipeType.SHAPED),
                recipeTypeLabel(RecipeType.SHAPELESS)
        ));
    }

    private List<String> matchModeLore(RecipeMatchMode selected) {
        return optionLore(matchModeLabel(selected), List.of(
                matchModeLabel(RecipeMatchMode.MATERIAL_ONLY),
                matchModeLabel(RecipeMatchMode.MATERIAL_META),
                matchModeLabel(RecipeMatchMode.STRICT_ALL)
        ));
    }

    private List<String> optionLore(String selected, List<String> options) {
        if (options == null || options.isEmpty()) {
            return List.of("{bad}No options configured.");
        }

        String safeSelected = selected == null ? "" : selected;
        List<String> lore = new ArrayList<>();
        for (String option : options) {
            String label = option == null ? "" : option;
            boolean isSelected = label.equalsIgnoreCase(safeSelected);
            lore.add((isSelected ? "{theme}» " : "{white}• ") + label);
        }
        return lore;
    }

    private String recipeTypeLabel(RecipeType type) {
        return switch (type == null ? RecipeType.SHAPED : type) {
            case SHAPED -> "Shaped";
            case SHAPELESS -> "Shapeless";
        };
    }

    private String matchModeLabel(RecipeMatchMode mode) {
        return switch (mode == null ? RecipeMatchMode.MATERIAL_META : mode) {
            case MATERIAL_ONLY -> "Material Only";
            case MATERIAL_META -> "Material + Meta";
            case STRICT_ALL -> "Strict All";
        };
    }

    private <T> List<T> cropList(List<T> values, int max) {
        if (values.size() <= max) {
            return values;
        }
        return values.subList(0, max);
    }

    private List<CustomRecipe> getFilteredPlayerRecipes(Player player, PlayerRecipeState state) {
        List<CustomRecipe> base = new ArrayList<>();
        boolean showLocked = plugin.getConfig().getBoolean("recipes.show-locked", true);
        String query = state.search.toLowerCase(Locale.ROOT);

        for (CustomRecipe recipe : recipeManager.getEnabledRecipes()) {
            boolean hasPerm = hasRecipePermission(player, recipe);
            if (!hasPerm && !showLocked) {
                continue;
            }
            if (!query.isBlank()) {
                String haystack = (recipe.getId() + " " + recipe.getDisplayName()).toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) {
                    continue;
                }
            }
            base.add(recipe);
        }

        base.sort(state.sort.comparator());
        return base;
    }

    private List<CustomRecipe> getFilteredAdminRecipes(AdminListState state) {
        List<CustomRecipe> base = new ArrayList<>();
        String query = state.search.toLowerCase(Locale.ROOT);
        for (CustomRecipe recipe : recipeManager.getAllRecipes()) {
            if (!query.isBlank()) {
                String haystack = (recipe.getId() + " " + recipe.getDisplayName() + " " + recipe.getPermission())
                        .toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) {
                    continue;
                }
            }
            base.add(recipe);
        }
        base.sort(state.sort.comparator());
        return base;
    }

    private PlayerRecipeState getPlayerRecipeState(Player player) {
        return playerRecipeStates.computeIfAbsent(player.getUniqueId(), ignored -> {
            FileConfiguration config = plugin.getConfig();
            String defaultSort = config.getString("recipes.default-sort", "NAME_ASC");
            return new PlayerRecipeState("", SortMode.from(defaultSort));
        });
    }

    private AdminListState getAdminListState(Player player) {
        return adminListStates.computeIfAbsent(player.getUniqueId(), ignored ->
                new AdminListState("", SortMode.NAME_ASC)
        );
    }

    private RecipeMatchMode nextMatchMode(RecipeMatchMode mode) {
        return switch (mode) {
            case MATERIAL_ONLY -> RecipeMatchMode.MATERIAL_META;
            case MATERIAL_META -> RecipeMatchMode.STRICT_ALL;
            case STRICT_ALL -> RecipeMatchMode.MATERIAL_ONLY;
        };
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private String formatCost(RecipeCost cost) {
        if (cost == null || !cost.hasAnyCost()) {
            return "none";
        }
        List<String> parts = new ArrayList<>();
        if (cost.getXpLevels() > 0) {
            parts.add(cost.getXpLevels() + " levels");
        }
        if (cost.getXpPoints() > 0) {
            parts.add(cost.getXpPoints() + " xp");
        }
        if (cost.getVaultMoney() > 0.0D) {
            parts.add("$" + moneyFormat.format(cost.getVaultMoney()));
        }
        return String.join(", ", parts);
    }

    private String styleTitle(String fallback) {
        return GuiTitles.format(fallback);
    }

    private String color(String input) {
        return messages.renderTemplate(input, Map.of());
    }

    private static abstract class BaseHolder implements InventoryHolder {
        protected Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private static final class CraftHolder extends BaseHolder {
        private final CustomCraftingGui gui;

        private CraftHolder(CustomCraftingGui gui) {
            this.gui = gui;
        }
    }

    private static final class RecipeListHolder extends BaseHolder {
        private final int page;
        private final String search;
        private final SortMode sort;
        private final boolean returnToCraft;
        private final CustomRecipesGui gui;

        private RecipeListHolder(int page, String search, SortMode sort, boolean returnToCraft, CustomRecipesGui gui) {
            this.page = page;
            this.search = search;
            this.sort = sort;
            this.returnToCraft = returnToCraft;
            this.gui = gui;
        }
    }

    private static final class RecipePreviewHolder extends BaseHolder {
        private final int returnPage;
        private final boolean returnToCraft;
        private final RecipePreviewGui gui;

        private RecipePreviewHolder(int returnPage, boolean returnToCraft, RecipePreviewGui gui) {
            this.returnPage = returnPage;
            this.returnToCraft = returnToCraft;
            this.gui = gui;
        }
    }

    private static final class AdminListHolder extends BaseHolder {
        private final int page;
        private final String search;
        private final SortMode sort;

        private AdminListHolder(int page, String search, SortMode sort) {
            this.page = page;
            this.search = search;
            this.sort = sort;
        }
    }

    private static final class DeleteConfirmHolder extends BaseHolder {
        private final String recipeId;
        private final int returnPage;
        private final Inventory editorInventory;

        private DeleteConfirmHolder(String recipeId, int returnPage, Inventory editorInventory) {
            this.recipeId = recipeId;
            this.returnPage = returnPage;
            this.editorInventory = editorInventory;
        }
    }

    private static final class AdminEditorHolder extends BaseHolder {
        private final String recipeId;
        private final int returnPage;

        private AdminEditorHolder(String recipeId, int returnPage) {
            this.recipeId = recipeId;
            this.returnPage = returnPage;
        }
    }

    private static final class AdminRecipeItemsHolder extends BaseHolder {
        private final String recipeId;
        private final int returnPage;

        private AdminRecipeItemsHolder(String recipeId, int returnPage) {
            this.recipeId = recipeId;
            this.returnPage = returnPage;
        }
    }

    private record CraftAttempt(int crafted, String messageKey, Map<String, String> placeholders) {
    }

    private record CostCheck(boolean ok, String messageKey, Map<String, String> placeholders) {
        static CostCheck success() {
            return new CostCheck(true, null, Map.of());
        }
    }

    private record ChatPrompt(PromptType type, String recipeId, int returnPage, boolean returnToCraft) {
    }

    private record WorldSelectionSession(String recipeId, int returnPage) {
    }

    private enum PromptType {
        CREATE_RECIPE_NAME,
        PLAYER_SEARCH,
        ADMIN_SEARCH,
        EDIT_NAME,
        EDIT_PERMISSION,
        EDIT_DESCRIPTION,
        EDIT_COST,
        EDIT_COMMANDS
    }

    private static final class PlayerRecipeState {
        private String search;
        private SortMode sort;

        private PlayerRecipeState(String search, SortMode sort) {
            this.search = search == null ? "" : search;
            this.sort = sort == null ? SortMode.NAME_ASC : sort;
        }
    }

    private static final class AdminListState {
        private String search;
        private SortMode sort;

        private AdminListState(String search, SortMode sort) {
            this.search = search == null ? "" : search;
            this.sort = sort == null ? SortMode.NAME_ASC : sort;
        }
    }

    @FunctionalInterface
    private interface RecipeTextUpdater {
        void update(CustomRecipe recipe, String value);
    }

    private enum SortMode {
        NAME_ASC("Name A-Z"),
        NAME_DESC("Name Z-A");

        private final String displayName;

        SortMode(String displayName) {
            this.displayName = displayName;
        }

        private SortMode next() {
            return switch (this) {
                case NAME_ASC -> NAME_DESC;
                case NAME_DESC -> NAME_ASC;
            };
        }

        private Comparator<CustomRecipe> comparator() {
            return switch (this) {
                case NAME_ASC -> Comparator.comparing(CustomRecipe::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CustomRecipe::getId, String.CASE_INSENSITIVE_ORDER);
                case NAME_DESC -> Comparator.comparing(CustomRecipe::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                        .reversed()
                        .thenComparing(CustomRecipe::getId, String.CASE_INSENSITIVE_ORDER);
            };
        }

        private static SortMode from(String input) {
            if (input == null || input.isBlank()) {
                return NAME_ASC;
            }
            try {
                return SortMode.valueOf(input.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return NAME_ASC;
            }
        }
    }
}
