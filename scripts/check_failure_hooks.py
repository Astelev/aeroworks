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
