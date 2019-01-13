package com.ccf.encode_decode.encode.fbocamera.encodec;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import com.ccf.encode_decode.encode.fbocamera.camera.CameraRender;
import com.ccf.encode_decode.encode.fbocamera.util.EglHelper;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLContext;

public class BaseVideoEncoder {

    private static final String TAG = "BaseVideoEncoder";

    private Surface mSurface;
    private EGLContext mEGLContext;
    private GLSurfaceView.Renderer mRender;

    private MediaMuxer mMediaMuxer;
    private MediaCodec.BufferInfo mVideoBuffInfo;
    private MediaCodec mVideoEncoder;
    private int width, height;

    private VideoEncodeThread mVideoEncodecThread;
    private EGLMediaThread mEGLMediaThread;
    private boolean encodeStart;

    public final static int RENDERMODE_WHEN_DIRTY = 0;
    public final static int RENDERMODE_CONTINUOUSLY = 1;

    private int mRenderMode = RENDERMODE_WHEN_DIRTY;

    public BaseVideoEncoder(Context context, int textureId) {
        this.mRender = new CameraRender(context, textureId);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    public BaseVideoEncoder(GLSurfaceView.Renderer renderer) {
        this.mRender = renderer;
    }

    public void setRenderMode(int mRenderMode) {
        if (mRender == null) {
            throw new RuntimeException("must set render before");
        }
        this.mRenderMode = mRenderMode;
    }

    public void startRecode() {
        if (mSurface != null && mEGLContext != null) {
            encodeStart = false;

            mVideoEncodecThread = new VideoEncodeThread(new WeakReference<>(this));
            mEGLMediaThread = new EGLMediaThread(new WeakReference<>(this));

            mVideoEncodecThread.start();
            mEGLMediaThread.start();
        }
    }

    public void stopRecode() {
        if (mVideoEncodecThread != null) {
            mVideoEncodecThread.exit();
            mVideoEncodecThread = null;
        }

        if (mEGLMediaThread != null) {
            mEGLMediaThread.onDestroy();
            mEGLMediaThread = null;
        }
        encodeStart = false;

    }

    public void initEncoder(EGLContext eglContext, String savePath, int width, int height) {
        this.width = width;
        this.height = height;
        this.mEGLContext = eglContext;
        initMediaEncoder(savePath, width, height);
    }

    private void initMediaEncoder(String savePath, int width, int height) {
        try {
            mMediaMuxer = new MediaMuxer(savePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            // h264
            initVideoEncoder(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initVideoEncoder(String mineType, int width, int height) {
        Log.e(TAG, "initVideoEncoder: " + width + " x " + height);
        try {
            mVideoEncoder = MediaCodec.createEncoderByType(mineType);

            MediaFormat videoFormat = MediaFormat.createVideoFormat(mineType, width, height);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);//30帧
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4);//RGBA
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            //设置压缩等级  默认是baseline
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                videoFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileMain);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    videoFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel3);
                }
            }

            mVideoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mVideoBuffInfo = new MediaCodec.BufferInfo();
            mSurface = mVideoEncoder.createInputSurface();
        } catch (IOException e) {
            e.printStackTrace();
            mVideoEncoder = null;
            mVideoBuffInfo = null;
            mSurface = null;
        }
    }

    static class VideoEncodeThread extends Thread {
        private WeakReference<BaseVideoEncoder> encoderWeakReference;
        private boolean isExit;

        private int videoTrackIndex;
        private long pts;

        private MediaCodec videoEncoder;
        private MediaCodec.BufferInfo videoBufferinfo;
        private MediaMuxer mediaMuxer;


        public VideoEncodeThread(WeakReference<BaseVideoEncoder> encoderWeakReference) {
            this.encoderWeakReference = encoderWeakReference;

            videoEncoder = encoderWeakReference.get().mVideoEncoder;
            videoBufferinfo = encoderWeakReference.get().mVideoBuffInfo;
            mediaMuxer = encoderWeakReference.get().mMediaMuxer;
            pts = 0;
            videoTrackIndex = -1;
        }

        @Override
        public void run() {
            super.run();
            isExit = false;
            videoEncoder.start();
            while (true) {
                if (isExit) {
                    videoEncoder.stop();
                    videoEncoder.release();
                    videoEncoder = null;

                    mediaMuxer.stop();
                    mediaMuxer.release();
                    mediaMuxer = null;
                    break;
                }

                int outputBufferIndex = videoEncoder.dequeueOutputBuffer(videoBufferinfo, 0);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    videoTrackIndex = mediaMuxer.addTrack(videoEncoder.getOutputFormat());

                    mediaMuxer.start();
                    encoderWeakReference.get().encodeStart = true;
                } else {
                    while (outputBufferIndex >= 0) {
                        if (!encoderWeakReference.get().encodeStart) {
                            SystemClock.sleep(10);
                            continue;
                        }
                        ByteBuffer outputBuffer = videoEncoder.getOutputBuffers()[outputBufferIndex];
                        outputBuffer.position(videoBufferinfo.offset);
                        outputBuffer.limit(videoBufferinfo.offset + videoBufferinfo.size);

                        //设置时间戳
                        if (pts == 0) {
                            pts = videoBufferinfo.presentationTimeUs;
                        }
                        videoBufferinfo.presentationTimeUs = videoBufferinfo.presentationTimeUs - pts;
                        //写入数据
                        mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, videoBufferinfo);
                        Log.w(TAG, "VideoTime = " + videoBufferinfo.presentationTimeUs / 1000000.0f);
                        videoEncoder.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = videoEncoder.dequeueOutputBuffer(videoBufferinfo, 0);
                    }
                }
            }
        }

        public void exit() {
            isExit = true;
        }
    }

    static class EGLMediaThread extends Thread {
        private WeakReference<BaseVideoEncoder> mEncoder;
        private EglHelper eglHelper;
        private Object object = new Object();
        private boolean isExit = false;

        private boolean isCreated = false;
        private boolean isChanged = false;

        public EGLMediaThread(WeakReference<BaseVideoEncoder> encoder) {
            this.mEncoder = encoder;
        }

        @Override
        public void run() {
            super.run();
            isExit = false;
            isCreated = false;

            object = new Object();
            eglHelper = new EglHelper();
            eglHelper.initEgl(mEncoder.get().mSurface, mEncoder.get().mEGLContext);

            while (true) {
                try {
                    if (isExit) {
                        release();
                        break;
                    }

                    if (!isCreated) {
                        mEncoder.get().mRender.onSurfaceCreated(null, null);
                        isCreated = true;
                    }

                    if (isChanged) {
                        mEncoder.get().mRender.onSurfaceChanged(null, mEncoder.get().width, mEncoder.get().height);
                        isChanged = false;
                    }

                    mEncoder.get().mRender.onDrawFrame(null);
                    eglHelper.swapBuffers();

                    if (mEncoder.get().mRenderMode == RENDERMODE_WHEN_DIRTY) {
                        synchronized (object) {
                            object.wait();
                        }
                    } else if (mEncoder.get().mRenderMode == RENDERMODE_CONTINUOUSLY) {
                        Thread.sleep(1000 / 60);
                    } else {
                        throw new IllegalArgumentException("renderMode");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        void requestRender() {
            if (object != null) {
                synchronized (object) {
                    object.notifyAll();
                }
            }
        }

        void onDestroy() {
            isExit = true;
            //释放锁
            requestRender();
        }


        void release() {
            if (eglHelper != null) {
                eglHelper.destoryEgl();
                eglHelper = null;
                object = null;
                mEncoder = null;
            }
        }

        EGLContext getEglContext() {
            if (eglHelper != null) {
                return eglHelper.getEglContext();
            }
            return null;
        }
    }

}
