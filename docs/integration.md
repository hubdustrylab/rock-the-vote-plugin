# Forking and integration

## Build a fork

Select **Fork** to keep a link to this repository on GitHub.
Select **Use this template** to start a new repository.

1. Clone your repository.
2. Set the plugin values in [`gradle.properties`](../gradle.properties).

   ```properties
   pluginId=arena-map-vote
   pluginDisplayName=Arena Map Vote
   pluginAuthor=Your team
   pluginDescription=Vote for the next map on the Arena server.
   pluginCommand=arena-vote
   pluginVersion=1.0.0
   ```

3. Run `bash ./gradlew clean build`. On Windows, run `./gradlew.bat clean build`.
4. Install the JAR from `build/libs/`. See [Install](../README.md#install).
5. In the game, run `/arena-vote`.

These settings produce `arena-map-vote-1.0.0.jar` and the data directory `config/mods/arena-map-vote/`.
Start `pluginId` and `pluginCommand` with a lowercase letter.
Use only lowercase letters, digits, and hyphens in these values.
Use a command name that is available in the game and the server console.

For server settings, see [Configuration](configuration.md).
For changes to the code, see [Development](development.md).
To publish your plugin, attach the standalone JAR from `build/libs/` to your release.

## Embed in a server plugin

Use Java 17 or later for the host plugin.
Put a checkout of this project in `vendor/rock-the-vote-plugin`.
Use the Git revision that you tested.

Add this line to the host's `settings.gradle`.

```groovy
includeBuild('vendor/rock-the-vote-plugin')
```

Add the module to the host's `build.gradle`.

```groovy
dependencies {
    implementation 'dev.hubdustry:rock-the-vote-runtime'
}
```

Use the same Mindustry version in both builds.
Add `RockTheVote` to the host plugin class.
Forward `registerClientCommands`, `registerServerCommands`, and `init` to the voting module.
In `init`, pass a directory under the host's `getConfigFolder()`.
See [ExampleServerPlugin.java](../examples/embedded/src/main/java/example/server/ExampleServerPlugin.java).

Put the voting module classes and resources in the host JAR.
Use the host's `plugin.json` as the JAR descriptor.
Use Mindustry and Arc as compile-only dependencies.
See the [example build](../examples/embedded/build.gradle).

## Test the example

Build the example with the Gradle wrapper.

```sh
bash ./gradlew -p examples/embedded build
```

On Windows, run `./gradlew.bat -p examples/embedded build`.
Install `examples/embedded/build/libs/server-with-map-vote.jar`.
In the server console, run `example-status`.
In the game, run `/mapvote`.
The example stores its settings in `config/mods/server-with-map-vote/map-voting/`.

Use one voting module per server.
Before you distribute an embedded JAR, read the [GPL-3.0 source and notice requirements](../LICENSE).
