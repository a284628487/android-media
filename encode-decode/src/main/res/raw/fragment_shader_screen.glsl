#extension GL_OES_EGL_image_external : require //申明使用扩展纹理
precision mediump float;//精度 为float
varying vec2 v_texPo;//纹理位置  接收于vertex_shader
uniform sampler2D  sTexture;
void main() {
   if(v_texPo.x < 0.5) {
       vec4 old = texture2D(sTexture, v_texPo);
       gl_FragColor=vec4(old.g / 2.0, old.g / 2.0, old.g / 2.0, old.a);
   }else{
       gl_FragColor=texture2D(sTexture, v_texPo);
    }
}