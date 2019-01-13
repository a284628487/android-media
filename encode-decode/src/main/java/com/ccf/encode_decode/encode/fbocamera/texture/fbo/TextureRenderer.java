package com.ccf.encode_decode.encode.fbocamera.texture.fbo;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.ccf.encode_decode.R;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class TextureRenderer implements GLSurfaceView.Renderer {

    private BitmapFboTexture bitmapFboTexture;

    private BitmapRenderTexture bitmapRenderTexture;

    public TextureRenderer(Context context) {

        bitmapFboTexture = new BitmapFboTexture(context);

        bitmapFboTexture.setBitmap(BitmapFactory.decodeResource(context.getResources(), R.drawable.yield_fetch));

        bitmapRenderTexture = new BitmapRenderTexture(context);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        String vertexSource = "attribute vec4 av_Position;//顶点位置\n" +
                "attribute vec2 af_Position;//纹理位置\n" +
                "varying vec2 v_texPo;//纹理位置  与fragment_shader交互\n" +
                "void main() {\n" +
                "    v_texPo = af_Position;\n" +
                "    gl_Position = av_Position;\n" +
                "}";
        String fragmentSource = "precision mediump float;//精度 为float\n" +
                "varying vec2 v_texPo;//纹理位置  接收于vertex_shader\n" +
                "uniform sampler2D sTexture;//纹理\n" +
                "void main() {\n" +
                "    gl_FragColor=texture2D(sTexture, v_texPo);\n" +
                "}";

        bitmapFboTexture.onSurfaceCreated(vertexSource, fragmentSource);

        bitmapRenderTexture.onSurfaceCreated(vertexSource, fragmentSource);
        
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        //宽高
        GLES20.glViewport(0, 0, width, height);

        bitmapFboTexture.onSurfaceChanged(width, height);
        bitmapRenderTexture.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        //清空颜色
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        //设置背景颜色
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        //FBO处理
        bitmapFboTexture.draw();
        //通过FBO处理之后，拿到纹理id，然后渲染
        bitmapRenderTexture.draw(bitmapFboTexture.getFboTextureId());
    }
}
