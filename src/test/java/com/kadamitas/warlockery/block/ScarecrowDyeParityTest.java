package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

final class ScarecrowDyeParityTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void robeStateSupportsEveryVanillaDyeAndTheModelTintsOnlyCloth() throws IOException {
        assertEquals(16, FetishBlock.ROBE.getPossibleValues().size());
        final String model = Files.readString(Path.of(
            "src", "main", "resources", "assets", "warlockery", "models", "block", "scarecrow.json"
        ));
        assertTrue(model.contains("\"texture\": \"#cloth\", \"tintindex\": 0"));
        assertTrue(model.contains("\"texture\": \"#wood\""));
        assertTrue(model.contains("\"texture\": \"#straw\""));
    }
}
