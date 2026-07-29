package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import com.kadamitas.warlockery.entity.EntEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.client.renderer.entity.VexRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
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
        final String textureName,
        final CreatureVisualProfile visual
    ) {
        event.registerEntityRenderer(
            (net.minecraft.world.entity.EntityType) type,
            context -> new Arcane(context, textureName, visual)
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerSpirit(
        final EntityRenderersEvent.RegisterRenderers event,
        final net.minecraft.world.entity.EntityType<?> type,
        final String textureName
    ) {
        event.registerEntityRenderer(
            (net.minecraft.world.entity.EntityType) type,
            context -> new Spirit(context, textureName)
        );
    }

    static final class Arcane extends MobRenderer<Mob, LivingEntityRenderState, ArcaneCreatureModel> {
        private final Identifier texture;

        Arcane(
            final EntityRendererProvider.Context context,
            final String textureName,
            final CreatureVisualProfile visual
        ) {
            super(
                context,
                ArcaneCreatureModel.create(visual.archetype()),
                Mth.clamp(visual.width() * 0.45F, 0.2F, 0.85F)
            );
            this.texture = texture(textureName);
        }

        @Override
        public Identifier getTextureLocation(final LivingEntityRenderState state) {
            return texture;
        }

        @Override
        public LivingEntityRenderState createRenderState() {
            return new LivingEntityRenderState();
        }
    }

    static final class Spirit extends VexRenderer {
        private final Identifier texture;

        Spirit(final EntityRendererProvider.Context context, final String textureName) {
            super(context);
            this.texture = texture(textureName);
        }

        @Override
        public Identifier getTextureLocation(final VexRenderState state) {
            return texture;
        }
    }

    static final class Hobgoblin extends VillagerRenderer {
        private final Identifier texture;

        Hobgoblin(final EntityRendererProvider.Context context, final String textureName) {
            super(context);
            this.texture = texture(textureName);
        }

        @Override
        public Identifier getTextureLocation(final VillagerRenderState state) {
            return texture;
        }
    }

    static final class WerewolfHunter extends PillagerRenderer {
        WerewolfHunter(final EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public Identifier getTextureLocation(final IllagerRenderState state) {
            return texture("werewolf_hunter");
        }
    }

    static final class Ent extends IronGolemRenderer {
        Ent(final EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public Identifier getTextureLocation(final IronGolemRenderState state) {
            return texture("ent");
        }

        @Override
        public EntRenderState createRenderState() {
            return new EntRenderState();
        }

        @Override
        public void extractRenderState(
            final IronGolem entity,
            final IronGolemRenderState state,
            final float partialTicks
        ) {
            super.extractRenderState(entity, state, partialTicks);
            if (state instanceof EntRenderState entState) {
                entState.tint = entity instanceof EntEntity ent ? ent.variant().tint() : -1;
            }
        }

        @Override
        protected int getModelTint(final IronGolemRenderState state) {
            return state instanceof EntRenderState entState ? entState.tint : -1;
        }
    }

    static final class EntRenderState extends IronGolemRenderState {
        private int tint = -1;
    }
}
