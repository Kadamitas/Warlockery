# Warlockery 1.5.6 release verification

Release 1.5.6 was first published on 2026-09-21 under an owner-approved waiver while the Fabric and Quilt rendered-client ritual partitions were still red. The two failing partitions were then diagnosed, fixed and re-verified, and the Fabric and Quilt release assets were replaced with bundles from fully green CI runs. Production sources and packaged resources did not change in that repair.

## What was wrong

Both failures were test-harness problems in the shared rendered-client ritual walkthrough. The same production code is unchanged since 1.5.5, and Forge and NeoForge do not run this rendered walkthrough at all, so the earlier statement that "the same test passes" on those loaders was never a comparison of the same test.

- Earth's Wrath (`client (rituals-4)`): the live cast reported "The circle spends its power, but nothing answers", so `raiseVolcano` returned false. The Blood Audience rite runs earlier in the same partition and its fixture generates a real vanilla Ocean Monument (bounding box y 39 to 62) around the golden heart, while the between-rite isolation only reset y 49 to 54. `raiseVolcano` measures its vent from the `MOTION_BLOCKING_NO_LEAVES` heightmap above the heart and refuses any occupied column, rim or basin block, so the monument roof left above the cleared band blocked the vent. Every rite now starts from the same flat fixture (stone floor, air from twelve blocks below the heart to twenty above, and the whole monument footprint cleared whenever one was generated), and the vent column, heightmap and staged lava sources are recorded as evidence.
- Marriage (`client (rituals-1)`): the assertion rewritten in the final observer change demanded exactly one wedding ring bearing both names. The recipe offers two wedding rings with `consume=false`, so both survive, and the action inscribes the first wedding-ring stack it finds; a merged stack of two therefore carries the names on both rings. The assertion now requires both offered rings to survive and at least one to be inscribed, which is what the ritual has done since 1.5.5.

## Green verification

| Loader | Build commit | Build run | Unit tests | Server GameTests | Rendered client |
| --- | --- | --- | ---: | ---: | --- |
| Forge | `a10e71f65df592fcba6e75bf0f3983820012a2c2` | [35582378007](https://github.com/Kadamitas/Warlockery/actions/runs/35582378007) | 5,576 | 397 | not part of the Forge workflow |
| NeoForge | `0d3f831e668c72af2e6394ef82e8db745f6365ec` | [35582384988](https://github.com/Kadamitas/Warlockery/actions/runs/35582384988) | 5,580 | 397 | not part of the NeoForge workflow |
| Fabric | `ab1d96dc7c3f844177ca292b83a60fe620402fe0` | [35644917866](https://github.com/Kadamitas/Warlockery/actions/runs/35644917866) | 5,594 | 397 | all 22 partitions green |
| Quilt | `391ccc2db38de3c527c89d0832d876cf8172f150` | [35644921392](https://github.com/Kadamitas/Warlockery/actions/runs/35644921392) | 5,594 | 397 | all 22 partitions green |

The Fabric run needed one rerun of `client (rituals-0)`: its test step had passed, but the GitHub artifact service answered the upload finalize with HTTP 403; the rerun uploaded the same evidence and the aggregate verified-bundle job then completed. The release assets for Fabric and Quilt are the `warlockery-<sha>` verified bundles of these runs, and their SHA-256 sidecars were checked against the JARs before upload.

The Forge and NeoForge assets are unchanged from the original release. Supporter and legacy editions remain unchanged. No manual local Minecraft test is claimed.
