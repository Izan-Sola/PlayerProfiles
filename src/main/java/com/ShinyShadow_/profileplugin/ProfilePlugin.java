package com.ShinyShadow_.profileplugin;

import com.ShinyShadow_.profileplugin.commands.ProfileCommand;
import com.ShinyShadow_.profileplugin.config.FieldConfigManager;
import com.ShinyShadow_.profileplugin.storage.ProfileStorage;
import com.ShinyShadow_.profileplugin.storage.SQLiteProfileStorage;
import org.bukkit.plugin.java.JavaPlugin;

public class ProfilePlugin extends JavaPlugin {

    private ProfileStorage storage;
    private FieldConfigManager fieldConfigManager;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // creates config.yml from resources if missing

        this.fieldConfigManager = new FieldConfigManager(this);
        this.fieldConfigManager.load();

        String storageType = getConfig().getString("storage.type", "sqlite");
        if (!storageType.equalsIgnoreCase("sqlite")) {
            getLogger().warning("Unknown storage.type '" + storageType + "', falling back to sqlite.");
        }
        String dbFile = getConfig().getString("storage.file", "profiles.db");
        this.storage = new SQLiteProfileStorage(this, dbFile);
        this.storage.init();

        ProfileCommand profileCommand = new ProfileCommand(this, storage, fieldConfigManager);
        getCommand("profile").setExecutor(profileCommand);
        getCommand("profile").setTabCompleter(profileCommand);

        getLogger().info("ProfilePlugin enabled.");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
        getLogger().info("ProfilePlugin disabled.");
    }

    /** Reloads config.yml without restarting the server. */
    public void reloadAll() {
        reloadConfig();
        fieldConfigManager.load();
    }

    public ProfileStorage getStorage() {
        return storage;
    }

    public FieldConfigManager getFieldConfigManager() {
        return fieldConfigManager;
    }
}