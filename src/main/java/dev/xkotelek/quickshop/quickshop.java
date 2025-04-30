package dev.xkotelek.quickshop;

import dev.xkotelek.quickshop.commands.quickshopCommand;
import dev.xkotelek.quickshop.commands.quickshopTabCompleter;
import dev.xkotelek.quickshop.util.ConfigManager;
import dev.xkotelek.quickshop.util.PurchaseManager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;


public final class quickshop extends JavaPlugin {

    private ConfigManager configManager;
    private PurchaseManager purchaseManager;

    @Override
    public void onEnable() {
        Bukkit.getLogger().info("quickshop | Enabling quickshop v" + getDescription().getVersion());

        configManager = new ConfigManager(this);
        purchaseManager = new PurchaseManager(this);

        configManager.saveDefaultConfig();
        configManager.checkAndFixConfig();

        getCommand("quickshop").setExecutor(new quickshopCommand(this));
        getCommand("quickshop").setTabCompleter(new quickshopTabCompleter());

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, purchaseManager::checkForPurchases, 0, 100);

        Bukkit.getLogger().info("quickshop | Enabled quickshop v" + getDescription().getVersion());

        new dev.xkotelek.util.UpdateManager(this).checkForUpdates();
    }

    @Override
    public void onDisable() {
        Bukkit.getLogger().info("quickshop | Disabling quickshop v" + getDescription().getVersion());
        Bukkit.getLogger().info("quickshop | Disabled quickshop v" + getDescription().getVersion());
    }
}
