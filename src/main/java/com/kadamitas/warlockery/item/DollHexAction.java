package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public enum DollHexAction implements StringIdentified {
    PRICK("prick", (_, target) ->
        target.hurtServer((ServerLevel) target.level(), target.damageSources().magic(), 4.0F)),
    SHOVE("shove", (source, target) -> target.knockback(
        1.5,
        source.getX() - target.getX(),
        source.getZ() - target.getZ(),
        target.damageSources().magic(),
        0.0F
    )),
    IGNITE("ignite", (_, target) -> target.igniteForSeconds(8.0F)),
    DROWN("drown", (_, target) -> target.setAirSupply(-20));

    private static final DollHexAction[] VALUES = values();
    private static final EnumLookup<DollHexAction> LOOKUP = EnumLookup.create("doll hex action", VALUES);
    private final String id;
    private final BiConsumer<ServerPlayer, LivingEntity> effect;

    DollHexAction(final String id, final BiConsumer<ServerPlayer, LivingEntity> effect) {
        this.id = id;
        this.effect = effect;
    }

    public String id() {
        return id;
    }

    public DollHexAction next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public void apply(final ServerPlayer source, final LivingEntity target) {
        effect.accept(source, target);
    }

    public static DollHexAction fromId(final String id) {
        return LOOKUP.findOrElse(id, PRICK);
    }
}
