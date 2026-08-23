package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModSounds;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.world.HobgoblinJourneyData;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The dedicated exact {@code warlockery:hobgoblin} body: a reciprocal night-road helper, not a human
 * Villager and not the F10 Goblin enclave actor. No Brain, sensor, memory, schedule, POI claim,
 * gossip, golem support, native raid activity, Hero gift, human-Villager breeding, Witch conversion,
 * Zombie-Villager conversion, or blanket fall immunity reaches this class.
 *
 * <p>Its executor set is intentionally minimal - float, an attack-only defensive melee commit,
 * player look, and a look-only random look - and not one of those goals declares {@code MOVE}.
 * Ordinary navigation authority belongs entirely to {@link HobgoblinJourneyRuntime}, and there is no
 * target-selector goal at all: a Hobgoblin only ever answers a direct aggressor.</p>
 *
 * <p>Public identity is unchanged: registry ID, displayed profession names, category, dimensions,
 * attributes, renderer, model, texture, sound set, loot table, spawn egg, four profession
 * identities, and the exact trade catalog all stay exactly as registered.</p>
 *
 * <p>Naming note: F11 introduced this body under the placeholder name {@code
 * HobgoblinTravelerEntity} only because the shared 1.4 {@code HobgoblinEntity} still served the F12
 * Stonebroker and Forgewarden patrons. F12 gave those patrons their own bodies, which left the
 * shared class constructed by no registry entry; it has since been deleted and this body has taken
 * back the plain name. {@code ModEntities.HOBGOBLIN} registers the exact public ID {@code
 * warlockery:hobgoblin} against this class.</p>
 */
public final class HobgoblinEntity extends AbstractGoblinMerchantEntity {
    public static final String STATE_KEY = "WarlockeryHobgoblinJourney";
    private static final String LEGACY_PROSPECTING_KEY = "WarlockeryProspectingCooldown";
    private static final String LEGACY_GIFT_KEY = "WarlockeryNextFlowerGift";
    private static final EntityDataAccessor<Byte> DATA_PRESENTATION_MODE =
        SynchedEntityData.defineId(HobgoblinEntity.class, EntityDataSerializers.BYTE);

    private final CreatureBehavior contractBehavior = CreatureBehaviorFactory.create(CreatureKind.HOBGOBLIN);
    private final HobgoblinJourneyRuntime.Counters journeyCounters = new HobgoblinJourneyRuntime.Counters();
    private final HobgoblinJourneyRuntime.TransientState journeyTransient =
        new HobgoblinJourneyRuntime.TransientState();
    private HobgoblinJourneyState journeyState = HobgoblinJourneyState.empty();
    /** Set only while the shared contract binding runs; never persisted, never read elsewhere. */
    private transient boolean suppressContractPersistenceLatch;

