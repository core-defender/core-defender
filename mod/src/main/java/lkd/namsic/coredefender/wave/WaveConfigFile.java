package lkd.namsic.coredefender.wave;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WaveConfigFile {
    private WaveConfigFile() {
    }

    public static String defaults() {
        try (InputStream stream = WaveConfigFile.class.getResourceAsStream("/defaults/coredefender-waves.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled wave configuration");
            }

            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled wave configuration", exception);
        }
    }

    public static String read(Path file) throws IOException {
        if (!Files.exists(file)) {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(file, defaults(), StandardCharsets.UTF_8);
        }

        if (Files.size(file) > 1_048_576) {
            throw new IOException("Wave configuration exceeds 1 MiB");
        }

        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
