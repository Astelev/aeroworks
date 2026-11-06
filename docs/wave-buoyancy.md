# Wave buoyancy

There are two independent displaced volumes in this pack:

* Deep Seas' sealed air cells: the existing cell immersion clamp receives wave height.
* Sable/Rapier's solid blocks: an impulse correction equals buoyancy at the wave surface minus buoyancy at the original flat surface. It can be negative in a trough. It does not add full block buoyancy a second time or replace the native water drag.

The second calculation follows Sable **2.0.6**'s native `do_float`: `10.5 * overlap_volume * voxel_collider_data.volume`. Small bodies use its eight quarter-offset sample cubes; larger bodies use one cube per block. Both paths use the same phase, game/physics time, shore patch and startup fade. Block volumes come from `PhysicsBlockPropertyHelper`, including datapack tuning. Empty collision shapes and liquid blocks are excluded, just as in the collider bakery. Air pockets are never included in the solid-block cache.

Reference: [Sable 2.0.6 native buoyancy](https://github.com/ryanhcode/sable/blob/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/sable_rapier/src/main/rust/rapier/src/buoyancy.rs).

Geometry is read on the server tick with a shared budget of 2048 positions per world per tick, up to 256 per body. Immutable snapshots are rebuilt after plot edits. Physics uses only cached samples and the shore patch, with one accumulated impulse/torque per body. There are no block/chunk queries or terrain generation on the physics path. Blocks away from the wave surface skip the trigonometric calculation.

Up to 4096 solid blocks per body are represented exactly. Larger builds use a deterministic uniform reservoir with volume weighting; their wave correction is an approximation. The existing 128-body and water-field work budgets remain in place. Initial cache construction can take several ticks. Shore fade disables the correction in shallow water and at unloaded boundaries. This is a local wave-surface approximation over continuous deep water, not a replacement for Rapier's full underwater voxel collision simulation.

`wave-physics-r3` identifies this build. The `/aerowaves` diagnostic's `solid=cached/sampled/changed` reports cached blocks, eligible sample cubes and cubes whose buoyancy changed on the last physics step. Air-cell diagnostics remain separate. A single wooden block should have nonzero `solid` counts without any sealed compartment.
