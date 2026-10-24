"""Verify the real Sable/Lift Patch pressure path and all three aerodynamic force branches."""
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
sable = root/'libs/sable-neoforge-1.21.1-2.0.6.jar'
patch = root/'libs/create-aeronautics-lift-patch-1.1.0.jar'

def inspect(jar, name):
    return subprocess.run(['javap','-c','-p','-s','-classpath',str(jar),name],
                          check=True,capture_output=True,text=True).stdout

def method(code, name):
    # A declaration starts with two spaces, instructions with more.
    return next(section for section in re.split(r'\n(?=  (?:public|private|protected|static))', code)
                if name+'(' in section.split('Code:')[0])

source = (root/'src/main/java/net/aste/aeroworks/mixin/atmosphere/DimensionAtmosphereMixin.java').read_text()
target = re.search(r'target = "L([^;]+);([^" ]+)"', source)
assert target
owner, call = target.groups()
pressure = method(inspect(sable,'dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData'), 'getAirPressure')
assert 'descriptor: (Lnet/minecraft/world/level/Level;Lorg/joml/Vector3dc;)D' in pressure
assert pressure.count(owner+'.'+call.replace('(',':(',1)) == 1, 'Height-curve hook must match one call'
assert 'basePressure:' in pressure and re.search(r'dmul\s*\n\s*\d+: dreturn', pressure), 'Base dimension pressure must be retained'
assert 'original.evaluateFunction(height)' in source, 'Disabled/unlisted dimensions must delegate'

pressure_call = 'DimensionPhysicsData.getAirPressure:(Lnet/minecraft/world/level/Level;Lorg/joml/Vector3dc;)D'
for jar, cls in [(sable,'dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider'),
                 (patch,'com.aerofix.ldfix.mixin.BlockSubLevelLiftProviderMixin')]:
    body = method(inspect(jar, cls), 'sable$contributeLiftAndDrag')
    assert body.count(pressure_call) == 1, f'One pressure sample expected in {jar.name}'
    slot = re.search(r'getAirPressure:[^\n]+\n\s*\d+: dstore\s+(\d+)', body).group(1)
    # Pressure is a linear multiplier in normal drag, directionless drag and lift.
    uses = list(re.finditer(r'\d+: dload\s+'+slot+r'\s*\n\s*\d+: dmul', body))
    assert len(uses) == 3, f'Expected pressure multipliers for both drag branches and lift in {jar.name}'
    normal = body.index('sable$getParallelDragScalar:')
    directionless = body.index('sable$getDirectionlessDragScalar:')
    lift = body.index('sable$getLiftScalar:')
    assert normal < uses[0].start() < directionless < uses[1].start() < lift < uses[2].start()

# Universal drag is initialized once per world, independently of altitude/pressure.
system = method(inspect(sable,'dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem'), 'initialize')
assert 'getUniversalDrag:' in system and 'getAirPressure:' not in system
print('Atmosphere hooks: shared height curve, preserved base pressure, lift + both drag branches in Sable/Lift Patch verified; universal damping remains separate')

# The propeller adapter already transforms storage plot coordinates into world coordinates.
propeller = inspect(sable,'dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller')
current = method(propeller,'getCurrentAirPressure')
assert current.count(pressure_call)==1 and 'projectOutOfSubLevel:' in current
assert current.index('projectOutOfSubLevel:')<current.index(pressure_call)
assert method(propeller,'getScaledThrust').count('getCurrentAirPressure:()D')==1
actor = inspect(sable,'dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor')
assert method(actor,'applyForces').count('getScaledThrust:()D')==1
hook = (root/'src/main/java/net/aste/aeroworks/mixin/atmosphere/PropellerAtmosphereMixin.java').read_text()
assert 'public interface PropellerAtmosphereMixin' in hook
assert 'method="getCurrentAirPressure"' in hook and 'require=1,expect=1,allow=1' in hook
assert 'worldPosition.y()' in hook and 'DimensionPhysicsData.getAirPressure(level,worldPosition)' in hook
settings = (root/'src/main/java/net/aste/aeroworks/atmosphere/PropellerSettings.java').read_text()
assert 'entity.getBlockState().is(AFFECTED)' in settings and 'AtmosphereSettings.applies(level)' in settings
assert 'original*settings.profile().efficiency(worldY)' in settings
assert not any(word in settings+hook for word in ('level.getBlockState(', 'getChunk(', 'getChunkNow(', 'getHeight('))
import json
path = root/'src/main/resources/data/aste_aeroworks/tags/block/altitude_sensitive_propellers.json'
blocks = json.loads(path.read_text())['values']
assert {v['id'] for v in blocks}=={'aeronautics:wooden_propeller','aeronautics:andesite_propeller',
        'aeronautics:smart_propeller','aeronautics:propeller_bearing','aeronautics:gyroscopic_propeller_bearing'}
assert all(v.get('required') is False for v in blocks)
assert 'atmosphere.PropellerAtmosphereMixin' in json.loads((root/'src/main/resources/aeroworks.mixins.json').read_text())['mixins']
print('Propeller hooks: projected world height, one existing pressure sample, tagged propellers only, retained base pressure and no extra chunk/block reads verified')
