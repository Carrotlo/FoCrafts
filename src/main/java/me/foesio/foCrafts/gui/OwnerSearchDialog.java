package me.foesio.foCrafts.gui;

import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.TextDialogRequest;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/** Loads owner search dialog text without backfilling current-schema omissions. */
final class OwnerSearchDialog {
    private final JavaPlugin plugin;
    private YamlConfiguration active;
    private boolean startup = true;

    OwnerSearchDialog(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    boolean available() {
        return active != null;
    }

    void validateInstalled() throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), "dialogs/search.yml");
        if (file.exists() && !file.isFile()) {
            throw new IOException("dialogs/search.yml is not a regular file");
        }
        if (!file.isFile()) return; // Removing the optional native screen selects chat input.
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(file);
        validate(candidate, bundled(), false, plugin.getLogger());
    }

    boolean reload() {
        File file = new File(plugin.getDataFolder(), "dialogs/search.yml");
        if (file.exists() && !file.isFile()) {
            plugin.getLogger().warning("Search dialog reload rejected: dialogs/search.yml is not a regular file.");
            startup = false;
            return false;
        }
        if (!file.isFile()) {
            plugin.getLogger().warning("Search dialog unavailable: installed dialogs/search.yml is missing.");
            active = null;
            startup = false;
            return true;
        }
        try {
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.load(file);
            YamlConfiguration bundled = bundled();
            active = validate(candidate, bundled, startup, plugin.getLogger());
            return true;
        } catch (IOException | InvalidConfigurationException error) {
            plugin.getLogger().warning("Search dialog reload rejected; owner file unchanged: " + error.getMessage());
            return false;
        } finally {
            startup = false;
        }
    }

    static YamlConfiguration validate(YamlConfiguration candidate, YamlConfiguration bundled,
                                      boolean startup, Logger logger) throws IOException {
        for (String required : List.of("title", "field-label", "confirm-button", "cancel-button")) {
            String path = required.endsWith("button") && candidate.isConfigurationSection(required)
                    ? required + ".label" : required;
            Object value = candidate.get(path);
            if (!(value instanceof String label) || label.isBlank()) {
                if (!startup) throw new IOException("Invalid required search dialog field " + required);
                candidate.set(path, bundled.get(required));
                logger.warning("Search dialog startup fallback " + path + " from bundled default");
            }
        }
        for (String number : List.of("body-width", "input-width", "button-width", "max-length")) {
            if (!candidate.isInt(number) || candidate.getInt(number) < 1) {
                if (!startup) throw new IOException("Invalid search dialog field " + number);
                candidate.set(number, bundled.get(number));
                logger.warning("Search dialog startup fallback " + number + " from bundled default");
            }
        }
        if (candidate.contains("body") && (!(candidate.get("body") instanceof List<?> lines)
                || lines.stream().anyMatch(line -> !(line instanceof String)))) {
            if (!startup) throw new IOException("Invalid search dialog body");
            candidate.set("body", bundled.get("body"));
            logger.warning("Search dialog startup fallback body from bundled default");
        }
        for (String text : List.of("initial-value", "placeholder", "confirm-icon", "cancel-icon",
                "confirm-button.tooltip", "cancel-button.tooltip", "confirm-button.icon", "cancel-button.icon")) {
            if (candidate.contains(text) && !candidate.isString(text)) {
                if (!startup) throw new IOException("Invalid search dialog field " + text);
                candidate.set(text, bundled.get(text));
                logger.warning("Search dialog startup fallback " + text + " from bundled default");
            }
        }
        for (String flag : List.of("label-visible", "can-close-with-escape", "pause")) {
            if (candidate.contains(flag) && !candidate.isBoolean(flag)) {
                if (!startup) throw new IOException("Invalid search dialog field " + flag);
                candidate.set(flag, bundled.get(flag));
                logger.warning("Search dialog startup fallback " + flag + " from bundled default");
            }
        }
        for (String button : List.of("confirm-button", "cancel-button")) {
            String width = button + ".width";
            if (candidate.contains(width) && (!candidate.isInt(width) || candidate.getInt(width) < 1)) {
                if (!startup) throw new IOException("Invalid search dialog field " + width);
                candidate.set(width, null);
                logger.warning("Search dialog startup fallback " + width + " to button-width");
            }
        }
        return candidate;
    }

    TextDialogRequest request(Map<String, String> replacements) {
        if (active == null) throw new IllegalStateException("Search dialog is unavailable");
        return render(active, replacements);
    }

    static TextDialogRequest render(YamlConfiguration config, Map<String, String> replacements) {
        List<String> body = config.getStringList("body").stream().map(line -> replace(line, replacements)).toList();
        return new TextDialogRequest(
                replace(config.getString("title", ""), replacements), body,
                replace(config.getString("field-label", ""), replacements),
                replace(config.getString("initial-value", ""), replacements),
                replace(config.getString("placeholder", ""), replacements),
                button(config, "confirm-button", replacements),
                button(config, "cancel-button", replacements),
                config.getInt("body-width", 300), config.getInt("input-width", 300),
                config.getInt("max-length"), config.getBoolean("label-visible", false),
                config.getBoolean("can-close-with-escape", false), config.getBoolean("pause", false));
    }

    private static DialogButton button(YamlConfiguration config, String path,
                                Map<String, String> replacements) {
        String label = config.isConfigurationSection(path)
                ? config.getString(path + ".label", "") : config.getString(path, "");
        String tooltip = config.getString(path + ".tooltip", "");
        String icon = config.getString(path + ".icon",
                config.getString(path.equals("confirm-button") ? "confirm-icon" : "cancel-icon", ""));
        int width = config.getInt(path + ".width", config.getInt("button-width", DialogButton.DEFAULT_WIDTH));
        return new DialogButton(replace(label, replacements), replace(tooltip, replacements), width, icon);
    }

    private static String replace(String value, Map<String, String> replacements) {
        String result = Objects.toString(value, "");
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", Objects.toString(entry.getValue(), ""));
        }
        return result;
    }

    private YamlConfiguration bundled() throws IOException, InvalidConfigurationException {
        try (InputStream stream = plugin.getResource("dialogs/search.yml")) {
            if (stream == null) throw new IOException("Bundled search dialog is missing");
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }
}
