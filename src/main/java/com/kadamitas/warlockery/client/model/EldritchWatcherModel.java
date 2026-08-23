package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.EldritchWatcherEntity;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.Mode;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class EldritchWatcherModel extends EntityModel<EldritchWatcherModel.State> {
    public static final int TEXTURE_WIDTH=128, TEXTURE_HEIGHT=64;
    private final ModelPart lensHead, centralEye, thorax, rearFins, frontTendril, leftTendril, rightTendril, rearTendril;
    public EldritchWatcherModel(final ModelPart root){ super(root); lensHead=root.getChild("lens_head"); centralEye=lensHead.getChild("central_eye"); thorax=root.getChild("suspended_thorax"); rearFins=root.getChild("rear_fins"); frontTendril=root.getChild("tendril_front"); leftTendril=root.getChild("tendril_left"); rightTendril=root.getChild("tendril_right"); rearTendril=root.getChild("tendril_rear"); }
    public static LayerDefinition createBodyLayer(){
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition lens = root.addOrReplaceChild(
            "lens_head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8.0F, -3.0F, -3.0F, 16.0F, 6.0F, 6.0F)
                .texOffs(0, 12).addBox(-5.0F, -5.0F, -2.5F, 10.0F, 10.0F, 5.0F),
            PartPose.offset(0.0F, 6.0F, 0.0F)
        );
        lens.addOrReplaceChild(
            "central_eye",
            CubeListBuilder.create().texOffs(44, 0).addBox(-3.0F, -3.0F, -1.0F, 6.0F, 6.0F, 2.0F),
            PartPose.offset(0.0F, 0.0F, -3.0F)
        );
        lens.addOrReplaceChild(
            "left_eye_cluster",
            CubeListBuilder.create().texOffs(60, 0).addBox(-4.0F, -1.0F, -0.7F, 4.0F, 2.0F, 1.5F),
            PartPose.offset(-4.0F, 0.0F, -3.0F)
        );
        lens.addOrReplaceChild(
            "right_eye_cluster",
            CubeListBuilder.create().texOffs(60, 4).addBox(0.0F, -1.0F, -0.7F, 4.0F, 2.0F, 1.5F),
            PartPose.offset(4.0F, 0.0F, -3.0F)
        );
        root.addOrReplaceChild(
            "suspended_thorax",
            CubeListBuilder.create().texOffs(72, 0).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 7.0F, 5.0F),
            PartPose.offset(0.0F, 10.5F, 0.0F)
        );

        final PartDefinition fins = root.addOrReplaceChild(
            "rear_fins", CubeListBuilder.create(), PartPose.offset(0.0F, 7.0F, 3.0F)
        );
        fins.addOrReplaceChild(
            "rear_fin_upper",
            CubeListBuilder.create().texOffs(0, 30).addBox(-7.0F, -1.0F, 0.0F, 14.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, -2.5F, 0.0F, -0.22F, 0.0F, 0.0F)
        );
        fins.addOrReplaceChild(
            "rear_fin_center",
            CubeListBuilder.create().texOffs(38, 30).addBox(-5.0F, -3.0F, 1.0F, 10.0F, 6.0F, 2.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        fins.addOrReplaceChild(
            "rear_fin_lower",
            CubeListBuilder.create().texOffs(0, 30).addBox(-7.0F, -1.0F, 0.0F, 14.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 3.5F, -0.2F, 0.24F, 0.0F, 0.0F)
        );

        final PartDefinition front = root.addOrReplaceChild(
            "tendril_front",
            CubeListBuilder.create().texOffs(64, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 13.5F, -2.5F, -0.45F, 0.0F, 0.0F)
        );
        final PartDefinition frontLower = front.addOrReplaceChild(
            "tendril_front_lower",
            CubeListBuilder.create().texOffs(64, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 4.5F, 0.0F, -0.20F, 0.0F, 0.0F)
        );
        frontLower.addOrReplaceChild(
            "tendril_front_terminal",
            CubeListBuilder.create().texOffs(60, 0).addBox(-2.0F, -1.0F, -0.75F, 4.0F, 2.0F, 1.5F),
            PartPose.offset(0.0F, 4.5F, 0.0F)
        );

        final PartDefinition left = root.addOrReplaceChild(
            "tendril_left",
            CubeListBuilder.create().texOffs(72, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(-3.5F, 13.5F, 0.0F, 0.0F, 0.0F, 0.45F)
        );
        final PartDefinition leftLower = left.addOrReplaceChild(
            "tendril_left_lower",
            CubeListBuilder.create().texOffs(72, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 4.5F, 0.0F, 0.0F, 0.0F, 0.20F)
        );
        leftLower.addOrReplaceChild(
            "tendril_left_terminal",
            CubeListBuilder.create().texOffs(60, 0).addBox(-2.0F, -1.0F, -0.75F, 4.0F, 2.0F, 1.5F),
            PartPose.offset(0.0F, 4.5F, 0.0F)
        );

        final PartDefinition right = root.addOrReplaceChild(
            "tendril_right",
            CubeListBuilder.create().texOffs(80, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(3.5F, 13.5F, 0.0F, 0.0F, 0.0F, -0.45F)
        );
        final PartDefinition rightLower = right.addOrReplaceChild(
            "tendril_right_lower",
            CubeListBuilder.create().texOffs(80, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 4.5F, 0.0F, 0.0F, 0.0F, -0.20F)
        );
        rightLower.addOrReplaceChild(
            "tendril_right_terminal",
            CubeListBuilder.create().texOffs(60, 4).addBox(-2.0F, -1.0F, -0.75F, 4.0F, 2.0F, 1.5F),
            PartPose.offset(0.0F, 4.5F, 0.0F)
        );

        final PartDefinition rear = root.addOrReplaceChild(
            "tendril_rear",
            CubeListBuilder.create().texOffs(88, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 13.5F, 2.5F, 0.45F, 0.0F, 0.0F)
        );
        final PartDefinition rearLower = rear.addOrReplaceChild(
            "tendril_rear_lower",
            CubeListBuilder.create().texOffs(88, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 4.5F, 0.0F, 0.20F, 0.0F, 0.0F)
        );
        rearLower.addOrReplaceChild(
            "tendril_rear_terminal",
            CubeListBuilder.create().texOffs(60, 4).addBox(-2.0F, -1.0F, -0.75F, 4.0F, 2.0F, 1.5F),
            PartPose.offset(0.0F, 4.5F, 0.0F)
        );
        return LayerDefinition.create(mesh,TEXTURE_WIDTH,TEXTURE_HEIGHT);
    }
    @Override public void setupAnim(final State state){ super.setupAnim(state); lensHead.yRot += state.yRot*Mth.DEG_TO_RAD; lensHead.xRot += state.xRot*Mth.DEG_TO_RAD*.5F; final float pulse=Mth.sin(state.ageInTicks*.15F); thorax.y += pulse*.35F; rearFins.xRot += pulse*.08F; frontTendril.xRot += pulse*.18F; leftTendril.zRot += pulse*.15F; rightTendril.zRot -= pulse*.18F; rearTendril.xRot -= pulse*.2F; if(state.focusing){ centralEye.zScale=1.55F; centralEye.xScale=1.25F; leftTendril.zRot-=.45F; rightTendril.zRot+=.45F; frontTendril.xRot-=.35F; rearTendril.xRot+=.35F; } }
    public static void extractRenderState(final EldritchWatcherEntity entity, final State state, final float partialTicks){ state.observationMode=entity.presentationMode(); state.focusing=state.observationMode==Mode.FOCUS_INSPECTION || state.observationMode==Mode.INTERCEPTING; }
    public static final class State extends LivingEntityRenderState { public Mode observationMode=Mode.QUIET_VIGIL; public boolean focusing; }
}
