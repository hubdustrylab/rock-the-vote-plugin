# Development

The project uses Java 17 and the bundled Gradle wrapper.
The development target is Mindustry v160.7. The minimum supported version is v160.
The build downloads the server dependency and verifies its SHA-256 hash.
Engine classes are excluded from the plugin JAR.

## Structure

Java sources are in `src/main/java/dev/hubdustry/rtv/`.
Message files are in `src/main/resources/rtv/i18n/`.

| Component | Purpose |
| --- | --- |
| `RockTheVotePlugin` | Commands, events, and map transitions. |
| `vote/` | Vote rules and session state. |
| `maps/` | Map selection, metadata, and IDs. |
| `ui/` | Panel layout and PNG assets. |
| `uiaccess/` | Player tokens and allowed panel actions. |
| `config/` | Settings and validation. |
| `i18n/` | Message loading and locale fallback. |

Engine operations run on the server application thread.
The vote engine has no Mindustry dependency.
The catalog sends the layout, slots, and card contents in separate UI updates.
Keep each UI packet below 24,000 bytes.

## Vote rules

A session copies the connected player UUIDs when it starts. Spectators are included.
The initiator casts YES automatically.
The required count is `ceil(requiredRatio * playerCount)`.

Disconnected players keep their ballots and voting rights for that session.
New players cannot join the active vote.
Players can change their ballots until the vote ends.

A vote passes when YES reaches the required count.
It fails when the remaining uncast ballots cannot reach that count.
It expires at the configured deadline.

After a passed vote, the plugin rechecks the selected map and loads it after the configured delay.
A world change, game over, or successful configuration reload cancels the active request.

## Customize the template

Use [Configuration](configuration.md) for server settings, translations, and images.
Edit `PanelRenderer` to change the panel layout.
Edit `VoteSession` to change vote rules.

To change the project identity, update these locations:

| Location | Values |
| --- | --- |
| `settings.gradle` | Project name. |
| `gradle.properties` | Plugin version and engine target. |
| `build.gradle` | Group, artifact name, and engine dependency hashes. |
| `src/main/resources/plugin.json` | Plugin name, entry class, author, and description. |
| Java packages and resource paths | Code namespace and message resources. |
| `RockTheVotePlugin` | Command name and configuration directory. |

## Tests

Build the plugin and run the unit tests:

```sh
bash ./gradlew build
```

Run the same checks against the minimum engine version:

```sh
bash ./gradlew build -PmindustryVersion=v160
```

On Windows, replace `bash ./gradlew` with `./gradlew.bat`.
Unit reports are in `build/reports/tests/test/`.

For panel changes, also test search, scrolling, images, and voting on desktop and mobile clients.
For map transitions, test with connected multiplayer clients.
