package me.giridharan.firstplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MusicCommand implements CommandExecutor, TabCompleter {

    private final FirstPlugin plugin;
    private final MusicManager musicManager;

    public MusicCommand(FirstPlugin plugin, MusicManager musicManager) {
        this.plugin = plugin;
        this.musicManager = musicManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // Support /music disk generate <name> OR /music disc generate <name>
        if (sub.equals("disk") || sub.equals("disc")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /music disk generate <name> [player]", NamedTextColor.RED));
                return true;
            }

            String action = args[1].toLowerCase();
            if (action.equals("generate") || action.equals("genrate") || action.equals("create")) {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Please specify a song name or ID! Example: /music disk generate 1", NamedTextColor.RED));
                    return true;
                }

                String songId = args[2];
                Player targetPlayer;

                if (args.length >= 4) {
                    targetPlayer = Bukkit.getPlayer(args[3]);
                    if (targetPlayer == null || !targetPlayer.isOnline()) {
                        sender.sendMessage(Component.text("Player not found: " + args[3], NamedTextColor.RED));
                        return true;
                    }
                } else {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(Component.text("From console, you must specify a player: /music disk generate <name> <player>", NamedTextColor.RED));
                        return true;
                    }
                    targetPlayer = p;
                }

                // Check if song exists in music folder
                boolean exists = musicManager.songExists(songId);
                ItemStack disc = musicManager.createDisc(songId);

                // Add to inventory (or drop at feet if full)
                var overflow = targetPlayer.getInventory().addItem(disc);
                if (!overflow.isEmpty()) {
                    for (ItemStack item : overflow.values()) {
                        targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), item);
                    }
                    targetPlayer.sendMessage(Component.text("Inventory was full! Disc dropped at your feet.", NamedTextColor.YELLOW));
                }

                targetPlayer.sendMessage(Component.text("⚡ You received Custom Music Disc: ", NamedTextColor.GREEN)
                        .append(Component.text(songId, NamedTextColor.GOLD)));

                if (!exists) {
                    targetPlayer.sendMessage(Component.text("Note: Song file '" + songId + ".ogg' was not yet detected in plugins/CustomMusicDiscs/music/ folder.", NamedTextColor.GRAY));
                }

                if (sender != targetPlayer) {
                    sender.sendMessage(Component.text("Gave disc '" + songId + "' to " + targetPlayer.getName(), NamedTextColor.GREEN));
                }
                return true;
            }
        }

        // Direct /music generate <name> [player]
        if (sub.equals("generate") || sub.equals("genrate")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /music generate <name> [player]", NamedTextColor.RED));
                return true;
            }
            // Delegate by redirecting to the disk generate handler
            String[] newArgs = new String[args.length + 1];
            newArgs[0] = "disk";
            System.arraycopy(args, 0, newArgs, 1, args.length);
            return onCommand(sender, command, label, newArgs);
        }

        // /music list
        if (sub.equals("list")) {
            Set<String> songs = musicManager.getSongIds();
            if (songs.isEmpty()) {
                sender.sendMessage(Component.text("No audio files detected in plugins/CustomMusicDiscs/music/ folder.", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Drop your .ogg or .mp3 files into plugins/CustomMusicDiscs/music/ and type /music reload", NamedTextColor.GRAY));
            } else {
                sender.sendMessage(Component.text("=== Available Music Tracks (" + songs.size() + ") ===", NamedTextColor.GOLD));
                for (String s : songs) {
                    sender.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(s, NamedTextColor.YELLOW))
                            .append(Component.text(" -> /music disk generate " + s, NamedTextColor.GRAY)));
                }
            }
            return true;
        }

        // /music stop
        if (sub.equals("stop")) {
            if (sender instanceof Player p) {
                musicManager.stopPortable(p);
            } else {
                musicManager.stopAll();
                sender.sendMessage(Component.text("Stopped all active music.", NamedTextColor.RED));
            }
            return true;
        }

        // /music reload
        if (sub.equals("reload")) {
            plugin.reloadConfig();
            musicManager.scanSongs();
            sender.sendMessage(Component.text("Music folder re-scanned and config reloaded successfully!", NamedTextColor.GREEN));
            return true;
        }

        // /music pack
        if (sub.equals("pack")) {
            sender.sendMessage(Component.text("=== Resource Pack Info ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("• Java Edition Pack: ", NamedTextColor.YELLOW)
                    .append(Component.text("plugins/CustomMusicDiscs/CustomMusicPack.zip", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("• Bedrock Edition Pack: ", NamedTextColor.YELLOW)
                    .append(Component.text("plugins/CustomMusicDiscs/CustomMusicPack-Bedrock.mcpack", NamedTextColor.WHITE)));

            File pluginsDir = plugin.getDataFolder().getParentFile();
            File geyserPacks = (pluginsDir != null) ? new File(pluginsDir, "Geyser-Spigot/packs/CustomMusicPack-Bedrock.mcpack") : null;
            if (geyserPacks != null && geyserPacks.exists()) {
                sender.sendMessage(Component.text("✔ Geyser Status: Installed in plugins/Geyser-Spigot/packs/", NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Bedrock players will download this pack automatically when connecting!", NamedTextColor.AQUA));
            } else {
                sender.sendMessage(Component.text("ℹ Note for Bedrock (Geyser): Copy CustomMusicPack-Bedrock.mcpack into your Geyser packs/ directory so Bedrock players hear music.", NamedTextColor.GRAY));
            }
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== ♫ Custom Music Discs ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/music disk generate <name> [player] ", NamedTextColor.YELLOW)
                .append(Component.text("- Generate custom disc into inventory", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/music list ", NamedTextColor.YELLOW)
                .append(Component.text("- List all scanned music files", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/music stop ", NamedTextColor.YELLOW)
                .append(Component.text("- Stop portable music", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/music reload ", NamedTextColor.YELLOW)
                .append(Component.text("- Re-scan music folder & reload config", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/music pack ", NamedTextColor.YELLOW)
                .append(Component.text("- View resource pack folder location", NamedTextColor.GRAY)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = List.of("disk", "disc", "list", "stop", "reload", "pack");
            return filter(options, args[0]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("disk") || args[0].equalsIgnoreCase("disc"))) {
            return filter(List.of("generate"), args[1]);
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("disk") || args[0].equalsIgnoreCase("disc")) && args[1].equalsIgnoreCase("generate")) {
            return filter(new ArrayList<>(musicManager.getSongIds()), args[2]);
        }

        if (args.length == 4 && (args[0].equalsIgnoreCase("disk") || args[0].equalsIgnoreCase("disc")) && args[1].equalsIgnoreCase("generate")) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            return filter(playerNames, args[3]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        String p = prefix.toLowerCase();
        return list.stream().filter(s -> s.toLowerCase().startsWith(p)).collect(Collectors.toList());
    }
}
