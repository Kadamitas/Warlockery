package com.kadamitas.warlockery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

public final class DedicatedCreatureRenderer<
    T extends Mob,
    S extends LivingEntityRenderState,
    M extends EntityModel<? super S>
> extends MobRenderer<T, S, M> {
    private final StateFactory<S> stateFactory;
    private final TextureSelector<S> textureSelector;
    private final StateExtractor<T, S> stateExtractor;
    private final TintSelector<S> tintSelector;
    private final ScaleTransform<S> scaleTransform;
    private final ShadowRadiusSelector<S> shadowRadiusSelector;
    private final boolean extractsArmedState;

    private DedicatedCreatureRenderer(
        final EntityRendererProvider.Context context,
        final ModelFactory<M> modelFactory,
        final StateFactory<S> stateFactory,
        final float shadowRadius,
        final TextureSelector<S> textureSelector,
        final StateExtractor<T, S> stateExtractor,
        final TintSelector<S> tintSelector,
        final ScaleTransform<S> scaleTransform,
        final ShadowRadiusSelector<S> shadowRadiusSelector,
        final boolean extractsArmedState
    ) {
        super(context, modelFactory.create(context), shadowRadius);
        this.stateFactory = stateFactory;
        this.textureSelector = textureSelector;
        this.stateExtractor = stateExtractor;
        this.tintSelector = tintSelector;
        this.scaleTransform = scaleTransform;
        this.shadowRadiusSelector = shadowRadiusSelector;
        this.extractsArmedState = extractsArmedState;
    }

    public static <
        T extends Mob,
        S extends LivingEntityRenderState,
        M extends EntityModel<? super S>
    > DedicatedCreatureRenderer<T, S, M> create(
        final EntityRendererProvider.Context context,
        final ModelFactory<M> modelFactory,
        final StateFactory<S> stateFactory,
        final float shadowRadius,
        final TextureSelector<S> textureSelector,
        final StateExtractor<T, S> stateExtractor,
        final TintSelector<S> tintSelector,
        final ScaleTransform<S> scaleTransform,
        final ShadowRadiusSelector<S> shadowRadiusSelector
    ) {
        return new DedicatedCreatureRenderer<>(
            context,
            modelFactory,
            stateFactory,
            shadowRadius,
            textureSelector,
            stateExtractor,
            tintSelector,
            scaleTransform,
            shadowRadiusSelector,
            false
        );
    }

    public static <
        T extends Mob,
        S extends ArmedEntityRenderState,
        M extends EntityModel<S> & ArmedModel<S>
    > DedicatedCreatureRenderer<T, S, M> createWithItemLayer(
        final EntityRendererProvider.Context context,
        final ModelFactory<M> modelFactory,
        final StateFactory<S> stateFactory,
        final float shadowRadius,
        final TextureSelector<S> textureSelector,
        final StateExtractor<T, S> stateExtractor,
        final TintSelector<S> tintSelector,
        final ScaleTransform<S> scaleTransform,
        final ShadowRadiusSelector<S> shadowRadiusSelector
    ) {
        final DedicatedCreatureRenderer<T, S, M> renderer = new DedicatedCreatureRenderer<>(
            context,
            modelFactory,
            stateFactory,
            shadowRadius,
            textureSelector,
            stateExtractor,
            tintSelector,
            scaleTransform,
            shadowRadiusSelector,
            true
        );
        renderer.addLayer(new ItemInHandLayer<S, M>(renderer));
        return renderer;
    }

    @Override
    public Identifier getTextureLocation(final S state) {
        return textureSelector.texture(state);
    }

    @Override
    public S createRenderState() {
        return stateFactory.create();
    }

    @Override
    public void extractRenderState(final T entity, final S state, final float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (extractsArmedState) {
            ArmedEntityRenderState.extractArmedEntityRenderState(
                entity,
                (ArmedEntityRenderState) state,
                itemModelResolver,
                partialTicks
            );
        }
        stateExtractor.extract(entity, state, partialTicks);
    }

    @Override
    protected int getModelTint(final S state) {
        return tintSelector.tint(state);
    }

    @Override
    protected void scale(final S state, final PoseStack poseStack) {
        scaleTransform.scale(state, poseStack);
    }

    @Override
    protected float getShadowRadius(final S state) {
        return shadowRadiusSelector.radius(state, super.getShadowRadius(state));
    }

    void addPresentationLayer(final RenderLayer<S, M> layer) {
        addLayer(layer);
    }

    @FunctionalInterface
    public interface ModelFactory<M> {
        M create(EntityRendererProvider.Context context);
    }

    @FunctionalInterface
    public interface StateFactory<S> {
        S create();
    }

    @FunctionalInterface
    public interface TextureSelector<S> {
        Identifier texture(S state);
    }

    @FunctionalInterface
    public interface StateExtractor<T, S> {
        void extract(T entity, S state, float partialTicks);
    }

    @FunctionalInterface
    public interface TintSelector<S> {
        int tint(S state);
    }

    @FunctionalInterface
    public interface ScaleTransform<S> {
        void scale(S state, PoseStack poseStack);
    }

    @FunctionalInterface
    public interface ShadowRadiusSelector<S> {
        float radius(S state, float baseRadius);
    }
}
