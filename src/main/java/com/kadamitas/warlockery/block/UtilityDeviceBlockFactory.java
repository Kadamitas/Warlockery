package com.kadamitas.warlockery.block;

import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class UtilityDeviceBlockFactory {
    private static final Set<String> SUPPORTED = java.util.stream.Stream.of(
        java.util.stream.Stream.of(
        "alluringskull",
        "beartrap",
        "chalice",
        "crystalball",
        "daylightcollector",
        "demonheart",
        "dreamcatcher",
        "doll_shelf",
        "pentacle",
        "scarecrow",
        "voidbramble",
        "wickerbundle",
        "wolfhead"
        ),
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
        return switch (id) {
            case "alluringskull" -> new AlluringSkullBlock(properties.noOcclusion().sound(SoundType.BONE_BLOCK));
            case "beartrap" -> new BearTrapBlock(properties.noCollision().noOcclusion().sound(SoundType.METAL));
            case "chalice" -> new AltarChaliceBlock(properties.noOcclusion().sound(SoundType.METAL));
            case "crystalball" -> new CrystalBallBlock(properties.noOcclusion().sound(SoundType.GLASS));
            case "daylightcollector" -> new SunCollectorBlock(properties.noOcclusion().sound(SoundType.GLASS));
            case "demonheart" -> new DemonHeartBlock(properties.noOcclusion().sound(SoundType.NETHER_WART));
            case "dreamcatcher" -> new DreamWeaverBlock(properties.noOcclusion().sound(SoundType.WOOL));
            case "doll_shelf" -> new DollShelfBlock(properties.sound(SoundType.WOOD));
            case "pentacle" -> new PentacleBlock(properties.noCollision().noOcclusion().instabreak().sound(SoundType.WOOL));
            case "scarecrow" -> new FetishBlock(properties.noOcclusion().sound(SoundType.WOOD));
            case "voidbramble" -> new VoidBrambleBlock(properties.noCollision().noOcclusion().instabreak().sound(SoundType.GRASS));
            case "wickerbundle" -> new WickerBundleBlock(properties.sound(SoundType.WOOD));
            case "wolfhead" -> new WolfHeadBlock(properties.noOcclusion().sound(SoundType.WOOL));
            default -> UtilityDeviceProfile.find(id)
                .<Block>map(profile -> new InteractiveUtilityBlock(properties, profile))
                .or(() -> StatueProfile.find(id)
                    .map(profile -> new StatueBlock(properties.noOcclusion().sound(SoundType.STONE), profile)))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported utility device block: " + id));
        };
    }
}
