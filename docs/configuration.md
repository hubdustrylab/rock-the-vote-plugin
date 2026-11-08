# Configuration

The plugin creates `config.properties` in `config/mods/rock-the-vote/` on its first start.
Use UTF-8 for the configuration and message files.
The paths on this page are relative to the plugin data directory.
For a fork, the directory is `config/mods/<pluginId>/`.
If you add voting to a host plugin, set the directory in the host's `init` method.

## Settings

| Key | Purpose | Default | Valid values |
| --- | --- | --- | --- |
| `defaultLocale` | Default language | `en` | Language tag |
| `requiredRatio` | Minimum share of eligible players who must vote yes | `0.6` | Greater than 0 and at most 1 |
| `minPlayers` | Minimum number of connected players | `1` | 1 to 10000 |
| `voteDurationSeconds` | Time to vote | `60` | 1 to 3600 |
| `cooldownSeconds` | Delay after a result | `120` | 0 to 86400 |
| `mapStartDelaySeconds` | Delay before voting after a map starts | `30` | 0 to 86400 |
| `changeDelaySeconds` | Delay before the map changes after a vote passes | `5` | 0 to 86400 |
| `includeDefaultMaps` | Include built-in maps | `true` | `true` or `false` |
| `panelWidth` | Vote panel width in pixels | `600` | 280 to 800 |
| `accentColor` | Vote progress color | `FFD37F` | 6 or 8 hexadecimal digits |
| `bannerRegion` | Optional UI image region | Empty | Letters, numbers, `_`, `-`, `.`, or `/` |

The map list fits the screen. `panelWidth` sets the width of the vote panel.

## Reload

In the console, run `rtv reload`. Admins can also run `/rtv reload` in the game.
If the reload succeeds, the plugin applies the new settings, messages, and images.
It also cancels the active vote or pending map change.
If the reload fails, the plugin keeps the active configuration.

## Locales

The plugin reads each player's language when the player connects.
It includes the 35 languages in Mindustry v160–v160.7 and the `router` mode.
To list the language codes, run `/rtv lang`.
To set a language for this connection, run `/rtv lang <code>`.
The code can contain an underscore or a hyphen. For example, use `pt_BR` or `pt-br`.

To change messages, add `key=value` lines to `locales/<code>.properties`.
Use the [English message keys](../runtime/src/main/resources/rtv/i18n/en.properties).
Keep placeholders such as `{0}` in the message.
In `title`, `{0}` is the display name. In `usage`, `{0}` is the client command, such as `/rtv`.

The plugin checks the full code, shorter codes, and regional aliases in that order.
For a missing message, the plugin checks `defaultLocale` and then English.
For example, `zh-Hant` uses `zh-tw` if the full code has no message.

## Map images

Put PNG files in `images/`.
Use lowercase letters, numbers, and hyphens in file names.
Each image has a limit of 1024 × 1024 pixels and 1 MiB.
The directory has a limit of 64 images and 8 MiB in total.

1. In the console, run `rtv maps` to list the map IDs.
2. Add the map ID and the image file name without `.png` to `map-images.properties`.

```properties
custom\:arena.msav=arena
```

This line sets the image for `custom:arena.msav` to `images/arena.png`.
Put `\` before each colon in the map ID.

To add a banner, put `banner.png` in `images/`.
Set `bannerRegion=net-rtv-image-banner`.
If you change the command name, use `net-<command>-image-banner`.
