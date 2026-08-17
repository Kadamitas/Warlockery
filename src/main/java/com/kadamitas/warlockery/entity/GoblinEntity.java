package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModSounds;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.world.GoblinEnclaveData;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
 * The dedicated exact {@code warlockery:goblin} body. It is a narrow merchant, not a human
 * Villager: no Brain, sensors, memories, schedules, POI claims, gossip, golem support, native raid
 * activities, Hero gifts, human-Villager breeding, Witch conversion, Zombie-Villager conversion, or
 * implicit no-distance despawn reaches this class.
 *
 * <p>Its executor set is intentionally minimal - float, an attack-only melee commit, player look,
 * and a look-only random look - and not one of those goals declares {@code MOVE}. Ordinary
 * navigation authority belongs entirely to {@link GoblinEnclaveRuntime}.</p>
 *
 * <p>Public identity is unchanged: registry ID, displayed name, category, dimensions, attributes,
 * renderer, model, texture, sound set, loot table, spawn egg, and trade catalog all stay exactly as
 * registered. Complete fall immunity is deliberately <em>not</em> reimplemented.</p>
 */
public final class GoblinEntity extends AbstractGoblinMerchantEntity {
    public static final String STATE_KEY = "WarlockeryGoblinEnclave";
    private static final String LEGACY_PROSPECTING_KEY = "WarlockeryProspectingCooldown";
    private static final String LEGACY_GIFT_KEY = "WarlockeryNextFlowerGift";
    private static final String ASSAULT_CENTER_KEY = "WarlockeryGoblinRaidCenter";
    private static final String ASSAULT_WAVE_KEY = "WarlockeryGoblinRaidWave";
    private static final String ASSAULT_LEADER_KEY = "WarlockeryGoblinRaidLeader";

    private final CreatureBehavior contractBehavior = CreatureBehaviorFactory.create(CreatureKind.GOBLIN);
    private final GoblinEnclaveRuntime.Counters enclaveCounters = new GoblinEnclaveRuntime.Counters();
    private final GoblinEnclaveRuntime.TransientState enclaveTransient =
        new GoblinEnclaveRuntime.TransientState();
    private GoblinEnclaveState enclaveState = GoblinEnclaveState.empty();
    /** Set only while the shared contract binding runs; never persisted, never read elsewhere. */
    private transient boolean suppressContractPersistenceLatch;
    private @Nullable BlockPos assaultCenter;
    private int assaultWave;
    private boolean assaultLeader;

    public GoblinEntity(final EntityType<? extends AbstractVillager> type, final Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.GOBLIN;
    }

    @Override
    protected String speciesTranslationKey() {
        return "goblin";
    }

    @Override
    protected ModSounds.CreatureSoundSet soundSet() {
        return ModSounds.GOBLIN;
    }

    // ---------------------------------------------------------------- semantic state

    public GoblinEnclaveState goblinEnclaveState() {
        return enclaveState;
    }

    public void setGoblinEnclaveState(final GoblinEnclaveState state) {
        enclaveState = state == null ? GoblinEnclaveState.empty() : state;
        setGoblinProfession(enclaveState.profession());
    }

    public GoblinEnclaveRuntime.Counters goblinCounters() {
        return enclaveCounters;
    }

    public GoblinEnclaveRuntime.TransientState goblinTransient() {
        return enclaveTransient;
    }

    @Override
    public void setGoblinProfession(final GoblinProfession profession) {
        super.setGoblinProfession(profession);
        if (enclaveState.profession() != goblinProfession()) {
            enclaveState = enclaveState.withProfession(goblinProfession());
        }
    }

    // ---------------------------------------------------------------- merchant hooks

    @Override
    public int merchantLevel() {
        return enclaveState.merchant().level();
    }

    @Override
    public int getVillagerXp() {
        return enclaveState.merchant().xp();
    }

    @Override
    protected void awardMerchantXp(final int xp) {
        enclaveState = enclaveState.withMerchant(
            enclaveState.merchant().withXp(enclaveState.merchant().xp() + Math.max(0, xp))
        );
    }

    @Override
    protected boolean safeToTrade() {
        return GoblinEnclaveRuntime.safeToTrade(this);
    }

    @Override
    public boolean canRestock() {
        return true;
    }

    // ---------------------------------------------------------------- executors

    /**
     * Four executors, none of which declares {@code MOVE}. The vanilla {@link RandomLookAroundGoal}
     * declares MOVE and LOOK, so it is redeclared LOOK-only, and melee is committed by a dedicated
     * attack-only goal that never creates or moves a path.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AttackOnlyMeleeGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new LookOnlyRandomLookGoal(this));
    }

    /** Exposed for the live identity fixture: F10 registers zero target-selector goals. */
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
     * Commits melee only. It declares LOOK, never MOVE, and never touches navigation: approach is
     * the runtime's job. The target is revalidated at windup and again at commit so a stale or
     * newly protected target receives no hit and no rider effect.
     */
    private static final class AttackOnlyMeleeGoal extends Goal {
        private static final int COOLDOWN_TICKS = 20;
        private final GoblinEntity goblin;
        private int cooldown;

