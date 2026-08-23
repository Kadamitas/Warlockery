package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.NaamahCourtRules.Action;
import com.kadamitas.warlockery.entity.NaamahCourtRules.Phase;
import com.kadamitas.warlockery.entity.NaamahEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** A dedicated tall court-goddess model for Naamah. */
public final class NaamahModel extends EntityModel<NaamahModel.State> {
    public static final int TEXTURE_WIDTH = 192;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart oceanMatriarch;
    private final ModelPart tideBodice;
    private final ModelPart regalHead;
    private final ModelPart authoredFace;
    private final ModelPart rightRubyEye;
    private final ModelPart leftRubyEye;
    private final ModelPart threeCrestCrown;
    private final ModelPart centerTideCrest;
    private final ModelPart rightTideCrest;
    private final ModelPart leftTideCrest;
    private final ModelPart architecturalHair;
    private final ModelPart rightSideLock;
    private final ModelPart leftSideLock;
    private final ModelPart backHairMantle;
    private final ModelPart crimsonInnerFin;
    private final ModelPart tideRibs;
    private final ModelPart throatJewel;
    private final ModelPart chestJewel;
    private final ModelPart rightWaveCrest;
    private final ModelPart leftWaveCrest;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightBellSleeve;
    private final ModelPart leftBellSleeve;
    private final ModelPart lowerGoddess;
    private final ModelPart splitGown;
    private final ModelPart rightFrontSkirt;
    private final ModelPart leftFrontSkirt;
    private final ModelPart oxbloodCenterPanel;
    private final ModelPart rearTidalMantle;
    private final ModelPart rightRearWave;
    private final ModelPart leftRearWave;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public NaamahModel(final ModelPart root) {
        super(root);
        oceanMatriarch = root.getChild("ocean_matriarch");
        tideBodice = oceanMatriarch.getChild("tide_bodice");
        regalHead = tideBodice.getChild("regal_head");
        authoredFace = regalHead.getChild("authored_face");
        rightRubyEye = authoredFace.getChild("right_ruby_eye");
        leftRubyEye = authoredFace.getChild("left_ruby_eye");
        threeCrestCrown = regalHead.getChild("three_crest_crown");
        centerTideCrest = threeCrestCrown.getChild("center_tide_crest");
        rightTideCrest = threeCrestCrown.getChild("right_tide_crest");
        leftTideCrest = threeCrestCrown.getChild("left_tide_crest");
        architecturalHair = regalHead.getChild("architectural_hair");
        rightSideLock = architecturalHair.getChild("right_side_lock");
        leftSideLock = architecturalHair.getChild("left_side_lock");
        backHairMantle = architecturalHair.getChild("back_hair_mantle");
        crimsonInnerFin = backHairMantle.getChild("crimson_inner_fin");
        tideRibs = tideBodice.getChild("tide_ribs");
        throatJewel = tideBodice.getChild("throat_jewel");
        chestJewel = tideBodice.getChild("chest_jewel");
        rightWaveCrest = tideBodice.getChild("right_wave_crest");
        leftWaveCrest = tideBodice.getChild("left_wave_crest");
        rightArm = tideBodice.getChild("right_arm");
        leftArm = tideBodice.getChild("left_arm");
        rightBellSleeve = rightArm.getChild("right_bell_sleeve");
        leftBellSleeve = leftArm.getChild("left_bell_sleeve");
        lowerGoddess = oceanMatriarch.getChild("lower_goddess");
        splitGown = lowerGoddess.getChild("split_gown");
        rightFrontSkirt = splitGown.getChild("right_front_skirt");
        leftFrontSkirt = splitGown.getChild("left_front_skirt");
        oxbloodCenterPanel = splitGown.getChild("oxblood_center_panel");
        rearTidalMantle = splitGown.getChild("rear_tidal_mantle");
        rightRearWave = rearTidalMantle.getChild("right_rear_wave");
        leftRearWave = rearTidalMantle.getChild("left_rear_wave");
        rightLeg = lowerGoddess.getChild("right_leg");
        leftLeg = lowerGoddess.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition matriarch = root.addOrReplaceChild(
            "ocean_matriarch",
            CubeListBuilder.create(),
            PartPose.ZERO
        );

        final PartDefinition bodice = matriarch.addOrReplaceChild(
            "tide_bodice",
            CubeListBuilder.create()
                .texOffs(0, 64).addBox(-4.0F, -5.5F, -2.5F, 8.0F, 11.0F, 5.0F)
                .texOffs(28, 64).addBox(-3.5F, 5.0F, -2.0F, 7.0F, 5.0F, 4.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        bodice.addOrReplaceChild(
            "tide_ribs",
            CubeListBuilder.create()
                .texOffs(52, 64).addBox(-3.5F, -4.4F, -3.0F, 1.0F, 9.0F, 1.0F)
                .texOffs(56, 64).addBox(2.5F, -4.4F, -3.0F, 1.0F, 9.0F, 1.0F)
                .texOffs(60, 64).addBox(-2.7F, -3.4F, -3.2F, 5.4F, 1.0F, 1.0F)
                .texOffs(60, 68).addBox(-2.7F, 2.1F, -3.2F, 5.4F, 1.0F, 1.0F),
            PartPose.ZERO
        );
        bodice.addOrReplaceChild(
            "throat_jewel",
            CubeListBuilder.create()
                .texOffs(74, 48).addBox(-1.0F, -1.0F, -0.7F, 2.0F, 2.0F, 1.4F)
                .texOffs(82, 48).addBox(-0.5F, -1.8F, -0.5F, 1.0F, 1.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -5.2F, -2.8F, 0.08F, 0.0F, 0.0F)
        );
        bodice.addOrReplaceChild(
            "chest_jewel",
            CubeListBuilder.create()
                .texOffs(88, 48).addBox(-1.3F, -1.3F, -0.8F, 2.6F, 2.6F, 1.6F)
                .texOffs(100, 48).addBox(-0.6F, -2.0F, -0.5F, 1.2F, 1.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -0.7F, -2.7F, 0.0F, 0.0F, 0.7853982F)
        );

        final PartDefinition head = bodice.addOrReplaceChild(
            "regal_head",
            CubeListBuilder.create(),
            PartPose.offset(0.0F, -5.5F, 0.0F)
        );
        final PartDefinition face = head.addOrReplaceChild(
            "authored_face",
            CubeListBuilder.create()
                .texOffs(0, 32).addBox(-3.5F, -7.0F, -3.2F, 7.0F, 7.0F, 6.4F)
                .texOffs(26, 32).addBox(-2.5F, -1.6F, -3.6F, 5.0F, 1.4F, 1.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        face.addOrReplaceChild(
            "right_ruby_eye",
            CubeListBuilder.create().texOffs(44, 32).addBox(-1.0F, -0.5F, -0.4F, 2.0F, 1.0F, 0.8F),
            PartPose.offsetAndRotation(-1.45F, -3.8F, -3.25F, 0.0F, 0.0F, 0.08F)
        );
        face.addOrReplaceChild(
            "left_ruby_eye",
            CubeListBuilder.create().texOffs(44, 32).mirror().addBox(-1.0F, -0.5F, -0.4F, 2.0F, 1.0F, 0.8F),
            PartPose.offsetAndRotation(1.45F, -3.8F, -3.25F, 0.0F, 0.0F, -0.08F)
        );
        face.addOrReplaceChild(
            "burgundy_lips",
            CubeListBuilder.create().texOffs(52, 32).addBox(-1.5F, -0.45F, -0.35F, 3.0F, 0.9F, 0.7F),
            PartPose.offset(0.0F, -1.45F, -3.42F)
        );
        face.addOrReplaceChild(
            "right_gold_earring",
            CubeListBuilder.create().texOffs(64, 32).addBox(-0.6F, -0.6F, -0.6F, 1.2F, 1.2F, 1.2F),
            PartPose.offsetAndRotation(-3.75F, -2.0F, 0.0F, 0.0F, 0.0F, 0.7853982F)
        );
        face.addOrReplaceChild(
            "left_gold_earring",
            CubeListBuilder.create().texOffs(64, 32).mirror().addBox(-0.6F, -0.6F, -0.6F, 1.2F, 1.2F, 1.2F),
            PartPose.offsetAndRotation(3.75F, -2.0F, 0.0F, 0.0F, 0.0F, 0.7853982F)
        );

        final PartDefinition crown = head.addOrReplaceChild(
            "three_crest_crown",
            CubeListBuilder.create()
                .texOffs(0, 48).addBox(-4.5F, -1.5F, -3.6F, 9.0F, 2.0F, 7.2F)
                .texOffs(30, 48).addBox(-1.0F, -2.3F, -4.0F, 2.0F, 1.5F, 1.0F),
            PartPose.offset(0.0F, -6.2F, 0.0F)
        );
        crown.addOrReplaceChild(
            "center_tide_crest",
            CubeListBuilder.create()
                .texOffs(38, 48).addBox(-1.1F, -9.5F, -1.0F, 2.2F, 9.5F, 2.0F)
                .texOffs(48, 48).addBox(-0.8F, -11.0F, -0.8F, 1.6F, 2.2F, 1.6F),
            PartPose.offsetAndRotation(0.0F, -0.8F, 0.0F, -0.1F, 0.0F, 0.0F)
        );
        crown.addOrReplaceChild(
            "right_tide_crest",
            CubeListBuilder.create()
                .texOffs(58, 48).addBox(-1.0F, -8.0F, -0.9F, 2.0F, 8.0F, 1.8F)
                .texOffs(68, 48).addBox(-0.7F, -9.4F, -0.7F, 1.4F, 2.0F, 1.4F),
            PartPose.offsetAndRotation(-2.7F, -0.8F, 0.2F, -0.08F, 0.0F, -0.16F)
        );
        crown.addOrReplaceChild(
            "left_tide_crest",
            CubeListBuilder.create().texOffs(58, 48).mirror()
                .addBox(-1.0F, -8.0F, -0.9F, 2.0F, 8.0F, 1.8F)
                .texOffs(68, 48).addBox(-0.7F, -9.4F, -0.7F, 1.4F, 2.0F, 1.4F),
            PartPose.offsetAndRotation(2.7F, -0.8F, 0.2F, -0.08F, 0.0F, 0.16F)
        );

        final PartDefinition hair = head.addOrReplaceChild(
            "architectural_hair",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.6F, -7.8F, -3.0F, 9.2F, 4.0F, 7.5F)
                .texOffs(36, 0).addBox(-4.9F, -5.0F, 2.2F, 9.8F, 6.0F, 2.8F),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        hair.addOrReplaceChild(
            "right_side_lock",
            CubeListBuilder.create()
                .texOffs(60, 0).addBox(-3.4F, 0.0F, -2.2F, 3.4F, 17.0F, 3.2F)
                .texOffs(74, 0).addBox(-2.8F, 16.0F, -1.8F, 2.8F, 5.0F, 2.6F),
            PartPose.offsetAndRotation(-3.5F, -4.5F, -0.4F, 0.04F, 0.04F, 0.03F)
        );
        hair.addOrReplaceChild(
            "left_side_lock",
            CubeListBuilder.create().texOffs(60, 0).mirror()
                .addBox(0.0F, 0.0F, -2.2F, 3.4F, 17.0F, 3.2F)
                .texOffs(74, 0).addBox(0.0F, 16.0F, -1.8F, 2.8F, 5.0F, 2.6F),
            PartPose.offsetAndRotation(3.5F, -4.5F, -0.4F, 0.04F, -0.04F, -0.03F)
        );
        final PartDefinition backHair = hair.addOrReplaceChild(
            "back_hair_mantle",
            CubeListBuilder.create()
                .texOffs(86, 0).addBox(-6.0F, 0.0F, -1.5F, 12.0F, 20.0F, 3.0F)
                .texOffs(118, 0).addBox(-4.8F, 19.0F, -1.2F, 9.6F, 6.0F, 2.4F)
                .texOffs(144, 0).addBox(-3.4F, 24.0F, -0.9F, 6.8F, 4.0F, 1.8F),
            PartPose.offsetAndRotation(0.0F, -4.3F, 4.0F, 0.12F, 0.0F, 0.0F)
        );
        hair.addOrReplaceChild(
            "asymmetric_tide_clasp",
            CubeListBuilder.create()
                .texOffs(142, 48).addBox(-3.0F, -2.0F, -0.8F, 3.0F, 4.0F, 1.6F)
                .texOffs(154, 48).addBox(-5.4F, -0.7F, -0.5F, 4.0F, 1.4F, 1.0F),
            PartPose.offsetAndRotation(-4.8F, -2.8F, 3.8F, 0.08F, -0.1F, -0.22F)
        );
        backHair.addOrReplaceChild(
            "crimson_inner_fin",
            CubeListBuilder.create()
                .texOffs(160, 0).addBox(-4.8F, 0.0F, -0.7F, 9.6F, 16.0F, 1.4F)
                .texOffs(160, 18).addBox(-3.2F, 15.0F, -0.5F, 6.4F, 7.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 4.0F, -1.7F, 0.02F, 0.0F, 0.0F)
        );

        bodice.addOrReplaceChild(
            "right_wave_crest",
            CubeListBuilder.create()
                .texOffs(106, 48).addBox(-6.0F, -1.2F, -2.0F, 6.0F, 2.4F, 4.0F)
                .texOffs(128, 48).addBox(-7.8F, -0.8F, -1.4F, 3.0F, 1.6F, 2.8F),
            PartPose.offsetAndRotation(-3.4F, -4.0F, 0.1F, 0.0F, -0.1F, -0.24F)
        );
        bodice.addOrReplaceChild(
            "left_wave_crest",
            CubeListBuilder.create().texOffs(106, 48).mirror()
                .addBox(0.0F, -1.2F, -2.0F, 6.0F, 2.4F, 4.0F)
                .texOffs(128, 48).addBox(4.8F, -0.8F, -1.4F, 3.0F, 1.6F, 2.8F),
            PartPose.offsetAndRotation(3.4F, -4.0F, 0.1F, 0.0F, 0.1F, 0.24F)
        );

        final PartDefinition rightArm = bodice.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create()
                .texOffs(0, 76).addBox(-3.0F, -1.0F, -2.0F, 3.0F, 10.0F, 4.0F)
                .texOffs(16, 76).addBox(-2.6F, 8.0F, -1.7F, 2.6F, 4.0F, 3.4F),
            PartPose.offsetAndRotation(-4.3F, -3.4F, -0.5F, 0.12F, 0.0F, 0.08F)
        );
        rightArm.addOrReplaceChild(
            "right_hand",
            CubeListBuilder.create().texOffs(32, 76).addBox(-2.2F, 0.0F, -1.5F, 2.2F, 3.0F, 3.0F),
            PartPose.offset(0.0F, 11.0F, 0.0F)
        );
        final PartDefinition rightSleeve = rightArm.addOrReplaceChild(
            "right_bell_sleeve",
            CubeListBuilder.create()
                .texOffs(44, 76).addBox(-4.8F, 0.0F, -3.1F, 5.0F, 10.0F, 6.2F)
                .texOffs(68, 76).addBox(-5.6F, 8.0F, -3.7F, 6.0F, 5.0F, 7.4F),
            PartPose.offsetAndRotation(-0.2F, 2.0F, 0.0F, 0.05F, 0.0F, 0.02F)
        );
        rightSleeve.addOrReplaceChild(
            "right_foam_hem",
            CubeListBuilder.create().texOffs(96, 76).addBox(-6.2F, -1.0F, -4.1F, 6.6F, 2.0F, 8.2F),
            PartPose.offsetAndRotation(0.0F, 12.5F, 0.0F, 0.0F, 0.0F, -0.04F)
        );

        final PartDefinition leftArm = bodice.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(0, 76).mirror()
                .addBox(0.0F, -1.0F, -2.0F, 3.0F, 10.0F, 4.0F)
                .texOffs(16, 76).addBox(0.0F, 8.0F, -1.7F, 2.6F, 4.0F, 3.4F),
            PartPose.offsetAndRotation(4.3F, -3.4F, 0.5F, -0.12F, 0.0F, -0.08F)
        );
        leftArm.addOrReplaceChild(
            "left_hand",
            CubeListBuilder.create().texOffs(32, 76).mirror().addBox(0.0F, 0.0F, -1.5F, 2.2F, 3.0F, 3.0F),
            PartPose.offset(0.0F, 11.0F, 0.0F)
        );
        final PartDefinition leftSleeve = leftArm.addOrReplaceChild(
            "left_bell_sleeve",
            CubeListBuilder.create().texOffs(44, 76).mirror()
                .addBox(-0.2F, 0.0F, -3.1F, 5.0F, 10.0F, 6.2F)
                .texOffs(68, 76).addBox(-0.4F, 8.0F, -3.7F, 6.0F, 5.0F, 7.4F),
            PartPose.offsetAndRotation(0.2F, 2.0F, 0.0F, 0.05F, 0.0F, -0.02F)
        );
        leftSleeve.addOrReplaceChild(
            "left_foam_hem",
            CubeListBuilder.create().texOffs(96, 76).mirror().addBox(-0.4F, -1.0F, -4.1F, 6.6F, 2.0F, 8.2F),
            PartPose.offsetAndRotation(0.0F, 12.5F, 0.0F, 0.0F, 0.0F, 0.04F)
        );

        final PartDefinition lower = matriarch.addOrReplaceChild(
            "lower_goddess",
            CubeListBuilder.create()
                .texOffs(0, 94).addBox(-4.2F, -1.0F, -3.0F, 8.4F, 4.0F, 6.0F)
                .texOffs(30, 94).addBox(-3.7F, 2.0F, -2.5F, 7.4F, 3.0F, 5.0F),
            PartPose.offset(0.0F, 9.0F, 0.0F)
        );
        final PartDefinition gown = lower.addOrReplaceChild(
            "split_gown",
            CubeListBuilder.create().texOffs(56, 94).addBox(-5.0F, 0.0F, -3.2F, 10.0F, 3.0F, 6.4F),
            PartPose.offset(0.0F, 0.3F, 0.0F)
        );
        gown.addOrReplaceChild(
            "right_front_skirt",
            CubeListBuilder.create()
                .texOffs(88, 88).addBox(-4.8F, 0.0F, -1.5F, 4.8F, 11.0F, 3.0F)
                .texOffs(108, 88).addBox(-4.0F, 10.0F, -1.2F, 4.0F, 3.5F, 2.4F),
            PartPose.offsetAndRotation(-0.2F, 1.0F, -2.1F, -0.04F, 0.0F, 0.08F)
        );
        gown.addOrReplaceChild(
            "left_front_skirt",
            CubeListBuilder.create().texOffs(88, 88).mirror()
                .addBox(0.0F, 0.0F, -1.5F, 4.8F, 11.0F, 3.0F)
                .texOffs(108, 88).addBox(0.0F, 10.0F, -1.2F, 4.0F, 3.5F, 2.4F),
            PartPose.offsetAndRotation(0.2F, 1.0F, -2.1F, -0.04F, 0.0F, -0.08F)
        );
        gown.addOrReplaceChild(
            "oxblood_center_panel",
            CubeListBuilder.create()
                .texOffs(126, 88).addBox(-2.0F, 0.0F, -0.8F, 4.0F, 12.0F, 1.6F)
                .texOffs(140, 88).addBox(-1.2F, 11.0F, -0.6F, 2.4F, 3.0F, 1.2F),
            PartPose.offsetAndRotation(0.0F, 0.6F, -3.2F, -0.04F, 0.0F, 0.0F)
        );
        final PartDefinition rearMantle = gown.addOrReplaceChild(
            "rear_tidal_mantle",
            CubeListBuilder.create()
                .texOffs(0, 108).addBox(-6.0F, 0.0F, -1.2F, 12.0F, 11.0F, 2.4F)
                .texOffs(34, 108).addBox(-4.5F, 10.0F, -0.9F, 9.0F, 4.0F, 1.8F),
            PartPose.offsetAndRotation(0.0F, 0.7F, 5.2F, 0.12F, 0.0F, 0.0F)
        );
        rearMantle.addOrReplaceChild(
            "right_rear_wave",
            CubeListBuilder.create()
                .texOffs(58, 108).addBox(-6.5F, -1.0F, -0.9F, 6.5F, 7.0F, 1.8F)
                .texOffs(76, 108).addBox(-8.4F, 4.0F, -0.6F, 3.0F, 4.0F, 1.2F),
            PartPose.offsetAndRotation(-3.8F, 2.0F, 0.0F, 0.0F, 0.28F, -0.18F)
        );
        rearMantle.addOrReplaceChild(
            "left_rear_wave",
            CubeListBuilder.create().texOffs(58, 108).mirror()
                .addBox(0.0F, -1.0F, -0.9F, 6.5F, 7.0F, 1.8F)
                .texOffs(76, 108).addBox(5.4F, 4.0F, -0.6F, 3.0F, 4.0F, 1.2F),
            PartPose.offsetAndRotation(3.8F, 2.0F, 0.0F, 0.0F, -0.28F, 0.18F)
        );

        final PartDefinition rightLeg = lower.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(92, 108).addBox(-2.4F, 0.0F, -2.2F, 3.8F, 12.0F, 4.4F),
            PartPose.offset(-1.7F, 3.0F, 0.0F)
        );
        rightLeg.addOrReplaceChild(
            "right_thigh_boot",
            CubeListBuilder.create()
                .texOffs(112, 108).addBox(-2.6F, 0.0F, -2.4F, 4.2F, 8.0F, 4.8F)
                .texOffs(134, 108).addBox(-2.7F, 6.8F, -3.2F, 4.4F, 2.2F, 5.6F),
            PartPose.offset(0.0F, 3.0F, 0.0F)
        );
        final PartDefinition leftLeg = lower.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(92, 108).mirror().addBox(-1.4F, 0.0F, -2.2F, 3.8F, 12.0F, 4.4F),
            PartPose.offset(1.7F, 3.0F, 0.0F)
        );
        leftLeg.addOrReplaceChild(
            "left_thigh_boot",
            CubeListBuilder.create().texOffs(112, 108).mirror()
                .addBox(-1.6F, 0.0F, -2.4F, 4.2F, 8.0F, 4.8F)
                .texOffs(134, 108).addBox(-1.7F, 6.8F, -3.2F, 4.4F, 2.2F, 5.6F),
            PartPose.offset(0.0F, 3.0F, 0.0F)
        );

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        regalHead.yRot += state.yRot * Mth.DEG_TO_RAD * 0.72F;
        regalHead.xRot += state.xRot * Mth.DEG_TO_RAD * 0.82F;

        final float time = state.ageInTicks;
        final float composure = Mth.sin(time * 0.055F);
        final float secondary = Mth.sin(time * 0.043F + 1.2F);
        rightSideLock.zRot += composure * 0.035F;
        leftSideLock.zRot -= composure * 0.035F;
        rightSideLock.xRot += secondary * 0.025F;
        leftSideLock.xRot -= secondary * 0.025F;
        backHairMantle.xRot += composure * 0.025F;
        crimsonInnerFin.xRot -= secondary * 0.035F;
        rightBellSleeve.zRot -= composure * 0.025F;
        leftBellSleeve.zRot += composure * 0.025F;
        rearTidalMantle.xRot += secondary * 0.028F;
        rightRearWave.zRot -= composure * 0.025F;
        leftRearWave.zRot += composure * 0.025F;

        final float pace = state.walkAnimationPos * 0.55F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F) * 0.34F;
        rightLeg.xRot += Mth.cos(pace) * stride;
        leftLeg.xRot += Mth.cos(pace + Mth.PI) * stride;
        rightArm.xRot += Mth.cos(pace + Mth.PI) * stride * 0.38F;
        leftArm.xRot += Mth.cos(pace) * stride * 0.38F;
        rightFrontSkirt.xRot += Mth.cos(pace + Mth.PI) * stride * 0.15F;
        leftFrontSkirt.xRot += Mth.cos(pace) * stride * 0.15F;
        oxbloodCenterPanel.xRot += Mth.sin(pace) * stride * 0.08F;

