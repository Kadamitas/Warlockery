package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.EchoShadeEntity;
import com.kadamitas.warlockery.entity.EchoShadeRules.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class EchoShadeModel extends EntityModel<EchoShadeModel.State> {
    public static final int TEXTURE_WIDTH=128, TEXTURE_HEIGHT=128;
    private final ModelPart mask,torso,rightArm,leftArm,rightLeg,leftLeg,scarf,leftCoat,rightCoat,firstEcho,secondEcho;
    public EchoShadeModel(final ModelPart root){ super(root); mask=root.getChild("asymmetric_mask"); torso=root.getChild("runner_torso"); rightArm=root.getChild("leading_right_arm"); leftArm=root.getChild("leading_left_arm"); rightLeg=root.getChild("leading_right_leg"); leftLeg=root.getChild("leading_left_leg"); scarf=root.getChild("scarf_ribbon"); leftCoat=root.getChild("coat_tail_left"); rightCoat=root.getChild("coat_tail_right"); firstEcho=root.getChild("first_afterimage"); secondEcho=root.getChild("second_afterimage"); }
    public static LayerDefinition createBodyLayer(){
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
            "asymmetric_mask",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 6.0F, 6.0F)
                .texOffs(24, 0).addBox(-2.0F, -3.0F, -0.5F, 3.0F, 5.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, -0.06F, -0.10F, -0.08F)
        );
        root.addOrReplaceChild(
            "runner_torso",
            CubeListBuilder.create()
                .texOffs(0, 14).addBox(-4.0F, -1.0F, -2.5F, 8.0F, 11.0F, 5.0F)
                .texOffs(26, 14).addBox(-5.0F, -1.0F, -3.0F, 10.0F, 3.0F, 6.0F),
            PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, 0.08F, 0.0F, -0.05F)
        );

        final PartDefinition rightArm = root.addOrReplaceChild(
            "leading_right_arm",
            CubeListBuilder.create().texOffs(54, 0).addBox(-2.0F, 0.0F, -2.0F, 3.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(-4.0F, 10.0F, -0.5F, -0.35F, 0.0F, 0.25F)
        );
        rightArm.addOrReplaceChild(
            "leading_right_forearm",
            CubeListBuilder.create().texOffs(54, 0).addBox(-2.0F, 0.0F, -2.0F, 3.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(-0.2F, 5.0F, 0.0F, -0.35F, 0.0F, -0.10F)
        );
        final PartDefinition leftArm = root.addOrReplaceChild(
            "leading_left_arm",
            CubeListBuilder.create().texOffs(68, 0).addBox(-1.0F, 0.0F, -2.0F, 3.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(4.0F, 10.0F, 0.3F, 0.25F, 0.0F, -0.28F)
        );
        leftArm.addOrReplaceChild(
            "leading_left_forearm",
            CubeListBuilder.create().texOffs(68, 0).addBox(-1.0F, 0.0F, -2.0F, 3.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(0.2F, 5.0F, 0.0F, 0.28F, 0.0F, 0.12F)
        );

        final PartDefinition rightLeg = root.addOrReplaceChild(
            "leading_right_leg",
            CubeListBuilder.create().texOffs(82, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(-2.0F, 15.0F, -0.2F, -0.45F, 0.0F, 0.25F)
        );
        rightLeg.addOrReplaceChild(
            "leading_right_shin",
            CubeListBuilder.create().texOffs(82, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.75F, 0.0F, -0.10F)
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "leading_left_leg",
            CubeListBuilder.create().texOffs(98, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(2.0F, 15.0F, 0.2F, 0.35F, 0.0F, -0.30F)
        );
        leftLeg.addOrReplaceChild(
            "leading_left_shin",
            CubeListBuilder.create().texOffs(98, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, -0.65F, 0.0F, 0.12F)
        );

        root.addOrReplaceChild(
            "scarf_ribbon",
            CubeListBuilder.create()
                .texOffs(0, 30).addBox(0.0F, -1.0F, -1.0F, 13.0F, 2.0F, 2.0F)
                .texOffs(0, 34).addBox(9.0F, 1.0F, -0.75F, 7.0F, 2.0F, 1.5F),
            PartPose.offsetAndRotation(2.0F, 8.0F, 1.0F, 0.08F, 0.20F, 0.12F)
        );
        root.addOrReplaceChild(
            "coat_tail_left",
            CubeListBuilder.create().texOffs(34, 30).addBox(-3.0F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F),
            PartPose.offsetAndRotation(-1.0F, 15.0F, 1.0F, 0.18F, 0.0F, 0.12F)
        );
        root.addOrReplaceChild(
            "coat_tail_right",
            CubeListBuilder.create().texOffs(46, 30).addBox(0.0F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F),
            PartPose.offsetAndRotation(1.0F, 16.0F, 1.0F, -0.12F, 0.0F, -0.15F)
        );

        final PartDefinition first = root.addOrReplaceChild(
            "first_afterimage", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.35F, 13.0F, 4.0F, 0.05F, 0.12F, -0.12F)
        );
        first.addOrReplaceChild("first_afterimage_mask", CubeListBuilder.create().texOffs(58, 30).addBox(-2.0F, -2.0F, -1.0F, 4.0F, 4.0F, 2.0F), PartPose.offset(0.0F, -8.0F, 0.0F));
        first.addOrReplaceChild("first_afterimage_torso", CubeListBuilder.create().texOffs(58, 30).addBox(-2.5F, -3.5F, -1.0F, 5.0F, 7.0F, 2.0F), PartPose.offset(0.0F, -2.5F, 0.0F));
        first.addOrReplaceChild("first_afterimage_leading_arm", CubeListBuilder.create().texOffs(58, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offsetAndRotation(-3.0F, -5.0F, 0.0F, -0.25F, 0.0F, 0.28F));
        first.addOrReplaceChild("first_afterimage_trailing_arm", CubeListBuilder.create().texOffs(58, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offsetAndRotation(3.0F, -5.0F, 0.0F, 0.22F, 0.0F, -0.30F));
        first.addOrReplaceChild("first_afterimage_leading_leg", CubeListBuilder.create().texOffs(58, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offsetAndRotation(-1.5F, 1.0F, 0.0F, -0.40F, 0.0F, 0.22F));
        first.addOrReplaceChild("first_afterimage_trailing_leg", CubeListBuilder.create().texOffs(58, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offsetAndRotation(1.5F, 1.0F, 0.0F, 0.35F, 0.0F, -0.24F));

        final PartDefinition second = root.addOrReplaceChild(
            "second_afterimage", CubeListBuilder.create(), PartPose.offsetAndRotation(7.0F, 13.0F, 7.0F, -0.05F, 0.22F, -0.24F)
        );
        second.addOrReplaceChild("second_afterimage_mask", CubeListBuilder.create().texOffs(58, 50).addBox(-1.8F, -1.8F, -1.0F, 3.6F, 3.6F, 2.0F), PartPose.offset(0.0F, -7.5F, 0.0F));
        second.addOrReplaceChild("second_afterimage_torso", CubeListBuilder.create().texOffs(58, 50).addBox(-2.2F, -3.0F, -1.0F, 4.4F, 6.0F, 2.0F), PartPose.offset(0.0F, -2.2F, 0.0F));
        second.addOrReplaceChild("second_afterimage_leading_arm", CubeListBuilder.create().texOffs(58, 50).addBox(-0.8F, 0.0F, -1.0F, 1.6F, 5.0F, 2.0F), PartPose.offsetAndRotation(-2.6F, -4.5F, 0.0F, -0.28F, 0.0F, 0.30F));
        second.addOrReplaceChild("second_afterimage_trailing_arm", CubeListBuilder.create().texOffs(58, 50).addBox(-0.8F, 0.0F, -1.0F, 1.6F, 5.0F, 2.0F), PartPose.offsetAndRotation(2.6F, -4.5F, 0.0F, 0.24F, 0.0F, -0.32F));
        second.addOrReplaceChild("second_afterimage_leading_leg", CubeListBuilder.create().texOffs(58, 50).addBox(-0.8F, 0.0F, -1.0F, 1.6F, 5.0F, 2.0F), PartPose.offsetAndRotation(-1.3F, 0.8F, 0.0F, -0.42F, 0.0F, 0.24F));
        second.addOrReplaceChild("second_afterimage_trailing_leg", CubeListBuilder.create().texOffs(58, 50).addBox(-0.8F, 0.0F, -1.0F, 1.6F, 5.0F, 2.0F), PartPose.offsetAndRotation(1.3F, 0.8F, 0.0F, 0.36F, 0.0F, -0.25F));
        return LayerDefinition.create(mesh,TEXTURE_WIDTH,TEXTURE_HEIGHT);
    }
    @Override public void setupAnim(final State state){ super.setupAnim(state); mask.yRot+=state.yRot*Mth.DEG_TO_RAD; mask.xRot+=state.xRot*Mth.DEG_TO_RAD; final float pace=state.walkAnimationPos*.6662F, stride=Math.min(state.walkAnimationSpeed,1F)*1.25F; rightArm.xRot+=Mth.cos(pace+Mth.PI)*stride; leftArm.xRot+=Mth.cos(pace)*stride; rightLeg.xRot+=Mth.cos(pace)*stride; leftLeg.xRot+=Mth.cos(pace+Mth.PI)*stride; scarf.yRot+=Mth.sin(state.ageInTicks*.12F)*.18F; firstEcho.yRot-=.08F; secondEcho.yRot-=.16F; if(state.replaying){ torso.xRot+=.35F; rightArm.xRot-=1.1F; leftArm.xRot+=.7F; rightLeg.xRot-=.8F; leftLeg.xRot+=.6F; scarf.yRot+=.6F; firstEcho.x+=3; firstEcho.z+=2; secondEcho.x+=6; secondEcho.z+=4; firstEcho.zRot-=.32F; secondEcho.zRot-=.58F; } }
    public static void extractRenderState(final EchoShadeEntity entity, final State state, final float partialTicks){ state.echoPhase=entity.presentationPhase(); state.replaying=state.echoPhase==Phase.ANSWER || state.echoPhase==Phase.STRIKE; }
    public static final class State extends LivingEntityRenderState { public Phase echoPhase=Phase.WATCH; public boolean replaying; }
}
