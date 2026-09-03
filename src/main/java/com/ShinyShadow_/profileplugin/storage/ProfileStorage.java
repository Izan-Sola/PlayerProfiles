package com.ShinyShadow_.profileplugin.storage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Backend-agnostic profile storage. All methods are async (return a
 * CompletableFuture) - implementations must NOT block the calling thread,
 * since commands will call these from the main server thread.
 *
 * Currently only SQLiteProfileStorage exists, but this interface is what
 * lets a MySQL implementation be dropped in later (e.g. for a network of
 * servers sharing one profile database) without touching command code.
 */
public interface ProfileStorage {

    /** Initializes the backend (creates tables/files as needed). Called once on enable. */
    void init();

    /** Closes any open connections. Called on disable. */
    void close();

    /** Fetches all fields for a player as a field-name -> value map. */
    CompletableFuture<Map<String, String>> getProfile(UUID uuid);

    /** Sets a single field's value for a player (insert or overwrite). */
    CompletableFuture<Void> setField(UUID uuid, String field, String value);

    /** Removes a single field from a player's profile. */
    CompletableFuture<Void> clearField(UUID uuid, String field);

    /** Removes every field from a player's profile. */
    CompletableFuture<Void> clearProfile(UUID uuid);
}
