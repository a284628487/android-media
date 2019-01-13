package com.ccf.encode_decode.encode.fbocamera.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Field;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.opengles.GL10;

// com.ccf.encode_decode.encode.fbocamera.camera.CameraGLSurfaceView
public class CameraGLSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer, CameraFboRender.OnSurfaceTextureCreatedCallback {

    private CameraHelper cameraHelper;
    private CameraFboRender render;
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private int textureId;

    public CameraGLSurfaceView(Context context) {
        super(context);
        init();
    }

    public CameraGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(2);

        cameraHelper = new CameraHelper(getContext());
        render = new CameraFboRender(getContext());
        render.setOnSurfaceCreatedCallback(this);
        setRenderer(render);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        previewAngle(getContext());

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraHelper != null) {
                    cameraHelper.autoFocus();
                }
            }
        });
    }

    public void onDestroy() {
        if (cameraHelper != null) {
            cameraHelper.stopPrive();
        }
    }

    public int getCameraPrivewWidth() {
        return cameraHelper.getPreviewWidth();
    }

    public int getCameraPrivewHeight() {
        return cameraHelper.getPreviewHeight();
    }

    public void previewAngle(Context context) {
        int angle = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        render.resetMatirx();
        switch (angle) {
            case Surface.ROTATION_0:
                if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    render.setAngle(90, 0, 0, 1);
                    render.setAngle(180, 1, 0, 0);
                } else {
                    render.setAngle(90f, 0f, 0f, 1f);
                }

                break;
            case Surface.ROTATION_90:
                if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    render.setAngle(180, 0, 0, 1);
                    render.setAngle(180, 0, 1, 0);
                } else {
                    render.setAngle(90f, 0f, 0f, 1f);
                }
                break;
            case Surface.ROTATION_180:
                if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    render.setAngle(90f, 0.0f, 0f, 1f);
                    render.setAngle(180f, 0.0f, 1f, 0f);
                } else {
                    render.setAngle(-90, 0f, 0f, 1f);
                }
                break;
            case Surface.ROTATION_270:
                if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    render.setAngle(180f, 0.0f, 1f, 0f);
                } else {
                    render.setAngle(0f, 0f, 0f, 1f);
                }
                break;
        }
    }

    public int getTextureId() {
        return textureId;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
    }

    @Override
    public void onDrawFrame(GL10 gl) {
    }

    @Override
    public void onSurfaceCreated(SurfaceTexture surfaceTexture, int fboTextureId) {
        cameraHelper.startCamera(surfaceTexture, cameraId);
        this.textureId = fboTextureId;
    }

    public EGLContext getEglContext() {
        // GLSurfaceView.mGLThread
        // GLThread.mEglHelper
        // EglHelper.mEglContext
        try {
            Field field = GLSurfaceView.class.getDeclaredField("mGLThread");
            field.setAccessible(true);
            Object threadObj = field.get(this);

            field = threadObj.getClass().getDeclaredField("mEglHelper");
            field.setAccessible(true);
            Object eglHelper = field.get(threadObj);

            field = eglHelper.getClass().getDeclaredField("mEglContext");
            field.setAccessible(true);
            Object eglContext = field.get(eglHelper);

            return (EGLContext) eglContext;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