        private AttackOnlyMeleeGoal(final GoblinEntity goblin) {
            this.goblin = goblin;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            final LivingEntity target = goblin.getTarget();
            return target != null && target.isAlive() && goblin.canAttack(target);
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
            final LivingEntity target = goblin.getTarget();
            if (target == null || !(goblin.level() instanceof ServerLevel level)) {
                return;
            }
            goblin.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            if (!goblin.isWithinMeleeAttackRange(target)) {
                return;
            }
            // Second revalidation immediately before the commit.
            if (!target.isAlive() || !goblin.canAttack(target)) {
                goblin.setTarget(null);
                return;
            }
            cooldown = COOLDOWN_TICKS;
            goblin.swing(InteractionHand.MAIN_HAND);
            goblin.doHurtTarget(level, target);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        GoblinEnclaveRuntime.tick(this, level);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason spawnReason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        // setGoblinProfession refreshes the displayed name unconditionally, so the one-in-four
            // roll that lands back on the PROSPECTOR default is still named.
            setGoblinProfession(GoblinProfession.values()[random.nextInt(GoblinProfession.values().length)]);
        // The registry-owned 3.0/24.0 attribute baseline is exact; the generic Mob random
        // follow-range spawn bonus would make every exact-attribute assertion nondeterministic.
        final AttributeInstance followRange = getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        return result;
    }

    /**
     * Night, low light, away from a human village, and locally capped. The bounded local count uses
     * the entity's own already-loaded section query and never touches an unloaded chunk.
     */
    public static boolean checkNaturalSpawnRules(
        final EntityType<GoblinEntity> type,
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
        return GoblinEnclaveRules.canSpawnNaturally(
            serverLevel.isDarkOutside(),
            serverLevel.getMaxLocalRawBrightness(position),
            serverLevel.isVillage(position) ? 0 : GoblinEnclaveRules.MIN_HUMAN_VILLAGE_DISTANCE,
            GoblinEnclaveRuntime.countLoadedGoblinsNear(serverLevel, position)
        );
    }

    /**
     * Unanchored wild Goblins use ordinary hostile despawn. Anchored residents, contracted Goblins,
     * and active assault members are the only exceptions, and each is an explicit recorded reason.
     */
    @Override
    public boolean removeWhenFarAway(final double distanceSquared) {
        return GoblinEnclaveRules.mayDespawn(
            enclaveState.anchor().present(),
            enclaveState.patron().bound(),
            isAssaultMember()
        );
    }

    /**
     * Persistence is the vanilla latch <em>plus</em> the three explicit F10 reasons.
     *
     * <p>{@code Mob.checkDespawn} short-circuits on {@code isPersistenceRequired()} before it ever
     * consults a derived predicate, so the explicit reasons have to be visible here or an anchored
     * resident would despawn. The vanilla latch is deliberately still honoured: every other
     * {@code setPersistenceRequired()} site in 26.2 is a real player or system intent that a Goblin
     * must respect exactly like any other mob - {@code NameTagItem}, the equipment-slot container
     * behind {@code /item replace entity} and hoppers, {@code EquipmentDispenseItemBehavior}, and
     * {@code GameTestEntityBuilder}, which is what keeps a GameTest-spawned mob alive for the length
     * of its own test.</p>
     *
     * <p>Only one latch write is suppressed, and it is suppressed at the source rather than here:
     * see {@link #setPersistenceRequired()}.</p>
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
     * every ex-patron permanently persistent - the 1.4 defect this family exists to remove. F10
     * already owns patron persistence through {@link GoblinEnclaveRules.PersistenceReason#CONTRACTED},
     * so the binding call is redundant as well as unclearable. The suppression flag is set only
     * around that single call, so a name tag, a dispenser, a command, or the GameTest entity builder
     * all still latch normally.</p>
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

    /**
     * Departure is wired to death and to every removal reason, so a dead, discarded, or unloaded
     * Goblin stops counting against its enclave. Without this, membership is monotonic: population
     * inflates forever, breeding blocks permanently once eight Goblins have ever joined, replacement
     * residents can never anchor, and the record never expires.
     */
    @Override
    public void remove(final RemovalReason reason) {
        departEnclave(reason);
        super.remove(reason);
    }

    private void departEnclave(final RemovalReason reason) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        enclaveState.anchor().enclaveKey().ifPresent(key -> {
            final GoblinEnclaveData data = GoblinEnclaveData.get(level);
            if (reason.shouldDestroy()) {
                // Death or discard: the seat is free immediately.
                data.leaveEnclave(key, getUUID());
                return;
            }
            // Unload or dimension change: the lease is released now and the membership entry is
            // left to age out on its own expiry, so a returning resident keeps its seat.
            data.releaseClaimsOf(key, getUUID());
        });
    }

