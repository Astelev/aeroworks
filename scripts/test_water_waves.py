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
