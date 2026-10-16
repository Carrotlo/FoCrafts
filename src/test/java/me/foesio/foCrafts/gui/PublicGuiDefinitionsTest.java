package me.foesio.foCrafts.gui;

import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiFrame;
import me.foesio.core.gui.publicgui.PublicGuiPlanner;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.gui.publicgui.PublicGuiText;
import me.foesio.core.gui.publicgui.GuiSnapshot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PublicGuiDefinitionsTest {
    @Test
    void generatedCraftingDefinitionKeepsInputAndOutputSeparate() throws Exception {
        PublicGuiDefinition definition = parse("custom-crafting", new PublicGuiContract(
                Set.of("craft.browse"), Map.of("grid-slots", PublicGuiContract.GroupRule.exactly(
                PublicGuiContract.GroupRole.PHYSICAL_INPUT, 9)), Set.of("cost", "permission", "world"),
                PublicGuiContract.InteractionMode.PLUGIN_OWNED));
        assertEquals(9, definition.requireGroup("grid-slots").size());
        assertEquals(24, definition.entries().get("result").slots().getFirst());
        assertEquals(PublicGuiDefinition.EditMode.REPLACE,
                definition.entries().get("result").sourceItem().lore().mode());
        assertFalse(definition.claimedSlots().contains(0));
    }

    @Test
    void generatedBrowserHasActionForEachVisibleEntryAndSafeNavigationStates() throws Exception {
        PublicGuiDefinition definition = parse("custom-recipes", new PublicGuiContract(
                Set.of("recipes.previous", "recipes.next", "recipes.search", "recipes.clear-search",
                        "recipes.back", "recipes.sort", "recipes.view"), Map.of(),
                Set.of("current", "target", "page", "max_page", "recipe_name", "description", "locked"),
                PublicGuiContract.InteractionMode.CORE));
        assertEquals(21, definition.entries().get("recipes").slots().size());
        assertEquals("recipes.view", definition.entries().get("recipes").actions().values().iterator().next());
        assertFalse(definition.buttons().get("back-to-craft").states().get("unavailable").clickable());
        assertNotNull(definition.buttons().get("sort").cycle());
    }

    @Test
    void generatedPreviewKeepsRealItemMetadataUntilExplicitLoreReplace() throws Exception {
        PublicGuiDefinition definition = parse("recipe-preview", new PublicGuiContract(
                Set.of("preview.back"), Map.of(), Set.of("recipe_name", "description", "cost", "status"),
                PublicGuiContract.InteractionMode.CORE));
        assertEquals(9, definition.entries().get("ingredients").slots().size());
        assertEquals(PublicGuiDefinition.EditMode.PRESERVE,
                definition.entries().get("ingredients").sourceItem().lore().mode());
        assertEquals(PublicGuiDefinition.EditMode.REPLACE,
                definition.entries().get("result").sourceItem().lore().mode());
    }

    @Test
    void movedCraftButtonMovesItsActionWithoutRestoringRemovedLoreOrSprite() throws Exception {
        YamlConfiguration yaml = resource("guis/custom-crafting.yml");
        yaml.set("title", "§bEigen werkbank");
        yaml.set("buttons.browse-recipes.slot", 48);
        yaml.set("buttons.browse-recipes.name", "Zoek recepten");
        yaml.set("buttons.browse-recipes.lore", java.util.List.of());
        PublicGuiDefinition definition = PublicGuiSchema.parse("focrafts.custom-crafting",
                "guis/custom-crafting.yml", yaml, new PublicGuiContract(
                        Set.of("craft.browse"), Map.of("grid-slots", PublicGuiContract.GroupRule.exactly(
                        PublicGuiContract.GroupRole.PHYSICAL_INPUT, 9)),
                        Set.of("cost", "permission", "world"), PublicGuiContract.InteractionMode.PLUGIN_OWNED));
        PublicGuiFrame frame = PublicGuiPlanner.plan(new GuiSnapshot<>(1,
                Map.of("focrafts.custom-crafting", definition)), "focrafts.custom-crafting",
                new PublicGuiRenderInput(Map.of("cost", "", "permission", "", "world", ""),
                        Map.of(), Map.of(), Map.of()));
        assertEquals("craft.browse", frame.bindings().get(48).action());
        assertEquals("§bEigen werkbank", frame.title());
        assertFalse(frame.bindings().containsKey(49));
        assertEquals("Zoek recepten", frame.visuals().get(48).fixed().name());
        assertEquals(java.util.List.of(), frame.visuals().get(48).fixed().lore());
        assertFalse(frame.reservedSlots().contains(48));
        assertTrue(frame.reservedSlots().contains(10));
    }

    @Test
    void browserSortRendersOwnerCycleAndRemovedButtonHasNoAction() throws Exception {
        YamlConfiguration yaml = resource("guis/custom-recipes.yml");
        yaml.set("buttons.sort.name", "§bEigen sortering");
        yaml.set("buttons.sort.lore", java.util.List.of("§dKeuzes:", "{options}"));
        yaml.set("buttons.sort.cycle.selected-format", "§a→ {option}");
        yaml.set("buttons.sort.cycle.unselected-format", "§7• {option}");
        yaml.set("buttons.search.enabled", false);
        PublicGuiDefinition definition = PublicGuiSchema.parse("focrafts.custom-recipes",
                "guis/custom-recipes.yml", yaml, new PublicGuiContract(
                        Set.of("recipes.previous", "recipes.next", "recipes.search", "recipes.clear-search",
                                "recipes.back", "recipes.sort", "recipes.view"), Map.of(),
                        Set.of("current", "target", "page", "max_page", "recipe_name", "description", "locked"),
                        PublicGuiContract.InteractionMode.CORE));
        PublicGuiFrame frame = PublicGuiPlanner.plan(new GuiSnapshot<>(1,
                Map.of("focrafts.custom-recipes", definition)), "focrafts.custom-recipes",
                new PublicGuiRenderInput(Map.of("current", "", "target", "", "page", "1", "max_page", "1"),
                        Map.of(), Map.of("sort", new PublicGuiText.CycleSelection(
                        java.util.List.of("Naam ↑", "Naam ↓"), 1)), Map.of()));
        PublicGuiFrame.Visual sort = frame.visuals().get(48);
        assertEquals("§bEigen sortering", PublicGuiText.scalar(
                sort.fixed().name(), sort.placeholders(), sort.path() + ".name"));
        assertEquals(java.util.List.of("§dKeuzes:", "§7• Naam ↑", "§a→ Naam ↓"),
                PublicGuiText.lore(sort.fixed().lore(), sort.placeholders(),
                        sort.cycle(), sort.selection(), sort.path() + ".lore"));
        assertEquals("recipes.sort", frame.bindings().get(48).action());
        assertFalse(frame.bindings().containsKey(51));
    }

    @Test
    void browserMissingRuntimeLoreStateIsRejectedBeforePlayersOpenIt() throws Exception {
        YamlConfiguration yaml = resource("guis/custom-recipes.yml");
        yaml.set("entries.recipes.source-item.lore-states.locked-only", null);
        PublicGuiDefinition definition = PublicGuiSchema.parse("focrafts.custom-recipes",
                "guis/custom-recipes.yml", yaml, new PublicGuiContract(
                        Set.of("recipes.previous", "recipes.next", "recipes.search", "recipes.clear-search",
                                "recipes.back", "recipes.sort", "recipes.view"), Map.of(),
                        Set.of("current", "target", "page", "max_page", "recipe_name", "description", "locked"),
                        PublicGuiContract.InteractionMode.CORE));
        assertTrue(GuiManager.validatePublicDefinitions(Map.of("focrafts.custom-recipes", definition))
                .stream().anyMatch(problem -> problem.key().equals(
                        "entries.recipes.source-item.lore-states.locked-only")));
    }

    private PublicGuiDefinition parse(String name, PublicGuiContract contract) throws Exception {
        String path = "guis/" + name + ".yml";
        return PublicGuiSchema.parse("focrafts." + name, path, resource(path), contract);
    }

    private YamlConfiguration resource(String path) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
