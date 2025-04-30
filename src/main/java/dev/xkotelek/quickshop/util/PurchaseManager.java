package dev.xkotelek.quickshop.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xkotelek.quickshop.quickshop;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.IOException;
import java.util.List;

public class PurchaseManager {

    private final ConfigManager configManager;
    private final quickshop plugin;
    private static final Gson gson = new Gson();

    private static boolean debug;
    private static String shopId;
    private static String apiKey;
    private static List<String> boughtMessages;

    public PurchaseManager(quickshop plugin) {
        this.plugin = plugin;
        this.configManager = new ConfigManager(plugin);
        loadConfigValues();
    }

    private void loadConfigValues() {
        debug = plugin.getConfig().getBoolean("debug");
        shopId = plugin.getConfig().getString("shopId");
        apiKey = plugin.getConfig().getString("apiKey");
        boughtMessages = plugin.getConfig().getStringList("boughtMessage");
    }

    public boolean checkConfigValues() {
        if (!plugin.getConfig().contains("debug") || shopId == null || apiKey == null) {
            configManager.checkAndFixConfig();
            return false;
        }
        return true;
    }

    public void checkForPurchases() {
        if (!checkConfigValues()) {
            if (debug) {
                Bukkit.getLogger().warning("quickshop | Invalid config values: shopId or apiKey is missing.");
            }
            return;
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = "https://quickpay.kotelek.dev/api/plugin/orders/undelivered.php?shop_id=" + shopId;
            HttpGet httpget = new HttpGet(url);
            httpget.addHeader("x-api-key", apiKey);
            HttpEntity entity = httpClient.execute(httpget).getEntity();

            if (entity != null) {
                String result = EntityUtils.toString(entity);
                JsonObject response = gson.fromJson(result, JsonObject.class);

                if (response.has("error") && response.get("error").getAsBoolean()) {
                    if (debug) {
                        String msg = response.has("message") ? response.get("message").getAsString() : "Unknown error";
                        Bukkit.getLogger().warning("quickshop | API error: " + msg);
                    }
                    return;
                }

                JsonArray orders = response.getAsJsonArray("orders");
                for (JsonElement element : orders) {
                    JsonObject order = element.getAsJsonObject();
                    String orderId = order.get("id").getAsString();
                    String playerName = order.has("nickname") ? order.get("nickname").getAsString() : "unknown player";
                    if (playerName.isEmpty()) playerName = "unknown player";

                    String itemName = order.has("product") ? order.get("product").getAsString() : "unknown product";

                    String command = "NONE";

                    if (order.has("actions") && !order.get("actions").getAsString().isEmpty()) {
                        try {
                            JsonArray actions = gson.fromJson(order.get("actions").getAsString(), JsonArray.class);
                            for (JsonElement actionElement : actions) {
                                JsonObject action = actionElement.getAsJsonObject();
                                if (action.has("type") && action.get("type").getAsString().equalsIgnoreCase("command")) {
                                    command = action.get("command").getAsString().replace("{{player}}", playerName);
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            if (debug) {
                                Bukkit.getLogger().warning("quickshop | Failed to parse actions for order " + orderId);
                            }
                        }
                    }

                    if (debug) {
                        System.out.println("quickshop | Order - Player: " + playerName + ", Item: " + itemName + ", Command: " + command);
                    }

                    markAsDelivered(orderId);
                    broadcastMessages(playerName, itemName);

                    if (!command.equals("NONE")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    }
                }
            }
        } catch (IOException e) {
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    private static void markAsDelivered(String orderId) {
        String url = "https://quickpay.kotelek.dev/api/plugin/orders/complete.php"
                + "?shop_id=" + shopId
                + "&order_id=" + orderId;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpget = new HttpGet(url);
            httpget.addHeader("x-api-key", apiKey);

            HttpEntity entity = httpClient.execute(httpget).getEntity();
            if (entity == null) {
                if (debug) {
                    Bukkit.getLogger().warning("quickshop | markAsDelivered: empty response for order " + orderId);
                }
                return;
            }

            String json = EntityUtils.toString(entity);
            JsonElement respElement = gson.fromJson(json, JsonElement.class);
            if (respElement.isJsonObject()) {
                JsonObject resp = respElement.getAsJsonObject();

                boolean error = resp.has("error") && resp.get("error").getAsBoolean();
                if (error) {
                    if (debug) {
                        String msg = resp.has("message") ? resp.get("message").getAsString() : "no message";
                        Bukkit.getLogger().warning("quickshop | API error for order " + orderId + ": " + msg);
                    }
                    return;
                }

                if (resp.has("message")) {
                    String message = resp.get("message").getAsString();
                    if ("order completed".equalsIgnoreCase(message)) {
                        if (debug) {
                            System.out.println("quickshop | Order " + orderId + " successfully marked delivered.");
                        }
                    } else {
                        if (debug) {
                            Bukkit.getLogger().warning("quickshop | Unexpected message for order " + orderId + ": " + message);
                        }
                    }
                } else {
                    if (debug) {
                        Bukkit.getLogger().warning("quickshop | No 'message' field in response for order " + orderId);
                    }
                }
            } else {
                if (debug) {
                    Bukkit.getLogger().warning("quickshop | Response is not a JSON object for order " + orderId + ". Raw response: " + json);
                }
            }

        } catch (IOException e) {
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    private static void broadcastMessages(String playerName, String itemName) {
        for (String message : boughtMessages) {
            if (message != null && !message.isEmpty()) {
                message = message.replace("%player%", playerName).replace("%item%", itemName);
                message = ChatColor.translateAlternateColorCodes('&', message);
                Bukkit.broadcastMessage(message);
            } else {
                Bukkit.broadcastMessage("");
            }
        }
    }

}