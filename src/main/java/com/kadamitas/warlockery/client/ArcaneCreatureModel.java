package com.kadamitas.warlockery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.kadamitas.warlockery.client.CreatureModelProfile.Variant;
import com.kadamitas.warlockery.entity.CreatureVisualProfile.Archetype;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

final class ArcaneCreatureModel extends EntityModel<TexturedCreatureRenderers.ArcaneState>
    implements ArmedModel<TexturedCreatureRenderers.ArcaneState> {
    private static final List<String> PART_NAMES = List.of(
        "head", "body", "right_arm", "left_arm", "right_front_leg", "left_front_leg",
        "right_hind_leg", "left_hind_leg", "right_middle_front_leg", "left_middle_front_leg",
        "right_middle_hind_leg", "left_middle_hind_leg", "right_wing", "left_wing", "tail", "crown"
    );
    private final Archetype archetype;
    private final Variant variant;
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

    private ArcaneCreatureModel(final Archetype archetype, final Variant variant, final ModelPart root) {
        super(root);
        this.archetype = archetype;
        this.variant = variant;
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

    static ArcaneCreatureModel create(final CreatureModelProfile profile) {
        return new ArcaneCreatureModel(profile.bodyPlan(), profile.variant(), createLayer(profile).bakeRoot());
    }

    static LayerDefinition createLayer(final Archetype archetype) {
        return createLayer(archetype, null);
    }

    static LayerDefinition createLayer(final CreatureModelProfile profile) {
        return createLayer(profile.bodyPlan(), profile.variant());
    }

    private static LayerDefinition createLayer(final Archetype archetype, final Variant variant) {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        if (!buildDistinctBody(root, variant)) {
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
                case IMP -> buildImp(root);
                case SIMIAN -> buildSimian(root);
                case SPIRIT -> buildSpirit(root);
            }
        }
        if (variant != null) {
            decorate(root, variant);
        }
        PART_NAMES.stream()
            .filter(name -> root.getChild(name) == null)
            .forEach(name -> root.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.ZERO));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static boolean buildDistinctBody(final PartDefinition root, final Variant variant) {
        if (variant == null) {
            return false;
        }
        return switch (variant) {
            case UMBRAL_SIGIL -> {
                buildUmbralSigil(root);
                yield true;
            }
            case ELDRITCH_WATCHER -> {
                buildEldritchWatcher(root);
                yield true;
            }
            case POLTERGEIST -> {
                buildPoltergeist(root);
                yield true;
            }
            case NAAMAH -> {
                buildNaamah(root);
                yield true;
            }
            case ABYSSAL_REGENT -> {
                buildAbyssalRegent(root);
                yield true;
            }
            case GOBLIN, HOBGOBLIN -> {
                buildPenguinGoblin(root);
                yield true;
            }
            default -> false;
        };
    }

    private static void buildPenguinGoblin(final PartDefinition root) {
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -6.0F, -3.5F, 8.0F, 6.0F, 7.0F)
                .texOffs(30, 0).addBox(-2.0F, -2.75F, -5.5F, 4.0F, 2.0F, 2.0F),
            PartPose.offset(0.0F, 10.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(0, 16).addBox(-5.0F, 0.0F, -3.0F, 10.0F, 10.0F, 6.0F),
            PartPose.offset(0.0F, 10.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(32, 12).addBox(-3.0F, 0.0F, -1.0F, 3.0F, 8.0F, 2.0F),
            PartPose.offsetAndRotation(-5.0F, 11.0F, 0.0F, 0.0F, 0.0F, 0.18F)
        );
        root.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(32, 12).mirror().addBox(0.0F, 0.0F, -1.0F, 3.0F, 8.0F, 2.0F),
            PartPose.offsetAndRotation(5.0F, 11.0F, 0.0F, 0.0F, 0.0F, -0.18F)
        );
        root.addOrReplaceChild(
            "right_hind_leg",
            CubeListBuilder.create().texOffs(44, 12).addBox(-2.0F, 0.0F, -3.0F, 4.0F, 3.0F, 5.0F),
            PartPose.offset(-2.25F, 20.0F, -0.5F)
        );
        root.addOrReplaceChild(
            "left_hind_leg",
            CubeListBuilder.create().texOffs(44, 12).mirror().addBox(-2.0F, 0.0F, -3.0F, 4.0F, 3.0F, 5.0F),
            PartPose.offset(2.25F, 20.0F, -0.5F)
        );
        root.addOrReplaceChild(
            "tail",
            CubeListBuilder.create().texOffs(44, 22).addBox(-2.0F, 0.0F, -1.0F, 4.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 17.0F, 3.0F, 0.45F, 0.0F, 0.0F)
        );
    }

    @Override
    public void setupAnim(final TexturedCreatureRenderers.ArcaneState state) {
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
            case IMP -> {
                animateBiped(rightSwing, leftSwing, state.ageInTicks);
                animateWings(state.ageInTicks, stride);
            }
            case SIMIAN -> {
                animateQuadruped(rightSwing, leftSwing, state.ageInTicks);
                rightArm.xRot += leftSwing * 0.65F;
                leftArm.xRot += rightSwing * 0.65F;
                animateWings(state.ageInTicks, stride * 0.6F);
            }
        }
        applyHexBatPose(hexBatPose(variant, state.hexBatRoosting, state.hexBatSwooping));
    }

    /**
     * Pure Hex Bat pose selection from synchronized render facts. Only the
     * exact HEX_BAT variant may produce a non-neutral pose, so Owl and every
     * other avian keep their existing animation. Pose reset happens every
     * frame through {@code super.setupAnim}, so no rotation can leak.
     */
    static HexBatPose hexBatPose(final Variant variant, final boolean roosting, final boolean swooping) {
        if (variant != Variant.HEX_BAT) {
            return HexBatPose.NEUTRAL;
        }
        if (roosting) {
            // Upside-down hanging silhouette with folded wings.
            return new HexBatPose(true, Mth.PI, 0.5F, 2.1F);
        }
        if (swooping) {
            // Forward-pitched attack pose with swept, narrowed wings.
            return new HexBatPose(true, 0.65F, -0.3F, -1.1F);
        }
        return HexBatPose.NEUTRAL;
    }

    record HexBatPose(boolean overrides, float bodyXRot, float headXRot, float wingFoldZRot) {
        static final HexBatPose NEUTRAL = new HexBatPose(false, 0.0F, 0.0F, 0.0F);
    }

    private void applyHexBatPose(final HexBatPose pose) {
        if (!pose.overrides()) {
            return;
        }
        body.xRot += pose.bodyXRot();
        head.xRot += pose.headXRot();
        rightWing.zRot = -pose.wingFoldZRot();
        leftWing.zRot = pose.wingFoldZRot();
    }

    @Override
    public void translateToHand(
        final TexturedCreatureRenderers.ArcaneState state,
        final HumanoidArm arm,
        final PoseStack poseStack
    ) {
        (arm == HumanoidArm.LEFT ? leftArm : rightArm).translateAndRotate(poseStack);
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
        root.addOrReplaceChild(
            "right_hind_leg", CubeListBuilder.create().texOffs(0, 26).addBox(-2.0F, 0.0F, -2.0F, 3.0F, 5.0F, 4.0F),
            PartPose.offset(-1.5F, 19.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "left_hind_leg", CubeListBuilder.create().texOffs(0, 26).mirror().addBox(-1.0F, 0.0F, -2.0F, 3.0F, 5.0F, 4.0F),
            PartPose.offset(1.5F, 19.0F, 0.0F)
        );
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

    private static void buildImp(final PartDefinition root) {
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, -7.0F, -3.0F, 7.0F, 7.0F, 6.0F)
                .texOffs(26, 0).addBox(-5.5F, -5.0F, -1.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(34, 0).mirror().addBox(3.5F, -5.0F, -1.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "body", CubeListBuilder.create().texOffs(0, 14).addBox(-3.0F, 0.0F, -2.0F, 6.0F, 8.0F, 4.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_arm", CubeListBuilder.create().texOffs(20, 14).addBox(-2.0F, 0.0F, -1.5F, 2.0F, 8.0F, 3.0F),
            PartPose.offsetAndRotation(-3.0F, 8.0F, 0.0F, -0.2F, 0.0F, 0.18F)
        );
        root.addOrReplaceChild(
            "left_arm", CubeListBuilder.create().texOffs(20, 14).mirror().addBox(0.0F, 0.0F, -1.5F, 2.0F, 8.0F, 3.0F),
            PartPose.offsetAndRotation(3.0F, 8.0F, 0.0F, -0.2F, 0.0F, -0.18F)
        );
        addBipedLegs(root, 15.0F, 3.0F);
        final PartDefinition rightWing = root.addOrReplaceChild(
            "right_wing", CubeListBuilder.create().texOffs(32, 12).addBox(-7.0F, 0.0F, 0.0F, 7.0F, 9.0F, 1.0F),
            PartPose.offsetAndRotation(-2.0F, 8.0F, 2.0F, 0.1F, 0.0F, 0.45F)
        );
        rightWing.addOrReplaceChild(
            "right_wing_finger",
            CubeListBuilder.create().texOffs(32, 22).addBox(-8.0F, 0.0F, -0.5F, 8.0F, 1.0F, 2.0F),
            PartPose.offsetAndRotation(-0.5F, 1.0F, 0.0F, 0.0F, 0.0F, -0.28F)
        );
        final PartDefinition leftWing = root.addOrReplaceChild(
            "left_wing", CubeListBuilder.create().texOffs(32, 12).mirror().addBox(0.0F, 0.0F, 0.0F, 7.0F, 9.0F, 1.0F),
            PartPose.offsetAndRotation(2.0F, 8.0F, 2.0F, 0.1F, 0.0F, -0.45F)
        );
        leftWing.addOrReplaceChild(
            "left_wing_finger",
            CubeListBuilder.create().texOffs(32, 22).mirror().addBox(0.0F, 0.0F, -0.5F, 8.0F, 1.0F, 2.0F),
            PartPose.offsetAndRotation(0.5F, 1.0F, 0.0F, 0.0F, 0.0F, 0.28F)
        );
        root.addOrReplaceChild(
            "tail", CubeListBuilder.create().texOffs(48, 12).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 14.0F, 2.0F, 0.9F, 0.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "crown",
            CubeListBuilder.create()
                .texOffs(0, 28).addBox(-3.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F)
                .texOffs(8, 28).addBox(1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offset(0.0F, 1.0F, 0.0F)
        );
    }

    private static void buildSimian(final PartDefinition root) {
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -7.0F, -3.5F, 8.0F, 7.0F, 7.0F)
                .texOffs(30, 0).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 4.0F, 3.0F),
            PartPose.offset(0.0F, 7.0F, -1.0F)
        );
        root.addOrReplaceChild(
            "body", CubeListBuilder.create().texOffs(0, 16).addBox(-4.5F, 0.0F, -3.0F, 9.0F, 10.0F, 6.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.2F, 0.0F, 0.0F)
        );
        final PartDefinition rightArm = root.addOrReplaceChild(
            "right_arm", CubeListBuilder.create().texOffs(30, 16).addBox(-3.0F, 0.0F, -2.0F, 3.0F, 13.0F, 4.0F),
            PartPose.offsetAndRotation(-4.0F, 8.0F, 0.0F, -0.35F, 0.0F, 0.2F)
        );
        rightArm.addOrReplaceChild(
            "right_hand",
            CubeListBuilder.create().texOffs(44, 16).addBox(-3.5F, 0.0F, -2.5F, 4.0F, 4.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, 0.18F, 0.0F, 0.0F)
        );
        final PartDefinition leftArm = root.addOrReplaceChild(
            "left_arm", CubeListBuilder.create().texOffs(30, 16).mirror().addBox(0.0F, 0.0F, -2.0F, 3.0F, 13.0F, 4.0F),
            PartPose.offsetAndRotation(4.0F, 8.0F, 0.0F, -0.35F, 0.0F, -0.2F)
        );
        leftArm.addOrReplaceChild(
            "left_hand",
            CubeListBuilder.create().texOffs(44, 16).mirror().addBox(-0.5F, 0.0F, -2.5F, 4.0F, 4.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, 0.18F, 0.0F, 0.0F)
        );
        addQuadrupedLegs(root, 2.5F, 4.0F, 5.0F, 17.0F, 4.0F);
        final PartDefinition rightWing = root.addOrReplaceChild(
            "right_wing", CubeListBuilder.create().texOffs(0, 36).addBox(-6.0F, 0.0F, 0.0F, 6.0F, 8.0F, 1.0F),
            PartPose.offsetAndRotation(-3.0F, 8.0F, 2.0F, 0.0F, 0.0F, 0.4F)
        );
        rightWing.addOrReplaceChild(
            "right_primary_feathers",
            CubeListBuilder.create().texOffs(28, 36).addBox(-8.0F, 0.0F, -0.5F, 8.0F, 10.0F, 1.0F),
            PartPose.offsetAndRotation(-1.0F, 3.0F, 0.0F, 0.0F, 0.0F, -0.22F)
        );
        final PartDefinition leftWing = root.addOrReplaceChild(
            "left_wing", CubeListBuilder.create().texOffs(0, 36).mirror().addBox(0.0F, 0.0F, 0.0F, 6.0F, 8.0F, 1.0F),
            PartPose.offsetAndRotation(3.0F, 8.0F, 2.0F, 0.0F, 0.0F, -0.4F)
        );
        leftWing.addOrReplaceChild(
            "left_primary_feathers",
            CubeListBuilder.create().texOffs(28, 36).mirror().addBox(0.0F, 0.0F, -0.5F, 8.0F, 10.0F, 1.0F),
            PartPose.offsetAndRotation(1.0F, 3.0F, 0.0F, 0.0F, 0.0F, 0.22F)
        );
        root.addOrReplaceChild(
            "tail", CubeListBuilder.create().texOffs(16, 36).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 14.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 15.0F, 3.0F, 1.05F, 0.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "crown", CubeListBuilder.create().texOffs(24, 36).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 4.0F, 2.0F),
            PartPose.offset(0.0F, 1.0F, 0.0F)
        );
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

    private static void buildUmbralSigil(final PartDefinition root) {
        addPart(root, "body", 12.0F, 12.0F, 2.0F, 0.0F, 10.0F, 1.0F);
        addPart(root, "head", 5.0F, 5.0F, 3.0F, 0.0F, 10.0F, -1.0F, 0.0F, 0.0F, 0.78F);
        addPart(root, "right_wing", 3.0F, 9.0F, 2.0F, -10.0F, 9.0F, 0.0F, 0.0F, 0.0F, -0.12F);
        addPart(root, "left_wing", 3.0F, 9.0F, 2.0F, 10.0F, 9.0F, 0.0F, 0.0F, 0.0F, 0.12F);
        addPart(root, "crown", 8.0F, 3.0F, 2.0F, 0.0F, -1.0F, 0.0F);
        addPart(root, "tail", 7.0F, 3.0F, 2.0F, 0.0F, 21.0F, 0.0F);
    }

    private static void buildEldritchWatcher(final PartDefinition root) {
        addPart(root, "head", 13.0F, 12.0F, 11.0F, 0.0F, 6.0F, 0.0F);
        addPart(root, "body", 10.0F, 5.0F, 9.0F, 0.0F, 14.0F, 1.0F);
        addPart(root, "right_wing", 3.0F, 15.0F, 3.0F, -5.0F, 21.0F, 0.0F, 0.18F, 0.0F, 0.2F);
        addPart(root, "left_wing", 3.0F, 15.0F, 3.0F, 5.0F, 21.0F, 0.0F, 0.18F, 0.0F, -0.2F);
        addPart(root, "tail", 3.0F, 14.0F, 3.0F, 0.0F, 22.0F, 2.0F, 0.25F, 0.0F, 0.0F);
        addPart(root, "crown", 8.0F, 3.0F, 8.0F, 0.0F, -1.0F, 0.0F);
    }

    private static void buildPoltergeist(final PartDefinition root) {
        addPart(root, "head", 5.0F, 4.0F, 5.0F, 0.0F, 6.0F, -1.0F, 0.15F, 0.1F, 0.2F);
        addPart(root, "body", 8.0F, 8.0F, 8.0F, 0.0F, 12.0F, 0.0F, -0.1F, 0.15F, -0.08F);
        addPart(root, "right_wing", 6.0F, 7.0F, 5.0F, -10.0F, 8.0F, 1.0F, 0.25F, -0.2F, -0.35F);
        addPart(root, "left_wing", 6.0F, 7.0F, 5.0F, 10.0F, 8.0F, 1.0F, -0.2F, 0.25F, 0.35F);
        addPart(root, "right_arm", 4.0F, 5.0F, 4.0F, -7.0F, 18.0F, -2.0F, 0.3F, 0.15F, 0.25F);
        addPart(root, "left_arm", 4.0F, 5.0F, 4.0F, 7.0F, 18.0F, 2.0F, -0.25F, -0.2F, -0.3F);
        addPart(root, "tail", 4.0F, 4.0F, 4.0F, 1.0F, 24.0F, 0.0F, 0.2F, 0.1F, 0.35F);
        addPart(root, "crown", 4.0F, 4.0F, 4.0F, -2.0F, -1.0F, 1.0F, -0.2F, 0.2F, -0.35F);
    }

    private static void buildNaamah(final PartDefinition root) {
        addPart(root, "head", 7.0F, 6.0F, 6.0F, 0.0F, 1.0F, -1.0F);
        addPart(root, "body", 10.0F, 14.0F, 8.0F, 0.0F, 10.0F, 0.0F);
        addPart(root, "right_front_leg", 2.0F, 13.0F, 2.0F, -7.0F, 4.0F, -2.0F, 0.0F, 0.2F, 0.75F);
        addPart(root, "left_front_leg", 2.0F, 13.0F, 2.0F, 7.0F, 4.0F, -2.0F, 0.0F, -0.2F, -0.75F);
        addPart(root, "right_middle_front_leg", 2.0F, 15.0F, 2.0F, -9.0F, 9.0F, -1.0F, 0.0F, 0.3F, 1.0F);
        addPart(root, "left_middle_front_leg", 2.0F, 15.0F, 2.0F, 9.0F, 9.0F, -1.0F, 0.0F, -0.3F, -1.0F);
        addPart(root, "right_middle_hind_leg", 2.0F, 14.0F, 2.0F, -8.0F, 15.0F, 1.0F, 0.0F, -0.3F, 1.05F);
        addPart(root, "left_middle_hind_leg", 2.0F, 14.0F, 2.0F, 8.0F, 15.0F, 1.0F, 0.0F, 0.3F, -1.05F);
        addPart(root, "right_hind_leg", 3.0F, 11.0F, 3.0F, -5.0F, 21.0F, 2.0F, 0.15F, 0.0F, 0.55F);
        addPart(root, "left_hind_leg", 3.0F, 11.0F, 3.0F, 5.0F, 21.0F, 2.0F, 0.15F, 0.0F, -0.55F);
        addPart(root, "tail", 8.0F, 8.0F, 7.0F, 0.0F, 19.0F, 3.0F);
        addPart(root, "crown", 9.0F, 4.0F, 7.0F, 0.0F, -5.0F, 0.0F);
    }

    private static void buildAbyssalRegent(final PartDefinition root) {
        addPart(root, "head", 8.0F, 8.0F, 8.0F, 0.0F, 0.0F, 0.0F);
        addPart(root, "body", 13.0F, 13.0F, 9.0F, 0.0F, 9.0F, 0.0F);
        addPart(root, "right_arm", 4.0F, 14.0F, 4.0F, -8.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.18F);
        addPart(root, "left_arm", 4.0F, 14.0F, 4.0F, 8.0F, 8.0F, 0.0F, 0.0F, 0.0F, -0.18F);
        addPart(root, "right_hind_leg", 3.0F, 14.0F, 3.0F, -4.0F, 21.0F, 0.0F, 0.2F, 0.0F, 0.2F);
        addPart(root, "left_hind_leg", 3.0F, 14.0F, 3.0F, 4.0F, 21.0F, 0.0F, 0.2F, 0.0F, -0.2F);
        addPart(root, "right_wing", 3.0F, 16.0F, 3.0F, -7.0F, 21.0F, 2.0F, 0.3F, 0.0F, 0.3F);
        addPart(root, "left_wing", 3.0F, 16.0F, 3.0F, 7.0F, 21.0F, 2.0F, 0.3F, 0.0F, -0.3F);
        addPart(root, "tail", 4.0F, 16.0F, 4.0F, 0.0F, 22.0F, 3.0F, 0.25F, 0.0F, 0.0F);
        addPart(root, "crown", 12.0F, 5.0F, 9.0F, 0.0F, -6.0F, 0.0F);
    }

    private static void decorate(final PartDefinition root, final Variant variant) {
        switch (variant) {
            case CIRCLE_MAGE -> {
                addHood(root, 4.5F, -4.0F);
                addRobe(root, 9.0F, 8.0F, 5.5F);
                addStaff(root, "ritual_staff", -6.5F, 8.0F, -1.0F);
            }
            case HEDGE_CRONE -> {
                addHood(root, 5.0F, -4.0F);
                addRobe(root, 10.0F, 9.0F, 6.0F);
                addPart(root, "crooked_nose", 2.0F, 2.0F, 4.0F, 0.0F, -3.0F, -5.0F);
                addStaff(root, "bone_staff", -7.0F, 7.0F, 0.0F);
            }
            case VAMPIRE -> {
                addPart(root, "vampire_cape_mantle", 15.0F, 5.0F, 7.0F, 0.0F, 2.0F, 2.5F);
                addPair(root, "vampire_cape_panel", 6.0F, 17.0F, 2.0F, 4.0F, 11.0F, 4.0F, 0.1F, 0.0F, 0.12F);
                addPair(root, "vampire_coat_tail", 4.0F, 10.0F, 4.0F, 2.5F, 17.0F, 1.5F, 0.08F, 0.0F, 0.08F);
                addPair(root, "high_collar", 3.0F, 7.0F, 3.0F, 5.0F, 0.0F, 1.8F, 0.0F, 0.0F, 0.18F);
                addPart(root, "blood_medallion", 2.0F, 3.0F, 1.0F, 0.0F, 4.0F, -3.0F);
            }
            case BLOOD_THRALL -> {
                addPart(root, "thrall_torso_mass", 12.0F, 12.0F, 8.0F, 0.0F, 6.0F, 0.0F);
                addPair(root, "thrall_shoulders", 7.0F, 6.0F, 7.0F, 7.0F, 2.0F, 0.0F);
                addPair(root, "thrall_gauntlet", 6.0F, 7.0F, 6.0F, 7.0F, 12.0F, 0.0F);
                addPair(root, "iron_shackle", 7.0F, 2.0F, 7.0F, 7.0F, 15.0F, 0.0F);
                addPart(root, "chest_chain", 2.0F, 10.0F, 1.0F, 0.0F, 6.0F, -3.0F, 0.0F, 0.0F, 0.45F);
                addPart(root, "thrall_mask", 7.0F, 4.0F, 1.0F, 0.0F, -4.5F, -4.5F);
            }
            case CORPSE -> {
                addPart(root, "corpse_back_mass", 13.0F, 13.0F, 9.0F, 0.0F, 8.0F, 1.5F, 0.15F, 0.0F, 0.08F);
                addPair(root, "corpse_shoulder", 7.0F, 6.0F, 8.0F, 7.0F, 3.0F, 0.0F, 0.0F, 0.0F, 0.15F);
                addPair(root, "corpse_forearm", 6.0F, 9.0F, 6.0F, 7.0F, 13.0F, -1.0F, 0.2F, 0.0F, 0.1F);
                addPart(root, "grave_shroud", 12.0F, 8.0F, 7.0F, 0.0F, 14.0F, 0.0F, 0.15F, 0.0F, 0.08F);
                addPart(root, "grave_cairn", 11.0F, 5.0F, 9.0F, 0.0F, -7.0F, 1.0F, 0.0F, 0.15F, 0.0F);
                addPart(root, "exposed_jaw", 5.0F, 2.0F, 2.0F, 0.0F, -0.5F, -5.0F);
                addPart(root, "burial_board", 4.0F, 14.0F, 2.0F, 5.0F, 7.0F, 5.0F, 0.0F, 0.0F, -0.3F);
            }
            case GLASS_DOPPELGANGER -> {
                addPair(root, "glass_shoulder", 4.0F, 6.0F, 3.0F, 5.5F, 2.0F, 0.0F, 0.0F, 0.0F, 0.45F);
                addPair(root, "glass_crown", 2.0F, 6.0F, 2.0F, 2.5F, -10.0F, 0.0F, 0.0F, 0.0F, 0.28F);
                addPart(root, "glass_core", 3.0F, 5.0F, 1.0F, 0.0F, 5.0F, -3.0F);
            }
            case WEREWOLF_HUNTER -> {
                addPart(root, "hunter_hat_brim", 11.0F, 1.0F, 10.0F, 0.0F, -8.0F, 0.0F);
                addPart(root, "hunter_hat_crown", 7.0F, 3.0F, 7.0F, 0.0F, -10.0F, 0.0F);
                addPart(root, "hunter_coat_mantle", 13.0F, 5.0F, 7.0F, 0.0F, 3.0F, 1.5F);
                addPair(root, "hunter_coat_panel", 5.0F, 14.0F, 4.0F, 3.0F, 13.0F, 1.0F, 0.08F, 0.0F, 0.08F);
                addPair(root, "hunter_bracer", 5.0F, 5.0F, 6.0F, 6.5F, 11.0F, 0.0F);
                addPair(root, "hunter_boot", 5.0F, 7.0F, 6.0F, 2.5F, 20.0F, 0.0F);
                addCrossbow(root);
            }
            case LYCAN_VILLAGER -> {
                addPart(root, "village_vest", 12.0F, 11.0F, 7.0F, 0.0F, 6.0F, 0.0F);
                addPair(root, "village_sleeve", 6.0F, 10.0F, 6.0F, 6.5F, 8.0F, 0.0F);
                addPair(root, "village_coat_tail", 5.0F, 9.0F, 5.0F, 3.0F, 16.0F, 1.0F);
                addPair(root, "village_boot", 5.0F, 7.0F, 6.0F, 2.5F, 21.0F, 0.0F);
                addPart(root, "work_apron", 7.0F, 7.0F, 1.0F, 0.0F, 11.0F, -3.5F);
                addPart(root, "moon_badge", 2.0F, 2.0F, 1.0F, 2.5F, 3.0F, -3.5F);
            }
            case BANSHEE -> {
                addHood(root, 4.5F, 3.0F);
                addPart(root, "hair_veil", 9.0F, 16.0F, 1.0F, 0.0F, 11.0F, 3.0F);
                addPair(root, "banshee_claw", 2.0F, 13.0F, 2.0F, 6.0F, 13.0F, 0.0F, 0.2F, 0.0F, 0.3F);
            }
            case UMBRAL_SIGIL -> addRuneOrbit(root);
            case ELDRITCH_WATCHER -> {
                addPart(root, "watcher_eye", 10.0F, 7.0F, 2.0F, 0.0F, 7.0F, -8.0F);
                addPart(root, "watcher_pupil", 4.0F, 4.0F, 3.0F, 0.0F, 7.0F, -9.5F);
                addPart(root, "watcher_rear_eye", 10.0F, 7.0F, 2.0F, 0.0F, 7.0F, 8.0F);
                addPart(root, "watcher_rear_pupil", 4.0F, 4.0F, 3.0F, 0.0F, 7.0F, 9.5F);
                addPair(root, "watcher_lateral_eye", 3.0F, 3.0F, 3.0F, 8.0F, 7.0F, 0.0F);
                addPair(root, "watcher_front_tentacle", 2.5F, 14.0F, 2.5F, 3.0F, 22.0F, -3.0F, 0.3F, 0.0F, 0.18F);
                addPair(root, "watcher_back_tentacle", 2.5F, 13.0F, 2.5F, 7.0F, 20.0F, 3.0F, -0.2F, 0.15F, 0.25F);
                addPair(root, "watcher_side_eye", 3.0F, 3.0F, 3.0F, 5.0F, 9.0F, -7.5F);
                addPair(root, "watcher_upper_eye", 3.0F, 3.0F, 3.0F, 3.0F, 2.0F, -7.5F);
                addPair(root, "watcher_lower_eye", 2.0F, 2.0F, 3.0F, 3.0F, 12.0F, -7.0F);
            }
            case SPECTRAL_FAMILIAR -> {
                addPart(root, "spectral_collar", 7.0F, 2.0F, 7.0F, 0.0F, 15.0F, -4.5F);
                addPart(root, "familiar_charm", 2.0F, 3.0F, 1.0F, 0.0F, 17.0F, -7.5F);
                addPart(root, "forked_tail_tip", 6.0F, 2.0F, 2.0F, 0.0F, 22.0F, 9.0F);
            }
            case POLTERGEIST -> addDebris(root);
            case SPECTRE -> {
                addHood(root, 4.5F, 3.0F);
                addRobe(root, 10.0F, 12.0F, 5.0F);
                addPair(root, "spectre_chain", 1.0F, 11.0F, 1.0F, 5.0F, 14.0F, -2.5F, 0.15F, 0.0F, 0.0F);
            }
            case SPIRIT -> {
                addPart(root, "spirit_halo", 9.0F, 1.0F, 9.0F, 0.0F, -2.0F, 0.0F);
                addPart(root, "spirit_heart", 3.0F, 3.0F, 1.0F, 0.0F, 11.0F, -3.0F);
            }
            case LOST_SOUL -> {
                addPart(root, "soul_flame", 5.0F, 7.0F, 5.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.78F);
                addPair(root, "soul_wisp", 2.0F, 5.0F, 2.0F, 4.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.45F);
            }
            case ECHO_SHADE -> {
                addPair(root, "echo_shell", 7.0F, 12.0F, 1.0F, 5.0F, 12.0F, 3.0F, 0.0F, 0.2F, 0.15F);
                addPart(root, "echo_crown", 10.0F, 2.0F, 2.0F, 0.0F, 1.0F, 2.0F);
            }
            case WEREWOLF -> {
                addPair(root, "wolf_shoulder", 6.0F, 6.0F, 7.0F, 6.0F, 2.5F, 0.0F);
                addPart(root, "wolf_mane", 10.0F, 10.0F, 3.0F, 0.0F, 2.0F, 3.2F);
                addPair(root, "wolf_forearm", 5.0F, 9.0F, 5.0F, 7.0F, 12.0F, -1.0F, 0.18F, 0.0F, 0.12F);
                addPair(root, "wolf_claw", 5.0F, 3.0F, 7.0F, 7.0F, 18.0F, -2.0F, 0.25F, 0.0F, 0.12F);
                addPair(root, "wolf_hock", 5.0F, 8.0F, 5.0F, 3.0F, 18.0F, 1.0F, -0.2F, 0.0F, 0.08F);
                addPair(root, "wolf_foot", 5.0F, 3.0F, 8.0F, 3.0F, 23.0F, -2.0F);
            }
            case FERAL_LYCAN -> {
                addPart(root, "feral_mane", 8.0F, 6.0F, 10.0F, 0.0F, 10.0F, -1.0F);
                addPair(root, "broken_chain", 1.5F, 7.0F, 1.5F, 4.5F, 17.0F, 0.0F, 0.0F, 0.0F, 0.45F);
            }
            case HELLHOUND -> {
                addPair(root, "hell_horn", 2.0F, 5.0F, 2.0F, 2.7F, 6.0F, -7.0F, -0.4F, 0.0F, 0.2F);
                addPart(root, "ember_spine", 4.0F, 3.0F, 13.0F, 0.0F, 7.0F, 1.0F);
                addPart(root, "hell_collar", 9.0F, 3.0F, 7.0F, 0.0F, 12.0F, -5.0F);
            }
            case PALE_STEED -> {
                addPart(root, "pale_barding", 12.0F, 8.0F, 15.0F, 0.0F, 8.0F, 1.0F);
                addPart(root, "moon_chestplate", 8.0F, 8.0F, 2.0F, 0.0F, 10.0F, -9.0F, -0.2F, 0.0F, 0.0F);
            }
            case NIGHTMARE -> {
                addPart(root, "nightmare_barding", 13.0F, 9.0F, 16.0F, 0.0F, 8.0F, 1.0F);
                addPart(root, "ember_mane", 3.0F, 10.0F, 16.0F, 0.0F, 2.0F, 0.0F, 0.35F, 0.0F, 0.0F);
                addPair(root, "hoof_plate", 4.0F, 3.0F, 5.0F, 3.0F, 21.0F, -6.0F);
            }
            case FAMILIAR_CAT -> {
                addPart(root, "moon_collar", 8.0F, 2.0F, 7.0F, 0.0F, 15.0F, -5.0F);
                addPart(root, "moon_charm", 2.0F, 3.0F, 1.0F, 0.0F, 17.0F, -8.0F);
                addPart(root, "curled_tail", 6.0F, 2.0F, 2.0F, 3.0F, 18.0F, 9.0F, 0.0F, 0.5F, 0.0F);
            }
            case OWL -> {
                addPart(root, "owl_brow", 10.0F, 2.0F, 6.0F, 0.0F, 7.0F, -3.5F);
                addPart(root, "owl_beak", 3.0F, 3.0F, 4.0F, 0.0F, 12.0F, -7.0F, 0.35F, 0.0F, 0.0F);
                addPair(root, "layered_feather", 3.0F, 8.0F, 3.0F, 6.0F, 16.0F, 1.0F, 0.0F, 0.0F, 0.3F);
            }
            case TOAD -> {
                addPart(root, "toad_throat", 7.0F, 5.0F, 4.0F, 0.0F, 21.0F, -5.0F);
                addPair(root, "toad_brow", 4.0F, 2.0F, 4.0F, 2.7F, 14.0F, -5.0F);
            }
            case HEX_BAT -> {
                addPair(root, "hex_wing_tip", 8.0F, 12.0F, 1.0F, 8.0F, 13.0F, 1.0F, 0.0F, 0.0F, 0.45F);
                addPart(root, "hex_rune", 4.0F, 4.0F, 1.0F, 0.0F, 16.0F, -4.0F);
                addPair(root, "bat_horn", 2.0F, 5.0F, 2.0F, 2.5F, 6.0F, -2.0F, -0.2F, 0.0F, 0.25F);
            }
            case PARASYTIC_LOUSE -> {
                addPart(root, "louse_shell", 11.0F, 8.0F, 14.0F, 0.0F, 14.0F, 4.0F);
                addPart(root, "feeding_core", 5.0F, 4.0F, 3.0F, 0.0F, 16.0F, -7.0F);
                addPair(root, "hooked_mandible", 2.0F, 2.0F, 6.0F, 3.0F, 17.0F, -10.0F, 0.0F, 0.25F, 0.0F);
            }
            case DEMON -> {
                addHorns(root, 5.0F, -8.0F, 7.0F);
                addPair(root, "demon_pauldrons", 7.0F, 6.0F, 7.0F, 7.5F, 1.5F, 0.0F);
                addPair(root, "demon_bracer", 6.0F, 6.0F, 6.0F, 7.0F, 10.0F, 0.0F);
                addPair(root, "demon_greave", 5.0F, 8.0F, 6.0F, 3.0F, 19.0F, 0.0F);
                addPart(root, "brass_belt", 12.0F, 2.0F, 7.0F, 0.0F, 11.0F, 0.0F);
                addPart(root, "demon_warhammer", 5.0F, 16.0F, 5.0F, -10.0F, 9.0F, -1.0F, 0.0F, 0.0F, -0.28F);
            }
            case EMBERHORN_ARCHFIEND -> {
                addHorns(root, 8.0F, -10.0F, 11.0F);
                addPair(root, "archfiend_pauldrons", 8.0F, 8.0F, 8.0F, 9.0F, 1.0F, 0.0F);
                addPart(root, "archfiend_chestplate", 15.0F, 11.0F, 8.0F, 0.0F, 6.0F, 0.0F);
                addPair(root, "archfiend_gauntlet", 7.0F, 9.0F, 7.0F, 9.0F, 12.0F, 0.0F);
                addPair(root, "archfiend_greave", 7.0F, 10.0F, 8.0F, 4.0F, 19.0F, 0.0F);
                addPart(root, "lava_core", 6.0F, 7.0F, 1.0F, 0.0F, 5.0F, -4.0F);
                addPart(root, "archfiend_maul", 5.0F, 17.0F, 5.0F, -10.0F, 9.0F, -1.0F, 0.0F, 0.0F, -0.3F);
            }
            case NAAMAH -> {
                addPair(root, "matriarch_crown_tine", 2.0F, 9.0F, 2.0F, 4.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.35F);
                addPart(root, "matriarch_faceplate", 7.0F, 6.0F, 2.0F, 0.0F, 1.0F, -4.0F);
                addPair(root, "upper_blade", 2.0F, 3.0F, 13.0F, 10.0F, 6.0F, -4.0F, 0.0F, 0.4F, 0.65F);
                addPair(root, "lower_blade", 2.0F, 3.0F, 11.0F, 10.0F, 15.0F, 1.0F, 0.0F, -0.4F, 0.5F);
            }
            case ABYSSAL_REGENT -> {
                addPart(root, "coral_crown", 14.0F, 8.0F, 9.0F, 0.0F, -9.0F, 0.0F);
                addPair(root, "regent_mantle", 8.0F, 7.0F, 9.0F, 8.0F, 3.0F, 0.0F);
                addPair(root, "outer_abyssal_tentacle", 2.0F, 16.0F, 2.0F, 10.0F, 21.0F, 2.0F, 0.3F, 0.0F, 0.35F);
                addPair(root, "inner_abyssal_tentacle", 2.0F, 13.0F, 2.0F, 2.0F, 23.0F, -2.0F, -0.2F, 0.0F, 0.18F);
                addStaff(root, "tidal_staff", -13.0F, 9.0F, 0.0F);
            }
            case DEATH -> {
                addHood(root, 5.0F, -4.0F);
                addPart(root, "death_mantle", 15.0F, 6.0F, 9.0F, 0.0F, 2.0F, 1.0F);
                addRobe(root, 14.0F, 16.0F, 8.0F);
                addPair(root, "death_robe_panel", 6.0F, 15.0F, 4.0F, 4.0F, 14.0F, 1.0F, 0.08F, 0.0F, 0.1F);
                addPart(root, "death_robe_hem", 17.0F, 4.0F, 10.0F, 0.0F, 22.0F, 1.0F);
                addPart(root, "bone_mask", 7.0F, 8.0F, 1.0F, 0.0F, -4.0F, -4.5F);
                addScythe(root);
            }
            case IRONBOUND_SENTINEL -> {
                addPart(root, "sentinel_chassis", 15.0F, 15.0F, 10.0F, 0.0F, 6.0F, 0.0F);
                addPair(root, "sentinel_shield", 7.0F, 19.0F, 12.0F, 11.0F, 6.0F, 0.0F);
                addPart(root, "sentinel_core", 6.0F, 6.0F, 2.0F, 0.0F, 5.0F, -4.0F);
                addPart(root, "sentinel_crest", 4.0F, 6.0F, 5.0F, 0.0F, -11.0F, 0.0F);
                addPart(root, "sentinel_hammer", 7.0F, 17.0F, 7.0F, -11.0F, 12.0F, -2.0F, 0.0F, 0.0F, -0.25F);
            }
            case ENT -> {
                addPart(root, "ent_crown_branch", 3.0F, 21.0F, 3.0F, -4.0F, -17.0F, 0.0F, 0.0F, 0.0F, -0.25F);
                addPart(root, "ent_reaching_branch", 3.0F, 18.0F, 3.0F, 9.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.65F);
                addPair(root, "ent_branch_tip", 2.0F, 13.0F, 2.0F, 10.0F, -23.0F, 0.0F, 0.0F, 0.0F, 0.5F);
                addPart(root, "left_ent_canopy", 11.0F, 9.0F, 9.0F, -9.0F, -18.0F, 0.0F);
                addPart(root, "right_ent_canopy", 8.0F, 7.0F, 8.0F, 10.0F, -12.0F, 1.0F);
                addPair(root, "ent_outer_leaves", 8.0F, 7.0F, 8.0F, 15.0F, -20.0F, 1.0F);
                addPart(root, "ent_beard", 6.0F, 9.0F, 3.0F, 0.0F, 3.0F, -4.0F);
                addPair(root, "ent_root_flare", 7.0F, 4.0F, 10.0F, 4.0F, 24.0F, 0.0F);
            }
            case MANDRAKE -> {
                addPart(root, "mandrake_leaf_knot", 6.0F, 3.0F, 6.0F, 0.0F, 7.0F, 0.0F);
                addPart(root, "mandrake_center_leaf", 3.0F, 11.0F, 4.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.1F);
                addPair(root, "mandrake_leaf_fan", 3.0F, 10.0F, 4.0F, 4.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.55F);
                addPair(root, "mandrake_outer_leaf", 3.0F, 9.0F, 4.0F, 7.0F, 4.0F, 1.0F, 0.0F, 0.0F, 0.8F);
                addPart(root, "mandrake_mouth", 4.0F, 5.0F, 2.0F, 0.0F, 13.0F, -4.0F);
                addPair(root, "mandrake_root_arm", 3.0F, 9.0F, 3.0F, 5.0F, 16.0F, 0.0F, 0.0F, 0.0F, 0.55F);
                addPair(root, "mandrake_root_toe", 3.0F, 3.0F, 7.0F, 2.5F, 23.0F, -2.0F, 0.3F, 0.0F, 0.0F);
            }
            case DREAMROOT -> {
                addPart(root, "dreamroot_stem", 9.0F, 20.0F, 8.0F, 0.0F, 6.0F, 0.0F);
                addPart(root, "dream_bulb", 10.0F, 10.0F, 10.0F, 0.0F, -14.0F, 0.0F);
                addPair(root, "dream_petals", 8.0F, 3.0F, 8.0F, 7.0F, -11.0F, 0.0F, 0.0F, 0.0F, 0.4F);
                addPair(root, "outer_dream_petals", 8.0F, 3.0F, 8.0F, 10.0F, -15.0F, 1.0F, 0.0F, 0.0F, 0.65F);
                addPair(root, "dream_crown_spire", 2.0F, 10.0F, 2.0F, 4.0F, -21.0F, 0.0F, 0.0F, 0.0F, 0.25F);
                addPair(root, "trailing_root", 2.0F, 16.0F, 2.0F, 5.0F, 20.0F, 0.0F, 0.25F, 0.0F, 0.25F);
                addPair(root, "outer_trailing_root", 2.0F, 13.0F, 2.0F, 9.0F, 20.0F, 2.0F, -0.2F, 0.0F, 0.4F);
                addPair(root, "root_fan", 2.0F, 11.0F, 2.0F, 12.0F, 22.0F, -1.0F, 0.2F, 0.0F, 0.65F);
            }
            case BRAMBLE_COLOSSUS -> {
                addPart(root, "bramble_core_mass", 17.0F, 14.0F, 11.0F, 0.0F, 7.0F, 0.0F);
                addPair(root, "bramble_pauldrons", 11.0F, 11.0F, 11.0F, 11.0F, 2.0F, 0.0F);
                addPair(root, "bramble_spike", 3.0F, 10.0F, 3.0F, 7.0F, -8.0F, 1.0F, -0.4F, 0.0F, 0.35F);
                addPart(root, "bramble_face", 7.0F, 8.0F, 2.0F, 0.0F, -3.0F, -5.0F);
                addPair(root, "bramble_hook_claw", 6.0F, 6.0F, 8.0F, 10.0F, 17.0F, -2.0F, 0.3F, 0.0F, 0.25F);
                addPair(root, "bramble_root_foot", 8.0F, 5.0F, 11.0F, 4.0F, 25.0F, -1.0F);
            }
            case THORNED_PURSUER -> {
                addPair(root, "pursuer_antler", 2.0F, 15.0F, 2.0F, 5.0F, -15.0F, 0.0F, 0.0F, 0.0F, 0.45F);
                addPair(root, "pursuer_antler_branch", 2.0F, 9.0F, 2.0F, 9.0F, -20.0F, 0.0F, 0.0F, 0.0F, 0.75F);
                addPart(root, "pursuer_branch_frame", 13.0F, 7.0F, 8.0F, 0.0F, 2.0F, 1.0F);
                addPair(root, "pursuer_leaf_mantle", 7.0F, 8.0F, 7.0F, 8.0F, 3.0F, 1.0F);
                addPart(root, "bramble_mask", 7.0F, 8.0F, 2.0F, 0.0F, -4.0F, -5.0F);
                addPair(root, "vine_whip", 2.0F, 19.0F, 2.0F, 10.0F, 10.0F, 0.0F, 0.2F, 0.0F, 0.5F);
                addPair(root, "pursuer_root_foot", 5.0F, 4.0F, 9.0F, 3.0F, 24.0F, -2.0F);
            }
            case HOBGOBLIN -> {
                addGoblinClothes(root, true);
            }
            case GOBLIN -> {
                addGoblinClothes(root, false);
            }
            case STONEBROKER -> {
                addMinerClothes(root, 1.0F, false, false);
                addPair(root, "geode_pauldron", 6.0F, 6.0F, 6.0F, 7.0F, 1.0F, 0.0F);
                addPart(root, "ledger_pouch", 4.0F, 6.0F, 2.0F, 4.0F, 10.0F, -3.5F);
            }
            case FORGEWARDEN -> {
                addMinerClothes(root, 1.0F, false, false);
                addPair(root, "forge_plate", 7.0F, 7.0F, 7.0F, 8.0F, 1.0F, 0.0F);
                addPart(root, "furnace_mask", 8.0F, 8.0F, 3.0F, 0.0F, -4.0F, -5.0F);
                addPart(root, "forge_tank", 8.0F, 11.0F, 5.0F, 0.0F, 6.0F, 5.0F);
            }
            case ILLUSION_CREEPER -> addFloatingPlates(root, 0.0F, 8.0F);
            case ILLUSION_SPIDER -> {
                addFloatingPlates(root, 0.0F, 13.0F);
                addPair(root, "ghost_leg", 10.0F, 1.0F, 1.0F, 9.0F, 12.0F, 4.0F, 0.0F, 0.35F, 0.4F);
            }
            case ILLUSION_ZOMBIE -> {
                addPart(root, "split_mask", 4.0F, 8.0F, 1.0F, 2.0F, -4.0F, -4.5F);
                addPart(root, "afterimage_torso", 8.0F, 11.0F, 4.0F, 3.0F, 5.0F, 4.5F, 0.0F, 0.15F, 0.08F);
                addPair(root, "afterimage_arm", 3.0F, 12.0F, 3.0F, 7.0F, 6.0F, 4.0F, 0.0F, 0.1F, 0.2F);
            }
            case IMP -> {
                addPair(root, "imp_ear", 5.0F, 2.0F, 3.0F, 5.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.35F);
                addPair(root, "imp_horn_base", 2.5F, 5.0F, 2.5F, 2.5F, 0.0F, 0.0F, -0.28F, 0.0F, 0.42F);
                addPair(root, "imp_horn_tip", 1.5F, 4.0F, 1.5F, 4.0F, -3.0F, 0.0F, -0.12F, 0.0F, 0.64F);
                addPart(root, "imp_tail_tip", 4.0F, 4.0F, 2.0F, 0.0F, 23.0F, 8.0F, 0.0F, 0.0F, 0.78F);
            }
            case STORM_SIMIAN -> {
                addPair(root, "storm_brow", 4.0F, 2.0F, 3.0F, 3.0F, 4.0F, -4.0F, 0.0F, 0.0F, 0.25F);
                addPair(root, "storm_ear", 3.0F, 4.0F, 2.0F, 4.5F, 7.0F, -1.0F);
                addPart(root, "storm_chest", 5.0F, 6.0F, 1.0F, 0.0F, 12.0F, -3.5F);
                addPair(root, "cloud_bracer", 4.0F, 3.0F, 5.0F, 5.5F, 18.0F, 0.0F);
            }
        }
    }

    private static void addHood(final PartDefinition root, final float radius, final float centerY) {
        addPart(root, "hood_top", radius * 2.0F, 2.0F, radius * 2.0F, 0.0F, centerY - radius, 0.0F);
        addPair(root, "hood_side", 2.0F, radius * 2.0F, radius * 2.0F, radius - 1.0F, centerY, 0.0F);
    }

    private static void addRobe(final PartDefinition root, final float width, final float height, final float depth) {
        addPart(root, "layered_robe", width, height, depth, 0.0F, 13.0F, 0.0F);
        addPart(root, "robe_hem", width + 2.0F, 2.0F, depth + 1.0F, 0.0F, 17.0F, 0.0F);
    }

    private static void addStaff(
        final PartDefinition root,
        final String name,
        final float x,
        final float y,
        final float z
    ) {
        addPart(root, name, 2.0F, 20.0F, 2.0F, x, y, z, 0.0F, 0.0F, -0.08F);
        addPart(root, name + "_focus", 5.0F, 5.0F, 5.0F, x + 1.5F, y - 10.0F, z, 0.0F, 0.0F, 0.78F);
    }

    private static void addCrossbow(final PartDefinition root) {
        addPart(root, "silver_crossbow_stock", 2.0F, 14.0F, 2.0F, 0.0F, 6.0F, 4.5F, 0.0F, 0.0F, 0.55F);
        addPart(root, "silver_crossbow_bow", 14.0F, 2.0F, 2.0F, 0.0F, 3.0F, 4.5F, 0.0F, 0.0F, -0.3F);
    }

    private static void addRuneOrbit(final PartDefinition root) {
        addPart(root, "sigil_outer_slash", 2.0F, 15.0F, 2.0F, 0.0F, 10.0F, -1.0F, 0.0F, 0.0F, 0.78F);
        addPart(root, "sigil_outer_backslash", 2.0F, 15.0F, 2.0F, 0.0F, 10.0F, -1.0F, 0.0F, 0.0F, -0.78F);
        addPart(root, "upper_right_tablet", 3.0F, 7.0F, 2.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.15F);
        addPart(root, "upper_left_tablet", 3.0F, 7.0F, 2.0F, -8.0F, 1.0F, 0.0F, 0.0F, 0.0F, -0.15F);
        addPart(root, "lower_right_tablet", 3.0F, 7.0F, 2.0F, 9.0F, 19.0F, 0.0F, 0.0F, 0.0F, -0.12F);
        addPart(root, "lower_left_tablet", 3.0F, 7.0F, 2.0F, -9.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.12F);
    }

    private static void addDebris(final PartDefinition root) {
        addPart(root, "debris_top", 5.0F, 4.0F, 5.0F, 0.0F, 1.0F, 0.0F, 0.2F, 0.15F, 0.3F);
        addPair(root, "debris_side", 4.0F, 6.0F, 4.0F, 13.0F, 12.0F, 0.0F, -0.15F, 0.2F, 0.25F);
        addPair(root, "debris_low", 3.0F, 3.0F, 3.0F, 7.0F, 24.0F, 1.0F, 0.3F, 0.0F, 0.4F);
        addPart(root, "debris_back", 6.0F, 3.0F, 5.0F, -2.0F, 17.0F, 7.0F, 0.3F, -0.2F, 0.2F);
        addPart(root, "debris_front", 3.0F, 5.0F, 3.0F, 3.0F, 4.0F, -7.0F, -0.25F, 0.15F, -0.3F);
    }

    private static void addHorns(
        final PartDefinition root,
        final float distance,
        final float centerY,
        final float length
    ) {
        addPair(root, "swept_horn", 3.0F, length, 3.0F, distance, centerY, 0.0F, -0.25F, 0.0F, 0.35F);
        addPair(root, "horn_tip", 2.0F, length * 0.65F, 2.0F, distance + 2.0F, centerY - length * 0.65F, 0.0F, 0.0F, 0.0F, 0.55F);
    }

    private static void addScythe(final PartDefinition root) {
        addPart(root, "scythe_staff", 3.0F, 31.0F, 3.0F, -12.0F, 7.0F, -5.0F, 0.0F, 0.0F, -0.1F);
        addTexturedPart(root, "scythe_blade", 3, 3, 17.0F, 3.0F, 5.0F,
            -6.0F, -8.0F, -5.0F, 0.0F, 0.2F, -0.35F);
        addTexturedPart(root, "scythe_hook", 4, 4, 3.0F, 10.0F, 4.0F,
            2.0F, -11.0F, -5.0F, 0.0F, 0.0F, 0.45F);
        addTexturedPart(root, "scythe_inner_edge", 5, 5, 10.0F, 2.0F, 3.0F,
            -1.0F, -5.0F, -5.0F, 0.0F, 0.1F, -0.55F);
    }

    private static void addMinerClothes(
        final PartDefinition root,
        final float yOffset,
        final boolean pickHarness,
        final boolean satchel
    ) {
        addPart(root, "miner_helmet", 9.0F, 3.0F, 9.0F, 0.0F, -8.0F + yOffset, 0.0F);
        addPart(root, "helmet_lamp", 3.0F, 3.0F, 2.0F, 0.0F, -8.0F + yOffset, -5.0F);
        addPart(root, "leather_apron", 7.0F, 11.0F, 1.0F, 0.0F, 6.0F + yOffset, -3.0F);
        if (pickHarness) {
            addPart(root, "pick_harness", 2.0F, 16.0F, 2.0F, 0.0F, 6.0F, 4.0F, 0.0F, 0.0F, 0.65F);
        }
        if (satchel) {
            addPart(root, "ore_satchel", 6.0F, 7.0F, 4.0F, 4.5F, 8.0F, 2.5F);
        }
    }

    private static void addGoblinClothes(final PartDefinition root, final boolean hobgoblin) {
        addPart(root, "miner_cap", 9.0F, 2.0F, 8.0F, 0.0F, 4.5F, 0.0F);
        addPart(root, "cap_lamp", 2.0F, 2.0F, 2.0F, 0.0F, 5.0F, -4.25F);
        addPart(root, "work_vest", 9.0F, 7.0F, 1.0F, 0.0F, 14.0F, -3.25F);
        addPart(root, "tool_belt", 10.0F, 2.0F, 6.5F, 0.0F, 18.5F, 0.0F);
        addPart(root, hobgoblin ? "prospector_satchel" : "ore_satchel", 4.0F, 5.0F, 3.0F, 4.5F, 16.5F, 2.5F);
    }

    private static void addFloatingPlates(final PartDefinition root, final float x, final float y) {
        addPart(root, "prism_plate_top", 7.0F, 2.0F, 7.0F, x + 1.5F, y - 9.0F, 1.0F, 0.1F, 0.2F, 0.0F);
        addPair(root, "prism_plate_side", 2.0F, 7.0F, 6.0F, 5.5F, y, 0.0F, 0.0F, 0.2F, 0.1F);
        addPart(root, "prism_plate_low", 7.0F, 2.0F, 5.0F, x - 1.0F, y + 10.0F, -1.0F, 0.1F, 0.0F, 0.2F);
    }

    private static void addPair(
        final PartDefinition root,
        final String name,
        final float width,
        final float height,
        final float depth,
        final float x,
        final float y,
        final float z
    ) {
        addPair(root, name, width, height, depth, x, y, z, 0.0F, 0.0F, 0.0F);
    }

    private static void addPair(
        final PartDefinition root,
        final String name,
        final float width,
        final float height,
        final float depth,
        final float x,
        final float y,
        final float z,
        final float xRot,
        final float yRot,
        final float zRot
    ) {
        addPart(root, "right_" + name, width, height, depth, -x, y, z, xRot, -yRot, -zRot);
        addPart(root, "left_" + name, width, height, depth, x, y, z, xRot, yRot, zRot);
    }

    private static void addPart(
        final PartDefinition root,
        final String name,
        final float width,
        final float height,
        final float depth,
        final float x,
        final float y,
        final float z
    ) {
        addPart(root, name, width, height, depth, x, y, z, 0.0F, 0.0F, 0.0F);
    }

    private static void addPart(
        final PartDefinition root,
        final String name,
        final float width,
        final float height,
        final float depth,
        final float x,
        final float y,
        final float z,
        final float xRot,
        final float yRot,
        final float zRot
    ) {
        root.addOrReplaceChild(
            name,
            CubeListBuilder.create().texOffs(32, 32).addBox(
                -width / 2.0F,
                -height / 2.0F,
                -depth / 2.0F,
                width,
                height,
                depth
            ),
            PartPose.offsetAndRotation(x, y, z, xRot, yRot, zRot)
        );
    }

    private static void addTexturedPart(
        final PartDefinition root,
        final String name,
        final int textureX,
        final int textureY,
        final float width,
        final float height,
        final float depth,
        final float x,
        final float y,
        final float z,
        final float xRot,
        final float yRot,
        final float zRot
    ) {
        root.addOrReplaceChild(
            name,
            CubeListBuilder.create().texOffs(textureX, textureY).addBox(
                -width / 2.0F,
                -height / 2.0F,
                -depth / 2.0F,
                width,
                height,
                depth
            ),
            PartPose.offsetAndRotation(x, y, z, xRot, yRot, zRot)
        );
    }
}
