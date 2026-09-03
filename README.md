# ProfilePlugin

Text-based, in-chat player profiles for Paper/Spigot servers. Players set
whatever fields they want (`/profile set age 20`), and profiles are viewed
privately in chat with `/profile show <nick>`.

## Build

```
mvn clean package
```

The shaded jar (with SQLite JDBC bundled and relocated) lands in `target/ProfilePlugin.jar`.
Drop it in your server's `plugins/` folder.

## Commands

- `/profile set <field> <value>` — set a field on your own profile.
  Multiline fields (configured in `fields.yml`) accept `|` as a line break:
  `/profile set description Hi there! | I build castles.`
- `/profile show [player]` — view a profile (yours by default). Only visible
  to you, in chat.
- `/profile clear <field> [player]` — clear a field. Admins (`profileplugin.admin`)
  can target other players.

## Configuration

- `config.yml` — plugin-wide behavior: storage backend, global field
  defaults, freeform-field toggle, max fields per profile, cooldown,
  display formatting, and all user-facing messages.
- `fields.yml` — **only** needed for fields that require special rules
  (longer max length, multiline, restricted values, admin-only). Simple
  fields like `age` or `gender` need no entry at all — they fall back to
  `field-defaults` in `config.yml` automatically.

## Storage

SQLite by default (`plugins/ProfilePlugin/profiles.db`), key-value schema
(`uuid, field, value`) so adding new fields never requires a migration.
All DB access is async, off the main server thread.

`ProfileStorage` is an interface — a MySQL implementation can be added later
(e.g. for a BungeeCord/Velocity network sharing profiles across servers)
without touching command code.

## Not yet implemented (ideas for later)

- `/profile list` — list which fields a player has set
- Per-field visibility toggles (e.g. hide age from non-friends)
- MiniMessage/Adventure formatting instead of legacy `&` codes
- MySQL storage backend for cross-server networks
