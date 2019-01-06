package com.ccf.encode_decode.encode.camera.nopreview;

import android.graphics.SurfaceTexture;
import android.util.Log;

public class SurfaceTextureManager implements SurfaceTexture.OnFrameAvailableListener {

    final String TAG = "SurfaceTextureManager";

    // 根据textureId创建出来的SurfaceTexture，将传递给Camera的预览SurfaceTexture。
    private SurfaceTexture mSurfaceTexture;
    private GLESRender mGlesRender;

    private Object mFrameSyncObject = new Object();     // guards mFrameAvailable

    private boolean mFrameAvailable;

    /**
     * Creates instances of TextureRender and SurfaceTexture.
     */
    public SurfaceTextureManager() {
        mGlesRender = new GLESRender();
        // 创建GLES环境，生成TextureId
        mGlesRender.surfaceCreated();
        // 根据TextureId创建SurfaceTexture
        mSurfaceTexture = new SurfaceTexture(mGlesRender.getTextureId());

        // This doesn't work if this object is created on the thread that CTS started for
        // these test cases.
        //
        // The CTS-created thread has a Looper, and the SurfaceTexture constructor will
        // create a Handler that uses it.  The "frame available" message is delivered
        // there, but since we're not a Looper-based thread we'll never see it.  For
        // this to do anything useful, OutputSurface must be created on a thread without
        // a Looper, so that SurfaceTexture uses the main application Looper instead.
        //
        // Java language note: passing "this" out of a constructor is generally unwise,
        // but we should be able to get away with it here.
        mSurfaceTexture.setOnFrameAvailableListener(this);
        // 添加SurfaceTexture的Frame监听事件
    }

    public void release() {
        // this causes a bunch of warnings that appear harmless but might confuse someone:
        // W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
        // mSurfaceTexture.release();
        mGlesRender = null;
        mSurfaceTexture = null;
    }

    /**
     * Returns the SurfaceTexture.
     */
    public SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    /**
     * Replaces the fragment shader.
     */
    public void changeFragmentShader(String fragmentShader) {
        mGlesRender.changeFragmentShader(fragmentShader);
    }

    /**
     * Latches the next buffer into the texture.  Must be called from the thread that created
     * the OutputSurface object.
     */
    public void awaitNewImage() {
        final int TIMEOUT_MS = 2500;

        synchronized (mFrameSyncObject) {
            while (!mFrameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    mFrameSyncObject.wait(TIMEOUT_MS);
                    if (!mFrameAvailable) {
                        // TODO: if "spurious wakeup", continue while loop
                        throw new RuntimeException("Camera frame wait timed out");
                    }
                } catch (InterruptedException ie) {
                    // shouldn't happen
                    throw new RuntimeException(ie);
                }
            }
            mFrameAvailable = false;
        }

        // Latch the data.
        mGlesRender.checkGlError("before updateTexImage");
        /**
         * Update the texture image to the most recent frame from the image stream.
         */
        // 更新传递给SurfaceTexture的TexImage，之后调用 drawFrame，绘制滤镜效果。
        mSurfaceTexture.updateTexImage();
        /**
         * Draws the data from SurfaceTexture onto the current EGL surface.
         */
        mGlesRender.drawFrame(mSurfaceTexture);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        synchronized (mFrameSyncObject) {
            Log.d(TAG, "onFrameAvailable: ");
            if (mFrameAvailable) {
                throw new RuntimeException("mFrameAvailable already set, frame could be dropped");
            }
            mFrameAvailable = true;
            mFrameSyncObject.notifyAll();
        }
    }
}
