package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.UmbralSigilEntity;
import com.kadamitas.warlockery.entity.UmbralSigilRules;
import com.kadamitas.warlockery.entity.UmbralSigilRules.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class UmbralSigilModel extends EntityModel<UmbralSigilModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;
    private final ModelPart core;
    private final ModelPart innerRing;
    private final ModelPart outerRing;
    private final ModelPart leftProng;
    private final ModelPart centerProng;
    private final ModelPart rightProng;

    public UmbralSigilModel(final ModelPart root) {
        super(root);
        core=root.getChild("faceted_core"); innerRing=root.getChild("inner_ring"); outerRing=root.getChild("outer_ring");
        leftProng=root.getChild("left_prong"); centerProng=root.getChild("center_prong"); rightProng=root.getChild("right_prong");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();

        // The crystal is a real three-dimensional diamond, not a flat rune plate.
        root.addOrReplaceChild(
            "faceted_core",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -5.0F, -2.0F, 6.0F, 10.0F, 4.0F)
                .texOffs(20, 0).addBox(1.0F, -6.5F, -1.5F, 3.0F, 13.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, 0.0F, 0.65F, 0.0F)
        );

        // Four small inner fragments leave diagonal air gaps around the crystal.
        final PartDefinition inner = root.addOrReplaceChild(
            "inner_ring", CubeListBuilder.create(), PartPose.offset(0.0F, 9.5F, 0.0F)
        );
        inner.addOrReplaceChild(
            "inner_arc_north",
            CubeListBuilder.create().texOffs(32, 20).addBox(-4.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F),
            PartPose.offset(0.0F, -9.0F, 0.0F)
        );
        inner.addOrReplaceChild(
            "inner_arc_south",
            CubeListBuilder.create().texOffs(32, 24).addBox(-4.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F),
            PartPose.offset(0.0F, 9.0F, 0.0F)
        );
        inner.addOrReplaceChild(
            "inner_arc_west",
            CubeListBuilder.create().texOffs(32, 28).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F),
            PartPose.offset(-9.0F, 0.0F, -5.5F)
        );
        inner.addOrReplaceChild(
            "inner_arc_east",
            CubeListBuilder.create().texOffs(40, 28).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F),
            PartPose.offset(9.0F, 0.0F, 5.5F)
        );

        // Six individually authored rune stones describe the broken outer ellipse.
        final PartDefinition outer = root.addOrReplaceChild(
            "outer_ring", CubeListBuilder.create(), PartPose.offset(0.0F, 9.5F, 0.0F)
        );
        outer.addOrReplaceChild(
            "outer_rune_upper_left",
            CubeListBuilder.create().texOffs(32, 20).addBox(-4.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(-5.0F, -12.5F, 0.0F, 0.0F, 0.0F, 0.28F)
        );
        outer.addOrReplaceChild(
            "outer_rune_upper_center",
            CubeListBuilder.create().texOffs(32, 28).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F),
            PartPose.offsetAndRotation(-13.0F, 0.0F, -9.0F, 0.0F, 0.0F, -0.18F)
        );
        outer.addOrReplaceChild(
            "outer_rune_upper_right",
            CubeListBuilder.create().texOffs(32, 20).addBox(-4.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(5.0F, -12.5F, 0.0F, 0.0F, 0.0F, -0.28F)
        );
        outer.addOrReplaceChild(
            "outer_rune_lower_left",
            CubeListBuilder.create().texOffs(32, 24).addBox(-4.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(-5.0F, 12.5F, 0.0F, 0.0F, 0.0F, -0.28F)
        );
        outer.addOrReplaceChild(
            "outer_rune_lower_center",
            CubeListBuilder.create().texOffs(40, 28).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F),
            PartPose.offsetAndRotation(13.0F, 0.0F, 9.0F, 0.0F, 0.0F, 0.18F)
        );
        outer.addOrReplaceChild(
            "outer_rune_lower_right",
            CubeListBuilder.create().texOffs(32, 24).addBox(-4.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(5.0F, 12.5F, 0.0F, 0.0F, 0.0F, 0.28F)
        );

        root.addOrReplaceChild(
            "left_prong",
            CubeListBuilder.create().texOffs(56, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F),
            PartPose.offsetAndRotation(-6.0F, 27.0F, 0.0F, 0.0F, 0.0F, 0.12F)
        );
        root.addOrReplaceChild(
            "center_prong",
            CubeListBuilder.create().texOffs(68, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 9.0F, 4.0F),
            PartPose.offset(0.0F, 26.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_prong",
            CubeListBuilder.create().texOffs(84, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F),
            PartPose.offsetAndRotation(6.0F, 27.0F, 0.0F, 0.0F, 0.0F, -0.12F)
        );
        return LayerDefinition.create(mesh,TEXTURE_WIDTH,TEXTURE_HEIGHT);
    }

    @Override public void setupAnim(final State state) {
        super.setupAnim(state); final float spin=state.ageInTicks*0.025F; core.yRot += state.yRot*Mth.DEG_TO_RAD*0.25F;
        innerRing.zRot += spin; outerRing.zRot -= spin*0.72F; core.xRot += Mth.sin(state.ageInTicks*.13F)*.06F;
        if(state.sealing){ innerRing.zScale=.78F; outerRing.zScale=.82F; outerRing.zRot += .78F; leftProng.zRot -= .72F; rightProng.zRot += .72F; centerProng.xRot += .3F; }
    }

    public static void extractRenderState(final UmbralSigilEntity entity, final State state, final float partialTicks) {
        state.sealPhase=entity.presentationPhase(); state.sealing=UmbralSigilRules.sealing(state.sealPhase);
    }
    public static final class State extends LivingEntityRenderState { public Phase sealPhase=Phase.DORMANT; public boolean sealing; }
}
