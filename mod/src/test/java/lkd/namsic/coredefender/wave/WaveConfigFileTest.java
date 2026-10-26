package lkd.namsic.coredefender.wave;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WaveConfigFileTest {
    @TempDir
    Path directory;

    @Test
    void createsDefaultsOnceAndDoesNotOverwriteEditedOrInvalidFiles() throws IOException {
        Path file = this.directory.resolve("kubejs/config/coredefender-waves.json");
        String initial = WaveConfigFile.read(file);
        assertEquals(600, JsonParser.parseString(initial).getAsJsonObject().get("waveDelaySeconds").getAsInt());
        Files.writeString(file, "{invalid edit");
        assertEquals("{invalid edit", WaveConfigFile.read(file));
        assertEquals("{invalid edit", Files.readString(file));
    }

    @Test
    void parsedWaveRetainsItsOwnRulesAfterAnotherConfigIsRead() {
        WaveSpec first = WaveSpec.parse("""
            {"mobs":[],"rules":{"wardReduction":0.75,"wardSeconds":30,"spawnMinDistance":32,"spawnMaxDistance":64}}
            """);
        WaveSpec second = WaveSpec.parse("""
            {"mobs":[],"rules":{"wardReduction":0.2,"wardSeconds":10}}
            """);
        assertEquals(.75, first.rules().wardReduction());
        assertEquals(30, first.rules().wardSeconds());
        assertEquals(.2, second.rules().wardReduction());
        assertEquals(.375, SummonerWard.reductionAt(300, first.rules().wardReduction(), first.rules().wardSeconds() * 20));
    }
}
