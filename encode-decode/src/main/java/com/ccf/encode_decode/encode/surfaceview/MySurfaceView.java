package com.ccf.encode_decode.encode.surfaceview;

import android.content.Context;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.ccf.encode_decode.utils.EGLHelper;

public class MySurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private EGLHelper mHelper;
    private EGLThread mThread;
    private Object mLock = new Object();
    private Surface mSurface;

    public MySurfaceView(Context context) {
        super(context);
        init();
    }

    public MySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MySurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        SurfaceHolder sh = getHolder();
        sh.addCallback(this);
        //
        mThread = new EGLThread();
        mThread.start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (mLock) {
            surfaceCreated = true;
            mSurface = holder.getSurface();
            mLock.notify();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceDestroyed = true;
    }

    private boolean surfaceCreated = false;
    private boolean surfaceDestroyed = false;
    private int index = 0;

    private class EGLThread extends Thread {
        @Override
        public void run() {
            super.run();
            while (true) {
                while (!surfaceCreated) {
                    synchronized (mLock) {
                        try {
                            mLock.wait();
                        } catch (InterruptedException e) {
                        }
                        mHelper = new EGLHelper(mSurface);
                        mHelper.makeCurrent();
                    }
                }
                // draw
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (mWidth > 0) {
                    drawSurfaceFrame(index++);
                    //
                    mHelper.swapBuffers();
                    //
                }
                if (surfaceDestroyed) {
                    mHelper.release();
                    break;
                }
            }
        }
    }

    // size of a frame, in pixels
    private int mWidth = -1;
    private int mHeight = -1;

    // 两组颜色值
    private static final int TEST_R0 = 0;
    private static final int TEST_G0 = 136;
    private static final int TEST_B0 = 0;
    //
    private static final int TEST_R1 = 236;
    private static final int TEST_G1 = 50;
    private static final int TEST_B1 = 186;

    private void drawSurfaceFrame(int frameIndex) {
        frameIndex %= 8;

        int startX, startY;
        if (frameIndex < 4) {
            // (0,0) is bottom-left in GL
            startX = frameIndex * (mWidth / 4);
            startY = mHeight / 2;
        } else {
            startX = (7 - frameIndex) * (mWidth / 4);
            startY = 0;
        }

        GLES20.glClearColor(TEST_R0 / 255.0f, TEST_G0 / 255.0f, TEST_B0 / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(startX, startY, mWidth / 4, mHeight / 2);
        GLES20.glClearColor(TEST_R1 / 255.0f, TEST_G1 / 255.0f, TEST_B1 / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    public android.opengl.EGLContext getShareContext() {
        return mHelper.getEGLContext();
    }

}
