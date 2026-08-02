
package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class BeastSpeechTradeSeed {
    private static final String TRADE_NONCE = "WarlockeryBeastSpeechTradeNonce";
    private static final long NONCE_MULTIPLIER = 0x9e3779b97f4a7c15L;

    private BeastSpeechTradeSeed() {
    }

    public static long next(
        final ServerLevel level,
        final ServerPlayer trader,
        final LivingEntity partner
    ) {
        return compose(
            trader.getUUID(),
            partner.getUUID(),
            level.getGameTime(),
            nextNonce(WarlockeryEntityData.get(trader)),
            level.getRandom().nextLong()
        );
    }

    static long nextNonce(final CompoundTag tradeState) {
        final long nonce = tradeState.getLongOr(TRADE_NONCE, 0L) + 1L;
        tradeState.putLong(TRADE_NONCE, nonce);
        return nonce;
    }

    static long compose(
        final UUID traderId,
        final UUID partnerId,
        final long gameTime,
        final long nonce,
        final long entropy
    ) {
        final long context = traderId.getMostSignificantBits()
            ^ Long.rotateLeft(traderId.getLeastSignificantBits(), 13)
            ^ Long.rotateLeft(partnerId.getMostSignificantBits(), 29)
            ^ Long.rotateLeft(partnerId.getLeastSignificantBits(), 47)
            ^ Long.rotateLeft(gameTime, 7)
            ^ entropy;
        return mix(context ^ nonce * NONCE_MULTIPLIER);
    }

    private static long mix(final long value) {
        long mixed = value;
        mixed ^= mixed >>> 30;
        mixed *= 0xbf58476d1ce4e5b9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94d049bb133111ebL;
        return mixed ^ mixed >>> 31;
    }
}

