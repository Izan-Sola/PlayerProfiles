package com.ShinyShadow_.profileplugin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Manages hardcoded field definitions that can be configured via config.yml.
 * Fields are defined in code but their properties (enabled, maxLength, etc.)
 * can be overridden in the config.
 */
public class FieldConfigManager {

    private final JavaPlugin plugin;
    private final Map<String, FieldDefinition> fields = new LinkedHashMap<>();
    private int maxFieldsPerProfile;

    public FieldConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        fields.clear();

        var config = plugin.getConfig();
        this.maxFieldsPerProfile = config.getInt("max-fields-per-profile", 20);

        // Load field configurations from config.yml
        ConfigurationSection fieldsSection = config.getConfigurationSection("fields");
        if (fieldsSection == null) {
            plugin.getLogger().warning("No 'fields' section found in config.yml! Using defaults.");
            loadDefaultFields();
            return;
        }

        // Define all hardcoded fields with their default values
        Map<String, FieldDefinition> defaultFields = createDefaultFields();

        // Apply config overrides
        for (String fieldKey : defaultFields.keySet()) {
            ConfigurationSection fieldConfig = fieldsSection.getConfigurationSection(fieldKey);
            FieldDefinition defaultDef = defaultFields.get(fieldKey);

            if (fieldConfig != null) {
                // Override with config values
                boolean enabled = fieldConfig.getBoolean("enabled", defaultDef.isEnabled());
                int maxLength = fieldConfig.getInt("max-length", defaultDef.getMaxLength());
                boolean multiline = fieldConfig.getBoolean("multiline", defaultDef.isMultiline());
                boolean adminOnly = fieldConfig.getBoolean("admin-only", defaultDef.isAdminOnly());
                List<String> allowedValues = fieldConfig.contains("allowed-values")
                        ? fieldConfig.getStringList("allowed-values")
                        : defaultDef.getAllowedValues();
                String displayName = fieldConfig.getString("display-name", defaultDef.getDisplayName());
                String color = fieldConfig.getString("color", defaultDef.getColor());

                fields.put(fieldKey, new FieldDefinition(
                        fieldKey, displayName, maxLength, multiline,
                        allowedValues, adminOnly, enabled, color
                ));
            } else {
                // Use default
                fields.put(fieldKey, defaultDef);
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

        // Name field - basic info
        defaultFields.put("name", new FieldDefinition(
                "name", "Display Name", 32, false, null, false, true, "&e"
        ));

        // Age field - numeric range
        defaultFields.put("age", new FieldDefinition(
                "age", "Age", 3, false, Arrays.asList("1-99"), false, true, "&b"
        ));

        // Gender field - restricted values
        defaultFields.put("gender", new FieldDefinition(
                "gender", "Gender", 10, false, Arrays.asList("Male", "Female"),
                false, true, "&d"
        ));

        // Description field - multiline
        defaultFields.put("description", new FieldDefinition(
                "description", "Description", 200, true, null, false, true, "&7"
        ));

        // Location field
        defaultFields.put("location", new FieldDefinition(
                "location", "Location", 50, false, null, false, true, "&a"
        ));

        // Discord field
        defaultFields.put("discord", new FieldDefinition(
                "discord", "Discord", 37, false, null, false, true, "&5"
        ));

        // GitHub field
        defaultFields.put("github", new FieldDefinition(
                "github", "GitHub", 50, false, null, false, true, "&6"
        ));

        // Minecraft Skills - restricted values
        defaultFields.put("skills", new FieldDefinition(
                "skills", "Skills", 100, false,
                Arrays.asList("Builder", "Redstone", "PvP", "Parkour", "Explorer", "Farms", "Fishing", "Mining"),
                false, true, "&2"
        ));

        // Minecraft Experience - admin only
        defaultFields.put("experience", new FieldDefinition(
                "experience", "Experience", 20, false,
                Arrays.asList("Beginner", "Intermediate", "Advanced", "Expert", "Legendary"),
                true, true, "&c"
        ));

        // Time Zone
        defaultFields.put("timezone", new FieldDefinition(
                "timezone", "Time Zone", 20, false, null, false, true, "&3"
        ));

        // Language
        defaultFields.put("language", new FieldDefinition(
                "language", "Language", 20, false, null, false, true, "&9"
        ));

        // Website
        defaultFields.put("website", new FieldDefinition(
                "website", "Website", 100, false, null, false, true, "&1"
        ));

        return defaultFields;
    }

    /**
     * Gets a field definition by name.
     * Returns null if the field doesn't exist or is disabled.
     */
    public FieldDefinition getField(String fieldName) {
        String key = fieldName.toLowerCase(Locale.ROOT);
        FieldDefinition def = fields.get(key);
        return (def != null && def.isEnabled()) ? def : null;
    }

    /**
     * Gets all field definitions (including disabled ones).
     */
    public Collection<FieldDefinition> getAllFields() {
        return fields.values();
    }

    /**
     * Gets only enabled field names.
     */
    public List<String> getEnabledFieldNames() {
        return fields.values().stream()
                .filter(FieldDefinition::isEnabled)
                .map(FieldDefinition::getName)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public int getMaxFieldsPerProfile() {
        return maxFieldsPerProfile;
    }
}