        if (state.seaBorne) {
            oceanMatriarch.y -= 0.35F + composure * 0.18F;
            rearTidalMantle.xRot -= 0.08F;
            rightRearWave.yRot -= 0.08F;
            leftRearWave.yRot += 0.08F;
        }

        final float wave = Mth.clamp(state.courtWaveProgress, 0.0F, 1.0F);
        rightArm.xRot -= wave * 1.08F;
        leftArm.xRot -= wave * 1.08F;
        rightArm.yRot -= wave * 0.42F;
        leftArm.yRot += wave * 0.42F;
        rightArm.zRot -= wave * 1.0F;
        leftArm.zRot += wave * 1.0F;
        rightBellSleeve.zRot -= wave * 0.28F;
        leftBellSleeve.zRot += wave * 0.28F;
        rightWaveCrest.zRot -= wave * 0.32F;
        leftWaveCrest.zRot += wave * 0.32F;
        rearTidalMantle.xRot -= wave * 0.32F;
        rightRearWave.zRot -= wave * 0.5F;
        leftRearWave.zRot += wave * 0.5F;

        final float surge = Mth.clamp(state.drowningSurgeProgress, 0.0F, 1.0F);
        rightArm.xRot -= surge * 2.45F;
        rightArm.yRot -= surge * 0.2F;
        rightArm.zRot -= surge * 0.34F;
        leftArm.xRot += surge * 0.48F;
        leftArm.yRot += surge * 0.32F;
        leftArm.zRot += surge * 0.22F;
        oceanMatriarch.y -= surge * 0.9F;
        backHairMantle.xRot -= surge * 0.42F;
        crimsonInnerFin.xRot -= surge * 0.24F;
        rearTidalMantle.xRot -= surge * 0.58F;
        rightRearWave.zRot -= surge * 0.24F;
        leftRearWave.zRot += surge * 0.24F;
        centerTideCrest.xRot -= surge * 0.14F;
        rightTideCrest.zRot -= surge * 0.08F;
        leftTideCrest.zRot += surge * 0.08F;

