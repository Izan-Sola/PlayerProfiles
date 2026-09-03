package com.ShinyShadow_.profileplugin.config;

import java.util.List;

public class FieldDefinition {

    private final String name;
    private final String displayName;
    private final int maxLength;
    private final boolean multiline;
    private final List<String> allowedValues;
    private final boolean adminOnly;
    private final boolean enabled;
    private final String color;
    private final boolean hardcodedAllowedValues;

    public FieldDefinition(String name, String displayName, int maxLength, boolean multiline,
                           List<String> allowedValues, boolean adminOnly, boolean enabled, String color) {
        this(name, displayName, maxLength, multiline, allowedValues, adminOnly, enabled, color, false);
    }

    public FieldDefinition(String name, String displayName, int maxLength, boolean multiline,
                           List<String> allowedValues, boolean adminOnly, boolean enabled, String color,
                           boolean hardcodedAllowedValues) {
        this.name = name;
        this.displayName = displayName;
        this.maxLength = maxLength;
        this.multiline = multiline;
        this.allowedValues = allowedValues;
        this.adminOnly = adminOnly;
        this.enabled = enabled;
        this.color = color;
        this.hardcodedAllowedValues = hardcodedAllowedValues;
    }

    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public int getMaxLength() { return maxLength; }
    public boolean isMultiline() { return multiline; }
    public List<String> getAllowedValues() { return allowedValues; }
    public boolean hasRestrictedValues() { return allowedValues != null && !allowedValues.isEmpty(); }
    public boolean isAdminOnly() { return adminOnly; }
    public boolean isEnabled() { return enabled; }
    public String getColor() { return color; }
    public boolean hasHardcodedAllowedValues() { return hardcodedAllowedValues; }

    public ValidationResult validate(String value) {
        if (value.length() > maxLength) {
            return ValidationResult.tooLong(maxLength);
        }
        if (hasRestrictedValues()) {
            for (String allowed : allowedValues) {
                if (allowed.contains("-") && allowed.matches("\\d+-\\d+")) {
                    String[] parts = allowed.split("-");
                    try {
                        int min = Integer.parseInt(parts[0]);
                        int max = Integer.parseInt(parts[1]);
                        int numValue = Integer.parseInt(value);
                        if (numValue >= min && numValue <= max) {
                            return ValidationResult.ok();
                        }
                    } catch (NumberFormatException e) {
                        // Skip this range check
                    }
                } else if (allowed.equalsIgnoreCase(value)) {
                    return ValidationResult.ok();
                }
            }
            return ValidationResult.notAllowed(allowedValues);
        }
        return ValidationResult.ok();
    }

    public static class ValidationResult {
        public final boolean valid;
        public final String reason;
        public final int maxLength;
        public final List<String> allowedValues;

        private ValidationResult(boolean valid, String reason, int maxLength, List<String> allowedValues) {
            this.valid = valid;
            this.reason = reason;
            this.maxLength = maxLength;
            this.allowedValues = allowedValues;
        }

        static ValidationResult ok() {
            return new ValidationResult(true, null, 0, null);
        }

        static ValidationResult tooLong(int maxLength) {
            return new ValidationResult(false, "too_long", maxLength, null);
        }

        static ValidationResult notAllowed(List<String> allowedValues) {
            return new ValidationResult(false, "not_allowed", 0, allowedValues);
        }
    }
}