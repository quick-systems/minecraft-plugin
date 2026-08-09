package dev.xkotelek.quickshop.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xkotelek.quickshop.quickshop;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PurchaseManager {
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_MS = 10000;

    private final quickshop plugin;

    private boolean debug;
    private String shopId;
    private String apiKey;
    private String serverIp;
    private boolean deliverToOffline;
    private boolean broadcastBought;
    private List<String> boughtMessages;

    public PurchaseManager(quickshop plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Re-reads config values so /quickshop reload takes effect without a restart. */
    public void reload() {
        plugin.reloadConfig();
        debug = plugin.getConfig().getBoolean("debug");
        shopId = plugin.getConfig().getString("shopId", "");
        apiKey = plugin.getConfig().getString("apiKey", "");
        String rawIp = plugin.getConfig().getString("serverInternalIp", "");
        serverIp = rawIp == null ? "" : rawIp.trim();
        deliverToOffline = plugin.getConfig().getBoolean("deliverToOfflinePlayers", true);
        broadcastBought = plugin.getConfig().getBoolean("broadcastBoughtMessage", true);
        boughtMessages = plugin.getConfig().getStringList("boughtMessage");
    }

    private boolean configured() {
        return shopId != null && !shopId.isEmpty() && apiKey != null && !apiKey.isEmpty();
    }

    public void pollOnce() {
        if (!configured()) {
            if (debug) plugin.getLogger().warning("shopId or apiKey is not set - skipping poll.");
            return;
        }

        final JsonArray orders;
        try {
            String url = quickshop.API_BASE_URL + "/api/plugin/orders/undelivered?shop_id=" + enc(shopId);
            if (serverIp != null && !serverIp.isEmpty()) url += "&ip=" + enc(serverIp);
            JsonObject response = httpGetJson(url);
            if (response == null) return;

            if (response.has("error") && response.get("error").getAsBoolean()) {
                if (debug) plugin.getLogger().warning("API error: " + optString(response, "message", "unknown"));
                return;
            }
            orders = response.getAsJsonArray("orders");
        } catch (Exception e) {
            if (debug) plugin.getLogger().warning("Could not fetch orders: " + e.getMessage());
            return;
        }

        if (orders == null || orders.size() == 0) return;

        // Apply on the main thread - Bukkit calls are not thread safe.
        Bukkit.getScheduler().runTask(plugin, () -> applyOrders(orders));
    }

    private void applyOrders(JsonArray orders) {
        for (JsonElement element : orders) {
            if (!element.isJsonObject()) continue;
            JsonObject order = element.getAsJsonObject();

            final String orderId = optString(order, "id", null);
            if (orderId == null) continue;

            String playerName = optString(order, "nickname", "");
            if (playerName.isEmpty()) playerName = "unknown player";
            final String itemName = optString(order, "product", "unknown product");
            final String command = extractCommand(order, playerName, orderId);
            final boolean broadcast = shouldBroadcast(order);

            // Hold delivery until the player is online, if configured that way.
            if (!deliverToOffline) {
                Player target = Bukkit.getPlayerExact(playerName);
                if (target == null) {
                    if (debug) plugin.getLogger().info("Holding order " + orderId + " - " + playerName + " is offline.");
                    continue;
                }
            }

            final String fPlayer = playerName;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean ok = markAsDelivered(orderId);
                if (!ok) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendBoughtMessages(fPlayer, itemName, broadcast);
                    if (command != null && !command.isEmpty()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    }
                });
            });
        }
    }

    private String extractCommand(JsonObject order, String playerName, String orderId) {
        if (!order.has("actions")) return null;
        String actionsRaw = order.get("actions").getAsString();
        if (actionsRaw == null || actionsRaw.isEmpty()) return null;

        try {
            JsonArray actions = GSON.fromJson(actionsRaw, JsonArray.class);
            for (JsonElement actionElement : actions) {
                JsonObject action = actionElement.getAsJsonObject();
                if (action.has("type") && "command".equalsIgnoreCase(action.get("type").getAsString())) {
                    return action.get("command").getAsString()
                            .replace("{{player}}", playerName)
                            .replace("%player%", playerName);
                }
            }
        } catch (Exception e) {
            if (debug) plugin.getLogger().warning("Could not parse actions for order " + orderId);
        }
        return null;
    }

    /** Tells the API an order was handled. Runs off the main thread. */
    private boolean markAsDelivered(String orderId) {
        try {
            String url = quickshop.API_BASE_URL + "/api/plugin/orders/complete";
            String payload = "{\"shop_id\":\"" + shopId + "\",\"order_id\":\"" + orderId + "\"}";
            JsonObject resp = httpPostJson(url, payload);
            if (resp == null) return false;

            if (resp.has("error") && resp.get("error").getAsBoolean()) {
                if (debug) plugin.getLogger().warning("Could not complete order " + orderId + ": " + optString(resp, "message", "unknown"));
                return false;
            }
            if (debug) plugin.getLogger().info("Order " + orderId + " marked delivered.");
            return true;
        } catch (Exception e) {
            if (debug) plugin.getLogger().warning("Could not complete order " + orderId + ": " + e.getMessage());
            return false;
        }
    }

    private boolean shouldBroadcast(JsonObject order) {
        for (String key : new String[]{"broadcast", "broadcast_message"}) {
            if (order.has(key) && !order.get(key).isJsonNull()) {
                try {
                    return order.get(key).getAsBoolean();
                } catch (Exception ignored) {
                    // Not a boolean - try the next key, then the config.
                }
            }
        }
        return broadcastBought;
    }

    /** Sends the bought message to everyone, or only to the buyer. */
    private void sendBoughtMessages(String playerName, String itemName, boolean broadcast) {
        if (boughtMessages == null || boughtMessages.isEmpty()) return;

        Player buyer = broadcast ? null : Bukkit.getPlayerExact(playerName);
        if (!broadcast && buyer == null) {
            if (debug) plugin.getLogger().info("Skipping the bought message - " + playerName + " is offline.");
            return;
        }

        for (String message : boughtMessages) {
            String out = message == null || message.isEmpty()
                    ? ""
                    : ChatColor.translateAlternateColorCodes('&',
                            message.replace("%player%", playerName).replace("%item%", itemName));
            if (broadcast) {
                Bukkit.broadcastMessage(out);
            } else {
                buyer.sendMessage(out);
            }
        }
    }

    // --- HTTP helpers (JDK only, with timeouts) ---

    private JsonObject httpGetJson(String url) throws Exception {
        HttpURLConnection connection = open(url, "GET");
        return readJson(connection);
    }

    private JsonObject httpPostJson(String url, String body) throws Exception {
        HttpURLConnection connection = open(url, "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return readJson(connection);
    }

    private HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("x-api-key", apiKey);
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        return connection;
    }

    private JsonObject readJson(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        java.io.InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) return null;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        } finally {
            connection.disconnect();
        }

        JsonElement parsed = GSON.fromJson(sb.toString(), JsonElement.class);
        return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    }

    private static String optString(JsonObject obj, String key, String fallback) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : fallback;
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

}
