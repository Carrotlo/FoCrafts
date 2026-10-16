package me.foesio.foCrafts.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublicGuiOwnerMigrationTest {
    private static final List<String> MENUS = List.of("custom-crafting", "custom-recipes", "recipe-preview");

    @TempDir Path directory;

    private InputStream resource(String name) {
        return getClass().getClassLoader().getResourceAsStream(name);
    }

    private byte[] bytes(String name) throws Exception {
        try (InputStream stream = resource(name)) {
            assertNotNull(stream, name);
            return stream.readAllBytes();
        }
    }

    private PublicGuiOwnerMigration migration(AtomicInteger version) {
        return new PublicGuiOwnerMigration(directory, this::resource,
                Logger.getLogger("migration-test"), version::get, version::set);
    }

    @Test
    void freshInstallCopiesExactGeneratedResourcesWithoutBackupsAndIsIdempotent() throws Exception {
        AtomicInteger version = new AtomicInteger();
        PublicGuiOwnerMigration migration = migration(version);
        assertTrue(migration.installFreshDefaults());
        assertEquals(2, version.get());
        for (String name : MENUS) {
            Path installed = directory.resolve("guis/" + name + ".yml");
            assertArrayEquals(bytes("guis/" + name + ".yml"), Files.readAllBytes(installed));
            assertFalse(Files.exists(installed.resolveSibling(name + ".yml.gui-v1-backup")));
        }
        assertArrayEquals(bytes("dialogs/search.yml"), Files.readAllBytes(directory.resolve("dialogs/search.yml")));
        byte[] first = Files.readAllBytes(directory.resolve("guis/custom-crafting.yml"));
        assertTrue(migration.migrateInstalled());
        assertArrayEquals(first, Files.readAllBytes(directory.resolve("guis/custom-crafting.yml")));
    }

    @Test
    void freshInstallVersionFailureRestoresConfigAndCanRetry() throws Exception {
        Path config = directory.resolve("config.yml");
        byte[] initialConfig = "native-dialogs:\n  enabled: true\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(config, initialConfig);
        AtomicInteger version = new AtomicInteger();
        PublicGuiOwnerMigration failing = new PublicGuiOwnerMigration(directory, this::resource,
                Logger.getLogger("fresh-rollback-test"), version::get, next -> {
                    version.set(next);
                    try {
                        Files.writeString(config, "public-gui-schema-version: " + next + "\n");
                    } catch (Exception error) {
                        throw new IllegalStateException(error);
                    }
                    if (next == 2) throw new IllegalStateException("version save failed after writing");
                });
        assertFalse(failing.installFreshDefaults());
        assertEquals(0, version.get());
        assertArrayEquals(initialConfig, Files.readAllBytes(config));
        assertFalse(Files.exists(directory.resolve("config.yml.gui-v1-backup")));
        for (String name : MENUS) assertFalse(Files.exists(directory.resolve("guis/" + name + ".yml")));
        assertFalse(Files.exists(directory.resolve("dialogs/search.yml")));

        assertTrue(migration(version).installFreshDefaults());
        assertEquals(2, version.get());
        assertArrayEquals(initialConfig, Files.readAllBytes(config));
    }

    @Test
    void freshInstallerDoesNotOverwriteAnOwnerGuiWhenConfigIsMissing() throws Exception {
        Path gui = directory.resolve("guis/custom-crafting.yml");
        Files.createDirectories(gui.getParent());
        byte[] ownerBytes = bytes("legacy-gui-v1/custom-crafting.yml");
        Files.write(gui, ownerBytes);
        AtomicInteger version = new AtomicInteger();

        assertFalse(migration(version).hasNoInstalledOwnerFiles());
        assertFalse(migration(version).installFreshDefaults());
        assertArrayEquals(ownerBytes, Files.readAllBytes(gui));
        assertEquals(0, version.get());
        assertFalse(Files.exists(gui.resolveSibling("custom-crafting.yml.gui-v1-backup")));
    }

    @Test
    void released20UnmarkedFilesMigrateWithoutInventingRemovedControlFields() throws Exception {
        Path guiRoot = directory.resolve("guis");
        Files.createDirectories(guiRoot);
        Files.writeString(directory.resolve("config.yml"), "config-version: 1\n");
        Files.write(directory.resolve("messages.yml"), bytes("messages.yml"));
        for (String name : MENUS)
            Files.write(guiRoot.resolve(name + ".yml"), bytes("legacy-gui-v0/" + name + ".yml"));
        Path crafting = guiRoot.resolve("custom-crafting.yml");
        YamlConfiguration owner = YamlConfiguration.loadConfiguration(crafting.toFile());
        owner.set("title", "§bCRAFT MIGRATION TEST");
        owner.set("items.browse-recipes.lore", List.of());
        owner.save(crafting.toFile());
        byte[] original = Files.readAllBytes(crafting);
        AtomicInteger version = new AtomicInteger();

        assertTrue(migration(version).migrateInstalled());
        assertEquals(2, version.get());
        assertArrayEquals(original, Files.readAllBytes(crafting.resolveSibling(
                "custom-crafting.yml.gui-v0-backup")));
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(crafting.toFile());
        assertEquals("§bCRAFT MIGRATION TEST", migrated.getString("title"));
        assertEquals(List.of(), migrated.getStringList("buttons.browse-recipes.lore"));
        YamlConfiguration browser = YamlConfiguration.loadConfiguration(
                guiRoot.resolve("custom-recipes.yml").toFile());
        assertEquals("ARROW", browser.getString("buttons.previous-page.material"));
        assertTrue(browser.getStringList("buttons.sort.lore").contains("&8ʙᴜᴛᴛᴏɴ"));
        assertTrue(migration(version).migrateInstalled());
        assertArrayEquals(original, Files.readAllBytes(crafting.resolveSibling(
                "custom-crafting.yml.gui-v0-backup")));
    }

    @Test
    void installed21FilesCarryOwnerEditsAndExactBackups() throws Exception {
        Path guiRoot = directory.resolve("guis");
        Files.createDirectories(guiRoot);
        byte[] oldConfig = "config-version: 1\nrecipes:\n  default-sort: NAME_ASC\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(directory.resolve("config.yml"), oldConfig);
        Map<String, byte[]> originals = new LinkedHashMap<>();
        for (String name : MENUS) {
            byte[] legacy = bytes("legacy-gui-v1/" + name + ".yml");
            Files.write(guiRoot.resolve(name + ".yml"), legacy);
        }
        YamlConfiguration craft = YamlConfiguration.loadConfiguration(guiRoot.resolve("custom-crafting.yml").toFile());
        craft.set("title", "§bEigen maakmenu");
        craft.set("items.browse-recipes.slot", 48);
        craft.set("items.browse-recipes.lore", List.of());
        craft.set("result-item.lore", null);
        craft.set("result-item.unnamed-name", 3);
        craft.set("labels.cost-none", "gratis");
        craft.set("rows", 9);
        craft.set("items.browse-recipes.amount", 0);
        craft.save(guiRoot.resolve("custom-crafting.yml").toFile());
        YamlConfiguration browser = YamlConfiguration.loadConfiguration(guiRoot.resolve("custom-recipes.yml").toFile());
        browser.set("items.search.material", "NOT_A_MATERIAL");
        browser.set("items.search.name", "§bZoeken");
        browser.set("items.search.flags", List.of("NOT_A_FLAG"));
        browser.set("recipe-item.lore", List.of("§bInfo {description}", "§c{locked}", "§aKlik"));
        browser.set("labels.cost-none", "kosteloos");
        browser.save(guiRoot.resolve("custom-recipes.yml").toFile());
        YamlConfiguration preview = YamlConfiguration.loadConfiguration(guiRoot.resolve("recipe-preview.yml").toFile());
        preview.set("status.locked", 4);
        preview.save(guiRoot.resolve("recipe-preview.yml").toFile());
        for (String name : MENUS) originals.put(name, Files.readAllBytes(guiRoot.resolve(name + ".yml")));
        Path dialog = directory.resolve("dialogs/search.yml");
        Files.createDirectories(dialog.getParent());
        YamlConfiguration oldDialog = new YamlConfiguration();
        oldDialog.loadFromString(new String(bytes("dialogs/search.yml"), java.nio.charset.StandardCharsets.UTF_8));
        for (String added : List.of("placeholder", "confirm-icon", "cancel-icon", "label-visible",
                "can-close-with-escape", "pause")) oldDialog.set(added, null);
        oldDialog.set("title", "§bZoek recepten");
        oldDialog.set("body-width", -1);
        oldDialog.set("body", List.of(4, 5));
        oldDialog.set("confirm-button", null);
        oldDialog.set("confirm-button.label", "§aEcht zoeken");
        oldDialog.set("confirm-button.tooltip", "§bEigen uitleg");
        oldDialog.set("confirm-button.icon", List.of("wrong"));
        oldDialog.set("confirm-button.width", -1);
        oldDialog.save(dialog.toFile());
        byte[] originalDialog = Files.readAllBytes(dialog);
        byte[] oldMessages = bytes("messages.yml");
        Files.write(directory.resolve("messages.yml"), oldMessages);
        AtomicInteger version = new AtomicInteger();
        assertTrue(migration(version).migrateInstalled());
        assertEquals(2, version.get());
        for (String name : MENUS) {
            assertArrayEquals(originals.get(name), Files.readAllBytes(guiRoot.resolve(name + ".yml.gui-v1-backup")));
        }
        assertArrayEquals(oldMessages, Files.readAllBytes(directory.resolve("messages.yml.gui-v1-backup")));
        assertArrayEquals(oldConfig, Files.readAllBytes(directory.resolve("config.yml.gui-v1-backup")));
        assertArrayEquals(originalDialog, Files.readAllBytes(directory.resolve("dialogs/search.yml.gui-v1-backup")));
        YamlConfiguration newDialog = YamlConfiguration.loadConfiguration(dialog.toFile());
        assertEquals("§bZoek recepten", newDialog.getString("title"));
        assertEquals("spyglass", newDialog.getString("confirm-icon"));
        assertEquals("§aEcht zoeken", newDialog.getString("confirm-button.label"));
        assertEquals("§bEigen uitleg", newDialog.getString("confirm-button.tooltip"));
        assertFalse(newDialog.contains("confirm-button.icon"));
        assertFalse(newDialog.contains("confirm-button.width"));
        assertEquals("{format}", newDialog.getString("placeholder"));
        assertEquals(300, newDialog.getInt("body-width"));
        assertEquals(List.of("{muted}Current query: {theme}{current}",
                "{muted}Expected input: {white}{format}"), newDialog.getStringList("body"));
        YamlConfiguration nextCraft = YamlConfiguration.loadConfiguration(guiRoot.resolve("custom-crafting.yml").toFile());
        assertEquals("§bEigen maakmenu", nextCraft.getString("title"));
        assertEquals(6, nextCraft.getInt("rows"));
        assertFalse(nextCraft.contains("buttons.browse-recipes.amount"));
        assertEquals(48, nextCraft.getInt("buttons.browse-recipes.slot"));
        assertEquals(List.of(), nextCraft.getStringList("buttons.browse-recipes.lore"));
        assertEquals("preserve", nextCraft.getString("entries.result.source-item.lore.mode"));
        assertFalse(nextCraft.contains("entries.result.source-item.lore.lines"));
        assertFalse(nextCraft.contains("items"));
        assertFalse(nextCraft.contains("entries.result.source-item.default-name"));
        YamlConfiguration nextBrowser = YamlConfiguration.loadConfiguration(guiRoot.resolve("custom-recipes.yml").toFile());
        assertEquals("NAME_TAG", nextBrowser.getString("buttons.search.material"));
        assertEquals("§bZoeken", nextBrowser.getString("buttons.search.name"));
        assertFalse(nextBrowser.contains("buttons.search.flags"));
        assertEquals(List.of("§aKlik"), nextBrowser.getStringList("entries.recipes.source-item.lore-states.plain.lines"));
        YamlConfiguration nextMessages = YamlConfiguration.loadConfiguration(directory.resolve("messages.yml").toFile());
        assertEquals("gratis", nextMessages.getString("public-gui.menu.custom-crafting.cost-none"));
        assertEquals("kosteloos", nextMessages.getString("public-gui.menu.custom-recipes.cost-none"));
        assertEquals("{bad}Locked", nextMessages.getString("public-gui.menu.recipe-preview.status-locked"));
        newDialog.set("confirm-icon", null);
        newDialog.save(dialog.toFile());
        assertTrue(migration(version).migrateInstalled());
        assertFalse(YamlConfiguration.loadConfiguration(dialog.toFile()).contains("confirm-icon"));
        assertArrayEquals(originals.get("custom-crafting"), Files.readAllBytes(guiRoot.resolve("custom-crafting.yml.gui-v1-backup")));
    }

    @Test
    void removed21GuiDirectoryIsNotRecreated() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "config-version: 1\n");
        AtomicInteger version = new AtomicInteger();
        PublicGuiOwnerMigration migration = new PublicGuiOwnerMigration(directory, this::resource,
                Logger.getLogger("removed-gui-test"), version::get, version::set, () -> 1);
        assertFalse(migration.migrateInstalled());
        assertEquals(0, version.get());
        assertFalse(Files.exists(directory.resolve("guis")));
    }

    @Test
    void failedVersionCommitRestoresEveryActiveFile() throws Exception {
        Path guiRoot = directory.resolve("guis");
        Files.createDirectories(guiRoot);
        Map<Path, byte[]> before = new LinkedHashMap<>();
        Path config = directory.resolve("config.yml");
        Files.writeString(config, "config-version: 1\n");
        before.put(config, Files.readAllBytes(config));
        for (String name : MENUS) {
            Path file = guiRoot.resolve(name + ".yml");
            Files.write(file, bytes("legacy-gui-v1/" + name + ".yml"));
            before.put(file, Files.readAllBytes(file));
        }
        Path messages = directory.resolve("messages.yml");
        Files.write(messages, bytes("messages.yml"));
        before.put(messages, Files.readAllBytes(messages));
        PublicGuiOwnerMigration migration = new PublicGuiOwnerMigration(directory, this::resource,
                Logger.getLogger("rollback-test"), () -> 0, ignored -> { throw new IllegalStateException("save failed"); });
        assertFalse(migration.migrateInstalled());
        for (Map.Entry<Path, byte[]> entry : before.entrySet())
            assertArrayEquals(entry.getValue(), Files.readAllBytes(entry.getKey()));
    }

    @Test
    void unparseableLegacyFileLeavesEveryInstalledFileUntouchedAndVersionPending() throws Exception {
        Path guiRoot = directory.resolve("guis");
        Files.createDirectories(guiRoot);
        for (String name : MENUS) Files.write(guiRoot.resolve(name + ".yml"), bytes("legacy-gui-v1/" + name + ".yml"));
        Files.writeString(guiRoot.resolve("custom-recipes.yml"), "title: [unfinished\n");
        Files.write(directory.resolve("messages.yml"), bytes("messages.yml"));
        byte[] craft = Files.readAllBytes(guiRoot.resolve("custom-crafting.yml"));
        AtomicInteger version = new AtomicInteger();
        assertFalse(migration(version).migrateInstalled());
        assertEquals(0, version.get());
        assertArrayEquals(craft, Files.readAllBytes(guiRoot.resolve("custom-crafting.yml")));
        assertFalse(Files.exists(guiRoot.resolve("custom-crafting.yml.gui-v1-backup")));
    }

    @Test
    void missingLegacyLayoutFieldRemainsPendingInsteadOfBeingRecreated() throws Exception {
        Path guiRoot = directory.resolve("guis");
        Files.createDirectories(guiRoot);
        for (String name : MENUS) Files.write(guiRoot.resolve(name + ".yml"),
                bytes("legacy-gui-v1/" + name + ".yml"));
        Files.write(directory.resolve("messages.yml"), bytes("messages.yml"));
        Path craft = guiRoot.resolve("custom-crafting.yml");
        YamlConfiguration old = YamlConfiguration.loadConfiguration(craft.toFile());
        old.set("grid-slots", null);
        old.save(craft.toFile());
        byte[] before = Files.readAllBytes(craft);
        AtomicInteger version = new AtomicInteger();
        assertFalse(migration(version).migrateInstalled());
        assertEquals(0, version.get());
        assertArrayEquals(before, Files.readAllBytes(craft));
        assertFalse(Files.exists(craft.resolveSibling("custom-crafting.yml.gui-v1-backup")));
    }

    @Test
    void missingLegacyRequiredDialogFieldLeavesUpgradePending() throws Exception {
        Path guiRoot = directory.resolve("guis");
        Files.createDirectories(guiRoot);
        for (String name : MENUS) Files.write(guiRoot.resolve(name + ".yml"),
                bytes("legacy-gui-v1/" + name + ".yml"));
        Files.write(directory.resolve("messages.yml"), bytes("messages.yml"));
        Path dialog = directory.resolve("dialogs/search.yml");
        Files.createDirectories(dialog.getParent());
        YamlConfiguration old = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(resource("legacy-dialog-v1/search.yml"),
                        java.nio.charset.StandardCharsets.UTF_8));
        old.set("confirm-button", null);
        old.save(dialog.toFile());
        byte[] before = Files.readAllBytes(dialog);
        byte[] craftBefore = Files.readAllBytes(guiRoot.resolve("custom-crafting.yml"));
        AtomicInteger version = new AtomicInteger();

        assertFalse(migration(version).migrateInstalled());
        assertEquals(0, version.get());
        assertArrayEquals(before, Files.readAllBytes(dialog));
        assertArrayEquals(craftBefore, Files.readAllBytes(guiRoot.resolve("custom-crafting.yml")));
        assertFalse(Files.exists(dialog.resolveSibling("search.yml.gui-v1-backup")));
    }

    @Test
    void missingFieldInsideLegacyButtonDoesNotRestoreBundledSlotOrMaterial() throws Exception {
        Path guiRoot = directory.resolve("guis");
        Files.createDirectories(guiRoot);
        for (String name : MENUS) Files.write(guiRoot.resolve(name + ".yml"),
                bytes("legacy-gui-v1/" + name + ".yml"));
        Files.write(directory.resolve("messages.yml"), bytes("messages.yml"));
        Path browser = guiRoot.resolve("custom-recipes.yml");
        AtomicInteger version = new AtomicInteger();

        for (String field : List.of("slot", "material")) {
            YamlConfiguration old = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(resource("legacy-gui-v1/custom-recipes.yml"),
                            java.nio.charset.StandardCharsets.UTF_8));
            old.set("items.search." + field, null);
            old.save(browser.toFile());
            byte[] before = Files.readAllBytes(browser);

            assertFalse(migration(version).migrateInstalled(), field);
            assertEquals(0, version.get());
            assertArrayEquals(before, Files.readAllBytes(browser));
            assertFalse(Files.exists(browser.resolveSibling("custom-recipes.yml.gui-v1-backup")));
        }
    }

    @Test
    void missingLegacySortCycleTextDoesNotRecreateDefault() throws Exception {
        Path guiRoot = directory.resolve("guis");
        Files.createDirectories(guiRoot);
        for (String name : MENUS) Files.write(guiRoot.resolve(name + ".yml"),
                bytes("legacy-gui-v1/" + name + ".yml"));
        Files.write(directory.resolve("messages.yml"), bytes("messages.yml"));
        Path browser = guiRoot.resolve("custom-recipes.yml");
        YamlConfiguration old = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(resource("legacy-gui-v1/custom-recipes.yml"),
                        java.nio.charset.StandardCharsets.UTF_8));
        old.set("items.sort.selected-line", null);
        old.save(browser.toFile());
        byte[] before = Files.readAllBytes(browser);
        AtomicInteger version = new AtomicInteger();

        assertFalse(migration(version).migrateInstalled());
        assertEquals(0, version.get());
        assertArrayEquals(before, Files.readAllBytes(browser));
        assertFalse(Files.exists(browser.resolveSibling("custom-recipes.yml.gui-v1-backup")));
    }

    @Test
    void newerSearchDialogEditsSurviveUpgradeOfOldGuiFiles() throws Exception {
        Path guiRoot = directory.resolve("guis");
        Files.createDirectories(guiRoot);
        for (String name : MENUS) Files.write(guiRoot.resolve(name + ".yml"),
                bytes("legacy-gui-v1/" + name + ".yml"));
        Files.write(directory.resolve("messages.yml"), bytes("messages.yml"));
        Path dialog = directory.resolve("dialogs/search.yml");
        Files.createDirectories(dialog.getParent());
        YamlConfiguration owner = new YamlConfiguration();
        owner.loadFromString(new String(bytes("dialogs/search.yml"), java.nio.charset.StandardCharsets.UTF_8));
        owner.set("placeholder", "§bEigen zoektekst");
        owner.set("confirm-icon", "diamond");
        owner.set("cancel-icon", null);
        owner.set("label-visible", false);
        owner.set("can-close-with-escape", false);
        owner.set("pause", true);
        owner.save(dialog.toFile());
        byte[] before = Files.readAllBytes(dialog);
        AtomicInteger version = new AtomicInteger();

        assertTrue(migration(version).migrateInstalled());
        assertEquals(2, version.get());
        assertArrayEquals(before, Files.readAllBytes(dialog.resolveSibling("search.yml.gui-v1-backup")));
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(dialog.toFile());
        assertEquals("§bEigen zoektekst", migrated.getString("placeholder"));
        assertEquals("diamond", migrated.getString("confirm-icon"));
        assertFalse(migrated.contains("cancel-icon"));
        assertFalse(migrated.getBoolean("label-visible"));
        assertFalse(migrated.getBoolean("can-close-with-escape"));
        assertTrue(migrated.getBoolean("pause"));
    }
}
