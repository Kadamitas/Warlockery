package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.client.model.*;
import com.kadamitas.warlockery.entity.*;
import com.kadamitas.warlockery.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** Explicit species-to-model routing. Geometry and animation remain owned by each model class. */
public final class DedicatedCreatureRenderers {
    private DedicatedCreatureRenderers() {
    }

    public static void registerAll() {
        register("abyssal_regent", context -> standard(
            context,
            ignored -> new AbyssalRegentModel(AbyssalRegentModel.createBodyLayer().bakeRoot()),
            AbyssalRegentModel.State::new,
            "abyssal_regent",
            AbyssalRegentModel::extractRenderState
        ));
        register("banshee", context -> standard(
            context,
            ignored -> new BansheeModel(BansheeModel.createBodyLayer().bakeRoot()),
            BansheeModel.State::new,
            "banshee",
            BansheeModel::extractRenderState
        ));
        register("blood_thrall", context -> standard(
            context,
            ignored -> new BloodThrallModel(BloodThrallModel.createBodyLayer().bakeRoot()),
            BloodThrallModel.State::new,
            "blood_thrall",
            BloodThrallModel::extractRenderState
        ));
        register("bramble_colossus", context -> standard(
            context,
            ignored -> new BrambleColossusModel(BrambleColossusModel.createBodyLayer().bakeRoot()),
            BrambleColossusModel.State::new,
            "bramble_colossus",
            BrambleColossusModel::extractRenderState
        ));
        register("circle_mage", context -> standard(
            context,
            ignored -> new CircleMageModel(CircleMageModel.createBodyLayer().bakeRoot()),
            CircleMageModel.State::new,
            "circle_mage",
            CircleMageModel::extractRenderState
        ));
        register("corpse", context -> standard(
            context,
            ignored -> new CorpseModel(CorpseModel.createBodyLayer().bakeRoot()),
            CorpseModel.State::new,
            "corpse",
            CorpseModel::extractRenderState
        ));
        register("death", context -> armed(
            context,
            ignored -> new DeathModel(DeathModel.createBodyLayer().bakeRoot()),
            DeathModel.State::new,
            "death",
            DeathModel::extractRenderState
        ));
        register("demon", context -> standard(
            context,
            ignored -> new DemonModel(DemonModel.createBodyLayer().bakeRoot()),
            DemonModel.State::new,
            "demon",
            DemonModel::extractRenderState
        ));
        register("dreamroot", context -> armed(
            context,
            ignored -> new DreamrootModel(DreamrootModel.createBodyLayer().bakeRoot()),
            DreamrootModel.State::new,
            "dreamroot",
            (DreamrootEntity entity, DreamrootModel.State state, float partialTicks) -> { }
        ));
        register("echo_shade", context -> standard(
            context,
            ignored -> new EchoShadeModel(EchoShadeModel.createBodyLayer().bakeRoot()),
            EchoShadeModel.State::new,
            "echo_shade",
            EchoShadeModel::extractRenderState
        ));
        register("eldritch_watcher", context -> standard(
            context,
            ignored -> new EldritchWatcherModel(EldritchWatcherModel.createBodyLayer().bakeRoot()),
            EldritchWatcherModel.State::new,
            "eldritch_watcher",
            EldritchWatcherModel::extractRenderState
        ));
        register("emberhorn_archfiend", context -> standard(
            context,
            ignored -> new EmberhornArchfiendModel(EmberhornArchfiendModel.createBodyLayer().bakeRoot()),
            EmberhornArchfiendModel.State::new,
            "emberhorn_archfiend",
            EmberhornArchfiendModel::extractRenderState
        ));
        register("ent", context -> ent(context));
        register("familiar_cat", context -> standard(
            context,
            ignored -> new FamiliarCatModel(FamiliarCatModel.createBodyLayer().bakeRoot()),
            FamiliarCatModel.State::new,
            "familiar_cat",
            FamiliarCatModel::extractRenderState
        ));
        register("feral_lycan", context -> standard(
            context,
            ignored -> new FeralLycanModel(FeralLycanModel.createBodyLayer().bakeRoot()),
            FeralLycanModel.State::new,
            "feral_lycan",
            FeralLycanModel::extractRenderState
        ));
        register("forgewarden", context -> armed(
            context,
            ignored -> new ForgewardenModel(ForgewardenModel.createBodyLayer().bakeRoot()),
            ForgewardenModel.State::new,
            "forgewarden",
            ForgewardenModel::extractRenderState
        ));
        register("goblin", context -> goblin(context));
        register("hedge_crone", context -> standard(
            context,
            ignored -> new HedgeCroneModel(HedgeCroneModel.createBodyLayer().bakeRoot()),
            HedgeCroneModel.State::new,
            "hedge_crone",
            HedgeCroneModel::extractRenderState
        ));
        register("hellhound", context -> standard(
            context,
            ignored -> new HellhoundModel(HellhoundModel.createBodyLayer().bakeRoot()),
            HellhoundModel.State::new,
            "hellhound",
            HellhoundModel::extractRenderState
        ));
        register("hex_bat", context -> standard(
            context,
            ignored -> new HexBatModel(HexBatModel.createBodyLayer().bakeRoot()),
            HexBatModel.State::new,
            "hex_bat",
            HexBatModel::extractRenderState
        ));
        register("hobgoblin", context -> hobgoblin(context));
        register("illusion_creeper", context -> standard(
            context,
            ignored -> new IllusionCreeperModel(IllusionCreeperModel.createBodyLayer().bakeRoot()),
            IllusionCreeperModel.State::new,
            "illusion_creeper",
            IllusionCreeperModel::extractRenderState
        ));
        register("illusion_spider", context -> standard(
            context,
            ignored -> new IllusionSpiderModel(IllusionSpiderModel.createBodyLayer().bakeRoot()),
            IllusionSpiderModel.State::new,
            "illusion_spider",
            IllusionSpiderModel::extractRenderState
        ));
        register("illusion_zombie", context -> standard(
            context,
            ignored -> new IllusionZombieModel(IllusionZombieModel.createBodyLayer().bakeRoot()),
            IllusionZombieModel.State::new,
            "illusion_zombie",
            IllusionZombieModel::extractRenderState
        ));
        register("imp", context -> armed(
            context,
            ignored -> new ImpModel(ImpModel.createBodyLayer().bakeRoot()),
            ImpModel.State::new,
            "imp",
            ImpModel::extractRenderState
        ));
        register("ironbound_sentinel", context -> standard(
            context,
            ignored -> new IronboundSentinelModel(IronboundSentinelModel.createBodyLayer().bakeRoot()),
            IronboundSentinelModel.State::new,
            "ironbound_sentinel",
            IronboundSentinelModel::extractRenderState
        ));
        register("lost_soul", context -> standard(
            context,
            ignored -> new LostSoulModel(LostSoulModel.createBodyLayer().bakeRoot()),
            LostSoulModel.State::new,
            "lost_soul",
            LostSoulModel::extractRenderState
        ));
        register("lycan_villager", DedicatedCreatureRenderers::lycanVillager);
        register("mandrake", context -> armed(
            context,
            ignored -> new MandrakeModel(MandrakeModel.createBodyLayer().bakeRoot()),
            MandrakeModel.State::new,
            "mandrake",
            (MandrakeEntity entity, MandrakeModel.State state, float partialTicks) -> { }
        ));
        register("naamah", context -> standard(
            context,
            ignored -> new NaamahModel(NaamahModel.createBodyLayer().bakeRoot()),
            NaamahModel.State::new,
            "naamah",
            NaamahModel::extractRenderState
        ));
        register("nightmare", context -> standard(
            context,
            ignored -> new NightmareModel(NightmareModel.createBodyLayer().bakeRoot()),
            NightmareModel.State::new,
            "nightmare",
            NightmareModel::extractRenderState
        ));
        register("owl", context -> standard(
            context,
            ignored -> new OwlModel(OwlModel.createBodyLayer().bakeRoot()),
            OwlModel.State::new,
            "owl",
            OwlModel::extractRenderState
        ));
        register("pale_steed", context -> standard(
            context,
            ignored -> new PaleSteedModel(PaleSteedModel.createBodyLayer().bakeRoot()),
            PaleSteedModel.State::new,
            "pale_steed",
            PaleSteedModel::extractRenderState
        ));
        register("parasytic_louse", context -> standard(
            context,
            ignored -> new ParasyticLouseModel(ParasyticLouseModel.createBodyLayer().bakeRoot()),
            ParasyticLouseModel.State::new,
            "parasytic_louse",
            ParasyticLouseModel::extractRenderState
        ));
        register("poltergeist", context -> standard(
            context,
            ignored -> new PoltergeistModel(PoltergeistModel.createBodyLayer().bakeRoot()),
            PoltergeistModel.State::new,
            "poltergeist",
            PoltergeistModel::extractRenderState
        ));
        register("spectral_familiar", context -> standard(
            context,
            ignored -> new SpectralFamiliarModel(SpectralFamiliarModel.createBodyLayer().bakeRoot()),
            SpectralFamiliarModel.State::new,
            "spectral_familiar",
            SpectralFamiliarModel::extractRenderState
        ));
        register("spectre", context -> standard(
            context,
            ignored -> new SpectreModel(SpectreModel.createBodyLayer().bakeRoot()),
            SpectreModel.State::new,
            "spectre",
            SpectreModel::extractRenderState
        ));
        register("spirit", context -> standard(
            context,
            ignored -> new SpiritModel(SpiritModel.createBodyLayer().bakeRoot()),
            SpiritModel.State::new,
            "spirit",
            SpiritModel::extractRenderState
        ));
        register("stonebroker", context -> armed(
            context,
            ignored -> new StonebrokerModel(StonebrokerModel.createBodyLayer().bakeRoot()),
            StonebrokerModel.State::new,
            "stonebroker",
            StonebrokerModel::extractRenderState
        ));
        register("storm_simian", context -> standard(
            context,
            ignored -> new StormSimianModel(StormSimianModel.createBodyLayer().bakeRoot()),
            StormSimianModel.State::new,
            "storm_simian",
            StormSimianModel::extractRenderState
        ));
        register("thorned_pursuer", context -> standard(
            context,
            ignored -> new ThornedPursuerModel(ThornedPursuerModel.createBodyLayer().bakeRoot()),
            ThornedPursuerModel.State::new,
            "thorned_pursuer",
            ThornedPursuerModel::extractRenderState
        ));
        register("toad", context -> standard(
            context,
            ignored -> new ToadModel(ToadModel.createBodyLayer().bakeRoot()),
            ToadModel.State::new,
            "toad",
            ToadModel::extractRenderState
        ));
        register("umbral_sigil", context -> standard(
            context,
            ignored -> new UmbralSigilModel(UmbralSigilModel.createBodyLayer().bakeRoot()),
            UmbralSigilModel.State::new,
            "umbral_sigil",
            UmbralSigilModel::extractRenderState
        ));
        register("vampire", context -> vampire(context));
        register("werewolf", DedicatedCreatureRenderers::werewolf);
        register("werewolf_hunter", context -> armed(
            context,
            ignored -> new WerewolfHunterModel(WerewolfHunterModel.createBodyLayer().bakeRoot()),
            WerewolfHunterModel.State::new,
            "werewolf_hunter",
            WerewolfHunterModel::extractRenderState
        ));
    }

