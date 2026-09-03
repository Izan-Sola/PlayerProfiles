package com.ShinyShadow_.profileplugin.commands;

import com.ShinyShadow_.profileplugin.config.FieldConfigManager;
import com.ShinyShadow_.profileplugin.config.FieldDefinition;
import com.ShinyShadow_.profileplugin.storage.ProfileStorage;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
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

    private final Map<UUID, Long> lastSetTime = new HashMap<>();

    private static final Set<String> SOCIAL_FIELDS = new HashSet<>(Arrays.asList(
            "github", "instagram", "twitter", "youtube", "twitch", "tiktok", "reddit"
    ));

    private static final Map<String, String> URL_PATTERNS = new HashMap<>();
    static {
        URL_PATTERNS.put("github", "github.com");
        URL_PATTERNS.put("instagram", "instagram.com");
        URL_PATTERNS.put("twitter", "twitter.com");
        URL_PATTERNS.put("youtube", "youtube.com");
        URL_PATTERNS.put("twitch", "twitch.tv");
        URL_PATTERNS.put("tiktok", "tiktok.com");
        URL_PATTERNS.put("reddit", "reddit.com");
    }

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
            case "set":
                handleSet(sender, args);
                break;
            case "show":
                handleShow(sender, args);
                break;
            case "unset":
                handleUnset(sender, args);
                break;
            case "clear":
                handleClear(sender, args);
                break;
            case "fields":
                handleFields(sender);
                break;
            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can set their own profile.");
            return;
        }
        Player player = (Player) sender;
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
            Long last = lastSetTime.get(player.getUniqueId());
            long lastTime = last == null ? 0L : last;
            if (now - lastTime < cooldown * 1000L) {
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

        if (SOCIAL_FIELDS.contains(field)) {
            String validationError = validateSocialUrl(field, value);
            if (validationError != null) {
                sender.sendMessage(ChatColor.RED + validationError);
                return;
            }
        }

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

    private String validateSocialUrl(String platform, String url) {
        if (url == null || url.trim().isEmpty()) {
            return "URL cannot be empty for " + platform + "!";
        }

        url = url.trim();

        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return platform + " URL must start with https:// or http://";
        }

        String expectedDomain = URL_PATTERNS.get(platform);
        if (expectedDomain == null) {
            return null;
        }

        String urlLower = url.toLowerCase();
        if (!urlLower.contains(expectedDomain)) {
            return platform + " URL must contain '" + expectedDomain + "' (e.g., https://www." + expectedDomain + "/username)";
        }

        if (url.contains(" ")) {
            return "URL cannot contain spaces!";
        }

        if (!url.contains(".")) {
            return "Invalid URL format for " + platform + "!";
        }

        return null;
    }

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

        // Get config values with proper methods
        String header = plugin.getConfig().getString("display.header", "&6%player%'s Profile")
                .replace("%player%", playerName);

        // Handle \n in header for breaklines
        if (header.contains("\\n")) {
            for (String line : header.split("\\\\n")) {
                sender.sendMessage(color(line));
            }
        } else {
            sender.sendMessage(color(header));
        }

        Map<String, String> regularFields = new LinkedHashMap<>();
        Map<String, String> socialFields = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String field = entry.getKey();
            String value = entry.getValue();
            FieldDefinition def = fieldConfigManager.getField(field);
            if (def != null && def.isEnabled()) {
                if (SOCIAL_FIELDS.contains(field)) {
                    if (isValidSocialUrl(field, value)) {
                        socialFields.put(field, value);
                    }
                } else {
                    regularFields.put(field, value);
                }
            }
        }

        String fieldLine = plugin.getConfig().getString("display.field-line", "&e%field%&7: &f%value%");

        for (Map.Entry<String, String> entry : regularFields.entrySet()) {
            String field = entry.getKey();
            String value = entry.getValue();
            String displayField = capitalizeFirstLetter(field);
            for (String line : value.split("\n")) {
                sender.sendMessage(" " + color(fieldLine
                        .replace("%field%", displayField)
                        .replace("%value%", line)));
            }
        }

        if (!socialFields.isEmpty()) {
            sender.sendMessage("");
            sender.sendMessage(" " + color("&e&lLinks:"));

            TextComponent message = new TextComponent("  ");
            int i = 0;
            for (Map.Entry<String, String> entry : socialFields.entrySet()) {
                String platform = capitalizeFirstLetter(entry.getKey());
                String url = entry.getValue();

                if (i > 0) {
                    TextComponent separator = new TextComponent(" §7---- ");
                    message.addExtra(separator);
                }

                TextComponent link = new TextComponent("§9[" + platform + "]§r");
                link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new Text("§7Click to open " + platform)));
                message.addExtra(link);
                i++;
            }

            sender.spigot().sendMessage(message);
        }

        String footer = plugin.getConfig().getString("display.footer", "&8----");
        sender.sendMessage(" " + color(footer));
    }
    private boolean isValidSocialUrl(String platform, String url) {
        if (url == null || url.trim().isEmpty()) return false;
        url = url.trim();
        return validateSocialUrl(platform, url) == null;
    }

    private void handleUnset(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can unset their own profile fields.");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("profileplugin.use")) {
            sender.sendMessage(msg("no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /profile unset <field>");
            return;
        }

        String field = args[1].toLowerCase(Locale.ROOT);

        FieldDefinition def = fieldConfigManager.getField(field);
        if (def == null) {
            sender.sendMessage(msg("field-invalid").replace("%field%", field));
            sender.sendMessage(ChatColor.GRAY + "Available fields: " + String.join(", ", fieldConfigManager.getEnabledFieldNames()));
            return;
        }
        if (!def.isEnabled()) {
            sender.sendMessage(msg("field-disabled").replace("%field%", field));
            return;
        }

        storage.getProfile(player.getUniqueId()).thenAccept(existingFields -> {
            if (!existingFields.containsKey(field)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(msg("field-not-set").replace("%field%", field)));
                return;
            }

            storage.clearField(player.getUniqueId(), field).thenRun(() ->
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(msg("field-unset").replace("%field%", field))));
        });
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!sender.hasPermission("profileplugin.admin")) {
                sender.sendMessage(msg("no-permission"));
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(msg("player-not-found"));
                return;
            }

            if (args.length >= 3 && args[2].equalsIgnoreCase("confirm")) {
                storage.clearProfile(target.getUniqueId()).thenRun(() ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage(ChatColor.GREEN + "Cleared " + target.getName() + "'s entire profile.")));
            } else {
                sender.sendMessage(ChatColor.RED + "⚠️ Are you sure you want to clear " + target.getName() + "'s entire profile?");
                sender.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.WHITE + "/profile clear " + target.getName() + " confirm" + ChatColor.YELLOW + " to confirm.");
            }
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /profile clear <player>");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("profileplugin.use")) {
            sender.sendMessage(msg("no-permission"));
            return;
        }

        if (args.length == 2 && args[1].equalsIgnoreCase("confirm")) {
            storage.clearProfile(player.getUniqueId()).thenRun(() ->
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(msg("profile-cleared"))));
            return;
        }

        sender.sendMessage(ChatColor.RED + "⚠️ Are you sure you want to clear your entire profile?");
        sender.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.WHITE + "/profile clear confirm" + ChatColor.YELLOW + " to confirm.");
    }

    private void handleFields(CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY + "💡 Fields appear in the order you set them.");
        sender.sendMessage(ChatColor.GRAY + "   Use " + ChatColor.YELLOW + "/profile unset <field>" + ChatColor.GRAY + " to remove a field.");
        sender.sendMessage(ChatColor.GRAY + "   Use " + ChatColor.YELLOW + "/profile clear" + ChatColor.GRAY + " to remove all fields.");
        sender.sendMessage(ChatColor.GRAY + "─────────────────────");

        sender.sendMessage(ChatColor.GOLD + "Available Profile Fields:");
        List<String> enabledFields = fieldConfigManager.getEnabledFieldNames();
        if (enabledFields.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No fields are currently enabled.");
            return;
        }

        for (String field : enabledFields) {
            FieldDefinition def = fieldConfigManager.getField(field);
            if (def != null) {
                String displayField = capitalizeFirstLetter(field);
                String info = ChatColor.YELLOW + displayField;
                if (def.isAdminOnly()) {
                    info += ChatColor.RED + " (admin only)";
                }
                if (SOCIAL_FIELDS.contains(field)) {
                    info += ChatColor.GRAY + " (social media - appears in Links section)";
                    info += ChatColor.GRAY + " [must be valid " + field + " URL]";
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
        sender.sendMessage(ChatColor.YELLOW + "/profile unset <field>" + ChatColor.GRAY + " - remove a field from your profile");
        sender.sendMessage(ChatColor.YELLOW + "/profile clear" + ChatColor.GRAY + " - clear your entire profile");
        sender.sendMessage(ChatColor.YELLOW + "/profile show [player]" + ChatColor.GRAY + " - view a profile (yours by default)");
        sender.sendMessage(ChatColor.YELLOW + "/profile fields" + ChatColor.GRAY + " - list available fields");
    }

    private String msg(String key) {
        return color(plugin.getConfig().getString("messages." + key, key));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("set", "show", "unset", "clear", "fields"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("unset")) {
                return filter(fieldConfigManager.getEnabledFieldNames(), args[1]);
            }
            if (args[0].equalsIgnoreCase("show")) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
            }
            if (args[0].equalsIgnoreCase("clear")) {
                List<String> options = new ArrayList<>();
                options.add("confirm");
                options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
                return filter(options, args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("clear")) {
            return filter(Collections.singletonList("confirm"), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}