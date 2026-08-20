package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The narrow shared goblin-society merchant foundation. It deliberately extends
 * {@link AbstractVillager} rather than {@code Villager}: the Minecraft 26.2 injected source shows
 * {@code AbstractVillager} supplies merchant offers, the eight-slot inventory, trading-player
 * lifecycle, age, and ordinary merchant persistence <em>without</em> installing the human Villager
 * Brain, sensors, memories, schedules, POI claims, gossip, golem support, native raid activities,
 * Hero gifts, or the Witch and Zombie-Villager conversion branches.
 *
 * <p>This class owns only trade, profession, inventory, age, and migration primitives. Every
 * semantic decision, and all ordinary navigation authority, belongs to the owning family runtime.
 * It registers no goal that declares {@code MOVE}.</p>
 *
 * <p>Scope note: F10 implements and uses this base for {@link GoblinEntity} only. F11 Hobgoblin and
 * F12 Stonebroker/Forgewarden are expected to adopt it in their own packages; nothing here assumes
 * or reserves their semantics.</p>
 */
public abstract class AbstractGoblinMerchantEntity extends AbstractVillager implements ArcaneCreature {
    protected static final String PROFESSION_KEY = "WarlockeryGoblinProfession";

    private GoblinProfession goblinProfession = GoblinProfession.FALLBACK;

    protected AbstractGoblinMerchantEntity(
        final EntityType<? extends AbstractVillager> type,
        final Level level
    ) {
        super(type, level);
    }

    // ---------------------------------------------------------------- profession

    public GoblinProfession goblinProfession() {
        return goblinProfession;
    }

    /**
     * Changing the profession invalidates the seeded offer list, which is rebuilt lazily.
     *
     * <p>The displayed name is refreshed unconditionally, including when the requested profession
     * equals the current field value. The field initialises to {@link GoblinProfession#FALLBACK} and
     * finalizeSpawn rolls uniformly over four values, so an early return here would leave one
     * spawned Goblin in four with no custom name at all - the retained 1.4 body named every Goblin
     * unconditionally, and the displayed name is an immutable public invariant.</p>
     */
    public void setGoblinProfession(final GoblinProfession profession) {
        final GoblinProfession updated = profession == null ? GoblinProfession.FALLBACK : profession;
        if (updated != goblinProfession) {
            goblinProfession = updated;
            // Null, not clear(): AbstractVillager.getOffers() only reseeds when the field is null,
            // so clearing it would leave the merchant permanently empty instead of rebuilding.
            offers = null;
        }
        refreshDisplayName();
    }

    /**
     * The exact existing displayed profession name. Localization keys are immutable public surface.
     *
     * <p>A name the player assigned, with a name tag or an anvil, is never overwritten. Only a name
     * this class itself wrote, or no name at all, is refreshed, so a renamed Goblin keeps its name
     * across profession changes and across every reload.</p>
     */
    protected void refreshDisplayName() {
        if (!isProfessionDisplayName(getCustomName(), speciesTranslationKey())) {
            return;
        }
        setCustomName(professionDisplayName());
        setCustomNameVisible(true);
    }

    protected Component professionDisplayName() {
        return Component.translatable(professionDisplayNameKey(speciesTranslationKey(), goblinProfession));
    }

    static String professionDisplayNameKey(final String species, final GoblinProfession profession) {
        return "entity.warlockery." + species + ".profession." + profession.id();
    }

    /**
     * True when the given name is absent or is one this class wrote, that is a name the body owns
     * and may replace. A literal name, or any translatable key outside this species' own profession
     * family, belongs to the player and is left alone.
     */
    static boolean isProfessionDisplayName(final @Nullable Component name, final String species) {
        if (name == null) {
            return true;
        }
        if (!(name.getContents() instanceof TranslatableContents contents)) {
            return false;
        }
        for (final GoblinProfession profession : GoblinProfession.values()) {
            if (contents.getKey().equals(professionDisplayNameKey(species, profession))) {
                return true;
            }
        }
        return false;
    }

    protected abstract String speciesTranslationKey();

    protected abstract ModSounds.CreatureSoundSet soundSet();

    // ---------------------------------------------------------------- merchant progression

    /** Merchant level 1-5, owned by the concrete body's own versioned state. */
    public abstract int merchantLevel();

    /** Records earned merchant XP; the concrete body decides how it maps to a level. */
    protected abstract void awardMerchantXp(int xp);

    /** True only when the body's runtime says a trade may currently start or continue. */
    protected abstract boolean safeToTrade();

    @Override
    protected void rewardTradeXp(final MerchantOffer offer) {
        if (offer.shouldRewardExp()) {
            awardMerchantXp(offer.getXp());
        }
    }

    @Override
    protected void updateTrades(final ServerLevel level) {
        getOffers().addAll(GoblinTradeCatalog.createOffers(
            creatureKind(),
            goblinProfession,
            GoblinEnclaveRules.offerSeed(getUUID(), goblinProfession, merchantLevel()),
            merchantLevel()
        ));
    }

    /**
     * Ordinary merchant interaction only. There is no gossip pricing, no Hero discount, and no
     * requirement to own a workstation POI: a trade opens when the body is alive, adult, not
     * already trading, and its runtime considers the moment safe.
     */
    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.VILLAGER_SPAWN_EGG) || !isAlive() || isTrading()
            || player.isSecondaryUseActive()) {
            return super.mobInteract(player, hand);
        }
        if (isBaby() || !safeToTrade()) {
            setUnhappyCounter(40);
            return InteractionResult.SUCCESS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (getOffers().isEmpty()) {
            setUnhappyCounter(40);
            if (hand == InteractionHand.MAIN_HAND) {
                player.awardStat(Stats.TALKED_TO_VILLAGER);
            }
            return InteractionResult.CONSUME;
        }
        if (hand == InteractionHand.MAIN_HAND) {
            player.awardStat(Stats.TALKED_TO_VILLAGER);
        }
        setTradingPlayer(player);
        openTradingScreen(player, getDisplayName(), merchantLevel());
        return InteractionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- body

    @Override
    public EntityDimensions getDefaultDimensions(final Pose pose) {
        final EntityDimensions dimensions = getType().getDimensions();
        return isBaby() ? dimensions.scale(GoblinLifecycleRules.BABY_DIMENSION_SCALE) : dimensions;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return (isTrading() ? soundSet().trade() : soundSet().ambient()).get();
    }

    @Override
    protected SoundEvent getHurtSound(final DamageSource source) {
        return soundSet().hurt().get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return soundSet().death().get();
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return soundSet().trade().get();
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(final boolean validTrade) {
        return (validTrade ? soundSet().trade() : soundSet().reject()).get();
    }

    /** The existing work sound event, emitted only through the owning runtime's rate limiter. */
    public void playWorkSound() {
        makeSound(soundSet().work().get());
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString(PROFESSION_KEY, goblinProfession.id());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        goblinProfession = GoblinProfession.byId(
            input.getStringOr(PROFESSION_KEY, GoblinProfession.FALLBACK.id())
        );
        // refreshDisplayName is already a no-op for a player-assigned name; the explicit guard
            // keeps the intent obvious at the reload seam, which is where a rename gets clobbered.
        if (!hasCustomName()) {
            refreshDisplayName();
        }
    }
}