    private static DedicatedCreatureRenderer<EntEntity, EntModel.State, EntModel> ent(
        final EntityRendererProvider.Context context
    ) {
        final EntModel model = new EntModel(EntModel.createBodyLayer().bakeRoot());
        final float displayScale = CreatureDisplayScale.factor("ent", model.root());
        return DedicatedCreatureRenderer.create(
            context,
            ignored -> model,
            EntModel.State::new,
            shadowRadius("ent"),
            state -> texture("ent"),
            EntModel::extractRenderState,
            state -> state.tint,
            (state, poseStack) -> scale(poseStack, displayScale),
            (state, baseRadius) -> baseRadius
        );
    }

    private static DedicatedCreatureRenderer<GoblinEntity, GoblinModel.State, GoblinModel> goblin(
        final EntityRendererProvider.Context context
    ) {
        final GoblinModel model = new GoblinModel(GoblinModel.createBodyLayer().bakeRoot());
        final float displayScale = CreatureDisplayScale.factor("goblin", model.root());
        return DedicatedCreatureRenderer.createWithItemLayer(
            context,
            ignored -> model,
            GoblinModel.State::new,
            shadowRadius("goblin"),
            state -> texture("goblin"),
            GoblinModel::extractRenderState,
            state -> -1,
            (state, poseStack) -> {
                scale(poseStack, displayScale);
                scaleBaby(state, poseStack);
            },
            DedicatedCreatureRenderers::babyShadow
        );
    }