    public HobgoblinEntity(final EntityType<? extends AbstractVillager> type, final Level level) {
        super(type, level);
        // Restored Villager-supertype capability. Vanilla `Villager` enables door opening in its
        // own constructor; `AbstractVillager` does not, so the split silently took it away with no
        // compile error. Door opening is ordinary physical navigation, not part of the Brain, POI,
        // gossip, or schedule surface this family deliberately removed, and 1.4 goblinfolk had it.
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
        }
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PRESENTATION_MODE,
            EntityPresentationSync.encode(HobgoblinJourneyRules.Mode.IDLE));
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.HOBGOBLIN;
    }

    @Override
    protected String speciesTranslationKey() {
        return "hobgoblin";
    }

    @Override
    protected ModSounds.CreatureSoundSet soundSet() {
        return ModSounds.HOBGOBLIN;
    }

    // ---------------------------------------------------------------- semantic state

    public HobgoblinJourneyState journeyState() {
        return journeyState;
    }

    public void setJourneyState(final HobgoblinJourneyState state) {
        journeyState = state == null ? HobgoblinJourneyState.empty() : state;
        setGoblinProfession(journeyState.profession());
        syncPresentationFromRuntime();
    }

    public HobgoblinJourneyRules.Mode presentationMode() {
        return EntityPresentationSync.decode(entityData.get(DATA_PRESENTATION_MODE),
            HobgoblinJourneyRules.Mode.IDLE);
    }

    private void syncPresentationFromRuntime() {
        final byte mode = EntityPresentationSync.encode(journeyState.mode());
        if (entityData.get(DATA_PRESENTATION_MODE) != mode) {
            entityData.set(DATA_PRESENTATION_MODE, mode);
        }
    }

    public HobgoblinJourneyRuntime.Counters journeyCounters() {
        return journeyCounters;
    }

    public HobgoblinJourneyRuntime.TransientState journeyTransient() {
        return journeyTransient;
    }

    @Override
    public void setGoblinProfession(final GoblinProfession profession) {
        super.setGoblinProfession(profession);
        if (journeyState.profession() != goblinProfession()) {
            journeyState = journeyState.withProfession(goblinProfession());
        }
    }

    // ---------------------------------------------------------------- merchant hooks

    @Override
    public int merchantLevel() {
        return journeyState.merchant().level();
    }

    @Override
    public int getVillagerXp() {
        return journeyState.merchant().xp();
    }

    @Override
    protected void awardMerchantXp(final int xp) {
        journeyState = journeyState.withMerchant(
            journeyState.merchant().withXp(journeyState.merchant().xp() + Math.max(0, xp))
        );
    }

    @Override
    protected boolean safeToTrade() {
        return HobgoblinJourneyRuntime.safeToTrade(this);
    }

    @Override
    public boolean canRestock() {
        return true;
    }

    // ---------------------------------------------------------------- executors

    /**
     * Four executors, none of which declares {@code MOVE}. The vanilla {@link RandomLookAroundGoal}
     * declares MOVE and LOOK, so it is redeclared LOOK-only, and the defensive strike is committed
     * by a dedicated attack-only goal that never creates or moves a path.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new DefensiveStrikeGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new LookOnlyRandomLookGoal(this));
    }

    /** Exposed for the live identity fixture: F11 registers zero target-selector goals. */
    public int operationalTargetGoalCount() {
        return targetSelector.getAvailableGoals().size();
    }

    public List<String> operationalGoalNames() {
        return goalSelector.getAvailableGoals().stream()
            .map(goal -> goal.getGoal().getClass().getSimpleName())
            .toList();
    }

    private static final class LookOnlyRandomLookGoal extends RandomLookAroundGoal {
        private LookOnlyRandomLookGoal(final Mob mob) {
            super(mob);
            setFlags(EnumSet.of(Flag.LOOK));
        }
    }

    /**
     * Commits one ordinary melee attempt against a remembered direct aggressor, and only while the
     * runtime is actually in {@code DEFEND}. It declares LOOK, never MOVE, and never touches
     * navigation: approach is the runtime's job, and the target is revalidated at windup and again
     * at commit so a lapsed or newly protected aggressor receives no hit.
     */
    private static final class DefensiveStrikeGoal extends Goal {
        private static final int COOLDOWN_TICKS = 20;
        private final HobgoblinEntity traveler;
        private int cooldown;

        private DefensiveStrikeGoal(final HobgoblinEntity traveler) {
            this.traveler = traveler;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            final LivingEntity target = traveler.getTarget();
            return target != null
                && target.isAlive()
                && traveler.journeyState().mode() == HobgoblinJourneyRules.Mode.DEFEND
                && traveler.canAttack(target);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            cooldown = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            final LivingEntity target = traveler.getTarget();
            if (target == null || !(traveler.level() instanceof ServerLevel level)) {
                return;
            }
            traveler.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            if (!traveler.isWithinMeleeAttackRange(target)) {
                return;
            }
            // Second revalidation immediately before the commit.
            if (!target.isAlive() || !traveler.canAttack(target)) {
                traveler.setTarget(null);
                return;
            }
            cooldown = COOLDOWN_TICKS;
            traveler.swing(InteractionHand.MAIN_HAND);
            traveler.doHurtTarget(level, target);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        HobgoblinJourneyRuntime.tick(this, level);
        syncPresentationFromRuntime();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason spawnReason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        // setGoblinProfession refreshes the displayed name unconditionally for a name this body
        // owns, so the one-in-four roll that lands back on the PROSPECTOR default is still named.
        setGoblinProfession(GoblinProfession.values()[random.nextInt(GoblinProfession.values().length)]);
        // The registry-owned attribute baseline is exact; the generic Mob random follow-range spawn
        // bonus would make every exact-attribute assertion nondeterministic.
        final AttributeInstance followRange = getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        return result;
    }

    /**
     * Village exclusion is true at the spawn layer, not only at the flee layer: a natural traveler
     * refuses a village origin, refuses the configured exclusion buffer around an observed village,
     * and refuses to stack past the local cap. The bounded local count uses the entity's own
     * already-loaded section query and never touches an unloaded chunk.
     */
    public static boolean checkNaturalSpawnRules(
        final EntityType<HobgoblinEntity> type,
        final ServerLevelAccessor level,
        final EntitySpawnReason spawnReason,
        final BlockPos position,
        final RandomSource random
    ) {
        if (!Mob.checkMobSpawnRules(type, level, spawnReason, position, random)) {
            return false;
        }
        if (!EntitySpawnReason.isSpawner(spawnReason) && spawnReason != EntitySpawnReason.NATURAL) {
            return true;
        }
        final ServerLevel serverLevel = level.getLevel();
        return HobgoblinJourneyRules.canSpawnNaturally(
            serverLevel.isVillage(position),
            serverLevel.isVillage(position) ? 0 : HobgoblinJourneyRules.MIN_HUMAN_VILLAGE_DISTANCE,
            HobgoblinJourneyRuntime.countLoadedTravelersNear(serverLevel, position)
        );
    }

    /**
     * Unanchored solitary travelers use ordinary creature despawn. Caravan members, camp residents,
     * contracted workers, and active external-event residents are the only exceptions, and each one
     * is an explicit recorded reason rather than a permanent latch.
     */
    @Override
    public boolean removeWhenFarAway(final double distanceSquared) {
        return HobgoblinJourneyRules.mayDespawn(
            journeyState.caravan().present(),
            journeyState.camp().present(),
            journeyState.contract().active(),
            journeyTransient.eventResident()
        );
    }

    /**
     * Persistence is the vanilla latch <em>plus</em> the four explicit F11 reasons.
     *
     * <p>{@code Mob.checkDespawn} short-circuits on {@code isPersistenceRequired()} before it ever
     * consults a derived predicate, so the explicit reasons have to be visible here or a camp
     * resident would despawn. The vanilla latch is deliberately still honoured: every other
     * {@code setPersistenceRequired()} site is a real player or system intent that a Hobgoblin must
     * respect exactly like any other mob, including {@code GameTestEntityBuilder}, which is what
     * keeps a GameTest-spawned mob alive for the length of its own test.</p>
     */
    @Override
    public boolean isPersistenceRequired() {
        return super.isPersistenceRequired() || persistenceReason().isPresent();
    }

    /**
     * Suppresses exactly one latch write: the one the shared contract path makes.
     *
     * <p>{@code CreatureBehaviorRuntime.bindCompanion} ends in the one-way
     * {@code setPersistenceRequired()}, which has no clearing setter, so honouring it would make
     * every ex-contractor permanently persistent - the 1.4 defect this family exists to remove. F11
     * already owns contract persistence through
     * {@link HobgoblinJourneyRules.PersistenceReason#CONTRACTED}, so the binding call is redundant
     * as well as unclearable. The flag is set only around that single call, so a name tag, a
     * dispenser, a command, a hopper, and the GameTest entity builder all still latch normally.</p>
     */
    @Override
    public void setPersistenceRequired() {
        if (suppressContractPersistenceLatch) {
            return;
        }
        super.setPersistenceRequired();
    }

    @Override
    public boolean requiresCustomPersistence() {
        return persistenceReason().isPresent() || super.requiresCustomPersistence();
    }

    public Optional<HobgoblinJourneyRules.PersistenceReason> persistenceReason() {
        return HobgoblinJourneyRules.persistenceReason(
            journeyState.caravan().present(),
            journeyState.camp().present(),
            journeyState.contract().active(),
            journeyTransient.eventResident()
        );
    }

    /**
     * Departure is wired to death and to every removal reason, so a dead, discarded, or unloaded
     * traveler stops counting against its caravan. Without this, membership is monotonic:
     * population inflates forever, conception blocks permanently once four travelers have ever
     * joined, and the abandoned work claim is never released.
     */
    @Override
    public void remove(final RemovalReason reason) {
        departCaravan(reason);
        super.remove(reason);
    }

    private void departCaravan(final RemovalReason reason) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        journeyState.caravan().key().ifPresent(key -> {
            final HobgoblinJourneyData data = HobgoblinJourneyData.get(level);
            if (reason.shouldDestroy()) {
                // Death or discard: the seat is free immediately.
                data.leaveCaravan(key, getUUID());
                return;
            }
            // Unload or dimension change: the claim is released now and the membership entry is
            // left to age out on its own lease, so a returning traveler keeps its seat.
            data.releaseClaimsOf(getUUID());
        });
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
        if (!(partner instanceof HobgoblinEntity other)) {
            return null;
        }
        final Entity offspring = getType().create(level, EntitySpawnReason.BREEDING);
        if (!(offspring instanceof HobgoblinEntity child)) {
            return null;
        }
        child.setGoblinProfession(HobgoblinJourneyRules.childProfession(
            getUUID(), goblinProfession(), other.getUUID(), other.goblinProfession()
        ));
        journeyState.caravan().key().ifPresent(key ->
            child.setJourneyState(child.journeyState()
                .withCaravan(child.journeyState().caravan().withKey(key))));
        child.setTarget(null);
        return child;
    }

    // ---------------------------------------------------------------- interaction and combat

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final InteractionResult contractResult;
        suppressContractPersistenceLatch = true;
        try {
            contractResult = contractBehavior.interact(this, player, hand);
        } finally {
            suppressContractPersistenceLatch = false;
        }
        if (contractResult != InteractionResult.PASS) {
            HobgoblinJourneyRuntime.onContractAccepted(this, player);
            return contractResult;
        }
        final ItemStack supplied = player.getItemInHand(hand);
        if (supplied.is(WarlockeryTags.Items.HOBGOBLIN_MINING_TOOLS)
            && level() instanceof ServerLevel toolLevel) {
            return HobgoblinJourneyRuntime.equipMiningTool(this, toolLevel, player, supplied);
        }
        if (level() instanceof ServerLevel foodLevel
            && HobgoblinJourneyRuntime.offerHospitality(this, foodLevel, player, supplied)) {
            return InteractionResult.SUCCESS;
        }
        // Hospitality is reciprocal: a player this traveler holds a negative impression of is
        // refused a customer screen outright rather than silently traded with.
        if (!isBaby()
            && HobgoblinJourneyRules.tradeRefused(journeyState.relationScore(player.getUUID()))) {
            setUnhappyCounter(40);
            return InteractionResult.SUCCESS;
        }
        final InteractionResult result = super.mobInteract(player, hand);
        if (isTrading()) {
            HobgoblinJourneyRuntime.onTradeOpened(this, player);
        }
        return result;
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return HobgoblinJourneyRuntime.canAttack(this, target) && contractBehavior.canAttack(this, target);
    }

    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        final boolean hurt = PrimaryAttackModifier.withDamageBonus(
            this,
            contractBehavior.attackDamageBonus(this, level),
            () -> super.doHurtTarget(level, target)
        );
        if (hurt) {
            contractBehavior.afterAttack(this, level, target);
        }
        return hurt;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && amount > 0.0F) {
            HobgoblinJourneyRuntime.onAcceptedDamage(this, level, source);
            contractBehavior.afterHurt(this, level, source, amount);
        }
        return hurt;
    }

    @Override
    public boolean wantsToPickUp(final ServerLevel level, final ItemStack stack) {
        if (isTrading() || isBaby()) {
            return false;
        }
        return stack.is(CreatureBehaviorTags.Items.HOBGOBLIN_COLLECTIBLES)
            || stack.is(ItemTags.DIRT)
            || stack.is(ItemTags.LOGS)
            || stack.is(net.neoforged.neoforge.common.Tags.Items.NATURAL_LOGS);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, journeyState.write());
    }

    /**
     * Reads the new versioned compound when present, otherwise migrates a 1.4 Hobgoblin
     * conservatively from its old custom profession, Villager XP, child-gift deadline, and owner
     * UUID. Deserialization never merges caravans by proximity, creates a child, edits a block,
     * paths, trades, attacks, or emits feedback.
     */
    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final String dimension = level().dimension().identifier().toString();
        journeyState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> HobgoblinJourneyState.read(tag, dimension))
            .orElseGet(() -> HobgoblinJourneyState.migrateLegacy(
                input.getStringOr(PROFESSION_KEY, GoblinProfession.FALLBACK.id()),
                input.getIntOr("Xp", 0),
                Math.max(0L, input.getLongOr(LEGACY_GIFT_KEY, 0L)),
                level().getGameTime(),
                legacyOwner()
            ));
        // The 1.4 prospecting cooldown is deliberately read and dropped: mining cadence is now a
        // bounded runtime scratch counter, so a stale saved cooldown can never delay live work.
        input.getIntOr(LEGACY_PROSPECTING_KEY, 0);
        setGoblinProfession(journeyState.profession());
        journeyTransient.resetForLoad();
        syncPresentationFromRuntime();
    }

    private Optional<UUID> legacyOwner() {
        return CreatureBehaviorState.owner(this);
    }

    void equipToolSlot(final ItemStack tool) {
        setItemSlotAndDropWhenKilled(EquipmentSlot.MAINHAND, tool);
    }
}
