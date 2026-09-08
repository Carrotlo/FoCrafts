package me.foesio.foCrafts;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.message.FoStyle;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foCrafts.command.FoCraftsCommand;
import me.foesio.foCrafts.config.GuiConfig;
import me.foesio.foCrafts.gui.GuiManager;
import me.foesio.foCrafts.recipe.RecipeManager;
import me.foesio.foCrafts.util.VaultHook;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoCrafts extends JavaPlugin {
    private static final String MODRINTH_PROJECT_ID = "focrafts";
    private static final int BSTATS_PLUGIN_ID = 33054;

    private FoCoreContext core;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;
    private FoGuiSounds guiSounds;
    private UpdateNoticeService updateNotices;
    private RecipeManager recipeManager;
    private FoMessageService messageService;
    private GuiConfig guiConfig;
    private GuiManager guiManager;
    private VaultHook vaultHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        applyNativeDialogDefaults();

        refreshCoreContext();
        this.messageService = FoMessageService.load(this, messageMigrations());
        migrateSprites();
        this.updateNotices = core.createUpdateNotices(messageService, MODRINTH_PROJECT_ID, adminSounds).start();
        this.recipeManager = new RecipeManager(this);
        this.recipeManager.load();
        this.vaultHook = new VaultHook(this);
        this.guiConfig = new GuiConfig(this);
        this.guiConfig.load();
        this.guiManager = new GuiManager(this, recipeManager, messageService, guiConfig, vaultHook, this::getCore);

        FoCraftsCommand commandHandler = new FoCraftsCommand(this, guiManager, messageService, updateNotices);
        registerCommand("craft", commandHandler);
        registerCommand("recipes", commandHandler);
        registerCommand("focraftsadmin", commandHandler);

        getServer().getPluginManager().registerEvents(guiManager, this);
        getLogger().info("FoCrafts enabled with " + recipeManager.getAllRecipes().size() + " recipes.");
    }

    @Override
    public void onDisable() {
        if (recipeManager != null) {
            recipeManager.save();
        }
        if (core != null) {
            core.close();
            core = null;
        }
        updateNotices = null;
    }

    public boolean reloadPluginData() {
        FoReloadResult result = FoReloadRegistry.create()
                .addConfig(this)
                .add("native dialog defaults", this::applyNativeDialogDefaults)
                .add("core", this::refreshCoreContext)
                .addMessages(messageService)
                .add("guis", guiConfig::load)
                .add("recipes", recipeManager::load)
                .add("vault", vaultHook::refresh)
                .add("gui runtime", guiManager::clearRuntimeState)
                .reload();
        if (!result.successful()) {
            getLogger().severe("Failed to reload FoCrafts at " + result.failedStep() + ": " + result.errorMessage());
        }
        return result.successful();
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public FoCoreContext getCore() {
        return core;
    }

    public FoGuiSounds getGuiSounds() {
        return guiSounds;
    }

    public FoSoundService getSounds() {
        return sounds;
    }

    public FoEditorSounds getEditorSounds() {
        return editorSounds;
    }

    public FoAdminSounds getAdminSounds() {
        return adminSounds;
    }

    private void registerCommand(String commandName, FoCraftsCommand handler) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            getLogger().warning("Command '" + commandName + "' is missing in plugin.yml");
            return;
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    private void applyNativeDialogDefaults() {
        NativeDialogConfigDefaults.addDefaults(this);
        saveConfig();
    }

    private void refreshCoreContext() {
        if (core != null) {
            core.close();
        }
        core = FoPluginCore.create(this);
        sounds = core.createSounds();
        adminSounds = FoAdminSounds.create(sounds);
        editorSounds = FoEditorSounds.create(sounds);
        guiSounds = FoGuiSounds.create(sounds);
        core.metrics(BSTATS_PLUGIN_ID);
        core.warnIfNativeDialogsUnavailable();
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .add(this::migrateLegacyMessageStyle)
                .build();
    }

    private void migrateSprites() {
        messageService.migrateToVersion(core.migrations(), 1, config -> {
            boolean changed = false;
            changed |= FoMessageService.addMissingToken(config, "tokens.prefix", ":crafting_table:", null);
            changed |= FoMessageService.addMissingToken(config, "reload-success", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "reload-failed", ":redstone:");
            changed |= FoMessageService.addMissingToken(config, "craft-success", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "craft-cost-failed", ":redstone:");
            changed |= FoMessageService.addMissingToken(config, "admin-created", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "admin-deleted", ":lava_bucket:");
            changed |= FoMessageService.addMissingToken(config, "admin-unsaved-warning", ":redstone:");
            return true;
        });
    }

    private boolean migrateLegacyMessageStyle(FileConfiguration messages) {
        boolean changed = false;
        String legacyPrefix = messages.getString("prefix");
        if (legacyPrefix != null) {
            messages.set("tokens.prefix", legacyPrefix);
            messages.set("prefix", null);
            changed = true;
        }
        changed |= migrateConfigStyleToken(messages, "theme", "style.theme", FoStyle.THEME);
        changed |= migrateConfigStyleToken(messages, "muted", "style.muted", FoStyle.MUTED);
        changed |= migrateConfigStyleToken(messages, "white", "style.white", FoStyle.WHITE);
        changed |= migrateConfigStyleToken(messages, "good", "style.good", FoStyle.GOOD);
        changed |= migrateConfigStyleToken(messages, "bad", "style.bad", FoStyle.BAD);
        return changed;
    }

    private boolean migrateConfigStyleToken(FileConfiguration messages, String token, String configPath, String defaultValue) {
        String configured = getConfig().getString(configPath);
        if (configured == null || configured.isBlank()) {
            return false;
        }
        String messagePath = "tokens." + token;
        String current = messages.getString(messagePath);
        if (current != null && !current.equals(defaultValue)) {
            return false;
        }
        messages.set(messagePath, configured);
        return !configured.equals(current);
    }
}
