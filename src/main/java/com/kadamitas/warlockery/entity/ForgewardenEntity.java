package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModSounds;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The dedicated exact {@code warlockery:forgewarden} body: a bounded craft authority.
 *
 * <p>It is a narrow merchant, not a human Villager. No Brain, sensors, memories, schedules, POI
 * claims, gossip, golem support, native raid activities, Hero gifts, breeding, Witch conversion, or
 * Zombie-Villager conversion reaches this class. Its four executors declare LOOK only, and ordinary
 * navigation authority belongs entirely to {@link GoblinPatronRuntime}.</p>
 *
 * <p>The body is deliberately thin. Everything it shares with {@link StonebrokerEntity} lives in
 * {@link GoblinPatronRuntime} and {@link GoblinPatronState}; what remains here is what actually
 * makes a Forgewarden a Forgewarden: its kind, its red boss bar, its intrinsic fire and lava
 * immunity, its committed fire-melee doctrine through the shared scheduler, and its own catalog.</p>
 *
 * <p>Public identity is unchanged: registry ID, displayed name, category, dimensions, attributes,
 * renderer, model, texture, sound set, loot table, spawn egg, and ritual target all stay exactly as
 * registered.</p>
 */
public final class ForgewardenEntity extends AbstractGoblinMerchantEntity
    implements GoblinPatronRuntime.PatronBody {
    public static final String STATE_KEY = "WarlockeryGoblinPatron";
    private static final String LEGACY_EMPOWERMENT_KEY = "WarlockeryEmpowerment";
    private static final EntityDataAccessor<Byte> DATA_PRESENTATION_ACTION =
        SynchedEntityData.defineId(ForgewardenEntity.class, EntityDataSerializers.BYTE);

    private final CreatureBehavior contractBehavior =
        CreatureBehaviorFactory.create(CreatureKind.FORGEWARDEN);
    private final GoblinPatronRuntime.Core patronCore;

    public ForgewardenEntity(final EntityType<? extends AbstractVillager> type, final Level level) {
        super(type, level);
        this.xpReward = 30;
        this.patronCore = new GoblinPatronRuntime.Core(CreatureKind.FORGEWARDEN, new ServerBossEvent(
            getUUID(),
            Component.translatable("entity.warlockery.forgewarden"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.NOTCHED_6
        ));
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PRESENTATION_ACTION,
            EntityPresentationSync.encode(GoblinPatronRules.Action.IDLE));
    }

    // ---------------------------------------------------------------- identity

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.FORGEWARDEN;
    }

    @Override
    public CreatureKind patronKind() {
        return CreatureKind.FORGEWARDEN;
    }

    @Override
    public AbstractGoblinMerchantEntity body() {
        return this;
    }

    @Override
    public GoblinPatronRuntime.Core patronCore() {
        return patronCore;
    }

    @Override
    protected String speciesTranslationKey() {
        return "forgewarden";
    }

    @Override
    protected ModSounds.CreatureSoundSet soundSet() {
        return ModSounds.HOBGOBLIN;
    }

    /**
     * A patron has no Goblin profession and therefore no profession display name. The exact
     * registered {@code entity.warlockery.forgewarden} name is the public invariant, and writing a
     * {@code ...forgewarden.profession.*} key here would invent a localization key that does not
     * exist while overwriting a name a player may have assigned.
     */
    @Override
    protected void refreshDisplayName() {
        // Intentionally empty.
    }

    public GoblinPatronState goblinPatronState() {
        return patronCore.state();
    }

    public void setGoblinPatronState(final GoblinPatronState state) {
        patronCore.setState(state);
        syncPresentationFromRuntime();
    }

    public GoblinPatronRules.Action presentationAction() {
        return EntityPresentationSync.decode(entityData.get(DATA_PRESENTATION_ACTION),
            GoblinPatronRules.Action.IDLE);
    }

    private void syncPresentationFromRuntime() {
        final byte action = EntityPresentationSync.encode(patronCore.state().combat().action());
        if (entityData.get(DATA_PRESENTATION_ACTION) != action) {
            entityData.set(DATA_PRESENTATION_ACTION, action);
        }
    }

    public GoblinPatronRuntime.Counters patronCounters() {
        return patronCore.counters();
    }

    public GoblinPatronRuntime.TransientState patronTransient() {
        return patronCore.scratch();
    }

    // ---------------------------------------------------------------- merchant hooks

    @Override
    public int merchantLevel() {
        return patronCore.state().merchant().level();
    }

    @Override
    public int getVillagerXp() {
        return patronCore.state().merchant().xp();
    }

    @Override
    protected void awardMerchantXp(final int xp) {
        GoblinPatronRuntime.awardMerchantXp(this, xp);
    }

    @Override
    protected boolean safeToTrade() {
        return GoblinPatronRuntime.safeToTrade(this);
    }

    @Override
    public boolean canRestock() {
        return true;
    }

    /**
     * The patron catalog, seeded from identity, exact kind, level, and restock epoch. The shared
     * base seeds from the Goblin kind, which would make both patrons roll identical specialties.
     */
    @Override
    protected void updateTrades(final ServerLevel level) {
        final List<MerchantOffer> offers = GoblinPatronRuntime.createOffers(this);
        getOffers().addAll(offers);
    }

    // ---------------------------------------------------------------- executors and tick

    @Override
    protected void registerGoals() {
        GoblinPatronRuntime.registerPatronGoals(this, goalSelector);
    }

    /** Exposed for the live identity fixture: F12 registers zero target-selector goals. */
    public int operationalTargetGoalCount() {
        return targetSelector.getAvailableGoals().size();
    }

    public List<String> operationalGoalNames() {
        return goalSelector.getAvailableGoals().stream()
            .map(goal -> goal.getGoal().getClass().getSimpleName())
            .toList();
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        GoblinPatronRuntime.tick(this, level);
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
        // The registry-owned 400/11/8/0.32 attribute baseline is exact; the generic Mob random
        // follow-range spawn bonus would make every exact-attribute assertion nondeterministic.
        final AttributeInstance followRange = getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        return result;
    }

    /**
     * Intrinsic fire and lava immunity, for hazard classification and for damage. Drowning, contact
     * hazards, collision, and unsafe footing are still ordinary hazards.
     */
    @Override
    public boolean fireImmune() {
        return true;
    }

    // ---------------------------------------------------------------- interaction and combat

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        return GoblinPatronRuntime.mobInteract(
            this, contractBehavior, player, hand, () -> super.mobInteract(player, hand)
        );
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return GoblinPatronRuntime.canAttack(this, target)
            && contractBehavior.canAttack(this, target);
    }

    /**
     * The shared-challenger mark supplies at most a transient +4, and only against that exact
     * challenger. The fire and launch riders land only after the hit was actually accepted.
     */
    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        final boolean hurt = PrimaryAttackModifier.withDamageBonus(
            this,
            GoblinPatronRuntime.attackDamageBonus(this, level, target),
            () -> super.doHurtTarget(level, target)
        );
        if (hurt) {
            // GoblinPatronRuntime.afterAttack is the COMPLETE rider model for this body, so the
            // legacy CreatureBehaviorRuntime.afterAttack path is deliberately not called. For the
            // Forgewarden profile (HEART_EMPOWERMENT, FORGE_AURA, FIRE_MELEE) that method does
            // exactly two things: the FIRE_MELEE four-second ignite, which this path already applies
            // identically, and a second 0.45 launch gated only on any counterpart being within 16
            // blocks. Entity.push is additive, so calling both produced a 0.90 launch against a
            // designed 0.45 that nobody chose. Every other branch of the legacy method is inert for
            // this profile. StonebrokerEntity still calls it, and must keep calling it: that method
            // is a no-op for the Stonebroker profile today, and dropping it there would be an
            // unrelated change to a body that has no defect.
            GoblinPatronRuntime.afterAttack(this, target);
        }
        return hurt;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && amount > 0.0F) {
            GoblinPatronRuntime.onAcceptedDamage(this, level, source);
            contractBehavior.afterHurt(this, level, source, amount);
        }
        return hurt;
    }

    /** A patron owns no work, so it never ingests loose items into its merchant inventory. */
    @Override
    public boolean wantsToPickUp(final ServerLevel level, final ItemStack stack) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
        return null;
    }

    // ---------------------------------------------------------------- persistence and lifecycle

    /** A summoned patron is a persistent challenge entity and never distance-despawns. */
    @Override
    public boolean removeWhenFarAway(final double distanceSquared) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return GoblinPatronRuntime.persistenceRequired(this, super.isPersistenceRequired());
    }

    /**
     * Suppresses exactly one latch write: the one the shared contract path makes.
     *
     * <p>{@code CreatureBehaviorRuntime.bindCompanion} ends in the one-way
     * {@code setPersistenceRequired()}, which has no clearing setter, so honouring it would make
     * every mob that ever reached a binding path permanently persistent. The suppression flag is
     * set only around the single {@code contractBehavior.interact} call, so a name tag, a
     * dispenser, a command, a hopper, and the GameTest entity builder all still latch normally.</p>
     */
    @Override
    public void setPersistenceRequired() {
        if (patronCore.contractLatchSuppressed()) {
            return;
        }
        super.setPersistenceRequired();
    }

    /** Exposed so the live fixture can prove both halves of the latch contract separately. */
    public boolean vanillaPersistenceLatched() {
        return super.isPersistenceRequired();
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void startSeenByPlayer(final ServerPlayer player) {
        super.startSeenByPlayer(player);
        patronCore.bossEvent().addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(final ServerPlayer player) {
        super.stopSeenByPlayer(player);
        patronCore.bossEvent().removePlayer(player);
    }

    @Override
    public void remove(final RemovalReason reason) {
        GoblinPatronRuntime.onRemoved(this);
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        GoblinPatronRuntime.writeSaveData(this, output, STATE_KEY);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        GoblinPatronRuntime.readSaveData(
            this,
            input,
            STATE_KEY,
            level().dimension().identifier().toString(),
            input.getIntOr(LEGACY_EMPOWERMENT_KEY, 0),
            input.getIntOr("Xp", 0)
        );
        syncPresentationFromRuntime();
    }
}
