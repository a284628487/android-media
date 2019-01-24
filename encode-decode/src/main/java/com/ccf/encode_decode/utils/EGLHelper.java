package com.ccf.encode_decode.utils;

import android.annotation.SuppressLint;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.view.Surface;

/**
 * Holds state associated with a Surface used for MediaCodec encoder input.
 * <p>
 * 构造函数需要一个从MediaCodec.createInputSurface()方法获取到的Surface，
 * 将使用它来创建EGL Window Surface，执行eglSwapBuffers()将frame传递给video encoder.
 * -----
 * The constructor takes a Surface obtained from MediaCodec.createInputSurface(), and uses that
 * to create an EGL window surface.  Calls to eglSwapBuffers() cause a frame of data to be sent
 * to the video encoder.
 * <p>
 * This object owns the Surface -- releasing this will release the Surface too.
 */
@SuppressLint("NewApi")
public class EGLHelper {
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;

    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLContext mEGLShareContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;

    private Surface mSurface;

    /**
     * Creates a CodecInputSurface from a Surface.
     */
    public EGLHelper(Surface surface, EGLContext shareContext) {
        if (surface == null) {
            throw new NullPointerException();
        }
        mSurface = surface;
        mEGLShareContext = shareContext;

        eglSetup();
    }

    public EGLHelper(Surface surface) {
        this(surface, EGL14.EGL_NO_CONTEXT);
    }

    /**
     * 准备EGL环境，需要一个ELGS 2.0的Context，和支持Recording的Surface
     * step:
     * 1. EGL14.eglGetDisplay
     * 2. EGL14.eglInitialize
     * 3. EGL14.eglChooseConfig
     * 4. EGL14.eglCreateContext
     * 5. EGL14.eglCreateWindowSurface
     */
    private void eglSetup() {
        // step1
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        // step2
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            throw new RuntimeException("unable to initialize EGL14");
        }

        // Configure EGL for recording and OpenGL ES 2.0.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8, // 通道R
                EGL14.EGL_GREEN_SIZE, 8, // 通道G
                EGL14.EGL_BLUE_SIZE, 8, // 通道B
                EGL14.EGL_ALPHA_SIZE, 8, // 通道Alpha
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, // EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        // step3
        EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0);
        checkEglError("eglCreateContext RGB888+recordable ES2");

        // Configure context for OpenGL ES 2.0.
        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        // step4
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], mEGLShareContext,
                attrib_list, 0);
        checkEglError("eglCreateContext");

        // Create a window surface, and attach it to the Surface we received.
        int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };
        // step5
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], mSurface,
                surfaceAttribs, 0);
        checkEglError("eglCreateWindowSurface");
    }

    /**
     * Discards all resources held by this class, notably the EGL context.  Also releases the
     * Surface that was passed to our constructor.
     */
    public void release() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(mEGLDisplay);
        }

        mSurface.release();

        mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        mEGLContext = EGL14.EGL_NO_CONTEXT;
        mEGLShareContext = EGL14.EGL_NO_CONTEXT;
        mEGLSurface = EGL14.EGL_NO_SURFACE;

        mSurface = null;
    }

    /**
     * Makes our EGL context and surface current.
     * eglMakeCurrent(EGLDisplay display, EGLSurface draw, EGLSurface read, EGLContext context)
     * 在完成EGL的初始化之后，需要通过eglMakeCurrent()函数来将当前的上下文切换，这样OpenGL ES的绘制函数才能起作用。
     * 该接口将申请到的display，draw（surface）和 context进行了绑定。也就是说，在context下的OpenGL#API指令将draw（surface）作为其渲染最终目的地。
     * 而display作为draw（surface）的前端显示。调用后，当前线程使用的EGLContext为context。
     */
    public void makeCurrent() {
        EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
        checkEglError("eglMakeCurrent");
    }

    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent(EGLSurface mRead) {
        EGL14.eglMakeCurrent(mEGLDisplay, mRead, mEGLSurface, mEGLContext);
        checkEglError("eglMakeCurrent");
    }

    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     */
    public boolean swapBuffers() {
        boolean result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
        checkEglError("eglSwapBuffers");
        return result;
    }

    /**
     * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
     */
    public void setPresentationTime(long nsecs) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
        checkEglError("eglPresentationTimeANDROID");
    }

    /**
     * Checks for EGL errors.  Throws an exception if one is found.
     */
    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    public EGLContext getEGLContext() {
        return mEGLContext;
    }
}
