"""Render the actual cloud fog GLSL on a headless EGL context (Mesa/llvmpipe in CI)."""
import ctypes as C
import os
from pathlib import Path

os.environ.setdefault('EGL_PLATFORM', 'surfaceless')
os.environ.setdefault('LIBGL_ALWAYS_SOFTWARE', '1')
egl = C.CDLL('libEGL.so.1')
gl = C.CDLL('libGL.so.1')

def bind(lib, name, result, *args):
    fn = getattr(lib, name)
    fn.restype, fn.argtypes = result, args
    return fn

P, I, U, F = C.c_void_p, C.c_int, C.c_uint, C.c_float
get_display = bind(egl, 'eglGetDisplay', P, P)
initialize = bind(egl, 'eglInitialize', U, P, C.POINTER(I), C.POINTER(I))
choose = bind(egl, 'eglChooseConfig', U, P, C.POINTER(I), C.POINTER(P), I, C.POINTER(I))
api = bind(egl, 'eglBindAPI', U, U)
surface = bind(egl, 'eglCreatePbufferSurface', P, P, P, C.POINTER(I))
context = bind(egl, 'eglCreateContext', P, P, P, P, C.POINTER(I))
current = bind(egl, 'eglMakeCurrent', U, P, P, P, P)
display = get_display(None)
assert initialize(display, None, None), 'EGL initialization failed'
attrs = (I * 11)(0x3033, 1, 0x3040, 8, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3038)
config, count = P(), I()
assert choose(display, attrs, C.byref(config), 1, C.byref(count)) and count.value
assert api(0x30A2)
pbuffer = surface(display, config, (I * 5)(0x3057, 1, 0x3056, 1, 0x3038))
ctx = context(display, config, None, (I * 7)(0x3098, 3, 0x30FB, 2, 0x30FD, 1, 0x3038))
assert ctx and pbuffer and current(display, pbuffer, pbuffer, ctx), 'EGL context failed'

create_shader = bind(gl, 'glCreateShader', U, U)
shader_source = bind(gl, 'glShaderSource', None, U, I, C.POINTER(C.c_char_p), P)
compile_shader = bind(gl, 'glCompileShader', None, U)
shader_iv = bind(gl, 'glGetShaderiv', None, U, U, C.POINTER(I))
shader_log = bind(gl, 'glGetShaderInfoLog', None, U, I, P, P)
create_program = bind(gl, 'glCreateProgram', U)
attach = bind(gl, 'glAttachShader', None, U, U)
link = bind(gl, 'glLinkProgram', None, U)
program_iv = bind(gl, 'glGetProgramiv', None, U, U, C.POINTER(I))
program_log = bind(gl, 'glGetProgramInfoLog', None, U, I, P, P)
use = bind(gl, 'glUseProgram', None, U)
location = bind(gl, 'glGetUniformLocation', I, U, C.c_char_p)
u1i = bind(gl, 'glUniform1i', None, I, I)
u1f = bind(gl, 'glUniform1f', None, I, F)
u3f = bind(gl, 'glUniform3f', None, I, F, F, F)
umat = bind(gl, 'glUniformMatrix4fv', None, I, I, U, C.POINTER(F))
gen_tex = bind(gl, 'glGenTextures', None, I, C.POINTER(U))
active_tex = bind(gl, 'glActiveTexture', None, U)
bind_tex = bind(gl, 'glBindTexture', None, U, U)
tex_image = bind(gl, 'glTexImage2D', None, U, I, I, I, I, I, U, U, P)
tex_param = bind(gl, 'glTexParameteri', None, U, U, I)
gen_fbo = bind(gl, 'glGenFramebuffers', None, I, C.POINTER(U))
bind_fbo = bind(gl, 'glBindFramebuffer', None, U, U)
fbo_tex = bind(gl, 'glFramebufferTexture2D', None, U, U, U, U, I)
check_fbo = bind(gl, 'glCheckFramebufferStatus', U, U)
gen_vao = bind(gl, 'glGenVertexArrays', None, I, C.POINTER(U))
bind_vao = bind(gl, 'glBindVertexArray', None, U)
viewport = bind(gl, 'glViewport', None, I, I, I, I)
draw = bind(gl, 'glDrawArrays', None, U, I, I)
read = bind(gl, 'glReadPixels', None, I, I, I, I, U, U, P)
error = bind(gl, 'glGetError', U)

def shader(kind, source):
    result = create_shader(kind)
    text = C.c_char_p(source.encode())
    shader_source(result, 1, C.byref(text), None)
    compile_shader(result)
    ok = I()
    shader_iv(result, 0x8B81, C.byref(ok))
    log = C.create_string_buffer(8192)
    shader_log(result, len(log), None, log)
    assert ok.value, log.value.decode()
    return result

