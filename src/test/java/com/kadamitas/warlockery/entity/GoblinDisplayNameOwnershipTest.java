package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Name-tag regression guard.
 *
 * <p>Two changes collided: {@code setGoblinProfession} began refreshing the displayed name
 * unconditionally so that the one spawn in four whose profession roll lands on the existing
 * PROSPECTOR default is still named, and {@code GoblinEntity.readAdditionalSaveData} calls
 * {@code setGoblinProfession} on every load. Without an ownership test, a player-assigned name would
 * be overwritten by the profession name on every single reload.</p>
 *
 * <p>The body may only replace a name it wrote itself. This suite pins that predicate.</p>
 */
final class GoblinDisplayNameOwnershipTest {
    private static final String GOBLIN = "goblin";
    private static final String HOBGOBLIN = "hobgoblin";

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void anUnnamedBodyIsOwnedSoAFreshSpawnAlwaysGetsItsProfessionName() {
        assertTrue(AbstractGoblinMerchantEntity.isProfessionDisplayName(null, GOBLIN));
    }

    @Test
    void everyProfessionNameThisBodyWritesIsRecognisedAsItsOwn() {
        Arrays.stream(GoblinProfession.values()).forEach(profession -> {
            final Component written = Component.translatable(
                AbstractGoblinMerchantEntity.professionDisplayNameKey(GOBLIN, profession)
            );
            assertTrue(AbstractGoblinMerchantEntity.isProfessionDisplayName(written, GOBLIN),
                "a name this body wrote must stay replaceable so professions can change");
        });
    }

    @Test
    void aPlayerAssignedNameTagIsNeverTreatedAsOwned() {
        // The exact player-visible case: a name tag produces a literal component.
        assertFalse(AbstractGoblinMerchantEntity.isProfessionDisplayName(
            Component.literal("Grubnash"), GOBLIN),
            "a name-tagged Goblin must keep the name the player gave it");
        assertFalse(AbstractGoblinMerchantEntity.isProfessionDisplayName(
            Component.literal(""), GOBLIN));
        // An anvil-renamed tag carrying formatting is still a literal.
        assertFalse(AbstractGoblinMerchantEntity.isProfessionDisplayName(
            Component.literal("Grubnash").withStyle(net.minecraft.ChatFormatting.GOLD), GOBLIN));
    }

    @Test
    void aTranslatableNameFromOutsideThisProfessionFamilyIsNotOwned() {
        assertFalse(AbstractGoblinMerchantEntity.isProfessionDisplayName(
            Component.translatable("entity.warlockery.goblin"), GOBLIN),
            "the plain species name is not one of the four profession names");
        assertFalse(AbstractGoblinMerchantEntity.isProfessionDisplayName(
            Component.translatable("entity.minecraft.villager"), GOBLIN));
        assertFalse(AbstractGoblinMerchantEntity.isProfessionDisplayName(
            Component.translatable("block.minecraft.stone"), GOBLIN));
    }

    @Test
    void oneSpeciesNeverClaimsAnotherSpeciesProfessionName() {
        final Component hobgoblinMiner = Component.translatable(
            AbstractGoblinMerchantEntity.professionDisplayNameKey(HOBGOBLIN, GoblinProfession.MINER)
        );
        assertFalse(AbstractGoblinMerchantEntity.isProfessionDisplayName(hobgoblinMiner, GOBLIN),
            "a Goblin must not overwrite a Hobgoblin profession name");
        assertTrue(AbstractGoblinMerchantEntity.isProfessionDisplayName(hobgoblinMiner, HOBGOBLIN));
    }

    @Test
    void theProfessionKeysAreTheExactRetainedLocalizationKeys() {
        // These strings are immutable public surface; the localization audit reads the same family.
        assertTrue("entity.warlockery.goblin.profession.miner".equals(
            AbstractGoblinMerchantEntity.professionDisplayNameKey(GOBLIN, GoblinProfession.MINER)));
        assertTrue("entity.warlockery.goblin.profession.smith".equals(
            AbstractGoblinMerchantEntity.professionDisplayNameKey(GOBLIN, GoblinProfession.SMITH)));
        assertTrue("entity.warlockery.goblin.profession.shaman".equals(
            AbstractGoblinMerchantEntity.professionDisplayNameKey(GOBLIN, GoblinProfession.SHAMAN)));
        assertTrue("entity.warlockery.goblin.profession.prospector".equals(
            AbstractGoblinMerchantEntity.professionDisplayNameKey(GOBLIN, GoblinProfession.PROSPECTOR)));
        assertTrue("entity.warlockery.hobgoblin.profession.miner".equals(
            AbstractGoblinMerchantEntity.professionDisplayNameKey(HOBGOBLIN, GoblinProfession.MINER)));
    }
}