    public Optional<GoblinEnclaveRules.PersistenceReason> persistenceReason() {
        return GoblinEnclaveRules.persistenceReason(
            enclaveState.anchor().present(),
            enclaveState.patron().bound(),
            isAssaultMember()
        );
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
        if (!(partner instanceof GoblinEntity other)) {
            return null;
        }
        final Entity offspring = getType().create(level, EntitySpawnReason.BREEDING);
        if (!(offspring instanceof GoblinEntity child)) {
            return null;
        }
        child.setGoblinProfession(GoblinEnclaveRules.childProfession(
            getUUID(), goblinProfession(), other.getUUID(), other.goblinProfession()
        ));
        enclaveState.anchor().enclaveKey().ifPresent(key -> enclaveState.anchor().position()
            .ifPresent(position -> enclaveState.anchor().dimension().ifPresent(dimension ->
                child.setGoblinEnclaveState(child.goblinEnclaveState()
                    .withAnchor(GoblinEnclaveState.Anchor.at(key, position, dimension))))));
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
            GoblinEnclaveRuntime.onContractAccepted(this, player);
            return contractResult;
        }
        final ItemStack supplied = player.getItemInHand(hand);
        if (supplied.is(WarlockeryTags.Items.HOBGOBLIN_MINING_TOOLS)
            && level() instanceof ServerLevel serverLevel) {
            return GoblinEnclaveRuntime.equipMiningTool(this, serverLevel, player, supplied);
        }
        final InteractionResult result = super.mobInteract(player, hand);
        if (isTrading()) {
            GoblinEnclaveRuntime.onTradeOpened(this);
        }
        return result;
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return GoblinEnclaveRuntime.canAttack(this, target) && contractBehavior.canAttack(this, target);
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
            GoblinEnclaveRuntime.onAcceptedDamage(this, level, source);
            contractBehavior.afterHurt(this, level, source, amount);
        }
        return hurt;
    }

    @Override
    public boolean wantsToPickUp(final ServerLevel level, final ItemStack stack) {
        if (isAssaultMember() || isTrading()) {
            return false;
        }
        return stack.is(ItemTags.DIRT)
            || stack.is(ItemTags.LOGS)
            || stack.is(net.minecraftforge.common.Tags.Items.NATURAL_LOGS)
            || CreatureBehaviorState.owner(this).isPresent()
            && stack.is(CreatureBehaviorTags.Items.HOBGOBLIN_COLLECTIBLES);
    }

    // ---------------------------------------------------------------- assault markers

    public void joinVillageAssault(final BlockPos center, final int wave, final boolean leader) {
        assaultCenter = center.immutable();
        assaultWave = wave;
        assaultLeader = leader;
        GoblinEnclaveRuntime.onAssaultJoined(this);
    }

    /** Releases every assault marker, and persistence itself when no other reason remains. */
    public void leaveVillageAssault() {
        assaultCenter = null;
        assaultWave = 0;
        assaultLeader = false;
        GoblinEnclaveRuntime.onAssaultLeft(this);
    }

    public Optional<BlockPos> assaultCenter() {
        return Optional.ofNullable(assaultCenter);
    }

    public int assaultWave() {
        return assaultWave;
    }

    public boolean isAssaultLeader() {
        return assaultLeader;
    }

    public boolean isAssaultMember() {
        return assaultCenter != null && assaultWave > 0;
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, enclaveState.write());
        if (assaultCenter != null) {
            output.putLong(ASSAULT_CENTER_KEY, assaultCenter.asLong());
            output.putInt(ASSAULT_WAVE_KEY, assaultWave);
            output.putBoolean(ASSAULT_LEADER_KEY, assaultLeader);
        }
    }

    /**
     * Reads the new versioned compound when present, otherwise migrates a 1.4 Goblin conservatively
     * from its old custom profession, Villager XP, and child-gift deadline. Deserialization never
     * merges enclaves by proximity, creates a child, edits a block, paths, trades, attacks, or emits
     * feedback.
     */
    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final String dimension = level().dimension().identifier().toString();
        enclaveState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> GoblinEnclaveState.read(tag, dimension))
            .orElseGet(() -> GoblinEnclaveState.migrateLegacy(
                input.getStringOr(PROFESSION_KEY, GoblinProfession.FALLBACK.id()),
                input.getIntOr("Xp", 0),
                Math.max(0L, input.getLongOr(LEGACY_GIFT_KEY, 0L)),
                level().getGameTime(),
                legacyOwner()
            ));
        // The 1.4 prospecting cooldown is deliberately read and dropped: mining cadence is now a
        // bounded runtime scratch counter, so a stale saved cooldown can never delay live work.
        input.getIntOr(LEGACY_PROSPECTING_KEY, 0);
        setGoblinProfession(enclaveState.profession());
        final long encodedCenter = input.getLongOr(ASSAULT_CENTER_KEY, Long.MIN_VALUE);
        assaultCenter = encodedCenter == Long.MIN_VALUE ? null : BlockPos.of(encodedCenter);
        assaultWave = input.getIntOr(ASSAULT_WAVE_KEY, 0);
        assaultLeader = input.getBooleanOr(ASSAULT_LEADER_KEY, false);
        enclaveTransient.resetForLoad();
    }

    private Optional<UUID> legacyOwner() {
        return CreatureBehaviorState.owner(this);
    }

    void equipToolSlot(final ItemStack tool) {
        setItemSlotAndDropWhenKilled(EquipmentSlot.MAINHAND, tool);
    }
}
