# Configuration

Settings are in `config/mods/rock-the-vote/config.properties`.
The plugin creates this UTF-8 file on first start.
Paths below are relative to `config/mods/rock-the-vote/`.

## Settings

| Key | Purpose | Default | Valid values |
| --- | --- | --- | --- |
| `defaultLocale` | Default message locale | `en` | Language tag |
| `requiredRatio` | YES ratio that passes a vote | `0.6` | Greater than 0 and at most 1 |
| `minPlayers` | Minimum number of players | `1` | 1 to 10000 |
| `voteDurationSeconds` | Vote lifetime | `60` | 1 to 3600 |
| `cooldownSeconds` | Delay after a result | `120` | 0 to 86400 |
| `mapStartDelaySeconds` | Warm-up after map start | `30` | 0 to 86400 |
| `changeDelaySeconds` | Delay before a passed map change | `5` | 0 to 86400 |
| `includeDefaultMaps` | Include built-in maps | `true` | `true` or `false` |
| `panelWidth` | Vote panel width in pixels | `600` | 280 to 800 |
| `accentColor` | Vote progress color | `FFD37F` | 6 or 8 hexadecimal digits |
| `bannerRegion` | Optional UI image region | Empty | Letters, numbers, `_`, `-`, `.`, or `/` |

The catalog uses the full native viewport and wraps map cards automatically.
`panelWidth` applies to the vote panel.

## Reload

Run `/rtv reload` as an admin, or `rtv reload` in the console.
A successful reload applies settings, messages, and images. It cancels the active vote or pending map change.
An invalid reload keeps the active configuration.

## Locales

Add or override translations in `locales/<code>.properties` with UTF-8 `key=value` entries.
Use the message keys from `src/main/resources/rtv/i18n/` and preserve placeholders such as `{0}`.
Missing messages fall back through the player locale, language, configured default, and built-in default.

Use `/rtv lang <code>` to select a language for the current connection.

## Map images

Put PNG files in `images/`.
Use lowercase letters, numbers, and hyphens in file names.
Each image can be at most 1024 by 1024 pixels and 1 MiB.
The library can contain up to 64 images and 8 MiB in total.

List map IDs with `rtv maps` in the console.
Map an ID to an image file stem in `map-images.properties`:

```properties
custom\:arena.msav=arena
```

This entry uses `images/arena.png` for `custom:arena.msav`. Escape colons in property keys.

To add a banner, place `banner.png` in `images/` and set `bannerRegion=net-rtv-image-banner`.
Streamed image regions use the name `net-rtv-image-<stem>`.
