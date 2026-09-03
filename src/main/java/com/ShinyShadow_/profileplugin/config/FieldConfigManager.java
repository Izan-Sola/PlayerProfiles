package com.ShinyShadow_.profileplugin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class FieldConfigManager {

    private final JavaPlugin plugin;
    private final Map<String, FieldDefinition> fields = new LinkedHashMap<>();
    private int maxFieldsPerProfile;

    public FieldConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        fields.clear();

        Map<String, Object> config = plugin.getConfig().getValues(true);
        this.maxFieldsPerProfile = plugin.getConfig().getInt("max-fields-per-profile", 20);

        ConfigurationSection fieldsSection = plugin.getConfig().getConfigurationSection("fields");
        if (fieldsSection == null) {
            plugin.getLogger().warning("No 'fields' section found in config.yml! Using defaults.");
            loadDefaultFields();
            return;
        }

        Map<String, FieldDefinition> defaultFields = createDefaultFields();

        for (String fieldKey : defaultFields.keySet()) {
            ConfigurationSection fieldConfig = fieldsSection.getConfigurationSection(fieldKey);
            FieldDefinition defaultDef = defaultFields.get(fieldKey);

            if (fieldConfig != null) {
                boolean enabled = fieldConfig.getBoolean("enabled", defaultDef.isEnabled());
                int maxLength = fieldConfig.getInt("max-length", defaultDef.getMaxLength());
                boolean multiline = fieldConfig.getBoolean("multiline", defaultDef.isMultiline());
                boolean adminOnly = fieldConfig.getBoolean("admin-only", defaultDef.isAdminOnly());

                List<String> allowedValues;
                if (defaultDef.hasHardcodedAllowedValues()) {
                    allowedValues = defaultDef.getAllowedValues();
                } else {
                    allowedValues = fieldConfig.contains("allowed-values")
                            ? fieldConfig.getStringList("allowed-values")
                            : defaultDef.getAllowedValues();
                }

                String displayName = fieldConfig.getString("display-name", defaultDef.getDisplayName());
                String color = fieldConfig.getString("color", defaultDef.getColor());

                fields.put(fieldKey.toLowerCase(Locale.ROOT), new FieldDefinition(
                        fieldKey, displayName, maxLength, multiline,
                        allowedValues, adminOnly, enabled, color,
                        defaultDef.hasHardcodedAllowedValues()
                ));
            } else {
                fields.put(fieldKey.toLowerCase(Locale.ROOT), defaultDef);
            }
        }

        plugin.getLogger().info("Loaded " + fields.size() + " profile field configurations.");
        plugin.getLogger().info("Enabled fields: " + String.join(", ", getEnabledFieldNames()));
    }

    private void loadDefaultFields() {
        fields.putAll(createDefaultFields());
    }

    private Map<String, FieldDefinition> createDefaultFields() {
        Map<String, FieldDefinition> defaultFields = new LinkedHashMap<>();

        defaultFields.put("name", new FieldDefinition(
                "name", "Display Name", 32, false, null, false, true, "&e"
        ));

        defaultFields.put("age", new FieldDefinition(
                "age", "Age", 3, false, Arrays.asList("1-99"), false, true, "&b"
        ));

        defaultFields.put("gender", new FieldDefinition(
                "gender", "Gender", 10, false,
                Arrays.asList("Male", "Female"),
                false, true, "&d", true
        ));

        defaultFields.put("description", new FieldDefinition(
                "description", "Description", 200, true, null, false, true, "&7"
        ));

        defaultFields.put("location", new FieldDefinition(
                "location", "Location", 50, false, null, false, true, "&a"
        ));

        defaultFields.put("discord", new FieldDefinition(
                "discord", "Discord", 37, false, null, false, true, "&5"
        ));

        defaultFields.put("github", new FieldDefinition(
                "github", "GitHub", 100, false, null, false, true, "&6"
        ));

        defaultFields.put("instagram", new FieldDefinition(
                "instagram", "Instagram", 100, false, null, false, true, "&d"
        ));

        defaultFields.put("twitter", new FieldDefinition(
                "twitter", "Twitter", 100, false, null, false, true, "&b"
        ));

        defaultFields.put("youtube", new FieldDefinition(
                "youtube", "YouTube", 100, false, null, false, true, "&c"
        ));

        defaultFields.put("twitch", new FieldDefinition(
                "twitch", "Twitch", 100, false, null, false, true, "&5"
        ));

        defaultFields.put("tiktok", new FieldDefinition(
                "tiktok", "TikTok", 100, false, null, false, true, "&d"
        ));

        defaultFields.put("reddit", new FieldDefinition(
                "reddit", "Reddit", 100, false, null, false, true, "&c"
        ));

        defaultFields.put("skills", new FieldDefinition(
                "skills", "Skills", 100, false,
                Arrays.asList("Builder", "Redstone", "PvP", "Parkour", "Explorer", "Farms", "Fishing", "Mining"),
                false, true, "&2"
        ));

        defaultFields.put("experience", new FieldDefinition(
                "experience", "Experience", 20, false,
                Arrays.asList("Beginner", "Intermediate", "Advanced", "Expert", "Legendary"),
                true, true, "&c"
        ));

        defaultFields.put("timezone", new FieldDefinition(
                "timezone", "Time Zone", 20, false, null, false, true, "&3"
        ));

        defaultFields.put("language", new FieldDefinition(
                "language", "Language", 20, false, null, false, true, "&9"
        ));

        return defaultFields;
    }

    public FieldDefinition getField(String fieldName) {
        if (fieldName == null) return null;
        String key = fieldName.toLowerCase(Locale.ROOT);
        FieldDefinition def = fields.get(key);
        return (def != null && def.isEnabled()) ? def : null;
    }

    public Collection<FieldDefinition> getAllFields() {
        return fields.values();
    }

    public List<String> getEnabledFieldNames() {
        List<String> result = new ArrayList<>();
        for (FieldDefinition def : fields.values()) {
            if (def.isEnabled()) {
                result.add(def.getName());
            }
        }
        return result;
    }

    public int getMaxFieldsPerProfile() {
        return maxFieldsPerProfile;
    }
}