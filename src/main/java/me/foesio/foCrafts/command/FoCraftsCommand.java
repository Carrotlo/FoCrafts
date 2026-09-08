package me.foesio.foCrafts.command;

import me.foesio.core.command.FoAdminArguments;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foCrafts.FoCrafts;
import me.foesio.foCrafts.gui.GuiManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FoCraftsCommand implements CommandExecutor, TabCompleter {
    private final FoCrafts plugin;
    private final GuiManager guiManager;
    private final FoMessageService messages;
    private final UpdateNoticeService updates;

    public FoCraftsCommand(FoCrafts plugin, GuiManager guiManager, FoMessageService messages, UpdateNoticeService updates) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.messages = messages;
        this.updates = updates;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("craft")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "player-only");
                return true;
            }
            if (!hasUsePermission(player)) {
                messages.send(sender, "no-permission");
                plugin.getAdminSounds().updateError(sender);
                return true;
            }
            guiManager.openCraftGui(player);
            return true;
        }

        if (commandName.equals("recipes")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "player-only");
                return true;
            }
            if (!hasUsePermission(player)) {
                messages.send(player, "no-permission");
                plugin.getAdminSounds().updateError(sender);
                return true;
            }
            guiManager.openRecipeListGui(player, 0);
            return true;
        }

        if (commandName.equals("focraftsadmin")) {
            if (!sender.hasPermission("focrafts.admin")) {
                messages.send(sender, "no-permission");
                plugin.getAdminSounds().updateError(sender);
                return true;
            }

            if (args.length == 0) {
                messages.send(sender, "admin-usage");
                plugin.getAdminSounds().updateError(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("version")) {
                updates.checkAndSendVersion(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("editor")) {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "player-only");
                    return true;
                }
                plugin.getEditorSounds().open(player);
                guiManager.openAdminListGui(player, 0);
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                boolean ok = plugin.reloadPluginData();
                messages.send(sender, ok ? "reload-success" : "reload-failed");
                if (sender instanceof Player player) {
                    if (ok) {
                        plugin.getAdminSounds().reload(player);
                    } else {
                        plugin.getAdminSounds().reloadError(player);
                    }
                }
                return true;
            }

            messages.send(sender, "admin-usage");
            plugin.getAdminSounds().updateError(sender);
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("craft")) {
            return List.of();
        }

        if (commandName.equals("focraftsadmin")) {
            if (args.length == 1) {
                return FoAdminArguments.completeOptions(List.of("version", "editor", "reload"), args[0]);
            }
            return List.of();
        }
        return List.of();
    }

    private boolean hasUsePermission(Player player) {
        return player.hasPermission("focrafts.use");
    }
}
