package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.AbyssalBanishment;
import net.minecraft.server.level.ServerLevel;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.magic.SymbolBranchState;
import com.kadamitas.warlockery.magic.SymbolSpell;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.RitualWardData;
import com.kadamitas.warlockery.ritual.RitualWardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class MysticBranchAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final BlockPos BLOCK = new BlockPos(0, 100, 2);
    private static final List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> AILMENTS = List.of(
        MobEffects.SLOWNESS, MobEffects.WEAKNESS, MobEffects.NAUSEA, MobEffects.BLINDNESS, MobEffects.POISON);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private UUID target;
    private UUID secondary;
    private UUID looseItem;
    private Vec3 targetStart;
    private volatile DeflectionObservation deflection;
    private boolean guidesRead;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("mystic-branch-abilities").resolve(UUID.randomUUID().toString());
        for (SymbolSpell spell : SymbolSpell.VALUES) for (SymbolSpell.Target mode : SymbolSpell.Target.values()) {
            if (spell.supports(mode)) results.put(spell.id() + "/" + mode.name().toLowerCase(), pending());
        }
        results.put("deflection/main_hand", pending());
        results.put("deflection/offhand", pending());
        try {
            Files.createDirectories(evidence);
            write(false);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final DeflectionObservation current = deflection;
                if (current != null && current.player.level().getServer() == server) current.tick();
            });
            for (var entry : results.entrySet()) {
                final String id = entry.getKey();
                final var row = entry.getValue();
                row.put("status", "RUNNING");
                write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context);
                        if (!guidesRead) {
                            final var overview = new LinkedHashMap<String, Object>();
                            final var signs = new LinkedHashMap<String, Object>();
                            readBook(context, "mysticbranch", overview);
                            readBook(context, "mysticbranch_signs", signs);
                            row.put("book_overview", overview);
                            row.put("book_signs", signs);
                            guidesRead = true;
                        }
                        if (id.startsWith("deflection/")) deflect(context, id.endsWith("offhand"), row);
                        else {
                            final String[] parts = id.split("/");
                            spell(context, SymbolSpell.find(parts[0]).orElseThrow(),
                                SymbolSpell.Target.valueOf(parts[1].toUpperCase()), row);
                        }
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        row.put("status", "FAILED");
                        row.put("failure", failure.toString());
                        failures.add(id + ": " + failure);
                        try { screenshot(context, id.replace('/', '-') + "-failure"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
                        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
                        if (deflection != null) row.put("projectile_observation", serverValue(player -> deflection.details()));
                        deflection = null;
                        write(false);
                    }
                } catch (Throwable failure) {
                    row.put("status", "FAILED");
                    row.put("world_failure", failure.toString());
                    failures.add(id + " world: " + failure);
                    write(false);
                } finally { world = null; }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_MYSTIC_BRANCH_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); } catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Mystic Branch native evidence: " + evidence, failure);
        }
    }

    private static Map<String, Object> pending() {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", "NOT_RUN");
        row.put("acquisition_status", "NOT_RUN");
        row.put("persistence_status", "NOT_RUN");
        return row;
    }

    private void stage(final ClientGameTestContext context) {
        target = secondary = looseItem = null;
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.setHealth(20);
            player.setAbsorptionAmount(0);
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-12, 99, -12), new BlockPos(12, 105, 12)))
                player.level().setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.teleportTo(.5, 100, .5);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        });
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
    }

    private void spell(final ClientGameTestContext context, final SymbolSpell spell, final SymbolSpell.Target mode,
        final Map<String, Object> row) throws Exception {
        row.put("fixture", "Fresh Survival world per sign/target pair. Staged Infernal attunement and reserve80, branch, "
            + "ordinary target/block/items and relevant harmful-status or injury prerequisites. Selection uses native crouch-use; "
            + "casting uses actual pointer and right-click. Soul brews are supplied only after a locked-cast refusal. "
            + "No spell runtime, selection/unlock setter, intended outcome or projectile impact is invoked.");
        supply(context, new ItemStack(ModItems.ALL.get("mysticbranch").get()));
        select(context, spell);
        prepareTarget(spell, mode);
        if (spell == SymbolSpell.WITCHLIGHT && mode == SymbolSpell.Target.BLOCK) {
            cast(context, mode);
            check(serverValue(player -> player.level().getBlockState(BLOCK.above()).isAir()),
                "Missing infusion refuses Witchlight without placing light");
            row.put("missing_infusion_refusal", "Observed native cast left the prepared air space unchanged.");
        }
        server(player -> {
            MagicPathState.grantPermanent(player, MagicPath.INFERNAL);
            MagicPathState.spend(player, MagicPath.INFERNAL, MagicPath.INFERNAL.maximumReserve() - 80);
        });
        if (spell.soulIngredient().isPresent()) {
            cast(context, mode);
            check(serverValue(player -> !SymbolBranchState.unlocked(player.getMainHandItem(), spell)
                && reserve(player) == 80), "Locked soul sign refuses without reserve consumption or unlock");
            check(!serverValue(player -> outcome(player, spell, mode)), "Locked sign has not produced its intended outcome");
            row.put("locked_refusal", true);
            server(player -> {
                player.getInventory().setItem(9, new ItemStack(ModItems.ALL.get(spell.soulIngredient().orElseThrow()).get()));
                player.inventoryMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets();
        }
        screenshot(context, spell.id() + "-" + mode.name().toLowerCase() + "-before");
        cast(context, mode);
        for (int tick = 0; tick < 60 && !serverValue(player -> outcome(player, spell, mode)); tick++) context.waitTicks(1);
        check(serverValue(player -> outcome(player, spell, mode)), "Native " + spell.id() + " produces its actual " + mode + " outcome");
        check(serverValue(player -> reserve(player)) == (spell == SymbolSpell.RAVENOUS_COMMUNION ? 92 : 78),
            "Successful sign changes reserve by its exact documented cost or recharge");
        if (spell.soulIngredient().isPresent()) {
            check(serverValue(player -> SymbolBranchState.unlocked(player.getMainHandItem(), spell)
                && player.getInventory().getItem(9).isEmpty()), "Native casting consumes the soul brew and permanently marks this branch unlocked");
            final ItemStack learned = serverValue(player -> player.getMainHandItem().copy());
            check(!SymbolBranchState.unlocked(new ItemStack(ModItems.ALL.get("mysticbranch").get()), spell),
                "Unlock belongs to the used branch, not every new branch");
            check(SymbolBranchState.unlocked(learned, spell), "Learned branch retains unlock when copied for observation");
            row.put("soul_unlock", "One matching soul brew consumed; used branch unlocked; independent new branch remains locked.");
        }
        row.put("observed_outcome", serverValue(player -> describe(player, spell, mode)));
        row.put("remaining", "Unsupported-target refusals, exact radius/cap boundaries, prolonged expiry, multiplayer, "
            + "acquisition and save/reload are NOT_RUN. Ward creation is observed; all downstream ward protection/repulsion branches are not.");
        screenshot(context, spell.id() + "-" + mode.name().toLowerCase() + "-after");
    }

    private void prepareTarget(final SymbolSpell spell, final SymbolSpell.Target mode) {
        server(player -> {
            if (mode == SymbolSpell.Target.BLOCK || spell == SymbolSpell.SNUFF_LIGHT)
                player.level().setBlockAndUpdate(BLOCK, Blocks.STONE.defaultBlockState());
            if (mode == SymbolSpell.Target.ENTITY) {
                // A NoAI mob never runs travel(), so an applied knockback impulse would never become movement.
                final Mob mob = createMob(player, "minecraft:cow", new Vec3(.5, 100, 2.3),
                    spell != SymbolSpell.DOMINATE && spell != SymbolSpell.REPULSE);
                target = mob.getUUID();
                targetStart = mob.position();
                if (spell == SymbolSpell.ABYSSAL_BANISHMENT) {
                    // The arrival chunk must stay loaded for the banished creature to be visible to the observer.
                    final ServerLevel abyss = player.level().getServer().getLevel(AbyssalBanishment.DIMENSION);
                    check(abyss != null, "The abyss dimension exists on the native server");
                    final BlockPos arrival = AbyssalBanishment.arrivalFor(target);
                    abyss.setChunkForced(arrival.getX() >> 4, arrival.getZ() >> 4, true);
                }
                if (spell == SymbolSpell.DOMINATE) mob.setTarget(player);
                if (spell == SymbolSpell.DISARM) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
                if (spell == SymbolSpell.SOULFIRE_LANCE) secondary = createMob(player, "minecraft:cow", new Vec3(2,100,2.3), true).getUUID();
            }
            final LivingEntity recipient = mode == SymbolSpell.Target.ENTITY ? mob(player) : player;
            if (spell == SymbolSpell.AWAKEN) AILMENTS.forEach(effect -> recipient.addEffect(new MobEffectInstance(effect, 1200, 0)));
            if (spell == SymbolSpell.MEND_FLESH) recipient.setHealth(4);
            if (spell == SymbolSpell.GRASP_OF_AIR) {
                final ItemEntity loose = new ItemEntity(player.level(), .5,100,5, new ItemStack(Items.DIAMOND));
                loose.setNoPickUpDelay();
                player.level().addFreshEntity(loose);
                looseItem = loose.getUUID();
                targetStart = loose.position();
            }
            if (spell == SymbolSpell.CALM_SKIES) {
                final var weather = player.level().getWeatherData();
                weather.setRaining(true); weather.setThundering(true); weather.setRainTime(12000); weather.setThunderTime(12000);
            }
            if (spell == SymbolSpell.UNSEAL || spell == SymbolSpell.SEAL)
                player.level().setBlockAndUpdate(BLOCK, Blocks.IRON_TRAPDOOR.defaultBlockState()
                    .setValue(BlockStateProperties.OPEN, spell == SymbolSpell.SEAL));
            if (spell == SymbolSpell.SNUFF_LIGHT) player.level().setBlockAndUpdate(BLOCK.above(), Blocks.TORCH.defaultBlockState());
        });
    }

    private boolean outcome(final ServerPlayer player, final SymbolSpell spell, final SymbolSpell.Target mode) {
        final LivingEntity recipient = mode == SymbolSpell.Target.ENTITY && spell != SymbolSpell.ABYSSAL_BANISHMENT ? mob(player) : player;
        return switch (spell) {
            case SENTINEL_WARD, DREAD_SIGIL -> RitualWardData.get(player.level()).contains(
                spell == SymbolSpell.SENTINEL_WARD ? RitualWardType.PROTECTION : RitualWardType.SANCTITY,
                mode == SymbolSpell.Target.ENTITY ? mob(player).position() : Vec3.atCenterOf(BLOCK), player.level().getGameTime());
            case SOULFIRE_LANCE -> mob(player).getHealth() < mob(player).getMaxHealth() && mob(player).isOnFire()
                && ((LivingEntity) player.level().getEntity(secondary)).getHealth() < ((LivingEntity) player.level().getEntity(secondary)).getMaxHealth();
            case RAVENOUS_COMMUNION -> player.getHealth() <= 16 && reserve(player) == 92;
            case ABYSSAL_BANISHMENT -> player.level().getEntity(target) == null
                && player.level().getServer().getLevel(AbyssalBanishment.DIMENSION).getEntity(target) != null;
            case GRASP_OF_AIR -> {
                final var item = player.level().getEntity(looseItem);
                yield item == null ? player.getInventory().contains(new ItemStack(Items.DIAMOND))
                    : item.position().distanceTo(player.position()) < targetStart.distanceTo(player.position()) - .5;
            }
            case WELLSPRING -> player.level().getBlockState(BLOCK.above()).is(Blocks.WATER);
            case UNSEAL, SEAL -> player.level().getBlockState(BLOCK).getValue(BlockStateProperties.OPEN) == (spell == SymbolSpell.UNSEAL);
            case BEWILDER -> recipient.hasEffect(MobEffects.NAUSEA);
            case AGONY -> recipient.getHealth() < recipient.getMaxHealth();
            case DELVE -> player.level().getBlockState(BLOCK).isAir() && !player.level().getEntitiesOfClass(ItemEntity.class,
                new AABB(BLOCK).inflate(2), item -> item.getItem().is(Items.COBBLESTONE)).isEmpty();
            case AWAKEN -> AILMENTS.stream().noneMatch(recipient::hasEffect);
            case MEND_FLESH -> recipient.getHealth() >= 8;
            case DISARM -> recipient.getMainHandItem().isEmpty() && !player.level().getEntitiesOfClass(ItemEntity.class,
                recipient.getBoundingBox().inflate(3), item -> item.getItem().is(Items.STICK)).isEmpty();
            case REPULSE -> recipient.position().distanceTo(player.position()) > targetStart.distanceTo(player.position()) + 1.0;
            case HOBBLE -> recipient.hasEffect(MobEffects.SLOWNESS) && recipient.getEffect(MobEffects.SLOWNESS).getAmplifier() == 3;
            case DOMINATE -> mob(player).getTarget() == null && recipient.hasEffect(MobEffects.WEAKNESS)
                && recipient.position().distanceTo(player.position()) < targetStart.distanceTo(player.position()) - .2;
            case KINDLE -> player.level().getBlockState(BLOCK.above()).is(Blocks.FIRE);
            case WITCHLIGHT -> player.level().getBlockState(BLOCK.above()).is(Blocks.LIGHT);
            case CALM_SKIES -> !player.level().getWeatherData().isRaining() && !player.level().getWeatherData().isThundering();
            case SNUFF_LIGHT -> player.level().getBlockState(BLOCK.above()).isAir()
                && !player.level().getEntitiesOfClass(ItemEntity.class, new AABB(BLOCK).inflate(4), item -> item.getItem().is(Items.TORCH)).isEmpty();
            case STUN -> recipient.hasEffect(MobEffects.SLOWNESS) && recipient.getEffect(MobEffects.SLOWNESS).getAmplifier() == 6
                && recipient.hasEffect(MobEffects.WEAKNESS) && recipient.getEffect(MobEffects.WEAKNESS).getAmplifier() == 4;
        };
    }

    private Map<String, Object> describe(final ServerPlayer player, final SymbolSpell spell, final SymbolSpell.Target mode) {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("spell", spell.id()); value.put("target_mode", mode.name()); value.put("reserve", reserve(player));
        value.put("player_health", player.getHealth()); value.put("block", player.level().getBlockState(BLOCK).toString());
        value.put("above_block", player.level().getBlockState(BLOCK.above()).toString());
        if (target != null) {
            value.put("target_uuid", target.toString());
            final var entity = player.level().getEntity(target);
            if (entity instanceof LivingEntity living) {
                value.put("target_health", living.getHealth()); value.put("target_position", living.position().toString());
                value.put("target_effects", living.getActiveEffects().stream().map(Object::toString).toList());
            } else value.put("target_absent_from_source_dimension", true);
        }
        return value;
    }

    private void select(final ClientGameTestContext context, final SymbolSpell wanted) {
        air(context);
        for (int count = 0; count < SymbolSpell.VALUES.size()
            && serverValue(player -> SymbolBranchState.selected(player.getMainHandItem())) != wanted; count++) {
            final SymbolSpell previous = serverValue(player -> SymbolBranchState.selected(player.getMainHandItem()));
            context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(2);
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(2);
            context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
            for (int tick = 0; tick < 15 && serverValue(player -> SymbolBranchState.selected(player.getMainHandItem())) == previous; tick++)
                context.waitTicks(1);
            check(serverValue(player -> SymbolBranchState.selected(player.getMainHandItem())) == previous.next(),
                "One native crouch-use advances exactly one named sign");
        }
        check(serverValue(player -> SymbolBranchState.selected(player.getMainHandItem())) == wanted, "Native cycling selects " + wanted.id());
        context.waitTicks(2);
    }

    private void cast(final ClientGameTestContext context, final SymbolSpell.Target mode) {
        world.getConnection().waitForClientboundPackets();
        if (mode == SymbolSpell.Target.SELF) air(context);
        else if (mode == SymbolSpell.Target.BLOCK) {
            final SymbolSpell selected = serverValue(player -> SymbolBranchState.selected(player.getMainHandItem()));
            final BlockPos clicked = selected == SymbolSpell.SNUFF_LIGHT ? BLOCK.above() : BLOCK;
            final Vec3 point = selected == SymbolSpell.UNSEAL || selected == SymbolSpell.SEAL
                || selected == SymbolSpell.SNUFF_LIGHT
                ? serverValue(player -> player.level().getBlockState(clicked).getShape(player.level(), clicked)
                    .bounds().getCenter().add(Vec3.atLowerCornerOf(clicked)))
                : new Vec3(clicked.getX() + .5, clicked.getY() + .95, clicked.getZ() + .5);
            look(context, point);
            check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(clicked)),
                "Native pointer targets the prepared block");
        } else {
            context.waitFor(client -> java.util.stream.StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
                .anyMatch(entity -> entity.getUUID().equals(target)));
            look(context, serverValue(player -> mob(player).position().add(0, mob(player).getBbHeight() * .55,0)));
            check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(target)),
                "Native pointer targets the exact creature");
        }
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(3);
    }

    private void deflect(final ClientGameTestContext context, final boolean offhand, final Map<String, Object> row) throws Exception {
        supply(context, new ItemStack(ModItems.ALL.get("mysticbranch").get()));
        if (offhand) {
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F);
            context.waitTicks(3);
            check(serverValue(player -> player.getOffhandItem().is(ModItems.ALL.get("mysticbranch").get())
                && player.getMainHandItem().isEmpty()), "Native swap puts the branch in the offhand");
        }
        server(player -> {
            final Mob archer = createMob(player, "minecraft:skeleton", new Vec3(.5,100,9), false);
            archer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            archer.setTarget(player);
            target = archer.getUUID();
            deflection = new DeflectionObservation(player, target);
        });
        row.put("fixture", "Normal-AI skeleton with bow and daylight helmet targets the Survival player. "
            + "Branch moved into the tested hand by native input. No projectile is created or impact/deflection invoked by the test.");
        air(context);
        for (int tick = 0; tick < 300 && !serverValue(player -> deflection.reflected); tick++) context.waitTicks(1);
        check(serverValue(player -> deflection.reflected), "Skeleton's real projectile reverses travel and ownership after hitting held branch");
        check(serverValue(player -> player.getHealth() == 20 && MagicPathState.selected(player).isEmpty()),
            "Deflection prevents the incoming hit without infusion or casting reserve");
        row.put("remaining", "Other projectile types, maximum-speed impacts, repeated deflections and multiplayer are NOT_RUN.");
        screenshot(context, "branch-deflection-" + (offhand ? "offhand" : "main-hand"));
    }

    private static final class DeflectionObservation {
        private final ServerPlayer player;
        private final UUID archer;
        private final Map<UUID, Vec3> incoming = new LinkedHashMap<>();
        private boolean reflected;
        private UUID projectile;
        private Vec3 outgoing = Vec3.ZERO;
        private DeflectionObservation(final ServerPlayer player, final UUID archer) { this.player = player; this.archer = archer; }
        private void tick() {
            for (Projectile arrow : player.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(20))) {
                if (arrow.getOwner() != null && arrow.getOwner().getUUID().equals(archer))
                    incoming.put(arrow.getUUID(), arrow.getDeltaMovement());
                if (arrow.getOwner() == player && incoming.containsKey(arrow.getUUID())
                    && arrow.getDeltaMovement().dot(incoming.get(arrow.getUUID())) < 0) {
                    reflected = true; projectile = arrow.getUUID(); outgoing = arrow.getDeltaMovement();
                }
            }
        }
        private Map<String, Object> details() {
            return Map.of("seen_shooter_projectiles", incoming.size(), "reflected", reflected,
                "reflected_projectile", projectile == null ? "" : projectile.toString(), "outgoing_velocity", outgoing.toString());
        }
    }

    private static Mob createMob(final ServerPlayer player, final String id, final Vec3 position, final boolean noAi) {
        final var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
        final Mob mob = (Mob) type.create(player.level(), EntitySpawnReason.EVENT);
        check(mob != null, "Fixture creature exists");
        mob.snapTo(position.x, position.y, position.z); mob.setNoAi(noAi); mob.setPersistenceRequired();
        check(player.level().addFreshEntity(mob), "Fixture creature joins the world");
        return mob;
    }
    private Mob mob(final ServerPlayer player) { return (Mob) player.level().getEntity(target); }
    private static int reserve(final ServerPlayer player) { return MagicPathState.reserve(player, MagicPath.INFERNAL); }
    private void supply(final ClientGameTestContext context, final ItemStack stack) {
        server(player -> { player.getInventory().clearContent(); player.getInventory().setItem(0, stack);
            player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets();
    }
    private static void look(final ClientGameTestContext context, final Vec3 point) {
        context.runOnClient(client -> { final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z,delta.x))-90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)))); });
        context.waitTicks(2);
    }
    private static void air(final ClientGameTestContext context) {
        context.runOnClient(client -> client.player.setXRot(-80)); context.waitTicks(2);
    }
    private void readBook(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final ManualProfile profile = ManualProfile.profiles().stream().filter(book -> book.sections().contains(id))
            .findFirst().orElseThrow(() -> new AssertionError("No indexed book instructions for " + id));
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(profile.id()).get()));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.setXRot(-70));
        context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, id);
        final String text = context.computeOnClient(client -> ManualArticleCatalog.article(profile, id).body().getString());
        check(!text.isBlank(), "Branch article must contain readable instructions");
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(),
                    ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), id)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> id.equals(field(client.gui.screen(), "selectedSection"))
                && expected == (int) field(client.gui.screen(), "bodyPage")), "Native Next reaches each branch instruction page");
            screenshot(context, id + "-book-" + page);
        }
        row.put("book", profile.id());
        row.put("section", id);
        row.put("book_text", text);
        row.put("book_pages_read", pages);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
    }


    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T serverValue(final Function<ServerPlayer,T> action) { return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer())); }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context,evidence,name,screenshots); }
    private static Object field(final Object object, final String name) {
        try { final var field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private void write(final boolean finished) throws Exception {
        final Map<String,Object> report=new LinkedHashMap<>();
        report.put("completed",finished);
        report.put("all_selected_scenarios_passed",finished && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete",false); report.put("scenarios",results);
        report.put("screenshots",screenshots); report.put("failures",failures);
        report.put("execution","Native book reading, crouch-use sign selection, pointer-targeted casting and normal skeleton shooting; isolated world per scenario.");
        final Path temp=evidence.resolve("mystic-branch-abilities.json.tmp");
        Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temp,evidence.resolve("mystic-branch-abilities.json"),StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean condition, final String message) { if(!condition) throw new AssertionError(message); }
}
