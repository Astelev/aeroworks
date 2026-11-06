"""Compile the exact Sodium terrain shader, then exercise wave vertices on Mesa."""
from pathlib import Path
import math
# Reuse only the EGL/GL setup helpers, without running the fog tests.
exec(Path('scripts/test_cloud_fog_shader.py').read_text().split("vertex = '''")[0])
source=Path('build/wave-test/terrain.vsh').read_text()
shader(0x8B31, source)
shader(0x8B31, source.replace('#define USE_FOG',''))
shader(0x8B31, Path('build/wave-test/terrain-processed.vsh').read_text())
shader(0x8B31, Path('build/wave-test/terrain-veil.vsh').read_text())
wave=Path('src/main/resources/assets/aste_aeroworks/shaders/waves/displacement.glsl').read_text()
vertex='#version 330 core\n'+wave+'''
uniform vec3 testPosition;
uniform vec2 testUV;
out vec3 result;
void main() { result=aste_wavePosition(testPosition,testUV); gl_Position=vec4(result,1); }
'''
program=create_program(); attach(program,shader(0x8B31,vertex))
feedback=bind(gl,'glTransformFeedbackVaryings',None,U,I,C.POINTER(C.c_char_p),U)
names=(C.c_char_p*1)(b'result'); feedback(program,1,names,0x8C8C)
link(program)
ok=I(); program_iv(program,0x8B82,C.byref(ok))
log=C.create_string_buffer(8192); program_log(program,len(log),None,log)
assert ok.value,log.value.decode()
use(program)
u2f=bind(gl,'glUniform2f',None,I,F,F)
u4f=bind(gl,'glUniform4f',None,I,F,F,F,F)
def loc(name): return location(program,name.encode())
tex=U(); gen_tex(1,C.byref(tex)); active_tex(0x84C0); bind_tex(0x0DE1,tex)
tex_param(0x0DE1,0x2801,0x2601); tex_param(0x0DE1,0x2800,0x2601)
u1i(loc('aste_WaveShore'),0)
u1f(loc('aste_WaveEnabled'),1)
u3f(loc('aste_WaveGrid'),0,0,0)
for name in ['aste_WaveStill','aste_WaveFlow','aste_WaveOverlay']:
    u4f(loc(name),.1,.1,.2,.2)
u2f(loc('testUV'),.15,.15)
height=10+8/9-.001
pixels=(F*(129*129*2))()
def field(weight):
    for i in range(129*129): pixels[2*i],pixels[2*i+1]=10,weight
    tex_image(0x0DE1,0,0x8230,129,129,0,0x8227,0x1406,pixels)
field(1)
gen_buffers=bind(gl,'glGenBuffers',None,I,C.POINTER(U))
bind_buffer=bind(gl,'glBindBuffer',None,U,U)
buffer_data=bind(gl,'glBufferData',None,U,C.c_ssize_t,P,U)
bind_base=bind(gl,'glBindBufferBase',None,U,U,U)
get_buffer=bind(gl,'glGetBufferSubData',None,U,C.c_ssize_t,C.c_ssize_t,P)
begin_tf=bind(gl,'glBeginTransformFeedback',None,U)
end_tf=bind(gl,'glEndTransformFeedback',None)
enable=bind(gl,'glEnable',None,U)
vao=U(); gen_vao(1,C.byref(vao)); bind_vao(vao)
buffer=U(); gen_buffers(1,C.byref(buffer)); bind_buffer(0x8C8E,buffer)
buffer_data(0x8C8E,12,None,0x88E9); bind_base(0x8C8E,0,buffer)
enable(0x8C89)
def sample(x=256,y=height,z=256,phase=0):
    u3f(loc('testPosition'),x,y,z)
    u4f(loc('aste_WaveProfile'),0,0,phase,.5)
    begin_tf(0); draw(0,0,1); end_tf()
    out=(F*3)(); get_buffer(0x8C8E,0,12,out)
    assert error()==0
    return tuple(out)
