package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;

public final class DivinationRuntime {
    private DivinationRuntime() {
    }

    public static DivinationRules.Prediction predict(final Level level, final BlockPos position) {
        if (level.isThundering() || level.isRaining()) {
            return DivinationRules.Prediction.STORM;
        }
        final long time = level.getOverworldClockTime() % 24_000L;
        final boolean night = time >= 13_000L && time <= 23_000L;
        if (night && level instanceof ServerLevel serverLevel
            && serverLevel.environmentAttributes().getValue(
                EnvironmentAttributes.MOON_PHASE,
                Vec3.atCenterOf(position)
            ) == MoonPhase.FULL_MOON) {
            return DivinationRules.Prediction.FULL_MOON;
        }
        return night ? DivinationRules.Prediction.NIGHT : DivinationRules.Prediction.DAY;
    }

    public static Progression progression(final Player player) {
        final List<MagicPath> paths = MagicPathState.active(player);
        return new Progression(
            SupernaturalState.getForm(player),
            SupernaturalState.getReserve(player),
            paths,
            paths.stream().mapToInt(path -> MagicPathState.reserve(player, path)).sum()
        );
    }

    public record Progression(
        SupernaturalForm form,
        int supernaturalReserve,
        List<MagicPath> paths,
        int totalPathReserve
    ) {
        public Progression {
            paths = List.copyOf(paths);
        }
    }
}
