package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.SpectralFamiliarEntity;
import com.kadamitas.warlockery.entity.SpectralFamiliarRules.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class SpectralFamiliarModel extends EntityModel<SpectralFamiliarModel.State> {
    public static final int TEXTURE_WIDTH=128, TEXTURE_HEIGHT=64;
    private final ModelPart head, leftEar, rightEar, body, frontLeft, frontRight, rearLeft, rearRight, tail, lantern;
    public SpectralFamiliarModel(final ModelPart root){ super(root); head=root.getChild("head"); leftEar=head.getChild("left_ear_fin"); rightEar=head.getChild("right_ear_fin"); body=root.getChild("body"); frontLeft=root.getChild("front_left_leg"); frontRight=root.getChild("front_right_leg"); rearLeft=root.getChild("rear_left_leg"); rearRight=root.getChild("rear_right_leg"); tail=root.getChild("lantern_tail"); lantern=tail.getChild("tail_middle").getChild("tail_tip").getChild("tail_lantern"); }
    public static LayerDefinition createBodyLayer(){
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition head = root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -4.0F, -4.0F, 7.0F, 7.0F, 7.0F),
            PartPose.offset(0.0F, 12.0F, -3.0F)
        );
        head.addOrReplaceChild(
            "left_ear_fin",
            CubeListBuilder.create().texOffs(28, 0).addBox(-2.0F, -7.0F, -1.0F, 3.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(-3.5F, -1.0F, 0.0F, 0.0F, 0.0F, -0.22F)
        );
        head.addOrReplaceChild(
            "right_ear_fin",
            CubeListBuilder.create().texOffs(38, 0).addBox(-1.0F, -7.0F, -1.0F, 3.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(3.5F, -1.0F, 0.0F, 0.0F, 0.0F, 0.22F)
        );
        head.addOrReplaceChild(
            "forehead_charm",
            CubeListBuilder.create().texOffs(48, 0).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 1.0F),
            PartPose.offset(0.0F, -2.0F, -4.0F)
        );
        root.addOrReplaceChild(
            "body",
            CubeListBuilder.create()
                .texOffs(0, 16).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 10.0F)
                .texOffs(32, 16).addBox(-4.0F, -2.0F, -3.0F, 8.0F, 5.0F, 6.0F),
            PartPose.offset(0.0F, 15.0F, 1.0F)
        );

        final PartDefinition frontLeft = root.addOrReplaceChild(
            "front_left_leg",
            CubeListBuilder.create().texOffs(60, 0).addBox(-1.0F, 0.0F, -1.5F, 2.0F, 4.0F, 3.0F),
            PartPose.offset(-3.2F, 17.0F, -2.5F)
        );
        final PartDefinition frontLeftLower = frontLeft.addOrReplaceChild(
            "front_left_leg_lower",
            CubeListBuilder.create().texOffs(60, 0).addBox(-1.0F, 0.0F, -1.5F, 2.0F, 3.0F, 3.0F),
            PartPose.offset(0.0F, 4.0F, 0.0F)
        );
        frontLeftLower.addOrReplaceChild(
            "front_left_leg_paw",
            CubeListBuilder.create().texOffs(80, 0).addBox(-1.5F, -2.0F, -2.0F, 3.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 3.0F, -0.4F)
        );

        final PartDefinition frontRight = root.addOrReplaceChild(
            "front_right_leg",
            CubeListBuilder.create().texOffs(70, 0).addBox(-1.0F, 0.0F, -1.5F, 2.0F, 4.0F, 3.0F),
            PartPose.offset(3.2F, 17.0F, -2.5F)
        );
        final PartDefinition frontRightLower = frontRight.addOrReplaceChild(
            "front_right_leg_lower",
            CubeListBuilder.create().texOffs(70, 0).addBox(-1.0F, 0.0F, -1.5F, 2.0F, 3.0F, 3.0F),
            PartPose.offset(0.0F, 4.0F, 0.0F)
        );
        frontRightLower.addOrReplaceChild(
            "front_right_leg_paw",
            CubeListBuilder.create().texOffs(94, 0).addBox(-1.5F, -2.0F, -2.0F, 3.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 3.0F, -0.4F)
        );

        final PartDefinition rearLeft = root.addOrReplaceChild(
            "rear_left_leg",
            CubeListBuilder.create().texOffs(80, 0).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 4.0F, 4.0F),
            PartPose.offset(-3.4F, 17.0F, 4.0F)
        );
        final PartDefinition rearLeftLower = rearLeft.addOrReplaceChild(
            "rear_left_leg_lower",
            CubeListBuilder.create().texOffs(80, 0).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 3.0F, 4.0F),
            PartPose.offset(0.0F, 4.0F, 0.0F)
        );
        rearLeftLower.addOrReplaceChild(
            "rear_left_leg_paw",
            CubeListBuilder.create().texOffs(80, 0).addBox(-1.5F, -2.0F, -2.0F, 3.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 3.0F, -0.3F)
        );

        final PartDefinition rearRight = root.addOrReplaceChild(
            "rear_right_leg",
            CubeListBuilder.create().texOffs(94, 0).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 4.0F, 4.0F),
            PartPose.offset(3.4F, 17.0F, 4.0F)
        );
        final PartDefinition rearRightLower = rearRight.addOrReplaceChild(
            "rear_right_leg_lower",
            CubeListBuilder.create().texOffs(94, 0).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 3.0F, 4.0F),
            PartPose.offset(0.0F, 4.0F, 0.0F)
        );
        rearRightLower.addOrReplaceChild(
            "rear_right_leg_paw",
            CubeListBuilder.create().texOffs(94, 0).addBox(-1.5F, -2.0F, -2.0F, 3.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 3.0F, -0.3F)
        );

        final PartDefinition tail = root.addOrReplaceChild(
            "lantern_tail",
            CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(2.5F, 14.0F, 3.3F, 0.28F, 1.18F, 0.0F)
        );
        final PartDefinition middle = tail.addOrReplaceChild(
            "tail_middle",
            CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 3.5F, 0.18F, 0.12F, 0.0F)
        );
        final PartDefinition tip = middle.addOrReplaceChild(
            "tail_tip",
            CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 3.5F, -0.08F, 0.10F, 0.0F)
        );
        tip.addOrReplaceChild(
            "tail_lantern",
            CubeListBuilder.create()
                .texOffs(28, 32).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F)
                .texOffs(52, 32).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 8.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 2.5F, 0.20F, 0.0F, 0.0F)
        );
        return LayerDefinition.create(mesh,TEXTURE_WIDTH,TEXTURE_HEIGHT);
    }
    @Override public void setupAnim(final State state){ super.setupAnim(state); head.yRot+=state.yRot*Mth.DEG_TO_RAD; head.xRot+=state.xRot*Mth.DEG_TO_RAD; final float pace=state.walkAnimationPos*.6662F, stride=Math.min(state.walkAnimationSpeed,1F)*.9F; frontLeft.xRot+=Mth.cos(pace)*stride; rearRight.xRot+=Mth.cos(pace)*stride; frontRight.xRot+=Mth.cos(pace+Mth.PI)*stride; rearLeft.xRot+=Mth.cos(pace+Mth.PI)*stride; final float alert=Mth.sin(state.ageInTicks*.16F); leftEar.zRot-=alert*.06F; rightEar.zRot+=alert*.06F; tail.yRot+=Mth.sin(state.ageInTicks*.1F)*.22F; if(state.surveying){ body.xRot+=.32F; body.y+=1.4F; head.xRot-=.2F; head.y+=1.8F; frontLeft.xRot-=.38F; frontRight.xRot-=.38F; rearLeft.xRot+=.42F; rearRight.xRot+=.42F; tail.xRot+=.25F; tail.yRot+=.68F; lantern.xScale=1.18F; lantern.yScale=1.18F; lantern.zScale=1.18F; leftEar.zRot-=.16F; rightEar.zRot+=.16F; } }
    public static void extractRenderState(final SpectralFamiliarEntity entity, final State state, final float partialTicks){ state.surveyPhase=entity.presentationPhase(); state.surveying=state.surveyPhase==Phase.SIGNAL || state.surveyPhase==Phase.APPROACH; }
    public static final class State extends LivingEntityRenderState { public Phase surveyPhase=Phase.DORMANT; public boolean surveying; }
}
