package dev.xkotelek.quickshop.util;

import dev.xkotelek.quickshop.quickshop;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

public class ConfigManager {

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void saveDefaultConfig() {
        plugin.saveDefaultConfig();
    }

    public void checkAndFixConfig() {
        FileConfiguration config = plugin.getConfig();
        boolean modified = false;

        if (!config.isSet("debug") || !config.get("debug").getClass().equals(Boolean.class)) {
            config.set("debug", false);
            modified = true;
        }

        if(!config.isSet("shopId") || !(config.get("shopId") instanceof String)) {
            config.set("shopId", "");
            modified = true;
        }

        if (!config.isSet("apiKey") || !(config.get("apiKey") instanceof String)) {
            config.set("apiKey", "");
            modified = true;
        }

        if (!config.isSet("apiBaseUrl") || !(config.get("apiBaseUrl") instanceof String)) {
            config.set("apiBaseUrl", "https://quickshop.kotelek.dev");
            modified = true;
        }

        if (!config.isSet("checkIntervalSeconds") || !(config.get("checkIntervalSeconds") instanceof Integer)) {
            config.set("checkIntervalSeconds", 5);
            modified = true;
        }

        if (!config.isSet("deliverToOfflinePlayers") || !(config.get("deliverToOfflinePlayers") instanceof Boolean)) {
            config.set("deliverToOfflinePlayers", true);
            modified = true;
        }

        if(!config.isSet("boughtMessage") || !(config.get("boughtMessage") instanceof Iterable<?>)) {
            config.set("boughtMessage", Arrays.asList(
                    "&lPLAYER &d&l%player%&r&l BOUGHT",
                    "",
                    "  &d&l%item%",
                    ""
            ));
            modified = true;
        }

        if(modified) {
            Bukkit.getLogger().info("quickshop | Modifying config with default values...");
            plugin.saveConfig();
            plugin.reloadConfig();
            Bukkit.getLogger().info("quickshop | Config updated.");
        } else {
            Bukkit.getLogger().info("quickshop | Config is already up-to-date.");
        }
    }

}