def near(a,b): assert abs(a-b)<2e-5,(a,b)
near(sample(phase=math.pi/2)[1],height+.5)
near(sample(phase=-math.pi/2)[1],height-.5)
near(sample(phase=0)[1],height)
near(sample(x=0,phase=math.pi/2)[1],height)
near(sample(x=-1,phase=math.pi/2)[1],height)
near(sample(y=height-1,phase=math.pi/2)[1],height-1)
near(sample(y=11,phase=math.pi/2)[1],11.5)
near(sample(y=10,phase=math.pi/2)[1],10)
near(sample(y=10.5,phase=math.pi/2)[1],10.5)
u2f(loc('testUV'),.1,.1); near(sample(phase=math.pi/2)[1],height+.5)
u2f(loc('testUV'),.8,.8); near(sample(phase=math.pi/2)[1],height)
u2f(loc('testUV'),.15,.15); field(0); near(sample(phase=math.pi/2)[1],height)
field(1); u1f(loc('aste_WaveEnabled'),0); near(sample(phase=math.pi/2)[1],height)
print('Actual Sodium GLSL compiled; crest, trough, zero crossing, shore, map edge, other heights/materials and disable verified on GPU')

# Exercise the FULL patched Sodium vertex shader (including its compressed vertex
# decoder and region translation), rather than only compiling it and testing a stub.
# Run the complete vertex path after the exact Veil formatter bundled in Sable.
source=Path('build/wave-test/terrain-veil.vsh').read_text()
full=create_program(); attach(full,shader(0x8B31,source))
bind_attribute=bind(gl,'glBindAttribLocation',None,U,U,C.c_char_p)
for index,name in enumerate(['a_Position','a_Color','a_TexCoord','a_LightAndData']):
    bind_attribute(full,index,name.encode())
names=(C.c_char_p*1)(b'gl_Position'); feedback(full,1,names,0x8C8C); link(full)
program_iv(full,0x8B82,C.byref(ok)); program_log(full,len(log),None,log)
assert ok.value,log.value.decode()
program=full; use(full)
identity=(F*16)(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1)
for name in ['u_ProjectionMatrix','u_ModelViewMatrix']: umat(loc(name),1,0,identity)
u3f(loc('u_RegionOffset'),256,48,256)
u3f(loc('aste_WaveGrid'),0,0,0)
u1f(loc('aste_WaveEnabled'),1); u1i(loc('aste_WaveShore'),0)
for name in ['aste_WaveStill','aste_WaveFlow','aste_WaveOverlay']: u4f(loc(name),.1,.1,.2,.2)
u4f(loc('aste_WaveProfile'),0,0,math.pi/2,.5)
# Ensure the terrain light sampler has a texture, separate from the shore map.
light=U(); gen_tex(1,C.byref(light)); active_tex(0x84C1); bind_tex(0x0DE1,light)
tex_param(0x0DE1,0x2801,0x2600); tex_param(0x0DE1,0x2800,0x2600)
tex_image(0x0DE1,0,0x8814,1,1,0,0x1908,0x1406,(F*4)(1,1,1,1))
u1i(loc('u_LightTex'),1); active_tex(0x84C0)
def q(v): return int((8+v)/32*(1<<20))&0xFFFFF
x,y,z=q(0),q(15+8/9-.001),q(0)
hi=(x>>10)|((y>>10)<<10)|((z>>10)<<20)
lo=(x&1023)|((y&1023)<<10)|((z&1023)<<20)
attr2=bind(gl,'glVertexAttribI2ui',None,U,U,U)
attr4=bind(gl,'glVertexAttribI4ui',None,U,U,U,U,U)
attr2(0,hi,lo); attr2(2,round(.15*32768)+1,round(.15*32768)+1)
attr4(3,128,128,0,0)
# Same top surface as a real sea-level source block.
for i in range(129*129): pixels[2*i],pixels[2*i+1]=63,1
tex_image(0x0DE1,0,0x8230,129,129,0,0x8227,0x1406,pixels)
buffer_data(0x8C8E,16,None,0x88E9)
begin_tf(0); draw(0,0,1); end_tf()
out=(F*4)(); get_buffer(0x8C8E,0,16,out)
assert error()==0
near(out[1],(y*32/(1<<20)-8)+48+.5)
print('Full Sodium compressed water vertex + region translation displaced by configured amplitude on GPU')

# Rasterize real Sodium water with Deep Seas' original fragment patch. Its own
# near/far textures describe a rotated hull mask; no block faces are removed.
native_mask=Path('build/wave-test/hull-fragment.fsh').read_text()
program=create_program(); attach(program,shader(0x8B31,source)); attach(program,shader(0x8B30,native_mask))
for index,name in enumerate(['a_Position','a_Color','a_TexCoord','a_LightAndData']):
    bind_attribute(program,index,name.encode())
