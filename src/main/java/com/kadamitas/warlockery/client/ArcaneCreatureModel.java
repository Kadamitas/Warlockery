package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.entity.CreatureVisualProfile.Archetype;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

final class ArcaneCreatureModel extends EntityModel<LivingEntityRenderState> {
    private static final List<String> PART_NAMES = List.of(
        "head", "body", "right_arm", "left_arm", "right_front_leg", "left_front_leg",
        "right_hind_leg", "left_hind_leg", "right_middle_front_leg", "left_middle_front_leg",
        "right_middle_hind_leg", "left_middle_hind_leg", "right_wing", "left_wing", "tail", "crown"
    );
    private final Archetype archetype;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftFrontLeg;
    private final ModelPart rightHindLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightMiddleFrontLeg;
    private final ModelPart leftMiddleFrontLeg;
    private final ModelPart rightMiddleHindLeg;
    private final ModelPart leftMiddleHindLeg;
    private final ModelPart rightWing;
    private final ModelPart leftWing;
    private final ModelPart tail;
    private final ModelPart crown;

    private ArcaneCreatureModel(final Archetype archetype, final ModelPart root) {
        super(root);
        this.archetype = archetype;
        head = root.getChild("head");
        body = root.getChild("body");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightFrontLeg = root.getChild("right_front_leg");
        leftFrontLeg = root.getChild("left_front_leg");
        rightHindLeg = root.getChild("right_hind_leg");
        leftHindLeg = root.getChild("left_hind_leg");
        rightMiddleFrontLeg = root.getChild("right_middle_front_leg");
        leftMiddleFrontLeg = root.getChild("left_middle_front_leg");
        rightMiddleHindLeg = root.getChild("right_middle_hind_leg");
        leftMiddleHindLeg = root.getChild("left_middle_hind_leg");
        rightWing = root.getChild("right_wing");
        leftWing = root.getChild("left_wing");
        tail = root.getChild("tail");
        crown = root.getChild("crown");
    }

    static ArcaneCreatureModel create(final Archetype archetype) {
        return new ArcaneCreatureModel(archetype, createLayer(archetype).bakeRoot());
    }

