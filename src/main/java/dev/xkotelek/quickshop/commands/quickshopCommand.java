package dev.xkotelek.quickshop.commands;

import dev.xkotelek.quickshop.quickshop;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class quickshopCommand implements CommandExecutor {

    private final quickshop plugin;

    public quickshopCommand(quickshop plugin) {
        this.plugin = plugin;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§d§lquickshop §5v1.0");
            sender.sendMessage("§dby xKotelek @ https://kotelek.dev");
            sender.sendMessage("");
            sender.sendMessage("§rAvailable commands:");
            sender.sendMessage("§f/quickshop reload §d- §fReloads config.");
            sender.sendMessage("§f/quickshop help §d- §fShows help.");
            return true;
        }

        if(args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("quickshop.reload")) {
                plugin.reloadConfig();
                sender.sendMessage("§d§lquickshop §r§5| §rThe config has been reloaded.");
            } else {
                sender.sendMessage("§d§lquickshop §r§5| §r§cYou don't have permissions to do that!");
            }
            return true;
        }

        sender.sendMessage("§d§lquickshop §r§5| §rUnknown command. Use §d§l/quickshop§r help for available commands.");
        return true;
    }
}
