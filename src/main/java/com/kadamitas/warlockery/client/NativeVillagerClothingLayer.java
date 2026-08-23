package com.kadamitas.warlockery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.VillagerLikeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerDataHolderRenderState;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Loader-native bridge that lets an independently authored creature body wear vanilla villager
 * biome, profession and level textures through a separate species-owned clothing mesh.
 */
public final class NativeVillagerClothingLayer<
    S extends LivingEntityRenderState & VillagerDataHolderRenderState,
    P extends EntityModel<? super S>,
    C extends EntityModel<S> & VillagerLikeModel<S>
> extends RenderLayer<S, P> {
    private final VillagerProfessionLayer<S, C> delegate;

    public NativeVillagerClothingLayer(
        final RenderLayerParent<S, P> parent,
        final ResourceManager resourceManager,
        final C clothingModel,
        final C noHatClothingModel
    ) {
        super(parent);
        delegate = new VillagerProfessionLayer<>(
            () -> clothingModel,
            resourceManager,
            "villager",
            noHatClothingModel,
            noHatClothingModel
        );
    }

    @Override
    public void submit(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int lightCoords,
        final S state,
        final float yRot,
        final float xRot
    ) {
        if (state.getVillagerData() == null) {
            return;
        }
        delegate.submit(poseStack, submitNodeCollector, lightCoords, state, yRot, xRot);
    }
}
