package dev.hubdustry.rtv.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class PluginSettingsTest {
    @Test
    void createsDefaults() throws Exception {
        var settings = PluginSettings.load(Files.createTempDirectory("rtv-settings"));
        assertEquals("en", settings.defaultLocale());
        assertEquals(600, settings.panelWidth());
    }

    @Test
    void rejectsUnknownProperty() throws Exception {
        var directory = Files.createTempDirectory("rtv-settings");
        Files.writeString(directory.resolve("config.properties"), "defaultLocale=en\nunknown=true\n");
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.load(directory));
    }

    @Test
    void rejectsOutOfRangeValue() throws Exception {
        var directory = Files.createTempDirectory("rtv-settings");
        PluginSettings.load(directory);
        Files.writeString(directory.resolve("config.properties"), "panelWidth=801\n",
                java.nio.file.StandardOpenOption.APPEND);
        var error = assertThrows(IllegalArgumentException.class, () -> PluginSettings.load(directory));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("panelWidth"));
    }
}
