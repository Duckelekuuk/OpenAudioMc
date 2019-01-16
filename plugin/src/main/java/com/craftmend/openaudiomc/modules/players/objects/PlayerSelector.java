package com.craftmend.openaudiomc.modules.players.objects;

import com.craftmend.openaudiomc.OpenAudioMc;
import com.craftmend.openaudiomc.modules.regions.RegionModule;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public class PlayerSelector {

    private String selector;

    public List<Player> getPlayers(CommandSender commandSender) {

        List<Player> players = new ArrayList<>();

        if (selector.startsWith("@p")) {
            //get Location
            Location standPoint = getLocation(commandSender);

            if (getArgument("r").length() != 0) {
                Player nearest = Bukkit.getOnlinePlayers().stream()
                        .filter(player -> player.getLocation().getWorld().getName().equals(standPoint.getWorld().getName()))
                        .min(Comparator.comparing(player -> player.getLocation().distance(standPoint)))
                        .filter(player -> Integer.valueOf(getArgument("r")) > player.getLocation().distance(standPoint))
                        .get();
                players.add(nearest);
            }

            else {
                Bukkit.getOnlinePlayers().stream()
                        .filter(player -> player.getLocation().getWorld().getName().equals(standPoint.getWorld().getName()))
                        .min(Comparator.comparing(player -> player.getLocation().distance(standPoint)))
                        .ifPresent(players::add);
            }
        }
        else if (selector.startsWith("@a")) {
            //everyone
            Location standPoint = getLocation(commandSender);

            if (getArgument("region").length() != 0) {
                RegionModule regionModule = OpenAudioMc.getInstance().getRegionModule();
                String targetRegion = getArgument("region");
                if (regionModule != null) {
                    Bukkit.getOnlinePlayers().forEach(player -> regionModule.getRegions(player.getLocation()).forEach(region -> {
                        if (region.getId().equals(targetRegion)) {
                            players.add(player);
                        }
                    }));
                } else {
                    commandSender.sendMessage(OpenAudioMc.getLOG_PREFIX() + "You dont have worldguard installed. Skipping the region argument.");
                }
            }

            if (getArgument("r").length() != 0) {
                players.addAll(Bukkit.getOnlinePlayers().stream()
                        .filter(player -> player.getLocation().getWorld().getName().equals(standPoint.getWorld().getName()))
                        .filter(player -> Integer.valueOf(getArgument("r")) > player.getLocation().distance(standPoint))
                        .collect(Collectors.toList()));
            }

            else {
                players.addAll(Bukkit.getOnlinePlayers().stream()
                        .filter(player -> player.getLocation().getWorld().getName().equals(standPoint.getWorld().getName()))
                        .collect(Collectors.toList()));
            }
        }
        else if (selector.length() <= 16) {
            //player
            Player player = Bukkit.getPlayer(selector);
            if (player != null) players.add(player);
        }
        else {
            //you fucked it
            commandSender.sendMessage(OpenAudioMc.getLOG_PREFIX() + "Invalid player query. Try something like @a, @p, uuid, username or other arguments.");
        }
        return players;
    }

    private Location getLocation(CommandSender commandSender) {
        Location initialLocation = null;
        if (commandSender instanceof Player) {
            initialLocation = ((Player) commandSender).getLocation();
        } else if (commandSender instanceof BlockCommandSender) {
            initialLocation = ((BlockCommandSender) commandSender).getBlock().getLocation();
        }

        if (!getArgument("x").equals("") && !getArgument("y").equals("") && !getArgument("z").equals("")) {
            try {
                int x = Integer.valueOf(getArgument("x"));
                int y = Integer.valueOf(getArgument("y"));
                int z = Integer.valueOf(getArgument("z"));
                assert initialLocation != null;
                return new Location(initialLocation.getWorld(), x, y, z);
            } catch (Exception e) {
                commandSender.sendMessage(OpenAudioMc.getLOG_PREFIX() + "An error occurred when parsing the location as an Integer");
                return initialLocation;
            }
        }

        return initialLocation;
    }

    private String getArgument(String key) {
        StringBuilder result = new StringBuilder();
        String[] arguments = selector.split(key + "=");
        if (arguments.length == 1) return "";
        for (byte type : arguments[1].getBytes()) {
            char element = (char) type;
            if (element == ',' || element == ']') {
                return result.toString();
            } else {
                result.append(element);
            }
        }
        return result.toString();
    }

}
