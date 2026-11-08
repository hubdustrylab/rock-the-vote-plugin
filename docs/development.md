# Development

Use Java 17 or later. Run the Gradle wrapper in this repository.
The default build uses Mindustry v160.7. The minimum version is v160.
Gradle downloads the server JAR and checks its SHA-256 hash.
The running server supplies the Mindustry and Arc classes.

## Structure

The root project builds the standalone plugin in `build/libs/`.
It creates `plugin.json` from `gradle.properties` and includes the voting module.
The `:rock-the-vote-runtime` module in `runtime/` contains the voting code, messages, and images.
The `examples/embedded/` project uses the module in a host plugin.

| Component | Purpose |
| --- | --- |
| `RockTheVote` | Server hooks, commands, events, and map changes. |
| `vote/` | Vote rules and session state. |
| `maps/` | Map selection, metadata, and IDs. |
| `ui/` | Panels and PNG images. |
| `uiaccess/` | Player tokens and panel permissions. |
| `config/` | Settings and validation. |
| `i18n/` | Message loading and locale fallback. |

Run the voting hooks on the server application thread.
The vote rules use Java types only.
The plugin sends the layout, slots, and cards in separate packets.
Keep each panel packet below 24,000 bytes.

## Vote rules

The plugin records the UUIDs of all connected players at the start of a vote.
This includes spectators. The player who starts the vote votes yes automatically.
The number of yes votes necessary to pass is `ceil(requiredRatio * playerCount)`.

Players who leave keep their votes and can vote again if they reconnect in time.
Players who join after a vote starts must wait for the next vote.
Players can change their votes until the vote ends.

A vote passes when the number of yes votes reaches the required count.
The vote fails if the yes votes and the votes left add up to less than the required count.
The vote expires when the time to vote ends.

If a vote passes, the plugin waits for `changeDelaySeconds`.
It checks that the map is available before it loads the map.
A world change, game over, or successful reload cancels the active vote or pending map change.

## Tests

Build and test with Mindustry v160.7.

```sh
bash ./gradlew build
```

Build and test with Mindustry v160.

```sh
bash ./gradlew build -PmindustryVersion=v160
```

Build the example host plugin.

```sh
bash ./gradlew -p examples/embedded build
```

On Windows, use `./gradlew.bat` in place of `bash ./gradlew`.
The test report is in `runtime/build/reports/tests/test/`.
CI starts the example host on v160 and v160.7.

If you change a panel, test it on desktop and mobile clients.
If you change map loading, test a vote with two connected players.

## English text

Use the [ASD-STE100 Issue 9 writing rules](https://www.asd-ste100.org/about_STE.html) for instructions and messages.
Use one action in each instruction. Use a maximum of 20 words in an instruction.
Use the same term for each item. Keep command names, file paths, and message keys as they appear in the code.
