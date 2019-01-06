/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ccf.encode_decode.encode.camera.nopreview;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;


/**
 * 从Camera的Preview中录制并编码成MP4格式的视频。
 * 相比起使用MediaRecorder来录制，该示例可以在encode的时候，对视频进行编辑。
 * The output file will be something like "/sdcard/test.640x480.mp4".
 */
public class CameraToMp4 {
    private Context mContext;
    private static final String TAG = "CameraToMp4";

    private static File OUTPUT_DIR;

    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int I_FRAME_INTERVAL = 5;          // 5 seconds between I-frames
    private static final long DURATION_SEC = 6;             // x seconds of video

    // Fragment shader that swaps color channels around.
    private static final String SWAPPED_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture, vTextureCoord).gbra;\n" +
                    "}\n";

    // encoder / muxer state
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    // EGL环境
    private EGLHelper mEGLHelper;
    private int mTrackIndex;
    private boolean mMuxerStarted;

    // camera state
    private Camera mCamera;
    private SurfaceTextureManager mStManager;
    //
    private MediaCodec.BufferInfo mBufferInfo;

    public CameraToMp4(Context context) {
        this.mContext = context;
    }

    /**
     * test entry point
     */
    public void startRecording() throws Throwable {
        /**
         * Wraps encodeCameraToMpeg() to a thread. This is necessary because SurfaceTexture will try to use
         * the looper in the current thread if one exists.
         */
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    encodeCameraToMpeg();
                } catch (Throwable th) {
                    Log.e(TAG, "run: " + th.toString());
                }
            }
        }, "codecTest");
        th.start();
    }


    /**
     * Tests encoding of AVC video from Camera input.  The output is saved as an MP4 file.
     * step1: prepareCamera
     * step2: prepareEncoder
     * step3: prepareSurfaceTexture
     */
    private void encodeCameraToMpeg() {
        // arbitrary but popular values
        int encWidth = 1080;
        int encHeight = 1920;

        int encBitRate = 6000000;      // Mbps
        Log.e(TAG, MIME_TYPE + " output " + encWidth + "x" + encHeight + " @" + encBitRate);

        try {
            // 1.打开并配置摄像头参数
            prepareCamera(encWidth, encHeight);
            // 2.初始化MediaCodec， MediaMuxer，以及EGLSurface。
            prepareEncoder(encWidth, encHeight, encBitRate);
            // 3.配置 SurfaceTexture 并开启摄像头预览。
            prepareSurfaceTexture();

            long startWhen = System.nanoTime();
            // 纳秒
            long desiredEnd = startWhen + DURATION_SEC * 1000000000L;
            SurfaceTexture st = mStManager.getSurfaceTexture();
            int frameCount = 0;
            // 开启循环，进行编码。
            while (System.nanoTime() < desiredEnd) {
                // Feed any pending encoder output into the muxer.
                drainEncoder(false);

                // 每15帧切换一下shader, 展示了如何对视频进行编辑。
                if ((frameCount % 15) == 0) {
                    String fragmentShader = null;
                    if ((frameCount & 0x01) != 0) {
                        fragmentShader = SWAPPED_FRAGMENT_SHADER;
                    }
                    mStManager.changeFragmentShader(fragmentShader);
                }
                frameCount++;

                // 从Camera获取数据帧，并且渲染到Surface.
                // If we had a GLSurfaceView we could switch EGL contexts and call drawImage() a second
                // time to render it on screen.  The texture can be shared between contexts by
                // passing the GLSurfaceView's EGLContext as eglCreateContext()'s share_context argument.
                mStManager.awaitNewImage(); // 获取最新的图片Frame并绘制到EGLSurface。

                Log.e(TAG, "present: " + ((st.getTimestamp() - startWhen) / 1000000.0) + "ms");

                // 从SurfaceTexture中获取time stamp, 并且传递给EGL。MediaMuxer将用这个timestamp作为转码视频的time stamp
                mEGLHelper.setPresentationTime(st.getTimestamp());

                // Submit it to the encoder. The eglSwapBuffers call will block if the input
                // is full, which would be bad if it stayed full until we dequeued an output
                // buffer (which we can't do, since we're stuck here).  So long as we fully drain
                // the encoder before supplying additional input, the system guarantees that we
                // can supply another frame without blocking.
                Log.e(TAG, "sending frame to encoder");
                mEGLHelper.swapBuffers();
            }

            // send end-of-stream to encoder, and drain remaining output
            drainEncoder(true);
        } finally {
            stop();
        }
    }

    /**
     * 停止录制并释放资源
     */
    private void stop() {
        releaseCamera();
        releaseEncoder();
        releaseSurfaceTexture();
        Log.e(TAG, "Stop Encode");
    }

    /**
     * Configures Camera for video capture. Open a Camera and sets parameters.  Does not start preview.
     */
    private void prepareCamera(int encWidth, int encHeight) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        // 尝试查找前置摄像头。
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera = Camera.open(i);
                break;
            }
        }
        if (mCamera == null) {
            mCamera = Camera.open();
        }
        if (null == mCamera)
            return;
        // 获取参数配置。
        Camera.Parameters parms = mCamera.getParameters();
        // 选择预览大小
        choosePreviewSize(parms, encWidth, encHeight);
        // 设置摄像头参数
        mCamera.setParameters(parms);
        Camera.Size size = parms.getPreviewSize();
        Log.e(TAG, "Camera preview size is " + size.width + "x" + size.height);
    }

    /**
     * 根据提供的视频的宽和高，选择一个合适的预览大小。
     * 如果未找到合适的尺寸，则使用默认的尺寸。
     */
    private static void choosePreviewSize(Camera.Parameters parms, int width, int height) {
        // We should make sure that the requested MPEG size is less than the preferred
        // size, and has the same aspect ratio.
        Camera.Size ppsfv = parms.getPreferredPreviewSizeForVideo();
        if (ppsfv != null) {
            Log.e(TAG, "Camera preferred preview size for video is " +
                    ppsfv.width + "x" + ppsfv.height);
            parms.setPreviewSize(ppsfv.width, ppsfv.height);
            return;
        }
        // 查找合适的size.
        List<Camera.Size> sizes = parms.getSupportedPreviewSizes();
        for (Camera.Size size : sizes) {
            Log.w(TAG, "choosePreviewSize: w=" + size.width + ", h=" + size.height);
            if (size.width == width && size.height == height) {
                parms.setPreviewSize(width, height);
                return;
            }
        }
        // 未找到，则使用默认的尺寸。
        Log.w(TAG, "Unable to set preview size to " + width + "x" + height);
        if (ppsfv != null) {
            parms.setPreviewSize(ppsfv.width, ppsfv.height);
        }
    }

    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private void releaseCamera() {
        Log.e(TAG, "releasing camera");
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * 配置 SurfaceTexture 并开启摄像头预览。
     * Configure the EGL surface that will be used for output before calling here.
     */
    private void prepareSurfaceTexture() {
        mStManager = new SurfaceTextureManager();
        // 获取使用TextureId创建的SurfaceTexture，它将作为Camera的预览输入对象
        SurfaceTexture st = mStManager.getSurfaceTexture();
        try {
            Display display = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

            if (display.getRotation() == Surface.ROTATION_0) {
                mCamera.setDisplayOrientation(90);
            }
            if (display.getRotation() == Surface.ROTATION_270) {
                mCamera.setDisplayOrientation(180);
            }
            // 将preview输出到SurfaceTexture
            mCamera.setPreviewTexture(st);
        } catch (IOException ioe) {
            throw new RuntimeException("setPreviewTexture failed", ioe);
        }
        mCamera.startPreview();
    }

    /**
     * Releases the SurfaceTexture.
     */
    private void releaseSurfaceTexture() {
        if (mStManager != null) {
            mStManager.release();
            mStManager = null;
        }
    }

    /**
     * 配置MediaCodec 和 MediaMuxer, 准备input Surface，初始化编码器MediaCodec, MediaMuxer,
     * InputSurface, BufferInfo, 获取 mTrackIndex, 设置 MediaMuxer 的状态 mMuxerStarted。
     */
    private void prepareEncoder(int width, int height, int bitRate) {
        // 创建BufferInfo
        mBufferInfo = new MediaCodec.BufferInfo();
        // 配置Format
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        // 设置format，COLOR_FormatSurface indicates that the data will be a GraphicBuffer metadata reference.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        Log.e(TAG, "format: " + format);

        // 根据 MIME_TYPE 创建 MediaCodec，并且根据format进行配置.
        // Get a Surface we can use for input and wrap it with a class that handles the EGL work.
        //
        // If you want to have two EGL contexts -- one for display, one for recording --
        // you will likely want to defer instantiation of EGLHelper until after the
        // "display" EGL context is created, then modify the eglCreateContext call to
        // take eglGetCurrentContext() as the share_context argument.
        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 配置编码参数，flags为编码 ENCODE
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // Requests a Surface to use as the input to an encoder, in place of input buffers.
        mEGLHelper = new EGLHelper(mEncoder.createInputSurface());
        // MediaCodec#createInputSurface()，创建一个Surface，它将作为encoder的输入源。
        // 可以预见的是，之后需要将Camera的Preview关联到该Surface上。

        // Makes our EGL context and surface current.
        // 之后在OpenGL里面绘制的内容，都将绘制到创建的EGLSurface中。
        mEGLHelper.makeCurrent();
        // 开启编码器
        mEncoder.start();

        // 文件保存地址
        OUTPUT_DIR = Environment.getExternalStorageDirectory();
        String outputPath = new File(OUTPUT_DIR,
                "test." + width + "x" + height + ".mp4").toString();

        // 创建MediaMuxer. 在这个时候不能添加video track并且start().
        // 只有当encoder开始处理数据之后，才能开启MediaMuxer。 These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        try {
            mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }

        mTrackIndex = -1;
        mMuxerStarted = false;
    }

    /**
     * Releases encoder resources.
     */
    private void releaseEncoder() {
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mEGLHelper != null) {
            mEGLHelper.release();
            mEGLHelper = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    /**
     * 将encoder中所有准备好的数据提取出来并且转交给muxer.
     * If endOfStream is not set, this returns when there is no more data to drain.
     * If set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     * <p>
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).
     */
    private void drainEncoder(boolean endOfStream) {
        final int TIMEOUT_USEC = 10000;

        if (endOfStream) {
            Log.e(TAG, "sending EOS to encoder");
            mEncoder.signalEndOfInputStream();
        }

        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) {
            // 获取已经被成功编码的帧的buffer索引。
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet, 没有可用的output输入，跳出循环。
                if (!endOfStream) {
                    break;// out of while
                } else {
                    Log.e(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                // 获取输入格式。
                MediaFormat newFormat = mEncoder.getOutputFormat();
                Log.e(TAG, "encoder output format changed: " + newFormat);
                // now that we have the Magic Goodies, start the muxer
                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
            } else if (encoderStatus < 0) { // 其它状态值，忽略不做处理。
                // let's ignore it
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Log.e(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG : " + mBufferInfo.size);
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    // Writes an encoded sample into the muxer.
                    mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    Log.e(TAG, "sent " + mBufferInfo.size + " bytes to muxer");
                }

                // release，释放buffer，不需要渲染
                mEncoder.releaseOutputBuffer(encoderStatus, false);
                // EndOfSteam -> break
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.e(TAG, "end of stream reached");
                    break; // out of while
                }
            }
        }
    }
}
