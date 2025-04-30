package dev.xkotelek.quickshop.commands;

import dev.xkotelek.quickshop.quickshop;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class quickshopCommand implements CommandExecutor {

    private final quickshop plugin;

    public quickshopCommand(quickshop plugin) {
        this.plugin = plugin;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§d§lquickshop §5v" + plugin.getDescription().getVersion());
            sender.sendMessage("§dby xKotelek @ https://kotelek.dev");
            sender.sendMessage("");
            sender.sendMessage("§rAvailable commands:");
            sender.sendMessage("§f/quickshop test §d- §fTest your API connection.");
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

        if(args[0].equalsIgnoreCase("test")) {
            if (sender.hasPermission("quickshop.test")) {
                String apiKey = plugin.getConfig().getString("apiKey");

                if (apiKey == null || apiKey.isEmpty()) {
                    sender.sendMessage("§d§lquickshop §r§5| §r§cAPI key not found in config.");
                    return true;
                }

                new Thread(() -> {
                    try {
                        URL url = new URL("https://quickpay.kotelek.dev/api/plugin/test.php");
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        connection.setRequestProperty("x-api-key", apiKey);

                        int responseCode = connection.getResponseCode();
                        if (responseCode == 200) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                            StringBuilder responseBuilder = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                responseBuilder.append(line);
                            }
                            reader.close();

                            String response = responseBuilder.toString();
                            if (response.contains("\"error\":false")) {
                                sender.sendMessage("§d§lquickshop §r§5| §aConnected to the API successfully.");
                            } else {
                                sender.sendMessage("§d§lquickshop §r§5| §cCouldn't connect. Please check your config.");
                            }
                        } else {
                            sender.sendMessage("§d§lquickshop §r§5| §cCouldn't connect. Please check your config.");
                        }
                    } catch (Exception e) {
                        sender.sendMessage("§d§lquickshop §r§5| §cAn error occurred while testing the API: " + e.getMessage());
                    }
                }).start();
            } else {
                sender.sendMessage("§d§lquickshop §r§5| §r§cYou don't have permissions to do that!");
            }
            return true;
        }

        sender.sendMessage("§d§lquickshop §r§5| §rUnknown command. Use §d§l/quickshop help §rfor available commands.");
        return true;
    }
}