vertex = '''#version 150
out vec2 texCoord;
void main() {
 vec2 points[3] = vec2[3](vec2(-1,-1), vec2(3,-1), vec2(-1,3));
 vec2 p=points[gl_VertexID]; texCoord=p*.5+.5; gl_Position=vec4(p,0,1);
}'''
root = Path(__file__).resolve().parents[1]
fragment = (root / 'src/main/resources/assets/aste_aeroworks/shaders/program/cloud_fog.fsh').read_text()
program = create_program()
attach(program, shader(0x8B31, vertex))
attach(program, shader(0x8B30, fragment))
link(program)
ok = I()
program_iv(program, 0x8B82, C.byref(ok))
log = C.create_string_buffer(8192)
program_log(program, len(log), None, log)
assert ok.value, log.value.decode()
use(program)
vao = U()
gen_vao(1, C.byref(vao))
bind_vao(vao)
viewport(0, 0, 1, 1)

def texture(unit, values):
    result = U()
    gen_tex(1, C.byref(result))
    active_tex(0x84C0 + unit)
    bind_tex(0x0DE1, result)
    tex_param(0x0DE1, 0x2801, 0x2600)
    tex_param(0x0DE1, 0x2800, 0x2600)
    data = (F * len(values))(*values)
    tex_image(0x0DE1, 0, 0x8814 if len(values) == 4 else 0x822E, 1, 1, 0,
              0x1908 if len(values) == 4 else 0x1903, 0x1406, data)
    return result

original = (.2, .4, .6, .8)
fog = (.8, .85, .9)
color_texture = texture(0, original)
main_texture = texture(1, [1])
dh_texture = texture(2, [1])
out_texture = texture(3, [0, 0, 0, 0])
cloud_texture = texture(4, [0, 0, 0, 0])
accum_texture = texture(5, [0, 0, 0, 0])
reveal_texture = texture(6, [1])
fbo = U()
gen_fbo(1, C.byref(fbo))
bind_fbo(0x8D40, fbo)
fbo_tex(0x8D40, 0x8CE0, 0x0DE1, out_texture, 0)
assert check_fbo(0x8D40) == 0x8CD5
for name, unit in [('DiffuseSampler', 0), ('MainDepthSampler', 1), ('DhDepthSampler', 2), ('CloudsSampler', 4), ('AccumSampler', 5), ('RevealSampler', 6)]:
    u1i(location(program, name.encode()), unit)
u1f(location(program, b'Visibility'), 24)
u3f(location(program, b'FogColor'), *fog)

def inverse_projection(near, far):
    a = (far + near) / (near - far)
    b = 2 * far * near / (near - far)
    return (F * 16)(1,0,0,0, 0,1,0,0, 0,0,0,1/b, 0,0,-1,a/b)

umat(location(program, b'InverseProjection'), 1, 0, inverse_projection(.1, 2048))
umat(location(program, b'InverseDhProjection'), 1, 0, inverse_projection(16, 65536))

def depth(distance, near, far):
    return 1 if distance is None else far * (distance - near) / (distance * (far - near))

def upload(unit, tex, value):
    active_tex(0x84C0 + unit)
    bind_tex(0x0DE1, tex)
    tex_image(0x0DE1, 0, 0x822E, 1, 1, 0, 0x1903, 0x1406, (F * 1)(value))

def render(main, dh, amount, expected_alpha, label):
    upload(1, main_texture, depth(main, .1, 2048))
    upload(2, dh_texture, depth(dh, 16, 65536))
    u1i(location(program, b'HasDhDepth'), int(dh is not None))
    u1f(location(program, b'Strength'), amount)
    draw(4, 0, 3)
    pixels = (F * 4)()
    read(0, 0, 1, 1, 0x1908, 0x1406, pixels)
    expected = [a + (b - a) * expected_alpha for a, b in zip(original[:3], fog)] + [1.0]
    assert max(abs(a-b) for a,b in zip(pixels, expected)) < .0001, (label, list(pixels), expected)
    assert error() == 0, label

render(200, None, 1, 1, 'ordinary distant chunks')
render(1, None, 1, 0, 'near geometry stays visible')
render(13.2, None, 1, .5, 'depth controls fog inside visibility range')
render(None, 200, 1, 1, 'DH-only far geometry')
render(8, 100, 1, ((8-2.4)/21.6)**2*(3-2*(8-2.4)/21.6), 'different projection depths: near main beats raw-nearer DH')
render(200, 20, 1, ((20-2.4)/21.6)**2*(3-2*(20-2.4)/21.6), 'DH depth selected when nearer')
render(200, 100, 0, 0, 'no cloud leaves scene unchanged')
render(200, 100, .1, .1, 'weak cloud opacity independent of render distance')
render(None, None, 1, 1, 'sky fogged too')
# A cloud that is in front of terrain must cover it, even with fog disabled.
active_tex(0x84C0 + 4)
bind_tex(0x0DE1, cloud_texture)
tex_image(0x0DE1, 0, 0x8814, 1, 1, 0, 0x1908, 0x1406, (F * 4)(.7,.6,.5,1))
def check_cloud(main, dh, expected, label):
    upload(1, main_texture, depth(main,.1,2048))
    upload(2, dh_texture, depth(dh,16,65536))
    u1i(location(program,b'HasDhDepth'),1)
    u1f(location(program,b'Strength'),0)
    draw(4,0,3)
    pixels=(F*4)()
    read(0,0,1,1,0x1908,0x1406,pixels)
    assert max(abs(a-b) for a,b in zip(pixels,expected)) < .0001, (label,list(pixels))
