# Warlockery 1.5.6 release verification

The owner explicitly requested publication without further tests on 2026-09-21. Remaining client-test workflows were cancelled. Final test-observer corrections for normal item pickup and stack merging were not runtime-retested. Full final rendered-client verification is incomplete; no all-green client matrix or successful aggregate receipt is claimed.

These production artifacts had already been built before that waiver:

| Loader | Production build commit | Build run |
| --- | --- | --- |
| Forge | `a10e71f65df592fcba6e75bf0f3983820012a2c2` | [35582378007](https://github.com/Kadamitas/Warlockery/actions/runs/35582378007) |
| NeoForge | `0d3f831e668c72af2e6394ef82e8db745f6365ec` | [35582384988](https://github.com/Kadamitas/Warlockery/actions/runs/35582384988) |
| Fabric | `8c9fd2d5568ae859a8c225d1d9cbc2683ff6a08c` | [35591043285](https://github.com/Kadamitas/Warlockery/actions/runs/35591043285) |
| Quilt | `769aca2b2b5dac5cc799442c1c36894c422206d1` | [35591046605](https://github.com/Kadamitas/Warlockery/actions/runs/35591046605) |

Build, unit, server and metadata checks completed successfully for these artifacts. Native Forge/NeoForge workflows completed successfully; Fabric/Quilt client verification remained incomplete. Release JAR metadata and SHA-256 sidecars were inspected separately as packaging integrity checks, without launching tests or Minecraft.

The subsequent source changes are limited to test observers, the case-insensitive SHA-256 comparison in CI, and this release documentation. Production sources and packaged resources remain identical to the listed build commits. Test observers are excluded from the production and source JARs. Supporter and legacy editions remain unchanged. No manual local Minecraft test is claimed.
