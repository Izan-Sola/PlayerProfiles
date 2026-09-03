package com.ShinyShadow_.profileplugin.commands;

import com.ShinyShadow_.profileplugin.config.FieldConfigManager;
import com.ShinyShadow_.profileplugin.config.FieldDefinition;
import com.ShinyShadow_.profileplugin.storage.ProfileStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class ProfileCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final ProfileStorage storage;
    private final FieldConfigManager fieldConfigManager;

    // Simple per-player cooldown tracking for /profile set
    private final Map<UUID, Long> lastSetTime = new HashMap<>();

    public ProfileCommand(JavaPlugin plugin, ProfileStorage storage, FieldConfigManager fieldConfigManager) {
        this.plugin = plugin;
        this.storage = storage;
        this.fieldConfigManager = fieldConfigManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> handleSet(sender, args);
            case "show" -> handleShow(sender, args);
            case "clear" -> handleClear(sender, args);
            case "fields" -> handleFields(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // /profile set <field> <value...>
    // Multiline fields: use "|" in the value to insert line breaks, e.g.
    //   /profile set description Hey there! | I like building castles.
    // ---------------------------------------------------------------------
    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can set their own profile.");
            return;
        }
        if (!player.hasPermission("profileplugin.use")) {
            sender.sendMessage(msg("no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /profile set <field> <value>");
            return;
        }

        int cooldown = plugin.getConfig().getInt("set-cooldown-seconds", 2);
        if (cooldown > 0) {
            long now = System.currentTimeMillis();
            long last = lastSetTime.getOrDefault(player.getUniqueId(), 0L);
            if (now - last < cooldown * 1000L) {
                sender.sendMessage(msg("on-cooldown"));
                return;
            }
        }

        String field = args[1].toLowerCase(Locale.ROOT);
        String rawValue = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        FieldDefinition def = fieldConfigManager.getField(field);
        if (def == null) {
            sender.sendMessage(msg("field-invalid").replace("%field%", field));
            sender.sendMessage(ChatColor.GRAY + "Available fields: " + String.join(", ", fieldConfigManager.getEnabledFieldNames()));
            return;
        }
        if (def.isAdminOnly() && !player.hasPermission("profileplugin.admin")) {
            sender.sendMessage(msg("field-admin-only").replace("%field%", field));
            return;
        }
        if (!def.isEnabled()) {
            sender.sendMessage(msg("field-disabled").replace("%field%", field));
            return;
        }

        String value = def.isMultiline()
                ? rawValue.replace(" | ", "\n").replace("|", "\n")
                : rawValue;

        FieldDefinition.ValidationResult result = def.validate(value);
        if (!result.valid) {
            if ("too_long".equals(result.reason)) {
                sender.sendMessage(msg("value-too-long").replace("%max%", String.valueOf(result.maxLength)));
            } else {
                sender.sendMessage(msg("value-not-allowed")
                        .replace("%allowed%", String.join(", ", result.allowedValues)));
            }
            return;
        }

        // Check max-fields-per-profile before adding a brand new field
        storage.getProfile(player.getUniqueId()).thenAccept(existingFields -> {
            boolean isNewField = !existingFields.containsKey(field);
            int maxFields = fieldConfigManager.getMaxFieldsPerProfile();
            if (isNewField && existingFields.size() >= maxFields) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(msg("too-many-fields").replace("%max%", String.valueOf(maxFields))));
                return;
            }

            storage.setField(player.getUniqueId(), field, value).thenRun(() ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        lastSetTime.put(player.getUniqueId(), System.currentTimeMillis());
                        sender.sendMessage(msg("field-set")
                                .replace("%field%", field)
                                .replace("%value%", value.replace("\n", " / ")));
                    }));
        });
    }

    // ---------------------------------------------------------------------
    // /profile show [nick]  - defaults to sender's own profile.
    // Result is sent ONLY to the sender via chat, formatted per config.yml.
    // ---------------------------------------------------------------------
    private void handleShow(CommandSender sender, String[] args) {
        String targetName = args.length >= 2 ? args[1] : sender.getName();

        if (args.length >= 2 && !targetName.equalsIgnoreCase(sender.getName())
                && !sender.hasPermission("profileplugin.view.others")) {
            sender.sendMessage(msg("no-permission-others"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(msg("player-not-found"));
            return;
        }

        storage.getProfile(target.getUniqueId()).thenAccept(fields ->
                Bukkit.getScheduler().runTask(plugin, () -> sendFormattedProfile(sender, targetName, fields)));
    }

    private void sendFormattedProfile(CommandSender sender, String playerName, Map<String, String> fields) {
        if (fields.isEmpty()) {
            sender.sendMessage(msg("no-profile").replace("%player%", playerName));
            return;
        }

        var config = plugin.getConfig();
        sender.sendMessage(color(config.getString("display.header", "&6%player%'s Profile")
                .replace("%player%", playerName)));

        // Get fields in order, but only show enabled fields
        List<String> order = config.getStringList("display.field-order");
        List<String> enabledFields = fieldConfigManager.getEnabledFieldNames();

        // Filter to only show fields that are in the config and enabled
        List<String> orderedKeys = new ArrayList<>();
        for (String field : order) {
            if (fields.containsKey(field) && enabledFields.contains(field)) {
                orderedKeys.add(field);
            }
        }
        // Add any remaining fields that are enabled but not in the order list
        for (String field : fields.keySet()) {
            if (!orderedKeys.contains(field) && enabledFields.contains(field)) {
                orderedKeys.add(field);
            }
        }

        String fieldLine = config.getString("display.field-line", "&e%field%&7: &f%value%");
        for (String field : orderedKeys) {
            String value = fields.get(field);
            FieldDefinition def = fieldConfigManager.getField(field);
            if (def != null && def.isEnabled()) {
                for (String line : value.split("\n")) {
                    sender.sendMessage(color(fieldLine
                            .replace("%field%", field)
                            .replace("%value%", line)));
                }
            }
        }

        sender.sendMessage(color(config.getString("display.footer", "&8----")));
    }

    // ---------------------------------------------------------------------
    // /profile clear <field> [player]  - own field, or admin clearing another's
    // ---------------------------------------------------------------------
    private void handleClear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /profile clear <field> [player]");
            return;
        }
        String field = args[1].toLowerCase(Locale.ROOT);

        // Check if field exists and is enabled
        FieldDefinition def = fieldConfigManager.getField(field);
        if (def == null) {
            sender.sendMessage(msg("field-invalid").replace("%field%", field));
            return;
        }
        if (!def.isEnabled()) {
            sender.sendMessage(msg("field-disabled").replace("%field%", field));
            return;
        }

        UUID targetUuid;
        if (args.length >= 3) {
            if (!sender.hasPermission("profileplugin.admin")) {
                sender.sendMessage(msg("no-permission-others"));
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            targetUuid = target.getUniqueId();
        } else if (sender instanceof Player player) {
            targetUuid = player.getUniqueId();
        } else {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /profile clear <field> <player>");
            return;
        }

        storage.clearField(targetUuid, field).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(msg("field-cleared").replace("%field%", field))));
    }

    // ---------------------------------------------------------------------
    // /profile fields - list all available fields
    // ---------------------------------------------------------------------
    private void handleFields(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Available Profile Fields ---");
        List<String> enabledFields = fieldConfigManager.getEnabledFieldNames();
        if (enabledFields.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No fields are currently enabled.");
            return;
        }
        for (String field : enabledFields) {
            FieldDefinition def = fieldConfigManager.getField(field);
            if (def != null) {
                String info = ChatColor.YELLOW + field;
                if (def.isAdminOnly()) {
                    info += ChatColor.RED + " (admin only)";
                }
                if (def.hasRestrictedValues()) {
                    info += ChatColor.GRAY + " [allowed: " + String.join(", ", def.getAllowedValues()) + "]";
                }
                info += ChatColor.GRAY + " (max " + def.getMaxLength() + " chars)";
                sender.sendMessage(info);
            }
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- ProfilePlugin ---");
        sender.sendMessage(ChatColor.YELLOW + "/profile set <field> <value>" + ChatColor.GRAY + " - set a field on your profile");
        sender.sendMessage(ChatColor.YELLOW + "/profile show [player]" + ChatColor.GRAY + " - view a profile (yours by default)");
        sender.sendMessage(ChatColor.YELLOW + "/profile clear <field> [player]" + ChatColor.GRAY + " - clear a field");
        sender.sendMessage(ChatColor.YELLOW + "/profile fields" + ChatColor.GRAY + " - list available fields");
    }

    private String msg(String key) {
        return color(plugin.getConfig().getString("messages." + key, key));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("set", "show", "clear", "fields"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("clear")) {
                // Suggest available fields
                return filter(fieldConfigManager.getEnabledFieldNames(), args[1]);
            }
            if (args[0].equalsIgnoreCase("show")) {
                // Suggest online player names
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("clear")) {
            // Suggest online player names for clearing other's fields
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}