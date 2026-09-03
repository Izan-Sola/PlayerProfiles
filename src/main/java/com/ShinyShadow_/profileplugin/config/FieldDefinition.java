package com.ShinyShadow_.profileplugin.config;

import java.util.List;

/**
 * Validation rules for a single profile field.
 * Fields are defined in code and can be configured via config.yml.
 */
public class FieldDefinition {

    private final String name;
    private final String displayName;
    private final int maxLength;
    private final boolean multiline;
    private final List<String> allowedValues; // null = free text
    private final boolean adminOnly;
    private final boolean enabled;
    private final String color;

    public FieldDefinition(String name, String displayName, int maxLength, boolean multiline,
                           List<String> allowedValues, boolean adminOnly, boolean enabled, String color) {
        this.name = name;
        this.displayName = displayName;
        this.maxLength = maxLength;
        this.multiline = multiline;
        this.allowedValues = allowedValues;
        this.adminOnly = adminOnly;
        this.enabled = enabled;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public boolean isMultiline() {
        return multiline;
    }

    public List<String> getAllowedValues() {
        return allowedValues;
    }

    public boolean hasRestrictedValues() {
        return allowedValues != null && !allowedValues.isEmpty();
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getColor() {
        return color;
    }

    /**
     * Checks a candidate value against this field's rules.
     * Returns ValidationResult with valid=true if valid, false otherwise.
     */
    public ValidationResult validate(String value) {
        if (value.length() > maxLength) {
            return ValidationResult.tooLong(maxLength);
        }
        if (hasRestrictedValues()) {
            boolean matches = allowedValues.stream()
                    .anyMatch(v -> v.equalsIgnoreCase(value));
            if (!matches) {
                return ValidationResult.notAllowed(allowedValues);
            }
        }
        return ValidationResult.ok();
    }

    public static class ValidationResult {
        public final boolean valid;
        public final String reason; // "too_long" or "not_allowed"
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