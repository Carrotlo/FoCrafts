package me.foesio.foCrafts.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlFileUpdaterTest {

    @Test
    void legacyMigrationBackfillsOnlyNewPathsAndPreservesOwnerValues() {
        YamlConfiguration defaults = yaml("""
                schema-version: 1
                title: Default title
                items:
                  search:
                    slot: 51
                    name: New search
                    lore:
                      - New lore
                  sort:
                    lore:
                      - New wrapper
                optional-owner-default: Bundled value
                """);
        YamlConfiguration current = yaml("""
                title: Translated title
                items:
                  search:
                    slot: 47
                    name: Translated search
                    lore: []
                  sort:
                    lore:
                      - Old wrapper
                """);

        boolean changed = YamlFileUpdater.migrate(
                current,
                defaults,
                1,
                Set.of("items.search.name", "items.search.lore"),
                Map.of("items.sort.lore", List.of("Old wrapper"))
        );

        assertTrue(changed);
        assertEquals(1, current.getInt("schema-version"));
        assertEquals("Translated title", current.getString("title"));
        assertEquals(47, current.getInt("items.search.slot"));
        assertEquals("Translated search", current.getString("items.search.name"));
        assertEquals(List.of(), current.getStringList("items.search.lore"));
        assertEquals(List.of("New wrapper"), current.getStringList("items.sort.lore"));
        assertFalse(current.isSet("optional-owner-default"), "unlisted missing defaults must not be resurrected");

        String migrated = current.saveToString();
        assertFalse(YamlFileUpdater.migrate(
                current,
                defaults,
                1,
                Set.of("items.search.name", "items.search.lore"),
                Map.of("items.sort.lore", List.of("Old wrapper"))
        ));
        assertEquals(migrated, current.saveToString(), "a second load must be idempotent");
    }

    @Test
    void currentSchemaPreservesDeletedNewSettingAndIsIdempotent() {
        YamlConfiguration defaults = yaml("""
                schema-version: 1
                items:
                  back:
                    name: Back
                """);
        YamlConfiguration current = yaml("schema-version: 1\n");
        String before = current.saveToString();

        boolean changed = YamlFileUpdater.migrate(
                current,
                defaults,
                1,
                Set.of("items.back.name"),
                Map.of()
        );

        assertFalse(changed);
        assertEquals(before, current.saveToString());
        assertFalse(current.isSet("items.back.name"));
    }

    @Test
    void bundledPublicGuiResourcesContainCompleteButtonTemplates() throws Exception {
        YamlConfiguration recipes = resource("guis/custom-recipes.yml");
        for (String button : List.of("previous-page", "next-page", "search", "clear-search", "back-to-craft", "sort")) {
            String root = "buttons." + button;
            assertTrue(recipes.isInt(root + ".slot"), root + " needs a slot");
            assertNotNull(recipes.getString(root + ".material"), root + " needs a material");
            assertNotNull(recipes.getString(root + ".name"), root + " needs an editable name");
            assertTrue(recipes.isList(root + ".lore"), root + " needs editable lore");
        }

        YamlConfiguration preview = resource("guis/recipe-preview.yml");
        assertNotNull(preview.getString("buttons.back.name"));
        assertTrue(preview.isList("buttons.back.lore"));
        assertEquals("replace", preview.getString("entries.result.source-item.lore.mode"));

        YamlConfiguration crafting = resource("guis/custom-crafting.yml");
        assertNotNull(crafting.getString("buttons.browse-recipes.name"));
        assertTrue(crafting.isList("buttons.browse-recipes.lore"));
        assertEquals("replace", crafting.getString("entries.result.source-item.lore.mode"));
    }

    @Test
    void collidingControlsResolveToTheSameSafeSlotsUsedByRenderAndClickContracts() {
        Set<Integer> occupied = new LinkedHashSet<>(List.of(10, 11, 12));

        int result = GuiConfig.resolveAvailableSlot(10, 24, 54, occupied);
        occupied.add(result);
        int browse = GuiConfig.resolveAvailableSlot(24, 49, 54, occupied);
        occupied.add(browse);

        assertEquals(24, result);
        assertEquals(49, browse);
        assertEquals(-1, GuiConfig.resolveAvailableSlot(-1, 45, 54, occupied), "-1 intentionally disables a control");
        assertEquals(0, GuiConfig.resolveAvailableSlot(10, 24, 54, new LinkedHashSet<>(List.of(10, 24))));
    }

    private static YamlConfiguration resource(String path) throws Exception {
        try (var stream = YamlFileUpdaterTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return configuration;
        }
    }

    private static YamlConfiguration yaml(String value) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        return configuration;
    }
}
