"""Compile the exact Sodium terrain shader, then exercise wave vertices on Mesa."""
from pathlib import Path
import math
# Reuse only the EGL/GL setup helpers, without running the fog tests.
exec(Path('scripts/test_cloud_fog_shader.py').read_text().split("vertex = '''")[0])
source=Path('build/wave-test/terrain.vsh').read_text()
shader(0x8B31, source)
shader(0x8B31, source.replace('#define USE_FOG',''))
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
    for i in range(129*129): pixels[2*i],pixels[2*i+1]=height,weight
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
u2f(loc('testUV'),.8,.8); near(sample(phase=math.pi/2)[1],height)
u2f(loc('testUV'),.15,.15); field(0); near(sample(phase=math.pi/2)[1],height)
field(1); u1f(loc('aste_WaveEnabled'),0); near(sample(phase=math.pi/2)[1],height)
print('Actual Sodium GLSL compiled; crest, trough, zero crossing, shore, map edge, other heights/materials and disable verified on GPU')
