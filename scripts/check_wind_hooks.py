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
