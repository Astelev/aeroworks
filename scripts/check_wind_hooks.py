"""Check both Sable call sites and that Lift Patch still supplies the aero method."""
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root/'src/main/java/net/aste/aeroworks/mixin/wind/ServerSubLevelWindMixin.java').read_text()
target = re.search(r'target = "L([^;]+);([^" ]+)"', source)
assert target
owner, method = target.groups()
needle = owner + '.' + method.replace('(', ':(', 1)
sable = root/'libs/sable-neoforge-1.21.1-2.0.6.jar'
patch = root/'libs/create-aeronautics-lift-patch-1.1.0.jar'
def inspect(jar, name):
    return subprocess.run(['javap','-c','-p','-s','-classpath',str(jar),name],check=True,capture_output=True,text=True).stdout
server = inspect(sable,'dev.ryanhcode.sable.sublevel.ServerSubLevel')
assert server.count(needle) == 2, 'Expected exactly two ordinary/nested lift-provider calls in Sable'
assert 'aste_aeroworks$wind.aerodynamicTimeStep(timeStep)' in source, 'Scale only aerodynamic integration, not the physics step'
assert '@Redirect' in source and 'aste_aeroworks$wind.velocity(linearVelocity)' in source
assert 'if (!aste_aeroworks$wind.ready())' in source, 'Query wind once per body step'
assert '@Inject(method = "prePhysicsTick", at = @At("HEAD"))' in source
for jar, name in [(sable,'dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider'),
                  (patch,'com.aerofix.ldfix.mixin.BlockSubLevelLiftProviderMixin')]:
    code = inspect(jar,name)
    descriptor = method[method.index('('):]
    assert 'descriptor: '+descriptor in code, f'Aerodynamics method changed in {jar.name}'
    assert 'transformNormalInverse' in code, 'Wind must be subtracted in world coordinates before local conversion'
print('Wind hooks: both Sable call sites and Lift Patch 1.1.0 method contract verified')

# Passive integration uses the two existing surface callers and the one final LOCAL impulse accumulator.
finish='dev/ryanhcode/sable/api/physics/handle/RigidBodyHandle.applyLinearAndAngularImpulse:(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;Z)V'
assert server.count(finish)==1, 'One body-level passivity correction before the final impulse submission'
assert 'aste_aeroworks$aerodynamics.add(' in source and 'aste_aeroworks$aerodynamics.finish(' in source
assert 'handle.applyLinearAndAngularImpulse(linear,angular,wakeUp)' in source
model=(root/'src/main/java/net/aste/aeroworks/aerodynamics/AerodynamicStep.java').read_text()
assert 'lever.set(point).sub(body.getMassTracker().getCenterOfMass())' in model
assert 'angularVelocity.cross(worldLever,velocity).add(relativeVelocity)' in model
assert 'pose.transformNormalInverse(velocity)' in model
assert 'group.totalLift().add(force.lift)' in model and 'group.totalDrag().add(force.drag)' in model
assert model.index('passivity.integrate(')<model.index('linearImpulse.add(impulse)')
assert not any(word in model for word in ('getPhysicsHandle(', 'getChunk(', 'WindFields.sample'))
for name in ('getMass','getInertiaTensor','getInverseInertiaTensor','getCenterOfMass'):
    assert name+'(' in inspect(sable,'dev.ryanhcode.sable.api.physics.mass.MassData')
config=inspect(patch,'com.aerofix.ldfix.AerofixConfig')
for name in ('LIFT_TUNING','PARALLEL_DRAG_TUNING','DIRECTIONLESS_DRAG_TUNING','LIFT_SOFTCAP','NORMAL_DRAG_SOFTCAP'):
    assert name in config
print('Passive aerodynamics: ordinary/nested callers, final local impulse submission, COM lever/rotating airflow, debug groups, mass/inertia APIs and optional Lift Patch tuning verified')

assert 'liftCap=patch?LiftPatchTuning.liftCap():40' in model
assert 'normalCap=patch?LiftPatchTuning.normalCap():50' in model
assert 'Config.AERODYNAMIC_SOFTCAPS' not in model
solver=(root/'src/main/java/net/aste/aeroworks/aerodynamics/PassiveImpulse.java').read_text()
assert 'Math.sqrt(target/proposed)' not in solver and 'addSurface(' in solver
assert 'passivity.addSurface(lever,velocity,normal,force.lift,force.normalDamping,force.baseDamping,timeStep)' in model
print('Control compatibility: unconditional Lift Patch caps, two-sided normal-pressure operator and coupled midpoint integration without whole-body rescaling verified')