link(program); program_iv(program,0x8B82,C.byref(ok)); program_log(program,len(log),None,log)
assert ok.value,log.value.decode()
use(program)
for name in ['SableWaterOcclusionEnabled','SableCloseSampler','SableFarSampler','aste_WaveShore']:
    assert loc(name)>=0, f'Native wave/mask uniform inactive: {name}'
# Original Deep Seas uses 8/9; AeroWorks' shore map stays on 7.
u1i(loc('SableCloseSampler'),8); u1i(loc('SableFarSampler'),9)
u1i(loc('aste_WaveShore'),7); active_tex(0x84C7); bind_tex(0x0DE1,tex)
u1i(loc('u_LightTex'),1); u1i(loc('u_BlockTex'),2); active_tex(0x84C2); bind_tex(0x0DE1,light)
u1f(loc('aste_WaveEnabled'),1); u3f(loc('aste_WaveGrid'),0,0,0)
for name in ['aste_WaveStill','aste_WaveFlow','aste_WaveOverlay']: u4f(loc(name),.1,.1,.2,.2)
u3f(loc('u_RegionOffset'),248,48,248)
u1f(loc('u_FogStart'),4000); u1f(loc('u_FogEnd'),5000)
umat(loc('u_ModelViewMatrix'),1,0,identity)
projection=(F*16)(1/16,0,0,0, 0,0,.2,0, 0,1/16,0,0, -16,-16,-(48+y*32/(1<<20)-8)*.2,1)
umat(loc('u_ProjectionMatrix'),1,0,projection)
vao2=U(); gen_vao(1,C.byref(vao2)); bind_vao(vao2)
positions=[]
for vx,vz in [(-8,-8),(23.99997,-8),(23.99997,23.99997),(-8,-8),(23.99997,23.99997),(-8,23.99997)]:
    qx,qz=q(vx),q(vz)
    positions += [(qx>>10)|((y>>10)<<10)|((qz>>10)<<20),(qx&1023)|((y&1023)<<10)|((qz&1023)<<20)]
vbo=U(); gen_buffers(1,C.byref(vbo)); bind_buffer(0x8892,vbo)
buffer_data(0x8892,len(positions)*4,(U*len(positions))(*positions),0x88E4)
enable_attr=bind(gl,'glEnableVertexAttribArray',None,U)
attr_pointer=bind(gl,'glVertexAttribIPointer',None,U,I,U,I,P)
enable_attr(0); attr_pointer(0,2,0x1405,0,None)
attr_color=bind(gl,'glVertexAttrib4f',None,U,F,F,F,F); attr_color(1,1,1,1,1)
attr2(2,round(.15*32768)+1,round(.15*32768)+1); attr4(3,128,128,0,0)
disable=bind(gl,'glDisable',None,U)
for flag in [0x8C89,0x0B44,0x0B71,0x0BE2]: disable(flag)
size=64
out_tex=U(); gen_tex(1,C.byref(out_tex)); active_tex(0x84C3); bind_tex(0x0DE1,out_tex)
tex_image(0x0DE1,0,0x8814,size,size,0,0x1908,0x1406,None)
fbo=U(); gen_fbo(1,C.byref(fbo)); bind_fbo(0x8D40,fbo)
fbo_tex(0x8D40,0x8CE0,0x0DE1,out_tex,0); assert check_fbo(0x8D40)==0x8CD5
viewport(0,0,size,size)
close_tex,far_tex=U(),U(); gen_tex(1,C.byref(close_tex)); gen_tex(1,C.byref(far_tex))
clear_color=bind(gl,'glClearColor',None,F,F,F,F); clear=bind(gl,'glClear',None,U)
clear_color(0,0,0,0)

