package me.foesio.foCrafts.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;

final class YamlFileUpdater {
    private YamlFileUpdater() {
    }

    static YamlConfiguration update(JavaPlugin plugin,
                                    String resourceName,
                                    int targetSchemaVersion,
                                    Set<String> addedPaths,
                                    Map<String, Object> exactDefaultReplacements) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
            YamlConfiguration created = loadFile(plugin, file);
            return created == null ? new YamlConfiguration() : created;
        }

        YamlConfiguration current = loadFile(plugin, file);
        YamlConfiguration defaults = loadResource(plugin, resourceName);
        if (current == null) {
            return defaults == null ? new YamlConfiguration() : defaults;
        }
        if (defaults == null) {
            return current;
        }

        if (migrate(current, defaults, targetSchemaVersion, addedPaths, exactDefaultReplacements)) {
            saveWithBackup(plugin, file, current, targetSchemaVersion);
        }
        return current;
    }

    static boolean migrate(YamlConfiguration current,
                           YamlConfiguration defaults,
                           int targetSchemaVersion,
                           Set<String> addedPaths,
                           Map<String, Object> exactDefaultReplacements) {
        int currentVersion = current.getInt("schema-version", 0);
        if (currentVersion >= targetSchemaVersion) {
            return false;
        }

        boolean changed = false;
        for (Map.Entry<String, Object> entry : exactDefaultReplacements.entrySet()) {
            String path = entry.getKey();
            Object oldDefault = entry.getValue();
            if (oldDefault.equals(current.get(path)) && defaults.isSet(path)) {
                current.set(path, defaults.get(path));
                changed = true;
            }
        }

        for (String path : addedPaths) {
            if (current.isSet(path)) {
                continue;
            }
            if (!defaults.isSet(path)) {
                continue;
            }
            Object value = defaults.get(path);
            if (value instanceof ConfigurationSection) {
                current.createSection(path);
            } else {
                current.set(path, value);
            }
            changed = true;
        }
        current.set("schema-version", targetSchemaVersion);
        return true;
    }

    private static YamlConfiguration loadFile(JavaPlugin plugin, File file) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Could not load " + file.getName() + ": " + exception.getMessage());
            return null;
        }
        return configuration;
    }

    private static YamlConfiguration loadResource(JavaPlugin plugin, String resourceName) {
        InputStream stream = plugin.getResource(resourceName);
        if (stream == null) {
            plugin.getLogger().warning("Missing bundled resource " + resourceName);
            return null;
        }

        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            configuration.load(reader);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Could not load bundled " + resourceName + ": " + exception.getMessage());
            return null;
        }
        return configuration;
    }

    private static void saveWithBackup(JavaPlugin plugin, File file, YamlConfiguration configuration, int targetSchemaVersion) {
        File backup = new File(file.getParentFile(), file.getName() + ".pre-gui-v" + targetSchemaVersion + ".bak");
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        boolean backupCreated = false;
        try {
            if (!backup.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                backupCreated = true;
            }
            Files.writeString(temporary.toPath(), configuration.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not update " + file.getName() + ": " + exception.getMessage());
            if (backupCreated && backup.exists()) {
                try {
                    Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    plugin.getLogger().warning("Restored " + file.getName() + " from " + backup.getName() + ".");
                } catch (IOException rollbackException) {
                    plugin.getLogger().severe("Could not restore " + file.getName() + " from backup: " + rollbackException.getMessage());
                }
            }
            if (temporary.exists() && !temporary.delete()) {
                plugin.getLogger().warning("Could not remove temporary GUI file " + temporary.getName());
            }
        }
    }
}
