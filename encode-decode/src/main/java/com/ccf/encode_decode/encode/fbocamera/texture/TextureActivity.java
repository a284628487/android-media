package com.ccf.encode_decode.encode.fbocamera.texture;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.ccf.encode_decode.encode.fbocamera.texture.fbo.TextureRenderer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class TextureActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private TextureRenderer bitmapTexture;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GLSurfaceView sv = new GLSurfaceView(this);
        setContentView(sv);
        sv.setEGLContextClientVersion(2);
        sv.setRenderer(this);
        sv.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        bitmapTexture = new TextureRenderer(this);
        bitmapTexture.onSurfaceCreated(gl, config);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        //清空颜色
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        bitmapTexture.onDrawFrame(gl);
    }
}