    private static DedicatedCreatureRenderer<HobgoblinEntity, HobgoblinModel.State, HobgoblinModel> hobgoblin(
        final EntityRendererProvider.Context context
    ) {
        final HobgoblinModel model = new HobgoblinModel(HobgoblinModel.createBodyLayer().bakeRoot());
        final float displayScale = CreatureDisplayScale.factor("hobgoblin", model.root());
        return DedicatedCreatureRenderer.createWithItemLayer(
            context,
            ignored -> model,
            HobgoblinModel.State::new,
            shadowRadius("hobgoblin"),
            state -> texture("hobgoblin"),
            HobgoblinModel::extractRenderState,
            state -> -1,
            (state, poseStack) -> {
                scale(poseStack, displayScale);
                scaleBaby(state, poseStack);
            },
            DedicatedCreatureRenderers::babyShadow
        );
    }

    private static DedicatedCreatureRenderer<LycanVillagerEntity, LycanVillagerModel.State, LycanVillagerModel>
        lycanVillager(final EntityRendererProvider.Context context) {
        final DedicatedCreatureRenderer<LycanVillagerEntity, LycanVillagerModel.State, LycanVillagerModel> renderer =
            standard(
                context,
                ignored -> new LycanVillagerModel(LycanVillagerModel.createBodyLayer().bakeRoot()),
                LycanVillagerModel.State::new,
                "lycan_villager",
                LycanVillagerModel::extractRenderState
            );
        renderer.addPresentationLayer(new NativeVillagerClothingLayer<>(
            renderer,
            context.getResourceManager(),
            new LycanVillagerModel(LycanVillagerModel.createBodyLayer().bakeRoot()),
            new LycanVillagerModel(LycanVillagerModel.createBodyLayerNoHat().bakeRoot())
        ));
        return renderer;
    }

