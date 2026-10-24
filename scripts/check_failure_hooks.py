"""Verify Create 6.0.10/Sable 2.0.6 failure, persistence and repair integration contracts."""
from pathlib import Path
import json
import re
import subprocess

root=Path(__file__).resolve().parents[1]
create=root/'libs/create-1.21.1-6.0.10.jar'
sable=root/'libs/sable-neoforge-1.21.1-2.0.6.jar'
def inspect(jar,cls):
    return subprocess.run(['javap','-p','-c','-s','-classpath',str(jar),cls],check=True,capture_output=True,text=True).stdout
def method(code,name):
    return next(section for section in re.split(r'\n(?=  (?:public|private|protected|static))',code)
            if name+'(' in section.split('Code:')[0])
kinetic=inspect(create,'com.simibubi.create.content.kinetics.base.KineticBlockEntity')
for name,descriptor in [('tick','()V'),('getSpeed','()F'),('read','(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V'),
        ('write','(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V'),
        ('addToGoggleTooltip','(Ljava/util/List;Z)Z')]:
    assert 'descriptor: '+descriptor in method(kinetic,name),name
assert method(kinetic,'read').count('return')>1, 'The moved-block early return must remain covered'
assert 'sendData()' in inspect(create,'com.simibubi.create.foundation.blockEntity.SyncedBlockEntity')
propeller=inspect(sable,'dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller')
assert 'getCurrentAirPressure:()D' in method(propeller,'getScaledThrust')
body=inspect(sable,'dev.ryanhcode.sable.sublevel.ServerSubLevel')
assert 'public final org.joml.Vector3d latestLinearVelocity' in body and 'getRuntimeId()' in body
# Create transports network rotation through theoretical speed; functional getSpeed can be hidden locally.
network=inspect(create,'com.simibubi.create.content.kinetics.RotationPropagator')
assert 'getTheoreticalSpeed:()F' in network
mixin=(root/'src/main/java/net/aste/aeroworks/mixin/failure/KineticFailureMixin.java').read_text()
assert 'Math.floorMod(' in mixin and ',20)!=0' in mixin
assert 'method="read",at=@At("RETURN")' in mixin
assert 'method="write",at=@At("RETURN")' in mixin
assert 'FailurePersistence.read(' in mixin and 'FailurePersistence.write(' in mixin
assert not any(word in mixin for word in ('setSpeed(', 'setSource(', 'detachKinetics(', 'disassemble('))
model=(root/'src/main/java/net/aste/aeroworks/failure/FailureSystem.java').read_text()
assert 'body.logicalPose().transformPosition(pos)' in model and 'latestLinearVelocity.length()' in model
assert 'WindWorlds.isTurbulent' in model
assert not any(word in model for word in ('CloudManager', 'getChunk(', 'getChunkNow(', 'getPhysicsHandle(', 'CloudDensity'))
thrust=(root/'src/main/java/net/aste/aeroworks/mixin/failure/PropellerFailureMixin.java').read_text()
assert 'method="getScaledThrust"' in thrust and 'cir.setReturnValue(0d)' in thrust
repair=(root/'src/main/java/net/aste/aeroworks/failure/FailureRepair.java').read_text()
assert 'EventPriority.HIGHEST' in repair and 'event.setCanceled(true)' in repair
assert 'if(level.isClientSide)return' in repair and 'isSpectator()' in repair and 'mayBuild()' in repair
assert 'aste_aeroworks$syncFailure()' in repair and 'tools/wrench' in repair
registered=json.loads((root/'src/main/resources/aeroworks.mixins.json').read_text())['mixins']
assert 'failure.KineticFailureMixin' in registered and 'failure.PropellerFailureMixin' in registered
print('Failure hooks: Create tick/RPM/NBT signatures, moved-block returns, retained network rotation, Sable thrust suppression, cached world context and authoritative wrench repair verified')

invoker=(root/'src/main/java/net/aste/aeroworks/mixin/failure/SyncedFailureInvoker.java').read_text()
assert 'foundation.blockEntity.SyncedBlockEntity' in invoker and '@Invoker(value="sendData",remap=false)' in invoker
assert '@Shadow public abstract void sendData' not in mixin
assert '((SyncedFailureInvoker)this).aste_aeroworks$sendFailureData()' in mixin
assert 'failure.SyncedFailureInvoker' in registered
assert 'now-aste_aeroworks$lastFailureCheck<20' in mixin
print('Failure sync: invoker binds sendData to its declaring superclass; duplicate ticks cannot repeat a check')

occupancy=(root/'src/main/java/net/aste/aeroworks/failure/FailureOccupancy.java').read_text()
assert 'getTrackingOrVehicleSubLevel(player)' in occupancy
assert 'snapshot.bodies().contains(body.getUniqueId())' in occupancy
assert 'snapshot.tick()!=now' in occupancy and 'player.isSpectator()' in occupancy and '!player.isAlive()' in occupancy
assert 'CACHE.remove(level)' in occupancy and 'CACHE.clear()' in occupancy
assert model.index('!FailureOccupancy.hasPlayer(level,serverBody)')<model.index('settings.risk().probability(')<model.index('level.random.nextDouble()')
assert '!(body instanceof ServerSubLevel serverBody)' in model
smoke=(root/'src/main/java/net/aste/aeroworks/client/FailureSmoke.java').read_text()
assert 'ParticleTypes.LARGE_SMOKE' in smoke and 'FailureSystem.stopped(entity)' in smoke
assert 'getContainingClient(entity)' in smoke and 'renderPose(1).transformPosition(POSITION,POSITION)' in smoke
assert 'emitted>=2' in smoke and 'ParticleStatus.MINIMAL' in smoke and '>96*96' in smoke
bridge=(root/'src/main/java/net/aste/aeroworks/failure/FailureVisuals.java').read_text()
assert 'net.minecraft.client' not in bridge
assert 'level.isClientSide' in mixin and 'FailureVisuals.tick(this)' in mixin
client=inspect(sable,'dev.ryanhcode.sable.sublevel.ClientSubLevel')
assert 'renderPose(float)' in client
helper=inspect(sable,'dev.ryanhcode.sable.ActiveSableCompanion')
assert 'getContainingClient(net.minecraft.world.level.block.entity.BlockEntity)' in helper
assert 'getTrackingOrVehicleSubLevel(net.minecraft.world.entity.Entity)' in helper
print('Failure scope/visuals: actual occupied ship identity before RNG, one player scan per level/tick, dead/spectator exclusion, lifecycle cleanup, transformed bounded smoke and server-safe client bridge verified')

# KineticBlockEntity.tick returns early on the client: TAIL would silently omit the smoke callback.
assert method(kinetic,'tick').count('return')>1
assert 'method="tick",at=@At("RETURN")' in mixin
print('Failure smoke hook: client early-return path is covered, not only the server method tail')
