package dev.xkotelek.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

public class UpdateManager {

    private final Plugin plugin;
    private final String repoOwner = "quick-systems";
    private final String repoName = "minecraft-plugin";

    public UpdateManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdates() {
        // 15 minutes interval (15 * 60 * 20 ticków = 18000 ticków)
        int checkInterval = 15 * 60 * 20;

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases/latest", repoOwner, repoName);
                    HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
                    connection.setRequestProperty("Accept", "application/vnd.github+json");
                    connection.setRequestProperty("User-Agent", "UpdateChecker");

                    if (connection.getResponseCode() != 200) {
                        plugin.getLogger().warning("quickshop | Couldn't fetch latest version.");
                        return;
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    reader.close();

                    String jsonResponse = responseBuilder.toString();
                    String latestVersion = parseTagName(jsonResponse);

                    if (latestVersion == null) {
                        plugin.getLogger().warning("quickshop | Couldn't fetch latest version.");
                        return;
                    }

                    String currentVersion = plugin.getDescription().getVersion();

                    if (isVersionOutdated(currentVersion, latestVersion)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            String message = "§d§lquickshop §r§5| §aThere is a new version available!\n§c" + currentVersion + " §5-> §a" + latestVersion + "§r\n\nPlease update the plugin at §dhttps://github.com/quick-systems/minecraft-plugin§r";
                            Bukkit.getOnlinePlayers().stream()
                                    .filter(player -> player.hasPermission("plugin.admin"))
                                    .forEach(player -> player.sendMessage(message));
                            plugin.getLogger().info(message);
                        });
                    }

                } catch (Exception e) {
                    plugin.getLogger().warning("quickshop | Couldn't fetch latest version: " + e.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0, checkInterval);
    }

    private String parseTagName(String jsonResponse) {
        String tagField = "\"tag_name\":\"";
        int index = jsonResponse.indexOf(tagField);
        if (index == -1) return null;
        int start = index + tagField.length();
        int end = jsonResponse.indexOf("\"", start);
        if (end == -1) return null;
        return jsonResponse.substring(start, end);
    }

    private boolean isVersionOutdated(String currentVersion, String latestVersion) {
        int[] current = parseVersion(currentVersion);
        int[] latest = parseVersion(latestVersion);

        int length = Math.max(current.length, latest.length);
        for (int i = 0; i < length; i++) {
            int curr = i < current.length ? current[i] : 0;
            int lat = i < latest.length ? latest[i] : 0;
            if (curr < lat) return true;
            if (curr > lat) return false;
        }
        return false;
    }

    private int[] parseVersion(String version) {
        return Arrays.stream(version.replace("v", "").split("\\."))
                .mapToInt(part -> {
                    try {
                        return Integer.parseInt(part);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .toArray();
    }
}
