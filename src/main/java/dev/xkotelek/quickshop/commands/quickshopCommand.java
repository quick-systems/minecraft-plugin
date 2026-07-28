package dev.xkotelek.quickshop.commands;

import dev.xkotelek.quickshop.quickshop;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class quickshopCommand implements CommandExecutor {

    private static final String PREFIX = "§d§lquickshop §r§5| §r";

    private final quickshop plugin;

    public quickshopCommand(quickshop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§d§lquickshop §5v" + plugin.getDescription().getVersion());
            sender.sendMessage("§dby xKotelek @ https://kotelek.dev");
            sender.sendMessage("");
            sender.sendMessage("§rAvailable commands:");
            sender.sendMessage("§f/quickshop test §d- §fTest your API connection.");
            sender.sendMessage("§f/quickshop reload §d- §fReloads config.");
            sender.sendMessage("§f/quickshop help §d- §fShows help.");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("quickshop.reload")) {
                sender.sendMessage(PREFIX + "§cYou don't have permission to do that!");
                return true;
            }
            plugin.getPurchaseManager().reload();
            sender.sendMessage(PREFIX + "The config has been reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("test")) {
            if (!sender.hasPermission("quickshop.test")) {
                sender.sendMessage(PREFIX + "§cYou don't have permission to do that!");
                return true;
            }
            runTest(sender);
            return true;
        }

        sender.sendMessage(PREFIX + "Unknown command. Use §d§l/quickshop help §rfor available commands.");
        return true;
    }

    private void runTest(CommandSender sender) {
        String apiKey = plugin.getConfig().getString("apiKey", "");
        String shopId = plugin.getConfig().getString("shopId", "");
        String base = plugin.getConfig().getString("apiBaseUrl", "https://quickshop.kotelek.dev");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        if (apiKey.isEmpty() || shopId.isEmpty()) {
            sender.sendMessage(PREFIX + "§cSet shopId and apiKey in the config first.");
            return;
        }

        final String url = base + "/api/plugin/test?shop_id=" + encode(shopId);
        final String key = apiKey;

        // Off the main thread; results are sent back on it.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String message;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("x-api-key", key);
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int code = connection.getResponseCode();
                java.io.InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();

                StringBuilder sb = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                    }
                }
                connection.disconnect();

                if (code >= 200 && code < 300 && sb.toString().contains("\"error\":false")) {
                    message = PREFIX + "§aConnected to the API successfully.";
                } else {
                    message = PREFIX + "§cCouldn't connect. Check your shopId, apiKey and apiBaseUrl.";
                }
            } catch (Exception e) {
                message = PREFIX + "§cError while testing the API: " + e.getMessage();
            }

            final String result = message;
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(result));
        });
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}