        if (state.sovereignRefusal) {
            regalHead.xRot -= 0.08F;
            tideBodice.xRot -= 0.045F;
            rightWaveCrest.zRot -= 0.12F;
            leftWaveCrest.zRot += 0.12F;
            oxbloodCenterPanel.xRot -= 0.06F;
        }
        if (state.gazeMending) {
            final float pulse = 0.12F + (Mth.sin(time * 0.24F) + 1.0F) * 0.09F;
            throatJewel.z -= pulse * 0.45F;
            chestJewel.z -= pulse;
            rightRubyEye.z -= pulse * 0.35F;
            leftRubyEye.z -= pulse * 0.35F;
            tideRibs.xRot -= pulse * 0.12F;
        }
    }

    public static void extractRenderState(
        final NaamahEntity entity,
        final State state,
        final float partialTicks
    ) {
        final Action action = entity.presentationAction();
        final Phase phase = entity.presentationPhase();
        state.courtWaveProgress = action == Action.COURT_WAVE ? 1.0F : 0.0F;
        state.drowningSurgeProgress = action == Action.DROWNING_SURGE ? 1.0F : 0.0F;
        state.sovereignRefusal = phase == Phase.SOVEREIGN_REFUSAL;
        state.seaBorne = entity.isInWater();
        state.gazeMending = entity.presentationGazeMending();
    }

    public static final class State extends LivingEntityRenderState {
        public float courtWaveProgress;
        public float drowningSurgeProgress;
        public boolean sovereignRefusal;
        public boolean seaBorne;
        public boolean gazeMending;
    }
}
