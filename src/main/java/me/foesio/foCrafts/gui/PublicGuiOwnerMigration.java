package me.foesio.foCrafts.gui;

import me.foesio.core.gui.publicgui.GuiDefinitionException;
import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.GuiDiagnostic;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.gui.publicgui.PublicGuiStartupFallback;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.logging.Logger;

/** One-time conversion of installed 2.1 public GUI files to the Core schema. */
public final class PublicGuiOwnerMigration {
    private static final int VERSION = 2;
    private static final List<String> MENUS = List.of("custom-crafting", "custom-recipes", "recipe-preview");
    private static final List<String> ITEM_FIELDS = List.of("material", "amount", "name", "lore",
            "custom-model-data", "glow", "flags");
    private final Path dataRoot;
    private final Function<String, InputStream> resources;
    private final Logger logger;
    private final IntSupplier version;
    private final IntConsumer saveVersion;
    private final IntSupplier oldConfigVersion;

    public PublicGuiOwnerMigration(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath(), plugin::getResource, plugin.getLogger(),
                () -> plugin.getConfig().getInt("public-gui-schema-version", 0), next -> {
                    plugin.getConfig().set("public-gui-schema-version", next);
                    plugin.saveConfig();
                    try {
                        YamlConfiguration disk = new YamlConfiguration();
                        disk.load(new java.io.File(plugin.getDataFolder(), "config.yml"));
                        if (disk.getInt("public-gui-schema-version", -1) != next)
                            throw new IllegalStateException("public-gui-schema-version was not saved");
                    } catch (IOException | InvalidConfigurationException failure) {
                        throw new IllegalStateException("Could not verify public GUI schema version", failure);
                    }
                }, () -> plugin.getConfig().getInt("config-version", 0));
    }

    PublicGuiOwnerMigration(Path dataRoot, Function<String, InputStream> resources,
                            Logger logger, IntSupplier version, IntConsumer saveVersion) {
        this(dataRoot, resources, logger, version, saveVersion, () -> 0);
    }

    PublicGuiOwnerMigration(Path dataRoot, Function<String, InputStream> resources,
                            Logger logger, IntSupplier version, IntConsumer saveVersion,
                            IntSupplier oldConfigVersion) {
        this.dataRoot = dataRoot;
        this.resources = resources;
        this.logger = logger;
        this.version = version;
        this.saveVersion = saveVersion;
        this.oldConfigVersion = oldConfigVersion;
    }

    public boolean hasNoInstalledOwnerFiles() {
        return !Files.exists(dataRoot.resolve("guis"))
                && !Files.exists(dataRoot.resolve("dialogs/search.yml"))
                && !Files.exists(dataRoot.resolve("messages.yml"));
    }

    /** Installs new resources on an empty data directory; no legacy conversion or backup applies. */
    public boolean installFreshDefaults() {
        if (!hasNoInstalledOwnerFiles()) {
            logger.severe("Fresh public GUI installation rejected: owner interface files already exist.");
            return false;
        }
        if (version.getAsInt() >= VERSION) return true;
        Map<Path, byte[]> proposed = new LinkedHashMap<>();
        Map<String, PublicGuiDefinition> definitions = new LinkedHashMap<>();
        Path config = dataRoot.resolve("config.yml");
        Path guiDirectory = dataRoot.resolve("guis");
        Path dialogDirectory = dataRoot.resolve("dialogs");
        boolean guiDirectoryExisted = Files.exists(guiDirectory);
        boolean dialogDirectoryExisted = Files.exists(dialogDirectory);
        byte[] originalConfig = null;
        int previousVersion = version.getAsInt();
        try {
            originalConfig = Files.isRegularFile(config) ? Files.readAllBytes(config) : null;
            for (String name : MENUS) {
                YamlConfiguration bundled = resource("guis/" + name + ".yml");
                definitions.put("focrafts." + name, validate(name, bundled));
                proposed.put(dataRoot.resolve("guis/" + name + ".yml"), resourceBytes("guis/" + name + ".yml"));
            }
            List<GuiDiagnostic> violations = GuiManager.validatePublicDefinitions(definitions);
            if (!violations.isEmpty()) throw new IOException("Bundled public GUI contract failed: " + violations);
            proposed.put(dataRoot.resolve("dialogs/search.yml"), resourceBytes("dialogs/search.yml"));
            for (Map.Entry<Path, byte[]> entry : proposed.entrySet())
                parse(entry.getValue(), entry.getKey().toString());
            for (Map.Entry<Path, byte[]> entry : proposed.entrySet()) replace(entry.getKey(), entry.getValue());
            saveVersion.accept(VERSION);
            return true;
        } catch (Exception failure) {
            try { saveVersion.accept(previousVersion); }
            catch (Exception error) { failure.addSuppressed(error); }
            Exception rollbackFailure = null;
            for (Path target : proposed.keySet()) {
                try { Files.deleteIfExists(target); }
                catch (Exception error) {
                    if (rollbackFailure == null) rollbackFailure = error;
                    else rollbackFailure.addSuppressed(error);
                }
            }
            for (Path directory : List.of(guiDirectory, dialogDirectory)) {
                if ((directory.equals(guiDirectory) && guiDirectoryExisted)
                        || (directory.equals(dialogDirectory) && dialogDirectoryExisted)) continue;
                try { Files.deleteIfExists(directory); }
                catch (Exception error) {
                    if (rollbackFailure == null) rollbackFailure = error;
                    else rollbackFailure.addSuppressed(error);
                }
            }
            try {
                if (originalConfig == null) Files.deleteIfExists(config);
                else replace(config, originalConfig);
            } catch (Exception error) {
                if (rollbackFailure == null) rollbackFailure = error;
                else rollbackFailure.addSuppressed(error);
            }
            if (rollbackFailure != null) failure.addSuppressed(rollbackFailure);
            logger.severe("Fresh public GUI installation failed"
                    + (rollbackFailure == null ? "; files restored: " : "; rollback also failed: ") + failure);
            return false;
        }
    }

    public boolean migrateInstalled() {
        if (version.getAsInt() >= VERSION) return true;
        int previousVersion = version.getAsInt();
        Path root = dataRoot;
        Path guiRoot = root.resolve("guis");
        boolean noGuiFiles = MENUS.stream().noneMatch(name -> Files.exists(guiRoot.resolve(name + ".yml")));
        if (!noGuiFiles && MENUS.stream().anyMatch(name ->
                !Files.isRegularFile(guiRoot.resolve(name + ".yml")))) {
            logger.severe("Public GUI upgrade pending: a 2.1 GUI file is missing or is not a regular file.");
            return false;
        }
        if (noGuiFiles && (Files.exists(guiRoot) || oldConfigVersion.getAsInt() >= 1)) {
            logger.severe("Public GUI upgrade pending: 2.1 GUI files may have been removed; preserving owner removals.");
            return false;
        }
        Map<Path, byte[]> original = new LinkedHashMap<>();
        Map<Path, byte[]> proposed = new LinkedHashMap<>();
        Map<String, PublicGuiDefinition> stagedDefinitions = new LinkedHashMap<>();
        int installedGuiSchema = -1;
        try {
            Path config = root.resolve("config.yml");
            // The version writer also changes config.yml; keep its pre-attempt bytes for rollback.
            if (Files.isRegularFile(config)) original.put(config, Files.readAllBytes(config));
            for (String name : MENUS) {
                Path target = guiRoot.resolve(name + ".yml");
                YamlConfiguration newest = resource("guis/" + name + ".yml");
                if (noGuiFiles) {
                    stagedDefinitions.put("focrafts." + name, validate(name, newest));
                    proposed.put(target, resourceBytes("guis/" + name + ".yml"));
                    continue;
                }
                byte[] bytes = Files.readAllBytes(target);
                original.put(target, bytes);
                YamlConfiguration old = parse(bytes, target.toString());
                Object schemaMarker = old.get("schema-version");
                boolean released20 = schemaMarker == null;
                int fileSchema = released20 ? 0 : 1;
                if (installedGuiSchema >= 0 && installedGuiSchema != fileSchema) {
                    throw new IOException("Mixed released GUI schemas; preserving all installed files");
                }
                installedGuiSchema = fileSchema;
                if (released20 && (old.contains("buttons", false) || old.contains("entries", false)
                        || old.contains("states", false) || !old.isConfigurationSection("items"))) {
                    throw new IOException(target + ": unmarked GUI does not match the released 2.0 layout");
                }
                if (!released20 && (!(schemaMarker instanceof Integer marker) || marker != 1)) {
                    throw new IOException(target + ": unsupported legacy schema-version " + schemaMarker);
                }
                requireLegacyLayout(name, old, target);
                YamlConfiguration oldDefault = legacyGuiDefault(name, released20);
                convert(name, old, oldDefault, newest);
                stagedDefinitions.put("focrafts." + name, validate(name, newest));
                proposed.put(target, newest.saveToString().getBytes(StandardCharsets.UTF_8));
            }
            List<GuiDiagnostic> violations = GuiManager.validatePublicDefinitions(stagedDefinitions);
            if (!violations.isEmpty()) throw new IOException("Staged public GUI contract failed: " + violations);
            Path dialog = root.resolve("dialogs/search.yml");
            if (Files.isRegularFile(dialog)) {
                byte[] bytes = Files.readAllBytes(dialog);
                original.put(dialog, bytes);
                YamlConfiguration oldDialog = parse(bytes, dialog.toString());
                YamlConfiguration nextDialog = resource("dialogs/search.yml");
                requireLegacyDialog(oldDialog, dialog);
                convertDialog(oldDialog, resource("legacy-dialog-v1/search.yml"), nextDialog);
                proposed.put(dialog, nextDialog.saveToString().getBytes(StandardCharsets.UTF_8));
            }
            if (!noGuiFiles) {
                Path messages = root.resolve("messages.yml");
                if (!Files.isRegularFile(messages)) throw new IOException("Installed messages.yml is missing");
                byte[] bytes = Files.readAllBytes(messages);
                original.put(messages, bytes);
                YamlConfiguration nextMessages = parse(bytes, messages.toString());
                YamlConfiguration newMessages = resource("messages.yml");
                for (String name : MENUS) {
                    YamlConfiguration old = parse(original.get(guiRoot.resolve(name + ".yml")), name);
                    YamlConfiguration oldDefault = legacyGuiDefault(name,
                            !old.contains("schema-version", false));
                    copyLabels(name, old, oldDefault, nextMessages, newMessages);
                }
                proposed.put(messages, nextMessages.saveToString().getBytes(StandardCharsets.UTF_8));
            }
            for (Map.Entry<Path, byte[]> entry : proposed.entrySet()) {
                Files.createDirectories(entry.getKey().getParent());
                // Stage and parse all output before touching any active file.
                parse(entry.getValue(), entry.getKey().toString());
            }
            for (Map.Entry<Path, byte[]> entry : original.entrySet()) {
                Path backup = entry.getKey().resolveSibling(entry.getKey().getFileName()
                        + ".gui-v" + (installedGuiSchema == 0 ? 0 : 1) + "-backup");
                writeBackupIfMissing(backup, entry.getValue());
            }
            try {
                for (Map.Entry<Path, byte[]> entry : proposed.entrySet()) replace(entry.getKey(), entry.getValue());
                saveVersion.accept(VERSION);
            } catch (Exception failure) {
                try { saveVersion.accept(previousVersion); }
                catch (Exception resetFailure) { failure.addSuppressed(resetFailure); }
                Exception rollbackFailure = null;
                for (Map.Entry<Path, byte[]> entry : original.entrySet()) {
                    try { replace(entry.getKey(), entry.getValue()); }
                    catch (Exception error) {
                        if (rollbackFailure == null) rollbackFailure = error;
                        else rollbackFailure.addSuppressed(error);
                    }
                }
                for (Path path : proposed.keySet()) if (!original.containsKey(path)) {
                    try { Files.deleteIfExists(path); }
                    catch (Exception error) {
                        if (rollbackFailure == null) rollbackFailure = error;
                        else rollbackFailure.addSuppressed(error);
                    }
                }
                if (!original.containsKey(config)) {
                    try { Files.deleteIfExists(config); }
                    catch (Exception error) {
                        if (rollbackFailure == null) rollbackFailure = error;
                        else rollbackFailure.addSuppressed(error);
                    }
                }
                if (rollbackFailure != null) failure.addSuppressed(rollbackFailure);
                throw failure;
            }
            return true;
        } catch (Exception failure) {
            logger.severe("Public GUI upgrade pending; inspect installed files and backups: " + failure);
            return false;
        }
    }

    private void requireLegacyDialog(YamlConfiguration old, Path file) throws IOException {
        for (String path : List.of("title", "field-label", "confirm-button", "cancel-button",
                "body-width", "input-width", "button-width", "max-length")) {
            if (!old.contains(path)) {
                throw new IOException(file + ": missing 2.1 required dialog field " + path
                        + "; cannot distinguish an owner removal from an old fallback");
            }
        }
    }

    private void convertDialog(YamlConfiguration old, YamlConfiguration prior, YamlConfiguration next) {
        for (String key : List.of("title", "field-label", "initial-value", "confirm-button",
                "cancel-button", "body")) {
            if (!old.contains(key)) {
                if (key.equals("body") || key.equals("initial-value")) next.set(key, null);
                continue;
            }
            Object value = old.get(key);
            if (Objects.equals(value, prior.get(key))) continue;
            boolean valid = key.equals("body")
                    ? value instanceof List<?> lines && lines.stream().allMatch(String.class::isInstance)
                    : value instanceof String;
            if (!valid && (key.equals("confirm-button") || key.equals("cancel-button"))
                    && old.isConfigurationSection(key)) {
                String defaultLabel = next.getString(key);
                next.set(key, null);
                Object label = old.get(key + ".label");
                if (label instanceof String text && !text.isBlank()) next.set(key + ".label", text);
                else {
                    next.set(key + ".label", defaultLabel);
                    logInvalid("dialogs/search.yml:" + key + ".label", label, defaultLabel);
                }
                for (String field : List.of("tooltip", "icon", "width")) {
                    String path = key + "." + field;
                    if (!old.contains(path)) continue;
                    Object custom = old.get(path);
                    boolean validSubfield = field.equals("width")
                            ? custom instanceof Integer number && number > 0
                            : custom instanceof String;
                    if (validSubfield) next.set(path, custom);
                    else logInvalid("dialogs/search.yml:" + path, custom,
                            field.equals("width") ? next.get("button-width")
                                    : field.equals("icon") ? next.get(key.equals("confirm-button")
                                    ? "confirm-icon" : "cancel-icon") : "<absent>");
                }
                continue;
            }
            if (valid && value instanceof String text && (key.equals("title") || key.equals("field-label")
                    || key.endsWith("button")) && text.isBlank()) valid = false;
            if (valid) next.set(key, value);
            else logInvalid("dialogs/search.yml:" + key, value, next.get(key));
        }
        for (String key : List.of("body-width", "input-width", "button-width", "max-length")) {
            if (!old.contains(key)) continue;
            Object value = old.get(key);
            if (Objects.equals(value, prior.get(key))) continue;
            if (value instanceof Integer number && number > 0) next.set(key, number);
            else logInvalid("dialogs/search.yml:" + key, value, next.get(key));
        }
        List<String> newerTextFields = List.of("placeholder", "confirm-icon", "cancel-icon");
        List<String> newerFlagFields = List.of("label-visible", "can-close-with-escape", "pause");
        boolean mixedNewerDialog = newerTextFields.stream().anyMatch(old::contains)
                || newerFlagFields.stream().anyMatch(old::contains);
        for (String key : newerTextFields) {
            if (!old.contains(key)) {
                if (mixedNewerDialog) next.set(key, null);
                continue;
            }
            Object value = old.get(key);
            if (value instanceof String text) next.set(key, text);
            else logInvalid("dialogs/search.yml:" + key, value, next.get(key));
        }
        for (String key : newerFlagFields) {
            if (!old.contains(key)) {
                if (mixedNewerDialog) next.set(key, null);
                continue;
            }
            Object value = old.get(key);
            if (value instanceof Boolean flag) next.set(key, flag);
            else logInvalid("dialogs/search.yml:" + key, value, next.get(key));
        }
    }

    private void convert(String name, YamlConfiguration old, YamlConfiguration prior, YamlConfiguration next) throws IOException {
        copy(old, prior, next, "title", "title");
        copy(old, prior, next, "rows", "rows");
        item(old, prior, next, "filler", "filler", false);
        if (name.equals("custom-crafting")) {
            copy(old, prior, next, "grid-slots", "grid-slots");
            singleton(old, prior, next, "result-slot", "entries.result.slots");
            item(old, prior, next, "items.browse-recipes", "buttons.browse-recipes", true);
            item(old, prior, next, "result-items.no-match", "entries.result.empty-item", false);
            item(old, prior, next, "result-items.locked", "states.locked", false);
            item(old, prior, next, "result-items.world-blocked", "states.world-blocked", false);
            sourceLore(old, prior, next, "result-item.lore", "entries.result.source-item.lore");
            if (old.contains("result-item.unnamed-name"))
                copy(old, prior, next, "result-item.unnamed-name", "entries.result.source-item.default-name");
        } else if (name.equals("custom-recipes")) {
            copy(old, prior, next, "content-slots", "entries.recipes.slots");
            item(old, prior, next, "empty-content", "entries.recipes.empty-item", false);
            for (String button : List.of("previous-page", "next-page", "search", "clear-search", "back-to-craft", "sort"))
                item(old, prior, next, "items." + button, "buttons." + button, true);
            item(old, prior, next, "invalid-recipe-item", "states.invalid-recipe", false);
            copy(old, prior, next, "recipe-item.name", "entries.recipes.source-item.default-name");
            for (String unsupported : List.of("material", "amount", "custom-model-data", "glow", "flags")) {
                String path = "recipe-item." + unsupported;
                if (old.contains(path) && !Objects.equals(old.get(path), prior.get(path)))
                    throw new IOException("Cannot safely map customized legacy recipe-item field " + path);
            }
            if (old.contains("recipe-item.lore")) {
                Object custom = old.get("recipe-item.lore");
                if (!Objects.equals(custom, prior.get("recipe-item.lore"))) {
                    if (!(custom instanceof List<?> lines) || lines.stream().anyMatch(line -> !(line instanceof String))) {
                        logInvalid("recipe-item.lore", custom, next.get("entries.recipes.source-item.lore-states.both.lines"));
                    } else {
                        for (String state : List.of("both", "description-only", "locked-only", "plain")) {
                            List<String> mapped = new ArrayList<>();
                            for (Object entry : lines) {
                                String line = ((String) entry).replace("{sort_options}", "{options}");
                                if (line.contains("{description}") && line.contains("{locked}"))
                                    throw new IOException("Cannot safely split legacy recipe-item.lore line with both conditional placeholders");
                                if (line.contains("{description}") && (state.equals("plain") || state.equals("locked-only"))) continue;
                                if (line.contains("{locked}") && (state.equals("plain") || state.equals("description-only"))) continue;
                                mapped.add(line);
                            }
                            next.set("entries.recipes.source-item.lore-states." + state + ".lines", mapped);
                        }
                    }
                }
            } else {
                for (String state : List.of("both", "description-only", "locked-only", "plain"))
                    next.set("entries.recipes.source-item.lore-states." + state + ".lines", new ArrayList<>());
            }
            if (old.isConfigurationSection("items.sort")) {
                for (String required : List.of("selected-line", "unselected-line")) {
                    String path = "items.sort." + required;
                    if (!old.contains(path)) {
                        throw new IOException("Missing 2.1 GUI cycle field " + path
                                + "; cannot distinguish an owner removal from an old fallback");
                    }
                }
            }
            copy(old, prior, next, "items.sort.selected-line", "buttons.sort.cycle.selected-format", "{sort}", "{option}");
            copy(old, prior, next, "items.sort.unselected-line", "buttons.sort.cycle.unselected-format", "{sort}", "{option}");
        } else {
            copy(old, prior, next, "grid-slots", "entries.ingredients.slots");
            singleton(old, prior, next, "result-slot", "entries.result.slots");
            item(old, prior, next, "items.details", "decorations.details", false);
            item(old, prior, next, "items.back", "buttons.back", true);
            item(old, prior, next, "invalid-result-item", "entries.result.empty-item", false);
            sourceLore(old, prior, next, "result-item.lore", "entries.result.source-item.lore");
            if (old.contains("result-item.unnamed-name"))
                copy(old, prior, next, "result-item.unnamed-name", "entries.result.source-item.default-name");
        }
    }

    /** The shipped 2.0 files had no schema marker and left several shared controls in Java. */
    private YamlConfiguration legacyGuiDefault(String name, boolean released20) throws IOException {
        return resource((released20 ? "legacy-gui-v0/" : "legacy-gui-v1/") + name + ".yml");
    }

    private void requireLegacyLayout(String name, YamlConfiguration old, Path file) throws IOException {
        List<String> required = new ArrayList<>(List.of("title", "rows"));
        if (name.equals("custom-recipes")) required.add("content-slots");
        else {
            required.add("grid-slots");
            required.add("result-slot");
        }
        for (String path : required) {
            if (!old.contains(path)) {
                throw new IOException(file + ": missing 2.1 layout field " + path
                        + "; cannot distinguish an owner removal from an old fallback");
            }
        }
    }

    private void item(YamlConfiguration old, YamlConfiguration prior, YamlConfiguration next,
                      String from, String to, boolean button) throws IOException {
        if (!old.contains(from)) {
            if (button || to.equals("filler")) next.set(to + ".enabled", false);
            else if (next.contains(to)) next.set(to, null);
            return;
        }
        if (!old.isConfigurationSection(from)) {
            logInvalid(from, old.get(from), next.get(to));
            return;
        }
        for (String required : button || to.startsWith("decorations.")
                ? List.of("slot", "material") : List.of("material")) {
            String path = from + "." + required;
            if (!old.contains(path) && prior.contains(path)) {
                throw new IOException("Missing 2.1 GUI item field " + path
                        + "; cannot distinguish an owner removal from an old fallback");
            }
        }
        if (button) copy(old, prior, next, from + ".slot", to + ".slot");
        else if (to.startsWith("decorations.")) singleton(old, prior, next, from + ".slot", to + ".slots");
        for (String field : ITEM_FIELDS) {
            String destination = to + "." + field;
            copy(old, prior, next, from + "." + field, destination,
                    "{sort_options}", "{options}");
        }
    }

    private void sourceLore(YamlConfiguration old, YamlConfiguration prior, YamlConfiguration next,
                            String from, String to) {
        if (!old.contains(from)) {
            next.set(to + ".mode", "preserve");
            next.set(to + ".lines", null);
            return;
        }
        copy(old, prior, next, from, to + ".lines");
    }

    private void singleton(YamlConfiguration old, YamlConfiguration prior, YamlConfiguration next,
                           String from, String to) {
        if (!old.contains(from)) return;
        Object value = old.get(from);
        if (Objects.equals(value, prior.get(from))) return;
        if (value instanceof Integer) next.set(to, List.of(value));
        else logInvalid(from, value, next.get(to));
    }

    private void copy(YamlConfiguration old, YamlConfiguration prior, YamlConfiguration next, String from, String to) {
        copy(old, prior, next, from, to, null, null);
    }

    private void copy(YamlConfiguration old, YamlConfiguration prior, YamlConfiguration next,
                      String from, String to, String find, String replace) {
        if (!old.contains(from)) {
            if (prior.contains(from) && next.contains(to) && isOptional(to)) next.set(to, null);
            return;
        }
        Object value = old.get(from);
        if (Objects.equals(value, prior.get(from))) return;
        if (!validShape(value, prior.get(from)) || !validField(from, value)) {
            logInvalid(from, value, next.get(to));
            return;
        }
        if (find != null) value = replaceTokens(value, find, replace);
        next.set(to, value);
    }

    private boolean isOptional(String path) {
        return path.endsWith(".lore") || path.endsWith(".lines") || path.endsWith(".name")
                || path.endsWith(".custom-model-data") || path.endsWith(".glow") || path.endsWith(".flags")
                || path.equals("entries.recipes.source-item.default-name");
    }

    private boolean validField(String path, Object value) {
        if (path.endsWith(".material")) return value instanceof String text
                && Material.matchMaterial(text) != null && Material.matchMaterial(text).isItem();
        if (path.endsWith(".amount")) return value instanceof Integer number && number >= 1 && number <= 64;
        if (path.endsWith(".custom-model-data")) return value instanceof Integer number && number >= 0;
        if (path.endsWith(".glow")) return value instanceof Boolean;
        if (path.endsWith(".flags")) {
            if (!(value instanceof List<?> flags)) return false;
            for (Object flag : flags) {
                if (!(flag instanceof String name)) return false;
                try { ItemFlag.valueOf(name); } catch (IllegalArgumentException invalid) { return false; }
            }
            return true;
        }
        if (path.endsWith(".lore") || path.endsWith(".lines"))
            return value instanceof List<?> lines && lines.stream().allMatch(String.class::isInstance);
        return true;
    }

    private boolean validShape(Object value, Object reference) {
        if (reference instanceof String) return value instanceof String;
        if (reference instanceof Integer) return value instanceof Integer;
        if (reference instanceof Boolean) return value instanceof Boolean;
        if (reference instanceof List<?>) return value instanceof List<?> list && list.stream().allMatch(entry ->
                entry instanceof String || entry instanceof Integer);
        return value instanceof String || value instanceof Integer || value instanceof Boolean || value instanceof List<?>;
    }

    private Object replaceTokens(Object value, String find, String replace) {
        if (value instanceof String text) return text.replace(find, replace);
        if (value instanceof List<?> lines) return lines.stream().map(line -> line instanceof String text
                ? text.replace(find, replace) : line).toList();
        return value;
    }

    private void copyLabels(String name, YamlConfiguration old, YamlConfiguration prior,
                            YamlConfiguration installed, YamlConfiguration bundled) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (String key : List.of("no-permission", "type-shaped", "type-shapeless", "match-material-only",
                "match-material-meta", "match-strict-all", "cost-none", "cost-levels", "cost-points",
                "cost-money", "cost-separator")) labels.put("labels." + key, key);
        if (name.equals("custom-recipes")) {
            labels.put("items.sort.name-ascending", "sort-name-ascending");
            labels.put("items.sort.name-descending", "sort-name-descending");
            labels.put("items.search.none-label", "search-none");
            labels.put("items.clear-search.target-label", "clear-search-target");
            labels.put("recipe-item.description-line", "browser-description");
            labels.put("recipe-item.locked-line", "browser-locked");
        }
        if (name.equals("recipe-preview")) {
            labels.put("description-line", "preview-description");
            labels.put("status.unlocked", "status-unlocked");
            labels.put("status.locked", "status-locked");
            labels.put("status.world-blocked", "status-world-blocked");
        }
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            String source = entry.getKey();
            String target = "public-gui.menu." + name + "." + entry.getValue();
            if (!old.contains(source)) {
                if (prior.contains(source)) installed.set(target, "");
                continue;
            }
            Object value = old.get(source);
            if (Objects.equals(value, prior.get(source))) continue;
            if (value instanceof String text) installed.set(target, text);
            else {
                String fallback = bundled.getString("public-gui." + entry.getValue());
                installed.set(target, fallback);
                logInvalid(source, value, fallback);
            }
        }
    }

    private PublicGuiDefinition validate(String name, YamlConfiguration yaml) throws IOException {
        Set<String> actions = switch (name) {
            case "custom-crafting" -> Set.of("craft.browse");
            case "custom-recipes" -> Set.of("recipes.previous", "recipes.next", "recipes.search",
                    "recipes.clear-search", "recipes.back", "recipes.sort", "recipes.view");
            default -> Set.of("preview.back");
        };
        Map<String, PublicGuiContract.GroupRule> groups = name.equals("custom-crafting")
                ? Map.of("grid-slots", PublicGuiContract.GroupRule.exactly(
                        PublicGuiContract.GroupRole.PHYSICAL_INPUT, 9)) : Map.of();
        PublicGuiContract contract = new PublicGuiContract(actions, groups,
                Set.of("recipe_id", "recipe_name", "description", "permission", "type", "match_mode", "cost",
                        "locked", "max_craftable", "world", "current", "target", "page", "max_page", "status", "material"),
                name.equals("custom-crafting") ? PublicGuiContract.InteractionMode.PLUGIN_OWNED
                        : PublicGuiContract.InteractionMode.CORE);
        YamlConfiguration bundled = resource("guis/" + name + ".yml");
        try {
            return PublicGuiStartupFallback.parse(name, name + ".yml", yaml, bundled, contract, replacement ->
                    logInvalid(replacement.path(), replacement.invalidValue(), replacement.defaultValue()));
        } catch (GuiDefinitionException error) {
            throw new IOException(name + ": " + error.key() + ": " + error.getMessage(), error);
        }
    }

    private YamlConfiguration resource(String name) throws IOException {
        try (InputStream stream = resources.apply(name)) {
            if (stream == null) throw new IOException("Missing bundled resource " + name);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        } catch (InvalidConfigurationException error) {
            throw new IOException("Invalid bundled resource " + name, error);
        }
    }

    private byte[] resourceBytes(String name) throws IOException {
        try (InputStream stream = resources.apply(name)) {
            if (stream == null) throw new IOException("Missing bundled resource " + name);
            return stream.readAllBytes();
        }
    }

    private YamlConfiguration parse(byte[] bytes, String name) throws IOException {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(bytes, StandardCharsets.UTF_8));
            return yaml;
        } catch (InvalidConfigurationException error) {
            throw new IOException("Invalid YAML in " + name, error);
        }
    }

    private void replace(Path target, byte[] data) throws IOException {
        Files.createDirectories(target.getParent());
        Path staged = Files.createTempFile(target.getParent(), ".public-gui-", ".yml");
        try {
            Files.write(staged, data);
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private void writeBackupIfMissing(Path backup, byte[] original) throws IOException {
        if (Files.exists(backup)) {
            if (!Files.isRegularFile(backup)) throw new IOException("Migration backup is not a regular file: " + backup);
            return;
        }
        Path staged = Files.createTempFile(backup.getParent(), ".gui-v1-backup-", ".tmp");
        try {
            Files.write(staged, original);
            try {
                Files.move(staged, backup, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(staged, backup);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private void logInvalid(String path, Object oldValue, Object replacement) {
        logger.warning("Public GUI migration replaced invalid " + path + "=" + oldValue
                + " with newest bundled default " + (replacement == null ? "<absent>" : replacement));
    }
}
