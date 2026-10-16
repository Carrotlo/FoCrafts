package me.foesio.foCrafts.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import me.foesio.core.dialog.TextDialogRequest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class OwnerSearchDialogTest {
    @Test
    void startupRepairsOnlyInvalidFieldAndReloadRejectsIt() throws Exception {
        YamlConfiguration bundled = new YamlConfiguration();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("dialogs/search.yml")) {
            assertNotNull(stream);
            bundled.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.loadFromString(bundled.saveToString());
        candidate.set("title", "§bEigen zoeken");
        candidate.set("max-length", -4);
        candidate.set("body", null);
        candidate.set("confirm-icon", null);
        YamlConfiguration recovered = OwnerSearchDialog.validate(candidate, bundled, true,
                java.util.logging.Logger.getLogger("dialog-test"));
        assertEquals("§bEigen zoeken", recovered.getString("title"));
        assertEquals(128, recovered.getInt("max-length"));
        assertFalse(recovered.contains("body"));
        assertFalse(recovered.contains("confirm-icon"));
        recovered.set("max-length", -4);
        assertThrows(java.io.IOException.class, () -> OwnerSearchDialog.validate(recovered, bundled, false,
                java.util.logging.Logger.getLogger("dialog-test")));
    }

    @Test
    void ownerTextAndRemovedIconReachFinalDialogRequest() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("dialogs/search.yml")) {
            assertNotNull(stream);
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        yaml.set("title", "§bRecepten zoeken");
        yaml.set("body", List.of("§aHuidig: {current}"));
        yaml.set("field-label", "§eTerm");
        yaml.set("confirm-button", "§bZoek nu");
        yaml.set("confirm-icon", null);
        yaml.set("cancel-icon", "paper");
        TextDialogRequest result = OwnerSearchDialog.render(yaml, Map.of("current", "diamant", "format", "naam"));
        assertEquals("§bRecepten zoeken", result.title());
        assertEquals(List.of("§aHuidig: diamant"), result.body());
        assertEquals("§eTerm", result.fieldLabel());
        assertEquals("§bZoek nu", result.submitButton().label());
        assertEquals("", result.submitButton().icon());
        assertEquals(":paper:", result.cancelButton().icon());
        assertEquals("naam", result.placeholder());
    }

    @Test
    void startupRepairsOnlyButtonLabelAndKeepsOwnerTooltip() throws Exception {
        YamlConfiguration bundled = new YamlConfiguration();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("dialogs/search.yml")) {
            assertNotNull(stream);
            bundled.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.loadFromString(bundled.saveToString());
        candidate.set("confirm-button", null);
        candidate.set("confirm-button.label", 4);
        candidate.set("confirm-button.tooltip", "§bEigen uitleg");
        YamlConfiguration recovered = OwnerSearchDialog.validate(candidate, bundled, true,
                java.util.logging.Logger.getLogger("dialog-test"));
        assertEquals(bundled.getString("confirm-button"), recovered.getString("confirm-button.label"));
        assertEquals("§bEigen uitleg", recovered.getString("confirm-button.tooltip"));
        assertEquals("§bEigen uitleg",
                OwnerSearchDialog.render(recovered, Map.of()).submitButton().tooltip());
        recovered.set("confirm-button.label", 4);
        assertThrows(java.io.IOException.class, () -> OwnerSearchDialog.validate(recovered, bundled, false,
                java.util.logging.Logger.getLogger("dialog-test")));
    }
}