def render_mask(angle,phase,mask_enabled=True,empty=False,foam=False):
    c,s=math.cos(angle),math.sin(angle)
    front,back=(F*(size*size))(),(F*(size*size))()
    expected=[]
    for iz in range(size):
        for ix in range(size):
            px,pz=(ix+.5)*2/size-1,(iz+.5)*2/size-1
            # A fractional, rotated compartment, deliberately not aligned to block/pixel edges.
            rx,rz=c*(px-.137)+s*(pz+.191),-s*(px-.137)+c*(pz+.191)
            inside=abs(rx)<.43 and abs(rz)<.183 and not empty
            i=iz*size+ix
            front[i]=.32+.05*px if inside else 1
            back[i]=.68+.05*pz if inside else 1
            expected.append(0 if inside and mask_enabled else 1)
    for unit,texture_id,pixels in [(8,close_tex,front),(9,far_tex,back)]:
        active_tex(0x84C0+unit); bind_tex(0x0DE1,texture_id)
        tex_param(0x0DE1,0x2801,0x2600); tex_param(0x0DE1,0x2800,0x2600)
        tex_image(0x0DE1,0,0x822E,size,size,0,0x1903,0x1406,pixels)
    u1f(loc('SableWaterOcclusionEnabled'),1 if mask_enabled else 0)
    u4f(loc('aste_WaveProfile'),0,0,phase,.5)
    clear(0x4000); draw(4,0,6)
    pixels=(F*(size*size*4))(); read(0,0,size,size,0x1908,0x1406,pixels)
    assert error()==0
    for i,wanted in enumerate(expected):
        if foam and wanted:
            assert .5999<=pixels[4*i+3]<=.8501,(i,pixels[4*i+3])
        else:
            assert abs(pixels[4*i+3]-wanted)<1e-4,(angle,phase,i,pixels[4*i+3],wanted)
    if foam:render_mask.last_pixels=tuple(pixels)
    return sum(1 for p in expected if p==0)

for degrees in [0,37,81]:
    for phase in [0,math.pi/2,-math.pi/2]:
        assert render_mask(math.radians(degrees),phase)>50
render_mask(.47,math.pi/2,mask_enabled=False)
render_mask(.47,-math.pi/2,empty=True)
print('Original Deep Seas mask: rotated fractional hull edges, crest/trough, no mask/empty mask and units 7/8/9 verified with real Sodium water on GPU')

# Native hull clipping also remains exact with the detailed wave path active on unit 6.
wind_tex=U();gen_tex(1,C.byref(wind_tex));active_tex(0x84C6);bind_tex(0x0DE1,wind_tex)
tex_param(0x0DE1,0x2801,0x2600);tex_param(0x0DE1,0x2800,0x2600)
wind_data=(F*(11*11*4))()
for i in range(11*11):wind_data[4*i],wind_data[4*i+1],wind_data[4*i+2],wind_data[4*i+3]=1,1,-10,1
tex_image(0x0DE1,0,0x8814,11,11,0,0x1908,0x1406,wind_data)
u1i(loc('aste_WaveWind'),6);u3f(loc('aste_WaveWindGrid'),0,0,0)
u1f(loc('aste_WaveDetailed'),1);u4f(loc('aste_WaveDetail'),16,0,.65,0)
active_tex(0x84C7);bind_tex(0x0DE1,tex)
depth_field=(F*(129*129*4))()
for i in range(129*129):depth_field[4*i],depth_field[4*i+1],depth_field[4*i+2]=63,1,32
tex_image(0x0DE1,0,0x8814,129,129,0,0x1908,0x1406,depth_field)
for degrees in [0,37,81]:
    for phase in [math.pi/2,-math.pi/2]:assert render_mask(math.radians(degrees),phase)>50
print('Detailed wave/wind path: original hull masking intact with independent texture units 6/7/8/9')

# Crest foam must whiten/tint actual water before fog, preserve troughs and retain hull holes.
water_tex=U();gen_tex(1,C.byref(water_tex));active_tex(0x84C2);bind_tex(0x0DE1,water_tex)
tex_param(0x0DE1,0x2801,0x2600);tex_param(0x0DE1,0x2800,0x2600)
tex_image(0x0DE1,0,0x8814,1,1,0,0x1908,0x1406,(F*4)(.05,.2,.5,.6))
u1f(loc('aste_FoamStrength'),.85);u2f(loc('aste_FoamOrigin'),0,0)
assert render_mask(.64,math.pi/2,foam=True)>50
crest_pixels=render_mask.last_pixels
assert render_mask(.64,-math.pi/2,foam=True)>50
trough_pixels=render_mask.last_pixels
crest_red=[crest_pixels[i] for i in range(0,len(crest_pixels),4) if crest_pixels[i+3]>.1]
trough_red=[trough_pixels[i] for i in range(0,len(trough_pixels),4) if trough_pixels[i+3]>.1]
assert sum(crest_red)/len(crest_red)>sum(trough_red)/len(trough_red)+.15
assert max(crest_red)-min(crest_red)>.05, 'Foam must have patchy detail, not uniform white bands'
assert abs(sum(trough_red)/len(trough_red)-.05)<1e-4, 'Trough water tint changed'
u1f(loc('aste_FoamStrength'),0)
render_mask(.64,math.pi/2,foam=True)
assert max(render_mask.last_pixels[::4])<.051, 'Foam disable must restore native water colour'
print('Crest foam: whitened irregular crest, native trough colour, disable and rotated hull-depth clipping verified on GPU; no particles')

