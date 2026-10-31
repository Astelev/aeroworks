"""Verify the lateral-only selector against a supplied bundled Aeronautics or Offroad JAR.

Usage: python3 scripts/check_wheel_hooks.py /path/to/create-aeronautics-bundled.jar [aerowheelfrictionfix.jar]
Uses javap without loading Minecraft/mod classes. No third-party binary is redistributed.
"""
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile
import hashlib

root = Path(__file__).resolve().parents[1]
jar = Path(sys.argv[1])
target = 'dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity'
descriptor = '(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;D)V'

def inspect(path, name):
    return subprocess.run(['java', '-m', 'jdk.jdeps/com.sun.tools.javap.Main', '-c', '-p', '-s', '-v',
                           '-classpath', str(path), name], check=True, capture_output=True, text=True).stdout

with tempfile.TemporaryDirectory() as tmp:
    with zipfile.ZipFile(jar) as bundle:
        nested = [n for n in bundle.namelist() if n.endswith('.jar') and 'offroad' in n.lower()]
        if nested:
            assert len(nested) == 1
            offroad = Path(tmp)/'offroad.jar'
            offroad.write_bytes(bundle.read(nested[0]))
        else:
            offroad = jar
    code = inspect(offroad, target)
    body = next(s for s in re.split(r'\n(?=  (?:public|private|protected|static))', code)
                if 'public void sable$physicsTick(' in s.split('Code:')[0])
    assert 'descriptor: '+descriptor in body
    call = 'Method org/joml/Vector3d.fma:(DLorg/joml/Vector3dc;)Lorg/joml/Vector3d;'
    matches = list(re.finditer(re.escape(call), body))
    assert len(matches) == 2, 'Ordinal 1 requires exactly two fma calls in this method'
    first, second = matches
    before_lateral = body[first.end():second.start()]
    assert 'double -0.6d' in before_lateral and 'Field touchingFriction:D' in before_lateral
    assert 'Method org/joml/Vector3d.dot:(Lorg/joml/Vector3dc;)D' in before_lateral
    # The projected side axis must be the same vector passed into the second fma.
    axes = re.findall(r'aload\s+(\d+)\s*\n\s*\d+: invokevirtual[^\n]*Vector3d.dot', before_lateral)
    applied = re.findall(r'aload\s+(\d+)\s*\n\s*\d+: invokevirtual[^\n]*$', before_lateral)
    assert len(axes) == 1 and applied == axes, 'Lateral impulse must use the projected wheel side axis'
    assert 'getSignal:' in body[:first.start()] and 'getSpeed:()F' in body[:first.start()]
    assert 'ForceTotal.applyImpulseAtPoint:' in body[second.end():]
    source = (root/'src/main/java/net/aste/aeroworks/mixin/wheels/WheelLateralGripMixin.java').read_text()
    assert '@ModifyArg' in source and 'ordinal=1' in source and 'index=0' in source
    assert 'sable$physicsTick'+descriptor in source and 'require=0' in source
    print('Wheel hook verified: explicit method descriptor; fma #0 rolling/brake/drive unchanged; fma #1 lateral only; optional injection')
    print('Offroad SHA256:', hashlib.sha256(offroad.read_bytes()).hexdigest())

if len(sys.argv) > 2:
    patch = inspect(Path(sys.argv[2]), 'com.Tropfen_33.aerowheelfrictionfix.mixin.WheelMountFrictionMixin')
    assert 'sable$physicsTick'+descriptor in patch
    assert 'org.spongepowered.asm.mixin.injection.Inject(' in patch
    assert 'org.spongepowered.asm.mixin.Overwrite(' not in patch
    assert 'value="FIELD"' in patch and 'WheelMountBlockEntity;forceTotal:' in patch
    assert 'getSignal:' in patch and 'CustomFriction' in patch
    print('Aero-Wheeldrag-Fix checked: FIELD injection, no replacement of physics method or fma calls; runtime coexistence still needs game testing')