    static LayerDefinition createLayer(final Archetype archetype) {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        switch (archetype) {
            case HUMANOID -> buildHumanoid(root, false);
            case BOSS -> buildHumanoid(root, true);
            case FELINE -> buildFeline(root);
            case AVIAN -> buildAvian(root);
            case AMPHIBIAN -> buildAmphibian(root);
            case MOUNT -> buildMount(root);
            case CANINE -> buildCanine(root);
            case PLANTLING -> buildPlantling(root);
            case PLANT_BRUTE -> buildPlantBrute(root);
            case ARTHROPOD -> buildArthropod(root);
            case CREEPER -> buildCreeper(root);
            case LYCAN -> buildLycan(root);
            case SPIRIT -> buildSpirit(root);
        }
        PART_NAMES.stream()
            .filter(name -> root.getChild(name) == null)
            .forEach(name -> root.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.ZERO));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(final LivingEntityRenderState state) {
        super.setupAnim(state);
        head.yRot += state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.6662F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F) * 1.35F;
        final float rightSwing = Mth.cos(pace) * stride;
        final float leftSwing = Mth.cos(pace + Mth.PI) * stride;
        switch (archetype) {
            case HUMANOID, BOSS, LYCAN, PLANTLING, PLANT_BRUTE -> animateBiped(rightSwing, leftSwing, state.ageInTicks);
            case FELINE, MOUNT, CANINE, CREEPER, AMPHIBIAN -> animateQuadruped(rightSwing, leftSwing, state.ageInTicks);
            case ARTHROPOD -> animateArthropod(pace, stride);
            case AVIAN, SPIRIT -> animateWings(state.ageInTicks, stride);
        }
    }

    private void animateBiped(final float rightSwing, final float leftSwing, final float age) {
        rightArm.xRot += leftSwing;
        leftArm.xRot += rightSwing;
        rightHindLeg.xRot += rightSwing;
        leftHindLeg.xRot += leftSwing;
        tail.yRot += Mth.cos(age * 0.18F) * 0.25F;
        if (archetype == Archetype.PLANTLING || archetype == Archetype.PLANT_BRUTE) {
            body.zRot += Mth.sin(age * 0.05F) * 0.025F;
            crown.zRot -= body.zRot * 0.75F;
        }
    }

    private void animateQuadruped(final float rightSwing, final float leftSwing, final float age) {
        rightFrontLeg.xRot += leftSwing;
        leftFrontLeg.xRot += rightSwing;
        rightHindLeg.xRot += rightSwing;
        leftHindLeg.xRot += leftSwing;
        tail.yRot += Mth.cos(age * 0.18F) * 0.3F;
    }

    private void animateArthropod(final float pace, final float stride) {
        final float front = Mth.cos(pace * 2.0F) * stride * 0.35F;
        final float middle = Mth.cos(pace * 2.0F + Mth.HALF_PI) * stride * 0.35F;
        rightFrontLeg.yRot += front;
        leftFrontLeg.yRot -= front;
        rightMiddleFrontLeg.yRot += middle;
        leftMiddleFrontLeg.yRot -= middle;
        rightMiddleHindLeg.yRot -= front;
        leftMiddleHindLeg.yRot += front;
        rightHindLeg.yRot -= middle;
        leftHindLeg.yRot += middle;
    }

    private void animateWings(final float age, final float stride) {
        final float flap = Mth.sin(age * 0.65F) * (0.35F + stride * 0.25F);
        rightWing.zRot -= flap;
        leftWing.zRot += flap;
        tail.xRot += Mth.cos(age * 0.2F) * 0.08F;
    }

    private static void buildHumanoid(final PartDefinition root, final boolean boss) {
        final float halfWidth = boss ? 5.0F : 4.0F;
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F)
                .texOffs(32, 0).addBox(-3.0F, -10.0F, -2.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(40, 0).addBox(1.0F, -10.0F, -2.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offset(0.0F, boss ? -1.0F : 0.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(16, 16).addBox(-halfWidth, 0.0F, -2.5F, halfWidth * 2.0F, 12.0F, 5.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 13.0F, 4.0F),
            PartPose.offset(-halfWidth - 1.0F, 2.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 13.0F, 4.0F),
            PartPose.offset(halfWidth + 1.0F, 2.0F, 0.0F)
        );
        addBipedLegs(root, 12.0F, boss ? 5.0F : 4.0F);
        if (boss) {
            root.addOrReplaceChild(
                "crown", CubeListBuilder.create().texOffs(0, 32).addBox(-6.0F, -1.0F, -3.0F, 12.0F, 3.0F, 6.0F),
                PartPose.offset(0.0F, 1.0F, 0.0F)
            );
        }
    }

    private static void buildLycan(final PartDefinition root) {
        buildHumanoid(root, false);
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -7.0F, -4.0F, 8.0F, 7.0F, 8.0F)
                .texOffs(32, 0).addBox(-2.5F, -3.0F, -7.0F, 5.0F, 3.0F, 4.0F)
                .texOffs(0, 15).addBox(-4.0F, -10.0F, -2.0F, 3.0F, 4.0F, 2.0F)
                .texOffs(10, 15).addBox(1.0F, -10.0F, -2.0F, 3.0F, 4.0F, 2.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "tail", CubeListBuilder.create().texOffs(48, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 10.0F, 2.0F, 0.75F, 0.0F, 0.0F)
        );
    }

    private static void addBipedLegs(final PartDefinition root, final float y, final float width) {
        root.addOrReplaceChild(
            "right_hind_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, width, 12.0F, 4.0F),
            PartPose.offset(-2.0F, y, 0.0F)
        );
        root.addOrReplaceChild(
            "left_hind_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, width, 12.0F, 4.0F),
            PartPose.offset(2.0F, y, 0.0F)
        );
    }

    private static void buildFeline(final PartDefinition root) {
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, -4.0F, -5.0F, 7.0F, 7.0F, 6.0F)
                .texOffs(26, 0).addBox(-3.5F, -6.0F, -3.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(34, 0).addBox(1.5F, -6.0F, -3.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(42, 0).addBox(-2.0F, 0.0F, -7.0F, 4.0F, 2.0F, 3.0F),
            PartPose.offset(0.0F, 15.0F, -5.0F)
        );
        addQuadrupedBody(root, 6.0F, 7.0F, 12.0F, 14.0F);
        addQuadrupedLegs(root, 1.5F, 8.0F, 2.5F, 16.0F, 5.0F);
        root.addOrReplaceChild(
            "tail", CubeListBuilder.create().texOffs(48, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 15.0F, 7.0F, 0.75F, 0.0F, 0.0F)
        );
    }

    private static void buildCanine(final PartDefinition root) {
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -4.0F, -5.0F, 8.0F, 8.0F, 7.0F)
                .texOffs(30, 0).addBox(-2.5F, -1.0F, -8.0F, 5.0F, 3.0F, 4.0F)
                .texOffs(48, 0).addBox(-4.0F, -7.0F, -2.0F, 3.0F, 4.0F, 2.0F)
                .texOffs(48, 0).mirror().addBox(1.0F, -7.0F, -2.0F, 3.0F, 4.0F, 2.0F),
            PartPose.offset(0.0F, 12.0F, -6.0F)
        );
        addQuadrupedBody(root, 7.0F, 8.0F, 14.0F, 12.0F);
        addQuadrupedLegs(root, 2.0F, 8.0F, 3.0F, 15.0F, 6.0F);
        root.addOrReplaceChild(
            "tail", CubeListBuilder.create().texOffs(48, 16).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 12.0F, 7.0F, 0.85F, 0.0F, 0.0F)
        );
    }

    private static void buildMount(final PartDefinition root) {
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, -5.0F, -5.0F, 7.0F, 7.0F, 8.0F)
                .texOffs(30, 0).addBox(-2.5F, -2.0F, -9.0F, 5.0F, 4.0F, 5.0F)
                .texOffs(50, 0).addBox(-3.5F, -8.0F, -1.0F, 2.0F, 4.0F, 2.0F)
                .texOffs(50, 0).mirror().addBox(1.5F, -8.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 8.0F, -8.0F, -0.18F, 0.0F, 0.0F)
        );
        addQuadrupedBody(root, 10.0F, 10.0F, 18.0F, 10.0F);
        addQuadrupedLegs(root, 2.5F, 12.0F, 4.0F, 12.0F, 8.0F);
        root.addOrReplaceChild(
            "tail", CubeListBuilder.create().texOffs(48, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 10.0F, 9.0F, 0.8F, 0.0F, 0.0F)
        );
    }

    private static void addQuadrupedBody(
        final PartDefinition root,
        final float width,
        final float height,
        final float depth,
        final float y
    ) {
        root.addOrReplaceChild(
            "body", CubeListBuilder.create().texOffs(0, 20).addBox(-width / 2.0F, -height / 2.0F, -depth / 2.0F, width, height, depth),
            PartPose.offset(0.0F, y, 0.0F)
        );
    }

    private static void addQuadrupedLegs(
        final PartDefinition root,
        final float width,
        final float height,
        final float depth,
        final float y,
        final float z
    ) {
        final CubeListBuilder rightLeg = CubeListBuilder.create().texOffs(0, 42).addBox(-width, 0.0F, -depth / 2.0F, width, height, depth);
        final CubeListBuilder leftLeg = CubeListBuilder.create().texOffs(0, 42).mirror().addBox(0.0F, 0.0F, -depth / 2.0F, width, height, depth);
        root.addOrReplaceChild("right_front_leg", rightLeg, PartPose.offset(-2.0F, y, -z));
        root.addOrReplaceChild("left_front_leg", leftLeg, PartPose.offset(2.0F, y, -z));
        root.addOrReplaceChild("right_hind_leg", rightLeg, PartPose.offset(-2.0F, y, z));
        root.addOrReplaceChild("left_hind_leg", leftLeg, PartPose.offset(2.0F, y, z));
    }

    private static void buildAvian(final PartDefinition root) {
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -5.0F, -4.0F, 8.0F, 7.0F, 7.0F)
                .texOffs(30, 0).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 2.0F, 3.0F)
                .texOffs(40, 0).addBox(-4.0F, -7.0F, -1.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(48, 0).addBox(2.0F, -7.0F, -1.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offset(0.0F, 12.0F, -3.0F)
        );
        root.addOrReplaceChild(
            "body", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -5.0F, -3.0F, 8.0F, 10.0F, 6.0F),
            PartPose.offset(0.0F, 17.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_wing", CubeListBuilder.create().texOffs(28, 16).addBox(-7.0F, 0.0F, -1.0F, 7.0F, 9.0F, 2.0F),
            PartPose.offsetAndRotation(-4.0F, 13.0F, 0.0F, 0.0F, 0.0F, 0.25F)
        );
        root.addOrReplaceChild(
            "left_wing", CubeListBuilder.create().texOffs(28, 16).mirror().addBox(0.0F, 0.0F, -1.0F, 7.0F, 9.0F, 2.0F),
            PartPose.offsetAndRotation(4.0F, 13.0F, 0.0F, 0.0F, 0.0F, -0.25F)
        );
        root.addOrReplaceChild(
            "tail", CubeListBuilder.create().texOffs(46, 16).addBox(-3.0F, 0.0F, -1.0F, 6.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 19.0F, 2.0F, 0.5F, 0.0F, 0.0F)
        );
        addBirdFeet(root);
    }

    private static void addBirdFeet(final PartDefinition root) {
        root.addOrReplaceChild(
            "right_hind_leg", CubeListBuilder.create().texOffs(0, 34).addBox(-1.0F, 0.0F, -2.0F, 2.0F, 5.0F, 3.0F),
            PartPose.offset(-2.0F, 19.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "left_hind_leg", CubeListBuilder.create().texOffs(0, 34).mirror().addBox(-1.0F, 0.0F, -2.0F, 2.0F, 5.0F, 3.0F),
            PartPose.offset(2.0F, 19.0F, 0.0F)
        );
    }

    private static void buildAmphibian(final PartDefinition root) {
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -3.0F, -5.0F, 8.0F, 5.0F, 8.0F)
                .texOffs(32, 0).addBox(-4.0F, -5.0F, -3.0F, 3.0F, 3.0F, 3.0F)
                .texOffs(44, 0).addBox(1.0F, -5.0F, -3.0F, 3.0F, 3.0F, 3.0F),
            PartPose.offset(0.0F, 18.0F, -3.0F)
        );
        root.addOrReplaceChild(
            "body", CubeListBuilder.create().texOffs(0, 16).addBox(-4.5F, -3.0F, -4.0F, 9.0F, 5.0F, 9.0F),
            PartPose.offset(0.0F, 20.0F, 2.0F)
        );
        addQuadrupedLegs(root, 2.0F, 3.0F, 4.0F, 21.0F, 4.0F);
    }

    private static void buildPlantling(final PartDefinition root) {
        root.addOrReplaceChild(
            "head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 6.0F, 6.0F),
            PartPose.offset(0.0F, 13.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "body", CubeListBuilder.create().texOffs(0, 14).addBox(-2.5F, 0.0F, -2.0F, 5.0F, 7.0F, 4.0F),
            PartPose.offset(0.0F, 14.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_arm", CubeListBuilder.create().texOffs(18, 14).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(-2.5F, 15.0F, 0.0F, 0.0F, 0.0F, 0.25F)
        );
        root.addOrReplaceChild(
            "left_arm", CubeListBuilder.create().texOffs(18, 14).mirror().addBox(0.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(2.5F, 15.0F, 0.0F, 0.0F, 0.0F, -0.25F)
        );
        addBipedLegs(root, 20.0F, 3.0F);
        root.addOrReplaceChild(
            "crown",
            CubeListBuilder.create()
                .texOffs(28, 14).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(36, 14).addBox(-4.0F, -5.0F, -1.0F, 8.0F, 2.0F, 2.0F),
            PartPose.offset(0.0F, 9.0F, 0.0F)
        );
    }

    private static void buildPlantBrute(final PartDefinition root) {
        root.addOrReplaceChild(
            "head", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -9.0F, -4.0F, 10.0F, 9.0F, 8.0F),
            PartPose.offset(0.0F, 1.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "body",
            CubeListBuilder.create()
                .texOffs(0, 18).addBox(-6.0F, 0.0F, -3.0F, 12.0F, 14.0F, 6.0F)
                .texOffs(36, 18).addBox(-8.0F, 1.0F, -2.0F, 16.0F, 4.0F, 4.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_arm", CubeListBuilder.create().texOffs(0, 38).addBox(-3.0F, 0.0F, -2.0F, 4.0F, 16.0F, 4.0F),
            PartPose.offsetAndRotation(-7.0F, 3.0F, 0.0F, 0.0F, 0.0F, 0.15F)
        );
        root.addOrReplaceChild(
            "left_arm", CubeListBuilder.create().texOffs(0, 38).mirror().addBox(-1.0F, 0.0F, -2.0F, 4.0F, 16.0F, 4.0F),
            PartPose.offsetAndRotation(7.0F, 3.0F, 0.0F, 0.0F, 0.0F, -0.15F)
        );
        addBipedLegs(root, 14.0F, 5.0F);
        root.addOrReplaceChild(
            "crown",
            CubeListBuilder.create()
                .texOffs(18, 38).addBox(-2.0F, -9.0F, -2.0F, 4.0F, 9.0F, 4.0F)
                .texOffs(34, 38).addBox(-7.0F, -8.0F, -2.0F, 14.0F, 3.0F, 4.0F),
            PartPose.offset(0.0F, -7.0F, 0.0F)
        );
    }

    private static void buildArthropod(final PartDefinition root) {
        root.addOrReplaceChild(
            "head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -3.0F, -7.0F, 8.0F, 6.0F, 7.0F),
            PartPose.offset(0.0F, 16.0F, -3.0F)
        );
        root.addOrReplaceChild(
            "body",
            CubeListBuilder.create()
                .texOffs(0, 14).addBox(-4.0F, -3.0F, -4.0F, 8.0F, 6.0F, 8.0F)
                .texOffs(32, 14).addBox(-5.0F, -4.0F, 3.0F, 10.0F, 8.0F, 10.0F),
            PartPose.offset(0.0F, 16.0F, 0.0F)
        );
        addArthropodLeg(root, "right_hind_leg", false, 3.0F, 0.8F);
        addArthropodLeg(root, "left_hind_leg", true, 3.0F, -0.8F);
        addArthropodLeg(root, "right_middle_hind_leg", false, 1.0F, 0.4F);
        addArthropodLeg(root, "left_middle_hind_leg", true, 1.0F, -0.4F);
        addArthropodLeg(root, "right_middle_front_leg", false, -1.0F, -0.4F);
        addArthropodLeg(root, "left_middle_front_leg", true, -1.0F, 0.4F);
        addArthropodLeg(root, "right_front_leg", false, -3.0F, -0.8F);
        addArthropodLeg(root, "left_front_leg", true, -3.0F, 0.8F);
    }

    private static void addArthropodLeg(
        final PartDefinition root,
        final String name,
        final boolean left,
        final float z,
        final float yRot
    ) {
        final CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 40)
            .addBox(left ? 0.0F : -12.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F);
        root.addOrReplaceChild(
            name,
            leg,
            PartPose.offsetAndRotation(left ? 4.0F : -4.0F, 16.0F, z, 0.0F, yRot, left ? 0.55F : -0.55F)
        );
    }

    private static void buildCreeper(final PartDefinition root) {
        root.addOrReplaceChild(
            "head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.offset(0.0F, 6.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
            PartPose.offset(0.0F, 6.0F, 0.0F)
        );
        addQuadrupedLegs(root, 2.0F, 6.0F, 4.0F, 18.0F, 4.0F);
    }

    private static void buildSpirit(final PartDefinition root) {
        root.addOrReplaceChild(
            "head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -7.0F, -3.5F, 7.0F, 7.0F, 7.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "body", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 10.0F, 4.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_wing", CubeListBuilder.create().texOffs(24, 16).addBox(-7.0F, 0.0F, 0.0F, 7.0F, 10.0F, 1.0F),
            PartPose.offsetAndRotation(-3.0F, 7.0F, 2.0F, 0.0F, 0.0F, 0.35F)
        );
        root.addOrReplaceChild(
            "left_wing", CubeListBuilder.create().texOffs(24, 16).mirror().addBox(0.0F, 0.0F, 0.0F, 7.0F, 10.0F, 1.0F),
            PartPose.offsetAndRotation(3.0F, 7.0F, 2.0F, 0.0F, 0.0F, -0.35F)
        );
        root.addOrReplaceChild(
            "tail", CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, 0.0F, -2.0F, 6.0F, 8.0F, 4.0F),
            PartPose.offset(0.0F, 17.0F, 0.0F)
        );
    }
}