    private static DedicatedCreatureRenderer<VampireCourtEntity, VampireModel.State, VampireModel> vampire(
        final EntityRendererProvider.Context context
    ) {
        final VampireModel model = new VampireModel(VampireModel.createBodyLayer().bakeRoot());
        final float displayScale = CreatureDisplayScale.factor("vampire", model.root());
        return DedicatedCreatureRenderer.create(
            context,
            ignored -> model,
            VampireModel.State::new,
            shadowRadius("vampire"),
            state -> VampireModel.textureFor(state.variant),
            VampireModel::extractRenderState,
            state -> -1,
            (state, poseStack) -> scale(poseStack, displayScale),
            (state, baseRadius) -> baseRadius
        );
    }

    private static DedicatedCreatureRenderer<WerewolfEntity, WerewolfModel.State, WerewolfModel> werewolf(
        final EntityRendererProvider.Context context
    ) {
        final DedicatedCreatureRenderer<WerewolfEntity, WerewolfModel.State, WerewolfModel> renderer = standard(
            context,
            ignored -> new WerewolfModel(WerewolfModel.createBodyLayer().bakeRoot()),
            WerewolfModel.State::new,
            "werewolf",
            WerewolfModel::extractRenderState
        );
        renderer.addPresentationLayer(new NativeVillagerClothingLayer<>(
            renderer,
            context.getResourceManager(),
            new WerewolfVillagerClothingModel(
                WerewolfVillagerClothingModel.createBodyLayer(false).bakeRoot()
            ),
            new WerewolfVillagerClothingModel(
                WerewolfVillagerClothingModel.createBodyLayer(true).bakeRoot()
            )
        ));
        return renderer;
    }

