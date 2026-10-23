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
