package me.foesio.foCrafts.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class VaultHook {
    private final JavaPlugin plugin;
    private Object economyProvider;
    private Class<?> economyClass;

    public VaultHook(JavaPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        this.economyProvider = null;
        this.economyClass = null;

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }

        try {
            Class<?> clazz = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings("unchecked")
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) clazz);
            if (registration == null || registration.getProvider() == null) {
                return;
            }
            this.economyClass = clazz;
            this.economyProvider = registration.getProvider();
        } catch (ClassNotFoundException ignored) {
            // Vault API not present on classpath.
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Vault integration failed to initialize: " + throwable.getMessage());
        }
    }

    public boolean isAvailable() {
        return economyProvider != null && economyClass != null;
    }

    public boolean has(Player player, double amount) {
        if (!isAvailable() || amount <= 0.0D) {
            return amount <= 0.0D;
        }

        try {
            Method hasMethod = economyClass.getMethod("has", org.bukkit.OfflinePlayer.class, double.class);
            Object result = hasMethod.invoke(economyProvider, player, amount);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Vault has() check failed: " + throwable.getMessage());
            return false;
        }
    }

    public boolean withdraw(Player player, double amount) {
        if (!isAvailable() || amount <= 0.0D) {
            return amount <= 0.0D;
        }

        try {
            Method withdrawMethod = economyClass.getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class);
            Object response = withdrawMethod.invoke(economyProvider, player, amount);
            if (response == null) {
                return false;
            }
            Method successMethod = response.getClass().getMethod("transactionSuccess");
            Object success = successMethod.invoke(response);
            return success instanceof Boolean && (Boolean) success;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Vault withdraw failed: " + throwable.getMessage());
            return false;
        }
    }
}