    private static <
        T extends Mob,
        S extends LivingEntityRenderState,
        M extends EntityModel<? super S>
    > DedicatedCreatureRenderer<T, S, M> standard(
        final EntityRendererProvider.Context context,
        final DedicatedCreatureRenderer.ModelFactory<M> modelFactory,
        final DedicatedCreatureRenderer.StateFactory<S> stateFactory,
        final String id,
        final DedicatedCreatureRenderer.StateExtractor<T, S> stateExtractor
    ) {
        final M model = modelFactory.create(context);
        final float displayScale = CreatureDisplayScale.factor(id, model.root());
        return DedicatedCreatureRenderer.create(
            context,
            ignored -> model,
            stateFactory,
            shadowRadius(id),
            state -> texture(id),
            stateExtractor,
            state -> -1,
            (state, poseStack) -> scale(poseStack, displayScale),
            (state, baseRadius) -> baseRadius
        );
    }

    private static <
        T extends Mob,
        S extends ArmedEntityRenderState,
        M extends EntityModel<S> & ArmedModel<S>
    > DedicatedCreatureRenderer<T, S, M> armed(
        final EntityRendererProvider.Context context,
        final DedicatedCreatureRenderer.ModelFactory<M> modelFactory,
        final DedicatedCreatureRenderer.StateFactory<S> stateFactory,
        final String id,
        final DedicatedCreatureRenderer.StateExtractor<T, S> stateExtractor
    ) {
        final M model = modelFactory.create(context);
        final float displayScale = CreatureDisplayScale.factor(id, model.root());
        return DedicatedCreatureRenderer.createWithItemLayer(
            context,
            ignored -> model,
            stateFactory,
            shadowRadius(id),
            state -> texture(id),
            stateExtractor,
            state -> -1,
            (state, poseStack) -> scale(poseStack, displayScale),
            (state, baseRadius) -> baseRadius
        );
    }

    private static void scaleBaby(final LivingEntityRenderState state, final PoseStack poseStack) {
        if (state.isBaby) {
            poseStack.scale(
                GoblinLifecycleRules.BABY_RENDER_SCALE,
                GoblinLifecycleRules.BABY_RENDER_SCALE,
                GoblinLifecycleRules.BABY_RENDER_SCALE
            );
        }
    }

    private static void scale(final PoseStack poseStack, final float factor) {
        poseStack.scale(factor, factor, factor);
    }

    private static float babyShadow(final LivingEntityRenderState state, final float baseRadius) {
        return state.isBaby ? baseRadius * GoblinLifecycleRules.BABY_RENDER_SCALE : baseRadius;
    }

    private static float shadowRadius(final String id) {
        final CreatureVisualProfile visual = CreatureVisualProfile.forKind(ModEntities.kindFor(id));
        return Mth.clamp(visual.width() * 0.45F, 0.2F, 0.85F);
    }

    private static Identifier texture(final String id) {
        return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "textures/entity/" + id + ".png");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Mob> EntityType<T> type(final String id) {
        return (EntityType<T>) ModEntities.ALL.get(id).get();
    }

    private static <T extends Mob> void register(
        final String id,
        final EntityRendererProvider<T> provider
    ) {
        EntityRenderers.register(type(id), provider);
    }
}
