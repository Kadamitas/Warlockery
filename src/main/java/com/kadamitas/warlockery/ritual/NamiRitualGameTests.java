package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.entity.NaamahEntity;
import com.kadamitas.warlockery.entity.NamiEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.ritual.marriage.MarriageData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class NamiRitualGameTests {
    private NamiRitualGameTests() {
    }

    public static void bloodAudienceTransformsUnmarriedNami(final GameTestHelper helper) {
        final BlockPos relativeCenter = new BlockPos(1, 1, 1);
        helper.setBlock(relativeCenter.below(), Blocks.STONE);
        final NamiEntity nami = helper.spawn(ModEntities.NAMI.get(), relativeCenter, EntitySpawnReason.TRIGGERED);
        final BlockPos center = helper.absolutePos(relativeCenter);

        helper.assertTrue(RitualManager.transformNami(helper.getLevel(), center, null, 8),
            "Blood Audience must accept an unmarried Nami");
        helper.assertTrue(nami.isRemoved(), "successful transformation must replace Nami");
        helper.assertValueEqual(
            helper.getLevel().getEntitiesOfClass(NaamahEntity.class, new AABB(center).inflate(3.0)).size(),
            1,
            "successful transformation must create exactly one Naamah"
        );
        helper.succeed();
    }

    public static void bloodAudienceProtectsMarriedNami(final GameTestHelper helper) {
        final BlockPos relativeCenter = new BlockPos(1, 1, 1);
        helper.setBlock(relativeCenter.below(), Blocks.STONE);
        final NamiEntity nami = helper.spawn(ModEntities.NAMI.get(), relativeCenter, EntitySpawnReason.TRIGGERED);
        MarriageData.get(helper.getLevel()).marryNami(UUID.randomUUID(), nami.getUUID());
        final BlockPos center = helper.absolutePos(relativeCenter);

        helper.assertFalse(RitualManager.transformNami(helper.getLevel(), center, null, 8),
            "Blood Audience must reject a married Nami");
        helper.assertTrue(nami.isAlive() && !nami.isRemoved(), "failed transformation must leave Nami unharmed");
        helper.assertTrue(
            helper.getLevel().getEntitiesOfClass(NaamahEntity.class, new AABB(center).inflate(3.0)).isEmpty(),
            "failed transformation must not create Naamah"
        );
        helper.succeed();
    }
}
