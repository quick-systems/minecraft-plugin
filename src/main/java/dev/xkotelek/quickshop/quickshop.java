package dev.xkotelek.quickshop;

import dev.xkotelek.quickshop.commands.quickshopCommand;
import dev.xkotelek.quickshop.commands.quickshopTabCompleter;
import dev.xkotelek.quickshop.util.ConfigManager;
import dev.xkotelek.quickshop.util.PurchaseManager;
import dev.xkotelek.quickshop.util.UpdateManager;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class quickshop extends JavaPlugin {
    /** Base URL of the quickshop API. Fixed - it is not a config option. */
    public static final String API_BASE_URL = "https://quickshop.kotelek.dev";

    private ConfigManager configManager;
    private PurchaseManager purchaseManager;

    @Override
    public void onEnable() {
        getLogger().info("Enabling quickshop v" + getDescription().getVersion());

        configManager = new ConfigManager(this);
        configManager.saveDefaultConfig();
        configManager.checkAndFixConfig();

        purchaseManager = new PurchaseManager(this);

        PluginCommand command = getCommand("quickshop");
        if (command != null) {
            command.setExecutor(new quickshopCommand(this));
            command.setTabCompleter(new quickshopTabCompleter());
        } else {
            getLogger().severe("Command 'quickshop' is missing from plugin.yml - commands disabled.");
        }

        long intervalTicks = Math.max(3, getConfig().getInt("checkIntervalSeconds", 5)) * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, purchaseManager::pollOnce, 20L, intervalTicks);

        new UpdateManager(this).checkForUpdates();

        getLogger().info("Enabled quickshop v" + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("Disabled quickshop v" + getDescription().getVersion());
    }

    public PurchaseManager getPurchaseManager() {
        return purchaseManager;
    }
}
