package dev.xkotelek.quickshop.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class quickshopTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if(args.length >= 1) {
            if("test".startsWith(args[0].toLowerCase())) {
                suggestions.add("test");
            }
            if ("reload".startsWith(args[0].toLowerCase())) {
                suggestions.add("reload");
            }
            if ("help".startsWith(args[0].toLowerCase())) {
                suggestions.add("help");
            }
        }

        return suggestions;
    }

}
