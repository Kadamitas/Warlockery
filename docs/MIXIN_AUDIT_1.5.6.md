# First-party mixin audit — Minecraft 26.3 / Warlockery 1.5.6

The owner chose to retain only the necessary remaining hooks and finish the release. Publication
is pending CI verification, not further owner approval of the documented mixin scope.

## Scope and result

- Forge and NeoForge active editions contain **zero first-party mixin classes or configurations**.
- Fabric and Quilt previously contained 11 production mixin classes and two test-only mixin classes.
- All 13 were inherited from Fabric's 1.5.5 commit `5bd6756`. The initial 1.5.6 port changed three existing hooks' signatures/wrappers; it did not add a mixin class. Quilt inherited the same source.
- This audit removes one production class and one test-only class from each Fabric-based edition. Remaining: **10 production classes and one test-only class**.
- This count excludes loader/Fabric API/JEI internals, which are external dependencies and may themselves use mixins. It does not claim a mixin-free Fabric runtime.
- Supporter and legacy branches are outside this change.

## Removed without losing the existing behavior

1. `client.AvatarRendererMixin`: initialize the authored wolf/wolfman renderers through `LivingEntityRenderLayerRegistrationCallback.EVENT`, restricted to `EntityTypes.PLAYER`. Fabric rendering 27.0.14 invokes this public callback after constructing both vanilla player-renderer variants and supplies the same `EntityRendererProvider.Context`. The existing body/arm rendering code remains.
2. `GameTestHelperAccessor`: the server test helper now reads the real vanilla `GameTestHelper.testInfo` field once through ordinary test-only Java reflection, then installs the same once-only pass/fail cleanup listener. Missing/inaccessible/incorrect/null fields fail loudly. No field is modified, test result fabricated, or production class transformed. The test mixin configuration and its metadata entry are removed.

## Remaining production hooks and the actual API comparison

| Hook | Existing Forge implementation | Fabric 26.3 API finding / why deletion is not an equivalent fix |
| --- | --- | --- |
| `BroomDeathMixin` | `LivingDeathEvent` at highest priority returns the mounted broom before death loot | Fabric's `ALLOW_DEATH` is dispatched in the lethal-damage path; it is not the existing direct `ServerPlayer.die` hook. `AFTER_DEATH` is too late for inventory drop/keep-inventory handling. A replacement must preserve direct death and cancellation ordering. |
| `ItemEntityMixin` | Item overrides `onEntityItemUpdate` | `FabricItem` has no equivalent dropped-item update override. A level-tick tracker/custom item entity is possible design work, but must preserve ticking eligibility, age boundaries and the minedrake bulb's cancellation of that vanilla item tick. |
| `LivingEntityMixin`: mutable damage | `LivingDamageEvent` / `LivingHurtEvent` | `ServerLivingEntityEvents.AllowDamage.allowDamage(..., float)` returns only boolean; `AfterDamage` returns void after processing. Neither replaces the damage amount. Cancel-and-recursively-hurt would change the outer call's result/reentrancy, not preserve the current contract. |
| Same class: editable death drops | `LivingDropsEvent.getDrops()` | No public entity drop-list interception callback was found in entity-events 6.0.4. A loot-table-only change would miss equipment/other non-table death drops and the existing transactional drop handlers. |
| Same class: item-use completion | `LivingEntityUseItemEvent.Finish` | Existing custom brews are written onto vanilla potion stacks. Overriding a newly introduced mod item would not cover those saved stacks. A custom consume-effect/data migration needs separate design and coverage. |
| Same class: random-teleport prevention | `EntityTeleportEvent.EntityRandom` / `ChorusFruit` | No corresponding pre-teleport cancellation callback was found. Post-teleport rollback is not equivalent. |
| Same class and `PlayerMixin`: ammunition | `LivingGetProjectileEvent` | No public projectile-selection result callback was found. The hook currently governs bolts in vanilla bows/crossbows, not only mod-owned entities. |
| `PlayerMixin`: break speed | `PlayerEvent.BreakSpeed` | Fabric's block-break callbacks do not replace `Player.getDestroySpeed`'s float result on both client and server. |
| `ProjectileMixin` | `ProjectileImpactEvent.setImpactResult(SKIP_ENTITY)` | No equivalent cancellable vanilla-projectile impact callback was found. This is used by reflection/ward behavior; replacing only mod projectile subclasses would miss vanilla projectiles. |
| `SpiritWorldTransferMixin` | `EntityTravelToDimensionEvent`, then `PlayerChangedDimensionEvent` | Fabric's `ServerEntityLevelChangeEvents` exposes AFTER callbacks. The transaction needs original source state and cancellation before arbitrary external transfers mutate the player. |
| `ThrownEnderpearlMixin` | `EntityTeleportEvent.EnderPearl` | No matching pre-pearl-teleport cancellation callback was found. |
| `client.ItemInHandRendererMixin` | `RenderArmEvent` | Fabric rendering 27.0.14 has no equivalent cancellable vanilla first-person-arm render callback. Registering an added layer would leave the original human arm visible. |
| `client.LivingEntityRendererMixin` | `RenderAvatarEvent.Pre` | Layer-registration/cape callbacks do not cancel/replace the vanilla player body with the authored transformed rig. |
| `client.MultiPlayerGameModeMixin` | Item `onBlockStartBreak` | Fabric has server `PlayerBlockBreakEvents.BEFORE`, but client `ClientPlayerBlockBreakEvents` exposes AFTER only. `AttackBlockCallback` is at attack start, not the existing completed-break transformation point. |

The per-living-entity tick hook is also part of `LivingEntityMixin`. A level-tick traversal could replace that one injection, but needs ordering/ticking-eligibility analysis and does not remove the class while the damage/drop/teleport hooks remain.

These are findings against the installed API, not a claim that custom alternative designs are impossible. No remaining feature has been silently disabled, replaced with polling that changes semantics, or weakened to make a mixin disappear.

## Remaining test-only hook

`EmptyTestServerShutdownMixin` avoids an empty-player cleanup deadlock when Fabric's client-test server is parked at its synchronization barrier. It retains vanilla cleanup when players exist and is excluded from production JARs. Removing it requires a barrier-safe lifecycle adapter or an upstream harness fix; simply deleting it can hang rendered tests.

## Evidence and verification

The API inspection used the resolved 26.3 dependencies: Fabric entity-events 6.0.4, item-api 14.7.0, events-interaction 5.3.6, rendering 27.0.14, and the matching Forge 66.0.2 source implementations. Source/bytecode inspection confirmed the callback signatures and dispatch points listed above.

The existing source-contract test now checks native renderer initialization and absence of the removed renderer hook. The full unit/server/rendered CI suites remain enabled. No local Minecraft instance was launched.
