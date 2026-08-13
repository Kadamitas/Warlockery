package com.kadamitas.warlockery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.EntEntity;
import com.kadamitas.warlockery.entity.ArcaneMob;
import com.kadamitas.warlockery.entity.GoblinLifecycleRules;
import com.kadamitas.warlockery.entity.NamiEntity;
import com.kadamitas.warlockery.entity.NaamahEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

final class TexturedCreatureRenderers {
    private TexturedCreatureRenderers() {
    }

    static Identifier texture(final String name) {
        return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "textures/entity/" + name + ".png");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerArcane(
        final net.minecraft.world.entity.EntityType<?> type,
        final CreatureModelProfile profile
    ) {
        EntityRenderers.register(
            (net.minecraft.world.entity.EntityType) type,
            context -> new Arcane(context, profile)
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerNaamah(final net.minecraft.world.entity.EntityType<?> type) {
        EntityRenderers.register(
            (net.minecraft.world.entity.EntityType) type,
            Naamah::new
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerNami(final net.minecraft.world.entity.EntityType<?> type) {
        EntityRenderers.register(
            (net.minecraft.world.entity.EntityType) type,
            Nami::new
        );
    }

    static final class Nami extends SkinnedHumanoid<NamiEntity> {
        Nami(final EntityRendererProvider.Context context) {
            super(context, "nami", 0.4F);
        }
    }

    static final class Naamah extends SkinnedHumanoid<NaamahEntity> {
        Naamah(final EntityRendererProvider.Context context) {
            super(context, "naamah", 0.45F);
        }
    }

    private abstract static class SkinnedHumanoid<T extends Mob>
        extends HumanoidMobRenderer<T, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
        private final Identifier texture;

        SkinnedHumanoid(final EntityRendererProvider.Context context, final String textureName, final float shadow) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM)), shadow);
            texture = texture(textureName);
        }

        @Override
        public Identifier getTextureLocation(final HumanoidRenderState state) {
            return texture;
        }

        @Override
        public HumanoidRenderState createRenderState() {
            return new HumanoidRenderState();
        }
    }

    static final class Arcane extends MobRenderer<Mob, ArcaneState, ArcaneCreatureModel> {
        private final Identifier texture;
        private final boolean hasBabyModel;

        Arcane(final EntityRendererProvider.Context context, final CreatureModelProfile profile) {
            super(
                context,
                ArcaneCreatureModel.create(profile),
                Mth.clamp(profile.visual().width() * 0.45F, 0.2F, 0.85F)
            );
            texture = texture(profile.entityId());
            addLayer(new ItemInHandLayer<>(this));
            hasBabyModel = profile.variant() == CreatureModelProfile.Variant.GOBLIN
                || profile.variant() == CreatureModelProfile.Variant.HOBGOBLIN;
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
            ArmedEntityRenderState.extractArmedEntityRenderState(
                entity, state, this.itemModelResolver, partialTicks
            );
            state.hobgoblinAssaultVariant = entity instanceof ArcaneMob arcane
                && arcane.isHobgoblinAssaultVariant();
            state.tint = entity instanceof EntEntity ent
                ? ent.variant().tint()
                : state.hobgoblinAssaultVariant
                    ? 0xFF76964F
                    : -1;
        }

        @Override
        protected int getModelTint(final ArcaneState state) {
            return state.tint;
        }

        @Override
        protected void scale(final ArcaneState state, final PoseStack poseStack) {
            if (state.hobgoblinAssaultVariant) {
                poseStack.scale(0.68F, 0.68F, 0.68F);
            }
            if (hasBabyModel && state.isBaby) {
                poseStack.scale(
                    GoblinLifecycleRules.BABY_RENDER_SCALE,
                    GoblinLifecycleRules.BABY_RENDER_SCALE,
                    GoblinLifecycleRules.BABY_RENDER_SCALE
                );
            }
        }

        @Override
        protected float getShadowRadius(final ArcaneState state) {
            final float radius = super.getShadowRadius(state);
            final float variantRadius = state.hobgoblinAssaultVariant ? radius * 0.68F : radius;
            return hasBabyModel && state.isBaby
                ? variantRadius * GoblinLifecycleRules.BABY_RENDER_SCALE
                : variantRadius;
        }
    }

    static final class ArcaneState extends ArmedEntityRenderState {
        private int tint = -1;
        private boolean hobgoblinAssaultVariant;
    }
}
