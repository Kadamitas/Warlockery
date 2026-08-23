package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class EntPresentationSyncContractTest {
    private static final Path ENTITY = Path.of(
        "src/main/java/com/kadamitas/warlockery/entity/EntEntity.java"
    );
    private static final Path MODEL = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/EntModel.java"
    );

    @Test
    void publishesPrimitivePhaseAfterTheAuthoritativeRuntimeTick() throws Exception {
        final Method getter = EntEntity.class.getMethod("presentationPhase");
        assertEquals(EntRules.Phase.class, getter.getReturnType());

        final String source = Files.readString(ENTITY);
        assertTrue(source.contains("EntityDataAccessor<Byte> DATA_PRESENTATION_PHASE"));
        assertTrue(source.contains("EntityDataSerializers.BYTE"));
        assertTrue(source.contains("EntityPresentationSync.encode(EntRules.Phase.WARDING)"));
        assertTrue(source.contains("EntityPresentationSync.decode("));
        final int runtimeTick = source.indexOf("EntRuntime.tick(this,level);");
        final int publish = source.indexOf("syncPresentationFromRuntime();", runtimeTick);
        assertTrue(runtimeTick >= 0 && publish > runtimeTick);
    }

    @Test
    void modelRousesOnlyFromSyncedCombatPhasesOrAttackProgress() throws Exception {
        final String source = Files.readString(MODEL);
        assertTrue(source.contains("entity.presentationPhase()"));
        assertTrue(source.contains("EntRules.Phase.ROUSED"));
        assertTrue(source.contains("EntRules.Phase.WARN"));
        assertTrue(source.contains("EntRules.Phase.STRIKE"));
        assertTrue(source.contains("state.attackProgress > 0.0F"));
        assertFalse(source.contains("entity.getTarget()"));
        assertFalse(source.contains("entity.entTransient()"));
    }
}
