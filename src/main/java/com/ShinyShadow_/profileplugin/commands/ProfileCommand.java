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
import java.util.regex.Pattern;
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

    private List<String> filteredWords = new ArrayList<>();

    public ProfileCommand(JavaPlugin plugin, ProfileStorage storage, FieldConfigManager fieldConfigManager) {
        this.plugin = plugin;
        this.storage = storage;
        this.fieldConfigManager = fieldConfigManager;
        loadFilteredWords();
    }

    private void loadFilteredWords() {
        filteredWords = plugin.getConfig().getStringList("filtered-words");
    }

    private boolean containsFilteredWord(String text) {
        if (text == null || filteredWords.isEmpty()) {
            return false;
        }

        for (String word : filteredWords) {
            if (word == null || word.isEmpty()) continue;
            String pattern = buildFilterPattern(word);
            if (text.matches("(?i).*" + pattern + ".*")) {
                return true;
            }
        }
        return false;
    }

    private String buildFilterPattern(String word) {
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (i > 0) {
                pattern.append(".*?");
            }
            pattern.append(Pattern.quote(String.valueOf(c)));
        }
        return pattern.toString();
    }

    private String lang(String key) {
        String lang = plugin.getConfig().getString("language", "en");
        Map<String, String> messages = new HashMap<>();

        // English messages
        messages.put("only-players", "&cOnly players can set their own profile.");
        messages.put("no-permission", "&cYou don't have permission to use this command.");
        messages.put("usage-set", "&cUsage: /profile set <field> <value>");
        messages.put("field-invalid", "&cUnknown field: &e%field%");
        messages.put("available-fields", "&7Available fields: &e%fields%");
        messages.put("field-admin-only", "&cField %field% is admin-only.");
        messages.put("field-disabled", "&cField %field% is currently disabled.");
        messages.put("value-too-long", "&cValue is too long! Maximum length: &e%max%");
        messages.put("value-not-allowed", "&cThat value is not allowed. Allowed values: &e%allowed%");
        messages.put("field-set", "&aSet %field% to: &f%value%");
        messages.put("too-many-fields", "&cYou have reached the maximum of %max% profile fields.");
        messages.put("on-cooldown", "&cPlease wait before using /profile set again.");
        messages.put("no-permission-others", "&cYou don't have permission to view/modify other players' profiles.");
        messages.put("player-not-found", "&cPlayer not found.");
        messages.put("no-profile", "&e%player% has no profile set up.");
        messages.put("profile-cleared", "&aYour entire profile has been cleared.");
        messages.put("field-not-set", "&cField %field% is not set on your profile.");
        messages.put("field-unset", "&aRemoved field: &e%field%");
        messages.put("unset-usage", "&cUsage: /profile unset <field>");
        messages.put("only-players-unset", "&cOnly players can unset their own profile fields.");
        messages.put("clear-confirm", "&c⚠️ Are you sure you want to clear your entire profile?");
        messages.put("clear-confirm-type", "&eType &f/profile clear confirm&e to confirm.");
        messages.put("clear-admin-confirm", "&c⚠️ Are you sure you want to clear %player%'s entire profile?");
        messages.put("clear-admin-type", "&eType &f/profile clear %player% confirm&e to confirm.");
        messages.put("clear-success", "&aCleared %player%'s entire profile.");
        messages.put("clear-redirect", "&cTo remove a specific field, use: &e/profile unset %field%");
        messages.put("clear-redirect-clear", "&eTo clear your entire profile, use: &e/profile clear");
        messages.put("url-invalid", "&cInvalid URL for %platform%: %error%");
        messages.put("url-empty", "&cURL cannot be empty for %platform%!");
        messages.put("url-must-start", "&c%platform% URL must start with https:// or http://");
        messages.put("url-wrong-domain", "&c%platform% URL must contain '%domain%' (e.g., https://www.%domain%/username)");
        messages.put("url-has-spaces", "&cURL cannot contain spaces!");
        messages.put("url-invalid-format", "&cInvalid URL format for %platform%!");
        messages.put("filtered-word", "&cYour message contains filtered language.");

        // Spanish messages
        messages.put("only-players-es", "&cSolo los jugadores pueden establecer su propio perfil.");
        messages.put("no-permission-es", "&cNo tienes permiso para usar este comando.");
        messages.put("usage-set-es", "&cUso: /profile set <campo> <valor>");
        messages.put("field-invalid-es", "&cCampo desconocido: &e%field%");
        messages.put("available-fields-es", "&7Campos disponibles: &e%fields%");
        messages.put("field-admin-only-es", "&cEl campo %field% es solo para administradores.");
        messages.put("field-disabled-es", "&cEl campo %field% está actualmente desactivado.");
        messages.put("value-too-long-es", "&c¡El valor es demasiado largo! Longitud máxima: &e%max%");
        messages.put("value-not-allowed-es", "&cEse valor no está permitido. Valores permitidos: &e%allowed%");
        messages.put("field-set-es", "&aEstablecido %field% a: &f%value%");
        messages.put("too-many-fields-es", "&cHas alcanzado el máximo de %max% campos en tu perfil.");
        messages.put("on-cooldown-es", "&cEspera antes de usar /profile set de nuevo.");
        messages.put("no-permission-others-es", "&cNo tienes permiso para ver/modificar los perfiles de otros jugadores.");
        messages.put("player-not-found-es", "&cJugador no encontrado.");
        messages.put("no-profile-es", "&e%player% no tiene un perfil configurado.");
        messages.put("profile-cleared-es", "&aTu perfil completo ha sido eliminado.");
        messages.put("field-not-set-es", "&cEl campo %field% no está establecido en tu perfil.");
        messages.put("field-unset-es", "&aCampo eliminado: &e%field%");
        messages.put("unset-usage-es", "&cUso: /profile unset <campo>");
        messages.put("only-players-unset-es", "&cSolo los jugadores pueden eliminar sus propios campos de perfil.");
        messages.put("clear-confirm-es", "&c⚠️ ¿Estás seguro de que quieres eliminar todo tu perfil?");
        messages.put("clear-confirm-type-es", "&eEscribe &f/profile clear confirm&e para confirmar.");
        messages.put("clear-admin-confirm-es", "&c⚠️ ¿Estás seguro de que quieres eliminar el perfil de %player%?");
        messages.put("clear-admin-type-es", "&eEscribe &f/profile clear %player% confirm&e para confirmar.");
        messages.put("clear-success-es", "&aPerfil de %player% eliminado por completo.");
        messages.put("clear-redirect-es", "&cPara eliminar un campo específico, usa: &e/profile unset %field%");
        messages.put("clear-redirect-clear-es", "&ePara eliminar todo tu perfil, usa: &e/profile clear");
        messages.put("url-invalid-es", "&cURL inválida para %platform%: %error%");
        messages.put("url-empty-es", "&c¡La URL no puede estar vacía para %platform%!");
        messages.put("url-must-start-es", "&cLa URL de %platform% debe comenzar con https:// o http://");
        messages.put("url-wrong-domain-es", "&cLa URL de %platform% debe contener '%domain%' (ej: https://www.%domain%/usuario)");
        messages.put("url-has-spaces-es", "&c¡La URL no puede contener espacios!");
        messages.put("url-invalid-format-es", "&cFormato de URL inválido para %platform%!");
        messages.put("filtered-word-es", "&cTu mensaje contiene lenguaje filtrado.");

        String keyWithLang = key + "-" + lang;
        if (messages.containsKey(keyWithLang)) {
            return messages.get(keyWithLang);
        }
        return messages.getOrDefault(key, key);
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
            sender.sendMessage(color(lang("only-players")));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("profileplugin.use")) {
            sender.sendMessage(color(lang("no-permission")));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(color(lang("usage-set")));
            return;
        }

        int cooldown = plugin.getConfig().getInt("set-cooldown-seconds", 2);
        if (cooldown > 0) {
            long now = System.currentTimeMillis();
            Long last = lastSetTime.get(player.getUniqueId());
            long lastTime = last == null ? 0L : last;
            if (now - lastTime < cooldown * 1000L) {
                sender.sendMessage(color(lang("on-cooldown")));
                return;
            }
        }

        String field = args[1].toLowerCase(Locale.ROOT);
        String rawValue = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        FieldDefinition def = fieldConfigManager.getField(field);
        if (def == null) {
            sender.sendMessage(color(lang("field-invalid").replace("%field%", field)));
            sender.sendMessage(color(lang("available-fields").replace("%fields%", String.join(", ", fieldConfigManager.getEnabledFieldNames()))));
            return;
        }
        if (def.isAdminOnly() && !player.hasPermission("profileplugin.admin")) {
            sender.sendMessage(color(lang("field-admin-only").replace("%field%", field)));
            return;
        }
        if (!def.isEnabled()) {
            sender.sendMessage(color(lang("field-disabled").replace("%field%", field)));
            return;
        }

        String value = def.isMultiline()
                ? rawValue.replace(" | ", "\n").replace("|", "\n")
                : rawValue;

        // Check for filtered words - BLOCK if any found
        if (containsFilteredWord(value)) {
            sender.sendMessage(color(lang("filtered-word")));
            return;
        }

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
                sender.sendMessage(color(lang("value-too-long").replace("%max%", String.valueOf(result.maxLength))));
            } else {
                sender.sendMessage(color(lang("value-not-allowed")
                        .replace("%allowed%", String.join(", ", result.allowedValues))));
            }
            return;
        }

        storage.getProfile(player.getUniqueId()).thenAccept(existingFields -> {
            boolean isNewField = !existingFields.containsKey(field);
            int maxFields = fieldConfigManager.getMaxFieldsPerProfile();
            if (isNewField && existingFields.size() >= maxFields) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(color(lang("too-many-fields").replace("%max%", String.valueOf(maxFields)))));
                return;
            }

            storage.setField(player.getUniqueId(), field, value).thenRun(() ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        lastSetTime.put(player.getUniqueId(), System.currentTimeMillis());
                        sender.sendMessage(color(lang("field-set")
                                .replace("%field%", field)
                                .replace("%value%", value.replace("\n", " / "))));
                    }));
        });
    }

    private String validateSocialUrl(String platform, String url) {
        if (url == null || url.trim().isEmpty()) {
            return color(lang("url-empty").replace("%platform%", platform));
        }

        url = url.trim();

        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return color(lang("url-must-start").replace("%platform%", platform));
        }

        String expectedDomain = URL_PATTERNS.get(platform);
        if (expectedDomain == null) {
            return null;
        }

        String urlLower = url.toLowerCase();
        if (!urlLower.contains(expectedDomain)) {
            return color(lang("url-wrong-domain")
                    .replace("%platform%", platform)
                    .replace("%domain%", expectedDomain));
        }

        if (url.contains(" ")) {
            return color(lang("url-has-spaces"));
        }

        if (!url.contains(".")) {
            return color(lang("url-invalid-format").replace("%platform%", platform));
        }

        return null;
    }

    private void handleShow(CommandSender sender, String[] args) {
        String targetName = args.length >= 2 ? args[1] : sender.getName();

        if (args.length >= 2 && !targetName.equalsIgnoreCase(sender.getName())
                && !sender.hasPermission("profileplugin.view.others")) {
            sender.sendMessage(color(lang("no-permission-others")));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(color(lang("player-not-found")));
            return;
        }

        storage.getProfile(target.getUniqueId()).thenAccept(fields ->
                Bukkit.getScheduler().runTask(plugin, () -> sendFormattedProfile(sender, targetName, fields)));
    }

    private void sendFormattedProfile(CommandSender sender, String playerName, Map<String, String> fields) {
        if (fields.isEmpty()) {
            sender.sendMessage(color(lang("no-profile").replace("%player%", playerName)));
            return;
        }

        String header = plugin.getConfig().getString("display.header", "&6%player%'s Profile")
                .replace("%player%", playerName);

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
            sender.sendMessage(color(lang("only-players-unset")));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("profileplugin.use")) {
            sender.sendMessage(color(lang("no-permission")));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(color(lang("unset-usage")));
            return;
        }

        String field = args[1].toLowerCase(Locale.ROOT);

        FieldDefinition def = fieldConfigManager.getField(field);
        if (def == null) {
            sender.sendMessage(color(lang("field-invalid").replace("%field%", field)));
            sender.sendMessage(color(lang("available-fields").replace("%fields%", String.join(", ", fieldConfigManager.getEnabledFieldNames()))));
            return;
        }
        if (!def.isEnabled()) {
            sender.sendMessage(color(lang("field-disabled").replace("%field%", field)));
            return;
        }

        storage.getProfile(player.getUniqueId()).thenAccept(existingFields -> {
            if (!existingFields.containsKey(field)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(color(lang("field-not-set").replace("%field%", field))));
                return;
            }

            storage.clearField(player.getUniqueId(), field).thenRun(() ->
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(color(lang("field-unset").replace("%field%", field)))));
        });
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (args.length >= 2 && !args[1].equalsIgnoreCase("confirm")) {
            if (!sender.hasPermission("profileplugin.admin")) {
                sender.sendMessage(color(lang("no-permission")));
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(color(lang("player-not-found")));
                return;
            }

            if (args.length >= 3 && args[2].equalsIgnoreCase("confirm")) {
                storage.clearProfile(target.getUniqueId()).thenRun(() ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage(color(lang("clear-success").replace("%player%", target.getName())))));
            } else {
                sender.sendMessage(color(lang("clear-admin-confirm").replace("%player%", args[1])));
                sender.sendMessage(color(lang("clear-admin-type").replace("%player%", args[1])));
            }
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /profile clear <player>");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("profileplugin.use")) {
            sender.sendMessage(color(lang("no-permission")));
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
            storage.clearProfile(player.getUniqueId()).thenRun(() ->
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(color(lang("profile-cleared")))));
            return;
        }

        sender.sendMessage(color(lang("clear-confirm")));
        sender.sendMessage(color(lang("clear-confirm-type")));
    }

    private void handleFields(CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY + "💡 " + color("&7Fields appear in the order you set them."));
        sender.sendMessage(ChatColor.GRAY + "   " + color("&e/profile unset <field>") + " &7- " + color("&7remove a field"));
        sender.sendMessage(ChatColor.GRAY + "   " + color("&e/profile clear") + " &7- " + color("&7remove all fields"));
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
        sender.sendMessage(ChatColor.YELLOW + "/profile set <field> <value>" + ChatColor.GRAY + " - set a field");
        sender.sendMessage(ChatColor.YELLOW + "/profile unset <field>" + ChatColor.GRAY + " - remove a field");
        sender.sendMessage(ChatColor.YELLOW + "/profile clear" + ChatColor.GRAY + " - clear profile");
        sender.sendMessage(ChatColor.YELLOW + "/profile show [player]" + ChatColor.GRAY + " - view profile");
        sender.sendMessage(ChatColor.YELLOW + "/profile fields" + ChatColor.GRAY + " - list fields");
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