# Exercise the detailed height function itself against CPU fixtures, so shore vertices,
# air-cell buoyancy and solid-block buoyancy cannot quietly use different spectra.
vertex='#version 330 core\n'+wave+'''
uniform vec3 testPosition;
uniform float testDepth;
out vec3 result;
void main(){result=vec3(0,aste_waveHeight(testPosition.xz,testDepth),0);gl_Position=vec4(0,0,0,1);}
'''
names=(C.c_char_p*1)(b'result')
program=create_program();attach(program,shader(0x8B31,vertex));feedback(program,1,names,0x8C8C);link(program)
program_iv(program,0x8B82,C.byref(ok));program_log(program,len(log),None,log)
assert ok.value,log.value.decode()
use(program);enable(0x8C89)
bind_vao(vao);bind_buffer(0x8C8E,buffer);buffer_data(0x8C8E,12,None,0x88E9);bind_base(0x8C8E,0,buffer)
u3i=bind(gl,'glUniform3i',None,I,I,I,I);u1ui=bind(gl,'glUniform1ui',None,I,U)
u1f(loc('aste_WaveDetailed'),1);u4f(loc('aste_WaveDetail'),16,.2,.65,.35)
u1ui(loc('aste_WaveSeed'),0x12345678)
wind_tex=U();gen_tex(1,C.byref(wind_tex));active_tex(0x84C6);bind_tex(0x0DE1,wind_tex)
tex_param(0x0DE1,0x2801,0x2600);tex_param(0x0DE1,0x2800,0x2600);u1i(loc('aste_WaveWind'),6)
def spectrum_band(name,cx,cz,t,length,speed,angle):
    k=2*math.pi/length
    phase=math.remainder(k*(cx*math.cos(angle)+cz*math.sin(angle)-speed*t),2*math.pi)
    u4f(loc(name),k*math.cos(angle),k*math.sin(angle),phase,.5 if name=='aste_WaveProfile' else 0)
def spectrum_noise(band,cx,cz,t,scale,period):
    x,z,v=cx/scale,cz/scale,t/period
    ix,iz,it=math.floor(x),math.floor(z),math.floor(v)
    u4f(loc('aste_WaveNoise'+band),x-ix,z-iz,v-it,1/scale)
    u3i(loc('aste_WaveNoiseOrigin'+band),ix,iz,it)
maximum_error=0
for row in Path('build/wave-test/spectrum.txt').read_text().splitlines():
    cx,cz,px,pz,t,ratio,depth,expected=map(float,row.split())
    data=(F*(11*11*4))()
    for i in range(11*11):data[i*4],data[i*4+1],data[i*4+2],data[i*4+3]=ratio,ratio,-10,1
    tex_image(0x0DE1,0,0x8814,11,11,0,0x1908,0x1406,data)
    ox,oz=(math.floor(cx/64)-5)*64,(math.floor(cz/64)-5)*64
    u3f(loc('aste_WaveWindGrid'),cx-ox,cz-oz,0)
    spectrum_band('aste_WaveProfile',cx,cz,t,32,4,math.radians(35))
    spectrum_band('aste_WaveChopA',cx,cz,t,8,4*1.7,math.radians(35)+.7)
    spectrum_band('aste_WaveChopB',cx,cz,t,8/1.37,4*1.7*.83,math.radians(35)-1.1)
    spectrum_noise('A',cx,cz,t,48,24);spectrum_noise('B',cx,cz,t,48*.35,24*.23)
    u3f(loc('testPosition'),px,0,pz);u1f(loc('testDepth'),depth)
    begin_tf(0);draw(0,0,1);end_tf();out=(F*3)();get_buffer(0x8C8E,0,12,out)
    assert error()==0
    difference=abs(out[1]-expected);maximum_error=max(maximum_error,difference)
    assert difference<3e-5,(row,out[1],difference)
print(f'Detailed wave spectrum: 64 CPU/GPU matches, weak/storm wind, depth, noisy chop and distant/negative coordinates; maximum error {maximum_error:.2g}')
