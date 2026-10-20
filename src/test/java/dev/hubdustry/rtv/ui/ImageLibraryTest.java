package dev.hubdustry.rtv.ui;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ImageLibraryTest {
    @Test void alwaysLoadsBundledClock() throws Exception {
        ImageLibrary library = ImageLibrary.load(null);
        assertTrue(library.regions().contains("net-rtv-ui-clock"));
    }

    @Test void loadsAndMapsImage() throws Exception {
        Path root = Files.createTempDirectory("rtv-images");
        Files.createDirectories(root.resolve("images"));
        write(root.resolve("images/map-one.png"), 16, 8);
        Files.writeString(root.resolve("map-images.properties"), "custom\\:map-one=map-one" + System.lineSeparator());
        ImageLibrary library = ImageLibrary.load(root);
        assertTrue(library.regions().contains("net-rtv-image-map-one"));
        assertEquals("net-rtv-image-map-one", library.imageFor("custom:map-one"));
    }

    @Test void rejectsMalformedSignatureAndTruncatedInput() throws Exception {
        Path root = Files.createTempDirectory("rtv-bad");
        Files.createDirectories(root.resolve("images"));
        Files.write(root.resolve("images/bad.png"), new byte[]{(byte)0x89, 0x50});
        assertThrows(IOException.class, () -> ImageLibrary.load(root));
    }

    @Test void rejectsDimensionsAboveLimit() throws Exception {
        Path root = Files.createTempDirectory("rtv-large-dim");
        Files.createDirectories(root.resolve("images"));
        write(root.resolve("images/large.png"), 1025, 1);
        assertThrows(IOException.class, () -> ImageLibrary.load(root));
    }


    @Test void rejectsMappingWhenImagesDirectoryIsMissing() throws Exception {
        Path root = Files.createTempDirectory("rtv-no-images");
        Files.writeString(root.resolve("map-images.properties"), "custom\\:missing=missing" + System.lineSeparator());
        assertThrows(IOException.class, () -> ImageLibrary.load(root));
    }

    @Test void rejectsMissingMappingReference() throws Exception {
        Path root = Files.createTempDirectory("rtv-missing");
        Files.createDirectories(root.resolve("images"));
        write(root.resolve("images/present.png"), 1, 1);
        Files.writeString(root.resolve("map-images.properties"), "builtin\\:missing=missing" + System.lineSeparator());
        assertThrows(IOException.class, () -> ImageLibrary.load(root));
    }

    private static void write(Path file, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        assertTrue(ImageIO.write(image, "png", file.toFile()));
    }
}
