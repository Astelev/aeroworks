# Wave buoyancy

There are two independent displaced volumes in this pack:

* Deep Seas' sealed air cells: the existing cell immersion clamp receives wave height.
* Sable/Rapier's solid blocks: an impulse correction equals buoyancy at the wave surface minus buoyancy at the original flat surface. It can be negative in a trough. It does not add full block buoyancy a second time or replace the native water drag.

The second calculation follows Sable **2.0.6**'s native `do_float`: `10.5 * overlap_volume * voxel_collider_data.volume`. Small bodies use its eight quarter-offset sample cubes; larger bodies use one cube per block. Both paths use the same phase, game/physics time, shore patch and startup fade. Block volumes come from `PhysicsBlockPropertyHelper`, including datapack tuning. Empty collision shapes and liquid blocks are excluded, just as in the collider bakery. Air pockets are never included in the solid-block cache.

Reference: [Sable 2.0.6 native buoyancy](https://github.com/ryanhcode/sable/blob/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/sable_rapier/src/main/rust/rapier/src/buoyancy.rs).

Geometry is read on the server tick with a shared budget of 2048 positions per world per tick, up to 256 per body. Immutable snapshots are rebuilt after plot edits. Physics uses only cached samples and the shore patch, with one accumulated impulse/torque per body. There are no block/chunk queries or terrain generation on the physics path. Blocks away from the wave surface skip the trigonometric calculation.

Up to 4096 solid blocks per body are represented exactly. Larger builds use a deterministic uniform reservoir with volume weighting; their wave correction is an approximation. The existing 128-body and water-field work budgets remain in place. Initial cache construction can take several ticks. Shore fade disables the correction in shallow water and at unloaded boundaries. This is a local wave-surface approximation over continuous deep water, not a replacement for Rapier's full underwater voxel collision simulation.

`wave-physics-r3` identifies this build. The `/aerowaves` diagnostic's `solid=cached/sampled/changed` reports cached blocks, eligible sample cubes and cubes whose buoyancy changed on the last physics step. Air-cell diagnostics remain separate. A single wooden block should have nonzero `solid` counts without any sealed compartment.

## Wind, depth and irregular waves

`wave-spectrum-r4` adds a shared spectrum; network protocol 3 requires matching client/server builds. The new `[detail]` section is in `aste_aeroworks-waves-common.toml`. Existing amplitude, wavelength, speed, direction and shore fade remain the main-wave controls. `detail.enabled=false` restores the original uniform sine.

The local ambient wind speed is divided by the configured storm speed **at the height of the water surface** (surface/aloft interpolation from AeroWorks' wind config). Wave amplitude scales with the square root of this ratio, capped at 1.5 before the square root. Zero wind gives zero wind-driven waves. Depth multiplies the energy by smoothstep(depth/full_depth); the default full depth is 16 blocks. The depth uses the loaded chunk's OCEAN_FLOOR heightmap, with no vertical scan. Shoreline fading remains in addition to depth attenuation.

Broad value noise modulates the main wave's amplitude and phase (`noise_strength=.2`, `noise_scale=48` blocks, `noise_period=24` seconds). It is deterministic in world space and time. A pair of oblique shorter waves, with stronger and faster noise modulation, activates above `chop_threshold=.65`, i.e. 65% of the configured storm wind, and blends in smoothly. Defaults: `chop_fraction=.35` of main amplitude, `chop_length=.25` of main wavelength, `chop_speed=1.7` times main speed. The main carrier keeps its continuous phase as wind energy changes; a weather transition does not reset the wave clock.

Wind energy is sampled at 64-block knots once per second from the existing cached ambient wind field. Aircraft-specific turbulence is excluded. Targets interpolate over two seconds, starting from the previous interpolated value. The server retains at most 1024 knots per dimension and sends at most 121 nearby knots per player each second (about 3 KiB). Both buoyancy paths and the vertex shader evaluate the same interpolated grid and the same spectrum. CPU preparation computes the wave coefficients once per body/substep; the per-cell path has no weather, world or block queries. Native Deep Seas fragment masking is unchanged; wind, shore and hull textures use separate units 6, 7, 8/9.

## Crest foam and storm shape

`wave-foam-r5` (wave network protocol **4**) shapes the main sine with a zero-mean second harmonic as the strong-wind gate rises. Crests become narrower and troughs broader without raising the mean water level or exceeding the existing amplitude bound. The same shaped spectrum is used for air-cell buoyancy, solid-block correction and rendering.

Crest foam is a water-shader effect, not a particle system. A crest varying and a small periodic procedural breakup pattern whiten the water before native fog. Troughs, dry terrain and lower underwater vertices do not receive foam; original Deep Seas fragment-depth clipping still discards water inside hulls. The client settings `foam` and `foam_strength` in `aste_aeroworks-waves-client.toml` control presentation only.

## Simple Clouds rain optimizations

`aste_aeroworks-rain-client.toml` controls optimizations for the exact 0.7.3 precipitation path. The original strip density, scan volume, textures, rain/snow appearance, lifetime, sounds and lightning remain in place:

* Reject deterministic spawn candidates before allocating their BlockPos and RNG. The filter reproduces Minecraft LegacyRandomSource's first bounded roll exactly; accepted candidates still run the native biome and spawn checks. Reuse the seeded RNG for these checks.
* Reuse per-strip render scratch vectors/quaternions and cache packed lighting within one game tick instead of querying it at every rendered frame. Skip strips wholly behind the camera plane using the full slanted 32-block segment plus billboard width; strips crossing the view plane are retained.
* Cache only distant unchanged-angle rain raycasts, default interval 3 ticks (at most 150 ms). First samples, changed angles and rays passing within 8 horizontal blocks of the camera are checked every tick. Initial refreshes are staggered. Moving distant roofs can therefore take up to the configured interval to update clipping. Set `distant_ray_interval=1` for original collision frequency, or `enabled=false` for the full original path.

Tests compare 200,000 seeded spawn decisions against Minecraft and pin constructor, RNG, precipitation, lighting and scratch-object hooks to the supplied Simple Clouds JAR. These are workload reductions, not a measured FPS claim for the complete modpack.


## Curved crest fronts

`wave-warp-r6` uses wave network protocol **5**. `detail.crest_distortion` controls smooth horizontal domain warping of the carrier phases, default **0.22 of main wavelength**, range 0..0.35. At the default wavelength of 32 blocks each warp component has a maximum displacement of 7.04 blocks; actual smoothly interpolated displacements are generally smaller. Independent X/Z noise bends crest lines instead of only varying their height. The short-wave carriers use the same distorted coordinates; foam follows the resulting crest geometry.

The first warp component reuses the existing broad noise sample; the second adds one extra value-noise sample, sharing `noise_scale` and `noise_period`. Warp strength is independent of `noise_strength`, so turning off height noise still permits curved crests. Set `crest_distortion=0` to disable the new curvature. Wind/depth/shore queries remain at the original world positions: distortion changes only carrier phase, so it does not move coastlines or hull masks. Both native buoyancy integrations and GLSL use the same formula and coefficients.

Tests track crest positions along a transverse line with amplitude noise/chop disabled, check continuity at negative/positive spatial and temporal lattice boundaries, and compare distorted Java/GLSL heights at distant coordinates.
