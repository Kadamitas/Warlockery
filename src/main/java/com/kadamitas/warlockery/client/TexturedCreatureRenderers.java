package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.EntEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

final class TexturedCreatureRenderers {
    private TexturedCreatureRenderers() {
    }

    static Identifier texture(final String name) {
        return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "textures/entity/" + name + ".png");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerArcane(
        final EntityRenderersEvent.RegisterRenderers event,
        final net.minecraft.world.entity.EntityType<?> type,
        final CreatureModelProfile profile
    ) {
        event.registerEntityRenderer(
            (net.minecraft.world.entity.EntityType) type,
            context -> new Arcane(context, profile)
        );
    }

    static final class Arcane extends MobRenderer<Mob, ArcaneState, ArcaneCreatureModel> {
        private final Identifier texture;

        Arcane(final EntityRendererProvider.Context context, final CreatureModelProfile profile) {
            super(
                context,
                ArcaneCreatureModel.create(profile),
                Mth.clamp(profile.visual().width() * 0.45F, 0.2F, 0.85F)
            );
            texture = texture(profile.entityId());
        }

        @Override
        public Identifier getTextureLocation(final ArcaneState state) {
            return texture;
        }

        @Override
        public ArcaneState createRenderState() {
            return new ArcaneState();
        }

        @Override
        public void extractRenderState(final Mob entity, final ArcaneState state, final float partialTicks) {
            super.extractRenderState(entity, state, partialTicks);
            state.tint = entity instanceof EntEntity ent ? ent.variant().tint() : -1;
        }

        @Override
        protected int getModelTint(final ArcaneState state) {
            return state.tint;
        }
    }

    static final class ArcaneState extends LivingEntityRenderState {
        private int tint = -1;
    }
}
