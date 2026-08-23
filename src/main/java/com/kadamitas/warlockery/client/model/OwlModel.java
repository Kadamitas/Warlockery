package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.ArcaneMob;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class OwlModel extends EntityModel<OwlModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;
    private final ModelPart tail;

    public OwlModel(final ModelPart root) {
        super(root);
        body = root.getChild("body");
        head = root.getChild("head");
        leftWing = root.getChild("left_wing");
        rightWing = root.getChild("right_wing");
        leftLeg = root.getChild("left_leg");
        rightLeg = root.getChild("right_leg");
        tail = root.getChild("tail");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.5F, -3.0F, 8.0F, 9.0F, 6.0F),
            PartPose.offset(0.0F, 14.5F, 0.5F)
        );
        body.addOrReplaceChild(
            "belly",
            CubeListBuilder.create().texOffs(28, 0).addBox(-3.0F, -3.5F, -0.5F, 6.0F, 7.0F, 1.0F),
            PartPose.offset(0.0F, 0.5F, -3.0F)
        );
        final PartDefinition head = root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(48, 0).addBox(-4.0F, -3.5F, -3.5F, 8.0F, 7.0F, 7.0F),
            PartPose.offset(0.0F, 8.0F, -1.0F)
        );
        head.addOrReplaceChild(
            "face_disk",
            CubeListBuilder.create().texOffs(78, 0).addBox(-4.0F, -3.0F, -0.5F, 8.0F, 6.0F, 1.0F),
            PartPose.offset(0.0F, 0.25F, -3.5F)
        );
        head.addOrReplaceChild(
            "left_eye",
            CubeListBuilder.create().texOffs(96, 0).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F),
            PartPose.offset(2.0F, -0.3F, -4.1F)
        );
        head.addOrReplaceChild(
            "right_eye",
            CubeListBuilder.create().texOffs(104, 0).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F),
            PartPose.offset(-2.0F, -0.3F, -4.1F)
        );
        head.addOrReplaceChild(
            "beak",
            CubeListBuilder.create().texOffs(112, 0).addBox(-1.0F, -0.5F, -2.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offset(0.0F, 0.8F, -3.8F)
        );
        root.addOrReplaceChild(
            "left_wing",
            CubeListBuilder.create().texOffs(0, 28).addBox(0.0F, -4.0F, -2.5F, 2.0F, 8.0F, 5.0F),
            PartPose.offsetAndRotation(3.8F, 14.0F, 0.5F, 0.0F, 0.0F, -0.08F)
        );
        root.addOrReplaceChild(
            "right_wing",
            CubeListBuilder.create().texOffs(28, 28).addBox(-2.0F, -4.0F, -2.5F, 2.0F, 8.0F, 5.0F),
            PartPose.offsetAndRotation(-3.8F, 14.0F, 0.5F, 0.0F, 0.0F, 0.08F)
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(54, 28).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offset(2.0F, 19.0F, 0.0F)
        );
        leftLeg.addOrReplaceChild(
            "left_talon",
            CubeListBuilder.create().texOffs(70, 28).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 1.0F, 4.0F),
            PartPose.offset(0.0F, 2.5F, -0.5F)
        );
        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(62, 28).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offset(-2.0F, 19.0F, 0.0F)
        );
        rightLeg.addOrReplaceChild(
            "right_talon",
            CubeListBuilder.create().texOffs(70, 28).mirror().addBox(-1.5F, 0.0F, -3.0F, 3.0F, 1.0F, 4.0F),
            PartPose.offset(0.0F, 2.5F, -0.5F)
        );
        root.addOrReplaceChild(
            "tail",
            CubeListBuilder.create().texOffs(88, 28).addBox(-2.5F, 0.0F, -1.0F, 5.0F, 2.0F, 6.0F),
            PartPose.offsetAndRotation(0.0F, 17.0F, 2.5F, 0.55F, 0.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.15F, 1.15F);
        head.xRot = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.7F, 0.65F);
        if (state.flying) {
            final float beat = Mth.sin(state.ageInTicks * 0.72F) * 0.5F;
            leftWing.zRot = -1.25F - beat;
            rightWing.zRot = 1.25F + beat;
            leftLeg.xRot = 0.75F;
            rightLeg.xRot = 0.75F;
            tail.xRot = 0.15F;
        } else {
            final float step = Mth.cos(state.walkAnimationPos * 0.6F) * state.walkAnimationSpeed * 0.18F;
            leftLeg.xRot = step;
            rightLeg.xRot = -step;
        }
        if (state.diving) {
            body.xRot = 0.6F;
            head.xRot -= 0.4F;
            leftWing.zRot = -0.65F;
            rightWing.zRot = 0.65F;
            tail.xRot = 0.75F;
        }
    }

    public static void extractRenderState(final ArcaneMob entity, final State state, final float partialTicks) {
        state.flying = !entity.onGround();
        state.diving = state.flying && entity.getDeltaMovement().y < -0.12D;
    }

    public static final class State extends LivingEntityRenderState {
        public boolean flying;
        public boolean diving;
    }
}
