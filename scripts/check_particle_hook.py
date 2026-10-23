"""Check the mixin invocation against the actual mapped NeoForge build artifact."""
from pathlib import Path
import re
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
source = (root / 'src/main/java/net/aste/aeroworks/mixin/client/CloudFogProjectionMixin.java').read_text()
target = re.search(r'target = "L(net/minecraft/client/particle/ParticleEngine);(render\([^" ]+)', source)
assert target, 'Particle hook descriptor not found'
needle = target[1] + '.' + target[2].replace('render(', 'render:(', 1)
artifacts = []
for path in (root / 'build').rglob('*.jar'):
    with zipfile.ZipFile(path) as jar:
        if 'net/minecraft/client/renderer/LevelRenderer.class' in jar.namelist():
            artifacts.append(path)
assert artifacts, 'Mapped LevelRenderer artifact not found; run Gradle build first'
for path in artifacts:
    result = subprocess.run(['javap', '-c', '-p', '-classpath', str(path),
                             'net.minecraft.client.renderer.LevelRenderer'],
                            capture_output=True, text=True, check=True)
    count = result.stdout.count(needle)
    if count:
        print(f'Particle fog hook: {count} invocation sites verified in {path.name}')
        break
else:
    raise AssertionError('ParticleEngine invocation does not match mixin target in mapped NeoForge bytecode')
