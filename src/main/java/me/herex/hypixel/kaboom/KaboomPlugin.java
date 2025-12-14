package me.herex.hypixel.kaboom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;

public class KaboomPlugin extends JavaPlugin {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        config = getConfig();

        // Log enable message
        if (config.getBoolean("show-colors-in-console", true)) {
            getLogger().info(ChatColor.GREEN + "=============================");
            getLogger().info(ChatColor.GREEN + "Kaboom Enabled");
            getLogger().info(ChatColor.GREEN + "=============================");
        } else {
            getLogger().info("=============================");
            getLogger().info("Kaboom Enabled");
            getLogger().info("=============================");
        }
    }

    @Override
    public void onDisable() {
        // Log disable message
        if (config.getBoolean("show-colors-in-console", true)) {
            getLogger().info(ChatColor.RED + "=============================");
            getLogger().info(ChatColor.RED + "Kaboom Disabled");
            getLogger().info(ChatColor.RED + "=============================");
        } else {
            getLogger().info("=============================");
            getLogger().info("Kaboom Disabled");
            getLogger().info("=============================");
        }
    }

    private String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private boolean isWorldEnabled(String worldName) {
        List<String> enabledWorlds = config.getStringList("enable-worlds");

        // If enable-worlds list is empty, all worlds are enabled
        if (enabledWorlds.isEmpty()) {
            return true;
        }

        // Check if world is in the enabled list
        return enabledWorlds.contains(worldName);
    }

    private boolean isPlayerExempt(Player player) {
        return config.getBoolean("use-kaboom.exempt", true) &&
                player.hasPermission("kaboom.exempt");
    }

    private void kaboomPlayer(Player target, Player sender) {
        // Check if world is enabled
        if (!isWorldEnabled(target.getWorld().getName())) {
            if (sender != null) {
                sender.sendMessage(colorize(config.getString("prefix", "&c[Kaboom] ") +
                        "&cKaboom is disabled in this world!"));
            }
            return;
        }

        // Check if player is exempt
        if (isPlayerExempt(target)) {
            if (sender != null) {
                sender.sendMessage(colorize(config.getString("prefix", "&c[Kaboom] ") +
                        config.getString("exempt-message", "You cannot kaboom %player% because he has kaboom.exempt")
                                .replace("%player%", target.getDisplayName())));
            }
            return;
        }

        // Get power from config or use default
        double power = config.getDouble("kaboom-power", 2.0);

        // Launch player into the air
        target.setVelocity(new Vector(0.0, power, 0.0));

        // Lightning effect
        if (config.getBoolean("lightning-effect", true)) {
            target.getWorld().strikeLightningEffect(target.getLocation());
        }

        // Sound effect
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        // Send title to kaboomed player
        if (config.getBoolean("title-for-kaboomed-players.enabled", true) && sender != null) {
            String title = colorize(config.getString("title-for-kaboomed-players.title", "&eYou have been kaboomed")
                    .replace("%player%", sender.getDisplayName()));
            String subtitle = colorize(config.getString("title-for-kaboomed-players.subtitle", "&eby &c%player%")
                    .replace("%player%", sender.getDisplayName()));

            target.sendTitle(title, subtitle, 10, 40, 10);
        }

        // Send message to kaboomed player
        if (config.getBoolean("message-for-kaboomed-players.enabled", true) && sender != null) {
            String message = colorize(config.getString("message-for-kaboomed-players.message", "&eYou have been kaboomed by %player%")
                    .replace("%player%", sender.getDisplayName()));
            target.sendMessage(message);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("kaboom")) {
            return false;
        }

        String prefix = colorize(config.getString("prefix", "&c[Kaboom] "));

        // Handle reload command
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("kaboom.reload")) {
                sender.sendMessage(prefix + colorize(config.getString("no-permission", "&cOnly admins can use this command")));
                return true;
            }

            reloadConfig();
            config = getConfig();
            sender.sendMessage(prefix + colorize(config.getString("reload-message", "&aPlugin has been reloaded")));
            return true;
        }

        // Player must be online to use kaboom (except console for specific player kaboom)
        if (args.length == 1 && !(sender instanceof Player)) {
            // Console can kaboom specific player
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(prefix + colorize(config.getString("player-not-found", "&cCould not find player &e%player%")
                        .replace("%player%", args[0])));
                return true;
            }

            // Check if world is enabled for target
            if (!isWorldEnabled(target.getWorld().getName())) {
                sender.sendMessage(prefix + colorize("&cKaboom is disabled in " + target.getWorld().getName() + "!"));
                return true;
            }

            kaboomPlayer(target, null);
            sender.sendMessage(prefix + colorize("&eYou kaboomed &c" + target.getDisplayName()));
            return true;
        }

        // Regular player usage
        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix + colorize(config.getString("no-permission", "&cOnly admins can use this command")));
            return true;
        }

        Player player = (Player) sender;

        // Check if player's world is enabled
        if (!isWorldEnabled(player.getWorld().getName())) {
            player.sendMessage(prefix + colorize("&cKaboom is disabled in this world!"));
            return true;
        }

        // Check permission
        if (!player.hasPermission("kaboom.use")) {
            player.sendMessage(prefix + colorize(config.getString("no-permission", "&cOnly admins can use this command")));
            return true;
        }

        // Kaboom specific player
        if (args.length == 1) {
            Player target = Bukkit.getPlayer(args[0]);

            if (target == null || !target.isOnline()) {
                player.sendMessage(prefix + colorize(config.getString("player-not-found", "&cCould not find player &e%player%")
                        .replace("%player%", args[0])));
                return true;
            }

            // Don't allow kabooming yourself
            if (target.equals(player)) {
                player.sendMessage(prefix + colorize("&cYou cannot kaboom yourself!"));
                return true;
            }

            // Check if target's world is enabled
            if (!isWorldEnabled(target.getWorld().getName())) {
                player.sendMessage(prefix + colorize("&cKaboom is disabled in " + target.getWorld().getName() + "!"));
                return true;
            }

            // Check exemption
            if (config.getBoolean("use-kaboom.exempt", true)) {
                if (target.hasPermission("kaboom.exempt")) {
                    player.sendMessage(prefix + colorize(config.getString("exempt-message", "You cannot kaboom %player% because he has kaboom.exempt")
                            .replace("%player%", target.getDisplayName())));
                    return true;
                }
            }

            kaboomPlayer(target, player);
            player.sendMessage(prefix + colorize("&eYou kaboomed &c" + target.getDisplayName()));
            return true;
        }

        // Kaboom all players (no arguments)
        if (args.length == 0) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!target.equals(player)) { // Don't kaboom yourself
                    // Check if target's world is enabled
                    if (!isWorldEnabled(target.getWorld().getName())) {
                        continue;
                    }

                    // Check exemption
                    if (config.getBoolean("use-kaboom.exempt", true) && target.hasPermission("kaboom.exempt")) {
                        continue;
                    }

                    kaboomPlayer(target, player);
                    player.sendMessage(colorize("&aLaunched " + target.getDisplayName()));
                    count++;
                }
            }

            if (count == 0) {
                player.sendMessage(prefix + colorize("&cNo players to kaboom!"));
            }
            return true;
        }

        // Show usage
        player.sendMessage(colorize("&6&lKaboom Usage:"));
        player.sendMessage(colorize("&e/kaboom &7- Launch all players in enabled worlds"));
        player.sendMessage(colorize("&e/kaboom <player> &7- Launch specific player"));
        player.sendMessage(colorize("&e/kaboom reload &7- Reload configuration"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Add reload option
            if ("reload".startsWith(partial) && sender.hasPermission("kaboom.reload")) {
                completions.add("reload");
            }

            // Add online players (excluding sender and players in disabled worlds)
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    if (sender instanceof Player && player.equals(sender)) {
                        continue; // Don't suggest self
                    }
                    // Only suggest players in enabled worlds
                    if (isWorldEnabled(player.getWorld().getName())) {
                        completions.add(player.getName());
                    }
                }
            }
        }

        return completions;
    }
}