check_cloud(1000,100,(.7,.6,.5,1),'cloud covers later Sodium terrain')
check_cloud(10,100,(*original[:3],1),'near terrain occludes clouds')
u1i(location(program,b'HasTransparency'),1)
active_tex(0x84C0+5)
bind_tex(0x0DE1,accum_texture)
tex_image(0x0DE1,0,0x8814,1,1,0,0x1908,0x1406,(F*4)(.2,.4,.6,1))
upload(6,reveal_texture,.5)
check_cloud(1000,100,(.45,.5,.55,1),'weighted transparent cloud layer')
check_cloud(10,100,(*original[:3],1),'near terrain also occludes transparent clouds')
# Exercise BOTH post passes, including the JSON blend state and clearing the
# destination like PostPass.process. Shader-only tests missed alpha-zero sky/DH.
import json
resource_root = root / 'src/main/resources/assets/aste_aeroworks/shaders'
post = json.loads((resource_root / 'post/cloud_fog.json').read_text())
copy_name = post['passes'][1]['name'].split(':')[-1]
assert copy_name == 'cloud_fog_copy', 'Vanilla blit alpha-blends into a cleared target'
copy_config = json.loads((resource_root / ('program/' + copy_name + '.json')).read_text())
copy_fragment = (resource_root / ('program/' + copy_config['fragment'].split(':')[-1] + '.fsh')).read_text()
copy_program = create_program()
attach(copy_program, shader(0x8B31, vertex))
attach(copy_program, shader(0x8B30, copy_fragment))
link(copy_program)
program_iv(copy_program, 0x8B82, C.byref(ok))
assert ok.value, 'copy program link failed'
final_texture = texture(7, [0,0,0,0])
final_fbo = U()
gen_fbo(1, C.byref(final_fbo))
bind_fbo(0x8D40, final_fbo)
fbo_tex(0x8D40, 0x8CE0, 0x0DE1, final_texture, 0)
assert check_fbo(0x8D40) == 0x8CD5
enable = bind(gl,'glEnable',None,U)
disable = bind(gl,'glDisable',None,U)
blend_func = bind(gl,'glBlendFunc',None,U,U)
clear_color = bind(gl,'glClearColor',None,F,F,F,F)
clear = bind(gl,'glClear',None,U)
factors={'one':1,'zero':0,'srcalpha':0x0302,'1-srcalpha':0x0303}

def full_chain(source_alpha, amount, has_dh, label):
    use(program)
    disable(0x0BE2)
    bind_fbo(0x8D40,fbo)
    active_tex(0x84C0)
    bind_tex(0x0DE1,color_texture)
    tex_image(0x0DE1,0,0x8814,1,1,0,0x1908,0x1406,(F*4)(*original[:3],source_alpha))
    # Cloud buffers must not replace sky/LOD pixels without actual cloud color.
    active_tex(0x84C0+4)
    bind_tex(0x0DE1,cloud_texture)
    tex_image(0x0DE1,0,0x8814,1,1,0,0x1908,0x1406,(F*4)(0,0,0,0))
    upload(1,main_texture,1)
    upload(2,dh_texture,depth(200,16,65536) if has_dh else 1)
    u1i(location(program,b'HasDhDepth'),has_dh)
    u1i(location(program,b'HasTransparency'),0)
    u1f(location(program,b'Strength'),amount)
    draw(4,0,3)
    bind_fbo(0x8D40,final_fbo)
    clear_color(0,0,0,0)
    clear(0x00004000)
    use(copy_program)
    active_tex(0x84C0+3)
    bind_tex(0x0DE1,out_texture)
    u1i(location(copy_program,b'DiffuseSampler'),3)
    enable(0x0BE2)
    blend_func(factors[copy_config['blend']['srcrgb']],factors[copy_config['blend']['dstrgb']])
    draw(4,0,3)
    result=(F*4)()
    read(0,0,1,1,0x1908,0x1406,result)
    expected=[a+(b-a)*amount for a,b in zip(original[:3],fog)]+[1]
    assert max(abs(a-b) for a,b in zip(result,expected))<.0001,(label,list(result),expected)
    assert error()==0,label

full_chain(0,0,0,'alpha-zero sky survives both passes outside clouds')
full_chain(0,0,1,'alpha-zero DH RGB survives both passes outside clouds')
full_chain(.2,0,1,'partial scene alpha does not dim RGB')
full_chain(1,0,0,'opaque chunks retain brightness')
full_chain(0,1,1,'dense fog covers alpha-zero DH')
full_chain(0,.25,1,'weak fog preserves alpha-zero scene brightness')
print('Cloud fog GLSL: 19 software OpenGL checks passed (including 6 full-chain checks)')
