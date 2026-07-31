package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.FactoryCatalog;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class UtilityDeviceBlockFactory {
    private static final FactoryCatalog<BlockBehaviour.Properties, Block> FIXED_FACTORIES = new FactoryCatalog<>(
        "utility device block",
        Map.ofEntries(
            FactoryCatalog.entry("alluringskull", properties ->
                new AlluringSkullBlock(properties.noOcclusion().sound(SoundType.BONE_BLOCK))),
            FactoryCatalog.entry("beartrap", properties ->
                new BearTrapBlock(properties.noCollision().noOcclusion().sound(SoundType.METAL))),
            FactoryCatalog.entry("chalice", properties ->
                new AltarChaliceBlock(properties.noOcclusion().sound(SoundType.METAL))),
            FactoryCatalog.entry("crystalball", properties ->
                new CrystalBallBlock(properties.noOcclusion().sound(SoundType.GLASS))),
            FactoryCatalog.entry("daylightcollector", properties ->
                new SunCollectorBlock(properties.noOcclusion().sound(SoundType.GLASS))),
            FactoryCatalog.entry("demonheart", properties ->
                new DemonHeartBlock(properties.noOcclusion().sound(SoundType.NETHER_WART))),
            FactoryCatalog.entry("dreamcatcher", properties ->
                new DreamWeaverBlock(properties.noOcclusion().sound(SoundType.WOOL))),
            FactoryCatalog.entry("doll_shelf", properties -> new DollShelfBlock(properties.sound(SoundType.WOOD))),
            FactoryCatalog.entry("pentacle", properties ->
                new PentacleBlock(properties.noCollision().noOcclusion().instabreak().sound(SoundType.WOOL))),
            FactoryCatalog.entry("scarecrow", properties ->
                new FetishBlock(properties.noOcclusion().sound(SoundType.WOOD))),
            FactoryCatalog.entry("voidbramble", properties -> new VoidBrambleBlock(
                properties.noCollision().noOcclusion().strength(1.0F, 3_600_000.0F).sound(SoundType.GRASS)
            )),
            FactoryCatalog.entry("wickerbundle", properties -> new WickerBundleBlock(properties.sound(SoundType.WOOD))),
            FactoryCatalog.entry("wolfhead", properties ->
                new WolfHeadBlock(properties.noOcclusion().sound(SoundType.WOOL)))
        )
    );
    private static final Set<String> SUPPORTED = Stream.of(
        FIXED_FACTORIES.ids().stream(),
        StatueProfile.ids().stream(),
        UtilityDeviceProfile.blockIds().stream()
    ).flatMap(java.util.function.Function.identity()).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private UtilityDeviceBlockFactory() {
    }

    public static boolean supports(final String id) {
        return SUPPORTED.contains(id);
    }

    public static Set<String> supportedIds() {
        return SUPPORTED;
    }

    public static Block create(final String id, final BlockBehaviour.Properties properties) {
        return FIXED_FACTORIES.factoryFor(id)
            .map(factory -> factory.create(properties))
            .or(() -> UtilityDeviceProfile.find(id)
                .<Block>map(profile -> new InteractiveUtilityBlock(properties, profile)))
            .or(() -> StatueProfile.find(id)
                .map(profile -> new StatueBlock(properties.noOcclusion().sound(SoundType.STONE), profile)))
            .orElseThrow(() -> new IllegalArgumentException("Unsupported utility device block: " + id));
    }
}
