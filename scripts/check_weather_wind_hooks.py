"""Check the bundled SC metadata contracts and coordinate conventions used by server wind."""
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

root = Path(__file__).resolve().parents[1]
jar = root/'libs/simpleclouds-0.7.3+1.21.1-all.jar'
def inspect(path, cls):
    return subprocess.run(['javap','-c','-p','-s','-classpath',str(path),cls],
                          check=True,capture_output=True,text=True).stdout
manager = inspect(jar,'dev.nonamecrackers2.simpleclouds.common.world.CloudManager')
region = inspect(jar,'dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion')
generator = inspect(jar,'dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator')
cloud_type = inspect(jar,'dev.nonamecrackers2.simpleclouds.common.cloud.CloudType')
assert 'getCloudTypeAtWorldPos:(FF)' in manager
assert 'getScrollX(float)' in manager and 'getScrollZ(float)' in manager
assert 'getMovementDirection()' in region and 'getPosX(float)' in region and 'getPosZ(float)' in region
assert 'storminess()' in cloud_type and 'weatherType()' in cloud_type
assert 'getSpawnConfig()' in generator and 'getSpawnInterval:' in generator
# Actual region drift updates its position positively; noise scroll is a separate motion source.
assert 'Field velX:F' in region and 'Field velZ:F' in region
assert 'getCloudSpeed:' in manager and 'Field scrollAngle:F' in manager
with tempfile.TemporaryDirectory() as directory:
    api = Path(directory)/'api.jar'
    with zipfile.ZipFile(jar) as z:
        name = next(n for n in z.namelist() if n.endswith('.jar') and 'simplecloudsapi' in n)
        api.write_bytes(z.read(name))
    weather = inspect(api,'dev.nonamecrackers2.simpleclouds.api.common.cloud.weather.WeatherType')
    assert 'includesRain()' in weather and 'includesThunder()' in weather
metadata = (root/'src/main/templates/META-INF/neoforge.mods.toml').read_text()
for name in ('simpleclouds','crackerslib'):
    section = next(s for s in metadata.split('[[dependencies.') if f'modId="{name}"' in s)
    assert 'side="BOTH"' in section, 'Server weather requires both-side dependencies'
# Keep world metadata on the server update path, outside each surface/physics sampling callback.
field = (root/'src/main/java/net/aste/aeroworks/wind/WindFields.java').read_text()
assert 'CloudManager' not in field and 'WindWorlds.sample' in field
adapter = (root/'src/main/java/net/aste/aeroworks/wind/SimpleCloudsWind.java').read_text()
assert '(float) (x / 8)' in adapter and '(float) (z / 8)' in adapter
assert 'getPosX() - region.getPosX(0)' in adapter and '-(manager.getScrollX()' in adapter
assert not any(word in adapter for word in ('getChunk(', 'Minecraft.getInstance'))
print('Weather wind: Simple Clouds 0.7.3 server weather, region drift, noise-scroll fallback, spawning cadence and both-side dependencies verified')

worlds = (root/'src/main/java/net/aste/aeroworks/wind/WindWorlds.java').read_text()
physics = worlds[worlds.index('static void sample('):worlds.index('@SubscribeEvent')]
assert not any(word in physics for word in ('SimpleCloudsWind.read(', 'CloudDensity.sample(', 'getChunkNow('))
assert 'sampleDensity && y>=minY && y<=maxY' in adapter
assert 'now-packed.checked()>=10' in adapter
terrain = (root/'src/main/java/net/aste/aeroworks/wind/MountainFlowCatalog.java').read_text()
assert 'getChunkNow(' in terrain and 'budget = 32' in terrain
assert not any(word in terrain for word in ('getChunk(', 'getHeightmapPos(', 'getChunkFuture('))
print('Flight effects: height-culled cached density, loaded-only bounded terrain queries, metadata outside physics verified')
