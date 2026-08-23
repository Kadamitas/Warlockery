package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.LostSoulEntity;
import com.kadamitas.warlockery.entity.LostSoulRules.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** Independent vulnerable soul-wisp; the memory facets are not a reused Spirit core. */
public final class LostSoulModel extends EntityModel<LostSoulModel.State> {
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    private final ModelPart head;
    private final ModelPart torso;
    private final ModelPart heart;
    private final ModelPart memoryCluster;
    private final ModelPart memoryRose;
    private final ModelPart memoryMoss;
    private final ModelPart memoryBlue;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart tail;
    private final ModelPart curl;
    private final ModelPart nearBead;
    private final ModelPart middleBead;
    private final ModelPart farBead;

    public LostSoulModel(final ModelPart root) {
        super(root);
        head = root.getChild("drooping_head");
        torso = root.getChild("hunched_torso");
        heart = torso.getChild("heart_mote");
        memoryCluster = torso.getChild("memory_mote_cluster");
        memoryRose = memoryCluster.getChild("memory_mote_rose");
        memoryMoss = memoryCluster.getChild("memory_mote_moss");
        memoryBlue = memoryCluster.getChild("memory_mote_blue");
        rightArm = root.getChild("right_sheltering_arm");
        leftArm = root.getChild("left_sheltering_arm");
        tail = root.getChild("ribbon_tail");
        curl = tail.getChild("ribbon_tail_middle").getChild("ribbon_tail_tip").getChild("tail_curl");
        nearBead = root.getChild("soul_bead_near");
        middleBead = root.getChild("soul_bead_middle");
        farBead = root.getChild("soul_bead_far");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
            "drooping_head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -4.0F, -3.0F, 8.0F, 7.0F, 6.0F)
                .texOffs(28, 0).addBox(-5.0F, -2.0F, -2.0F, 3.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 8.0F, -1.0F, 0.18F, 0.0F, 0.0F)
        );
        final PartDefinition torso = root.addOrReplaceChild(
            "hunched_torso",
            CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, -2.0F, -2.5F, 6.0F, 8.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.18F, 0.0F, 0.0F)
        );
        torso.addOrReplaceChild(
            "heart_mote",
            CubeListBuilder.create().texOffs(22, 16).addBox(-1.5F, -1.5F, -1.0F, 3.0F, 3.0F, 2.0F),
            PartPose.offset(-0.6F, 1.2F, -2.5F)
        );
        final PartDefinition memory = torso.addOrReplaceChild(
            "memory_mote_cluster",
            CubeListBuilder.create().texOffs(34, 16).addBox(-0.6F, -0.6F, -0.5F, 1.2F, 1.2F, 1.0F),
            PartPose.offset(1.4F, -0.2F, -2.7F)
        );
        memory.addOrReplaceChild(
            "memory_mote_rose",
            CubeListBuilder.create().texOffs(40, 16).addBox(-0.8F, -0.8F, -0.6F, 1.6F, 1.6F, 1.2F),
            PartPose.offsetAndRotation(-0.8F, 0.9F, 0.0F, 0.0F, 0.0F, -0.18F)
        );
        memory.addOrReplaceChild(
            "memory_mote_moss",
            CubeListBuilder.create().texOffs(48, 16).addBox(-0.7F, -0.7F, -0.6F, 1.4F, 1.4F, 1.2F),
            PartPose.offsetAndRotation(0.7F, 0.5F, -0.1F, 0.0F, 0.0F, 0.2F)
        );
        memory.addOrReplaceChild(
            "memory_mote_blue",
            CubeListBuilder.create().texOffs(56, 16).addBox(-0.6F, -0.6F, -0.5F, 1.2F, 1.2F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -0.9F, 0.0F, 0.0F, 0.0F, 0.1F)
        );
        final PartDefinition rightArm = root.addOrReplaceChild(
            "right_sheltering_arm",
            CubeListBuilder.create().texOffs(32, 22).addBox(-2.0F, 0.0F, -1.5F, 2.0F, 7.0F, 3.0F),
            PartPose.offsetAndRotation(-2.5F, 12.0F, -1.0F, -0.65F, -0.15F, 0.3F)
        );
        rightArm.addOrReplaceChild(
            "right_sheltering_forearm",
            CubeListBuilder.create().texOffs(32, 22).addBox(-2.0F, 0.0F, -1.5F, 2.0F, 7.0F, 3.0F, new CubeDeformation(-0.5F)),
            PartPose.offsetAndRotation(-0.2F, 5.0F, 0.0F, 0.18F, 0.0F, -0.38F)
        );
        final PartDefinition leftArm = root.addOrReplaceChild(
            "left_sheltering_arm",
            CubeListBuilder.create().texOffs(42, 22).addBox(0.0F, 0.0F, -1.5F, 2.0F, 7.0F, 3.0F),
            PartPose.offsetAndRotation(2.5F, 12.0F, -1.0F, -0.65F, 0.15F, -0.3F)
        );
        leftArm.addOrReplaceChild(
            "left_sheltering_forearm",
            CubeListBuilder.create().texOffs(42, 22).addBox(0.0F, 0.0F, -1.5F, 2.0F, 7.0F, 3.0F, new CubeDeformation(-0.5F)),
            PartPose.offsetAndRotation(0.2F, 5.0F, 0.0F, 0.18F, 0.0F, 0.38F)
        );
        final PartDefinition tail = root.addOrReplaceChild(
            "ribbon_tail",
            CubeListBuilder.create().texOffs(0, 30).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 14.0F, 0.0F, 0.0F, 0.0F, 0.12F)
        );
        final PartDefinition tailMiddle = tail.addOrReplaceChild(
            "ribbon_tail_middle",
            CubeListBuilder.create().texOffs(0, 30).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F, new CubeDeformation(-0.5F)),
            PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, -0.22F)
        );
        final PartDefinition tailTip = tailMiddle.addOrReplaceChild(
            "ribbon_tail_tip",
            CubeListBuilder.create().texOffs(12, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(0.4F, 4.5F, 0.0F, 0.0F, 0.0F, -0.55F)
        );
        tailTip.addOrReplaceChild(
            "tail_curl",
            CubeListBuilder.create().texOffs(12, 30).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(0.7F, 1.5F, 0.0F, 0.0F, 0.0F, -0.95F)
        );
        root.addOrReplaceChild(
            "soul_bead_near",
            CubeListBuilder.create().texOffs(20, 30).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
            PartPose.offset(5.0F, 19.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "soul_bead_middle",
            CubeListBuilder.create().texOffs(28, 30).addBox(-0.75F, -0.75F, -0.75F, 1.5F, 1.5F, 1.5F),
            PartPose.offset(8.0F, 21.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "soul_bead_far",
            CubeListBuilder.create().texOffs(36, 30).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F),
            PartPose.offset(10.5F, 23.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.65F;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float tremble = Mth.sin(state.ageInTicks * 0.17F);
        torso.zRot += tremble * 0.025F;
        tail.zRot += tremble * 0.08F;
        memoryCluster.zRot -= tremble * 0.08F;
        memoryRose.y += tremble * 0.12F;
        memoryMoss.y -= tremble * 0.09F;
        memoryBlue.y += tremble * 0.07F;
        nearBead.y += tremble * 0.35F;
        middleBead.y -= tremble * 0.25F;
        farBead.y += tremble * 0.2F;
        rightArm.xRot += Mth.cos(state.walkAnimationPos * 0.4F) * state.walkAnimationSpeed * 0.18F;
        leftArm.xRot -= Mth.cos(state.walkAnimationPos * 0.4F) * state.walkAnimationSpeed * 0.18F;
        if (state.sheltering) {
            head.xRot += 0.42F;
            torso.xRot += 0.28F;
            rightArm.xRot -= 0.62F;
            leftArm.xRot -= 0.62F;
            rightArm.zRot += 0.28F;
            leftArm.zRot -= 0.28F;
            heart.xScale = 1.2F;
            heart.yScale = 1.2F;
            memoryCluster.xScale = 1.18F;
            memoryCluster.yScale = 1.18F;
            memoryRose.y -= 0.35F;
            memoryMoss.y -= 0.15F;
            memoryBlue.y += 0.25F;
            curl.zRot += 0.3F;
        }
    }

    public static void extractRenderState(final LostSoulEntity entity, final State state, final float partialTicks) {
        state.soulPhase = entity.presentationPhase();
        state.sheltering = state.soulPhase == Phase.PETITION || state.soulPhase == Phase.COOLDOWN;
    }

    public static final class State extends LivingEntityRenderState {
        public Phase soulPhase = Phase.WANDER;
        public boolean sheltering;
    }
}
