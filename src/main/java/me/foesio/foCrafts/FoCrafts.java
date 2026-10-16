package me.foesio.foCrafts;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.economy.VaultEconomyBridge;
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
import me.foesio.foCrafts.gui.GuiManager;
import me.foesio.foCrafts.gui.PublicGuiOwnerMigration;
import me.foesio.foCrafts.recipe.RecipeManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
    private GuiManager guiManager;
    private VaultEconomyBridge vaultEconomy;

    @Override
    public void onEnable() {
        boolean freshInstall = !new File(getDataFolder(), "config.yml").exists();
        saveDefaultConfig();
        PublicGuiOwnerMigration publicGuiMigration = new PublicGuiOwnerMigration(this);
        boolean publicGuiReady = freshInstall && publicGuiMigration.hasNoInstalledOwnerFiles()
                ? publicGuiMigration.installFreshDefaults()
                : publicGuiMigration.migrateInstalled();

        refreshCoreContext();
        if (freshInstall && !new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        this.messageService = FoMessageService.loadPreservingRemovals(this, FoMessageMigrations.none());
        if (publicGuiReady) migratePublicMessageFields(freshInstall);
        this.updateNotices = core.createUpdateNotices(messageService, MODRINTH_PROJECT_ID, adminSounds).start();
        this.recipeManager = new RecipeManager(this);
        this.recipeManager.load();
        this.vaultEconomy = core.createVaultEconomy();
        this.guiManager = new GuiManager(this, recipeManager, messageService, vaultEconomy,
                this::getCore);

        FoCraftsCommand commandHandler = new FoCraftsCommand(this, guiManager, messageService, updateNotices);
        registerCommand("craft", commandHandler);
        registerCommand("recipes", commandHandler);
        registerCommand("focraftsadmin", commandHandler);

        getLogger().info("FoCrafts enabled with " + recipeManager.getAllRecipes().size() + " recipes.");
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.closePublicGuis();
        }
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
                .add("public GUI validation", guiManager::validatePublicGuis)
                .add("search dialog validation", guiManager::validateSearchDialog)
                .add("public messages validation", this::validatePublicMessages)
                .add("sounds validation", this::validateSounds)
                .addConfig(this)
                .add("core", this::refreshCoreContext)
                .addMessages(messageService)
                .add("public guis", guiManager::reloadPublicGuis)
                .add("recipes", recipeManager::load)
                .add("vault", () -> vaultEconomy.reload())
                .add("gui runtime", guiManager::clearRuntimeState)
                .reload();
        if (!result.successful()) {
            getLogger().severe("Failed to reload FoCrafts at " + result.failedStep() + ": " + result.errorMessage());
        }
        return result.successful();
    }

    private void validatePublicMessages() throws IOException, org.bukkit.configuration.InvalidConfigurationException {
        validatePublicMessages(getDataFolder().toPath().resolve("messages.yml"));
    }

    static void validatePublicMessages(Path file) throws IOException,
            org.bukkit.configuration.InvalidConfigurationException {
        if (Files.notExists(file)) return; // An owner may remove all optional feedback.
        if (!Files.isRegularFile(file)) throw new IOException("messages.yml is not a regular file");
        YamlConfiguration installed = new YamlConfiguration();
        installed.load(file.toFile());
        if (installed.contains("public-gui") && !installed.isConfigurationSection("public-gui")) {
            throw new IOException("messages.yml:public-gui must be a section");
        }
        for (String token : java.util.List.of("prefix", "theme", "muted", "white", "good", "bad")) {
            String path = "tokens." + token;
            if (installed.contains(path) && !installed.isString(path)) {
                throw new IOException("messages.yml:" + path + " must be text");
            }
        }
        for (String path : java.util.List.of("no-permission", "player-only", "recipe-missing",
                "public-gui-unavailable", "recipe-inserted", "craft-no-match", "craft-success",
                "craft-no-recipe-permission", "craft-world-blocked", "craft-cooldown", "craft-cost-xp",
                "craft-cost-money", "craft-cost-vault-missing", "craft-cost-failed", "search-cleared",
                "chat-prompt-start", "chat-prompt-cancelled", "chat-prompt-invalid",
                "native-dialogs-fallback")) {
            if (installed.contains(path) && !installed.isString(path)) {
                throw new IOException("messages.yml:" + path + " must be text");
            }
        }
        for (String path : installed.getKeys(true)) {
            if (path.startsWith("public-gui.") && !installed.isConfigurationSection(path)
                    && !installed.isString(path)) {
                throw new IOException("messages.yml:" + path + " must be text");
            }
        }
    }

    private void validateSounds() {
        Path file = getDataFolder().toPath().resolve("sounds.yml");
        if (Files.notExists(file)) return; // An owner may disable all configured sounds.
        if (!sounds.validateInstalled()) throw new IllegalStateException("Invalid installed sounds.yml");
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

    private void refreshCoreContext() {
        if (core != null) {
            core.close();
        }
        core = FoPluginCore.create(this);
        if (!new java.io.File(getDataFolder(), "sounds.yml").exists()
                && getConfig().getInt("config-version", 0) < 1) {
            saveResource("sounds.yml", false);
        }
        sounds = FoSoundService.loadPreservingRemovals(this);
        adminSounds = FoAdminSounds.create(sounds);
        editorSounds = FoEditorSounds.create(sounds);
        guiSounds = FoGuiSounds.create(sounds);
        core.metrics(BSTATS_PLUGIN_ID);
        core.warnIfNativeDialogsUnavailable();
    }

    private boolean migrateSprites(FileConfiguration config) {
        boolean changed = migrateLegacyMessageStyle(config);
        try (InputStream stream = getResource("legacy-messages-v0/messages.yml")) {
            if (stream == null) return changed;
            YamlConfiguration prior = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (java.util.Objects.equals(config.getString("tokens.prefix"), prior.getString("prefix")))
                changed |= FoMessageService.addMissingToken(config, "tokens.prefix", ":crafting_table:", null);
            for (String key : java.util.List.of("reload-success", "reload-failed", "craft-success",
                    "craft-cost-failed", "admin-created", "admin-deleted", "admin-unsaved-warning")) {
                if (!java.util.Objects.equals(config.getString(key), prior.getString(key))) continue;
                String sprite = switch (key) {
                    case "reload-success", "craft-success", "admin-created" -> ":emerald:";
                    case "admin-deleted" -> ":lava_bucket:";
                    default -> ":redstone:";
                };
                changed |= FoMessageService.addMissingToken(config, key, sprite);
            }
        } catch (java.io.IOException exception) {
            getLogger().warning("Could not read legacy message defaults: " + exception.getMessage());
        }
        return changed;
    }

    private void migratePublicMessageFields(boolean freshInstall) {
        if (core.migrations().version() >= 2) return;
        int sourceVersion = core.migrations().version();
        Path active = getDataFolder().toPath().resolve("messages.yml");
        if (!Files.isRegularFile(active)) {
            getLogger().severe("Public GUI messages remain pending: installed messages.yml is missing.");
            return;
        }
        try (InputStream stream = getResource("messages.yml")) {
            if (stream == null) return;
            byte[] original = Files.readAllBytes(active);
            if (!freshInstall && !Files.exists(active.resolveSibling("messages.yml.gui-v1-backup"))) {
                Path backup = active.resolveSibling("messages.yml.public-ui-v" + sourceVersion + "-backup");
                if (!Files.exists(backup)) Files.copy(active, backup);
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            boolean migrated = messageService.migrateToVersion(core.migrations(), 2, installed -> {
                boolean changed = sourceVersion < 1 && migrateSprites(installed);
                String oldSuccess = installed.getString("craft-success");
                if (oldSuccess != null && java.util.Set.of(
                        "{prefix}{good}Crafted {theme}{amount}{good} item(s).",
                        "{prefix}:emerald: {good}Crafted {theme}{amount}{good} item(s).",
                        "{prefix}:crafting_table: {good}Crafted {theme}{amount}{good} item(s)."
                ).contains(oldSuccess)) {
                    installed.set("craft-success", bundled.getString("craft-success"));
                    changed = true;
                }
                for (String key : java.util.List.of("no-permission", "player-only", "recipe-missing",
                        "craft-no-match", "craft-success", "craft-no-recipe-permission",
                        "craft-world-blocked", "craft-cooldown", "craft-cost-xp", "craft-cost-money",
                        "craft-cost-vault-missing", "craft-cost-failed", "search-cleared",
                        "chat-prompt-start", "chat-prompt-cancelled", "chat-prompt-invalid",
                        "native-dialogs-fallback")) {
                    if (installed.contains(key) && !installed.isString(key) && bundled.isString(key)) {
                        getLogger().warning("Replacing invalid public message " + key + "=" + installed.get(key)
                                + " with bundled default " + bundled.getString(key));
                        installed.set(key, bundled.getString(key));
                        changed = true;
                    }
                }
                for (String path : bundled.getKeys(true)) {
                    if (!path.startsWith("public-gui.") && !path.equals("public-gui-unavailable")) continue;
                    if (bundled.isConfigurationSection(path) || installed.contains(path)) continue;
                    installed.set(path, bundled.get(path));
                    changed = true;
                }
                return changed;
            });
            if (!migrated) {
                Path staged = Files.createTempFile(active.getParent(), ".message-rollback-", ".yml");
                try {
                    Files.write(staged, original);
                    Files.move(staged, active, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(staged);
                }
                messageService.reload();
                getLogger().severe("Public GUI message migration failed; restored the previous owner file.");
            }
        } catch (Exception exception) {
            getLogger().severe("Could not migrate public GUI message fields: " + exception.getMessage());
        }
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
