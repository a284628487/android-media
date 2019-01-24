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

package com.ccf.encode_decode.encode.glesdraw;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.GLES20;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ccf.encode_decode.utils.EGLHelper;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

//20131106: removed hard-coded "/sdcard"
//20131205: added alpha to EGLConfig

/**
 * 使用OpenGL ES绘制，来生成一个MP4视频文件，
 * Generate an MP4 file using OpenGL ES drawing commands.  Demonstrates the use of MediaMuxer
 * and MediaCodec with Surface input.
 */
public class EncodeOpenGLES {
    private static final String TAG = "EncodeOpenGLES";
    private Handler mHandler;

    // where to put the output file (note: /sdcard requires WRITE_EXTERNAL_STORAGE permission)
    private static final File OUTPUT_DIR = Environment.getExternalStorageDirectory();

    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 20;               // 15fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames

    // 两组颜色值
    private static final int TEST_R0 = 0;
    private static final int TEST_G0 = 136;
    private static final int TEST_B0 = 0;
    //
    private static final int TEST_R1 = 236;
    private static final int TEST_G1 = 50;
    private static final int TEST_B1 = 186;

    private String fileName = null;
    // size of a frame, in pixels
    private int mWidth = -1;
    private int mHeight = -1;
    // bit rate, in bits per second
    private int mBitRate = -1;

    // encoder / muxer state
    private MediaCodec mEncoder;
    private EGLHelper mEGLHelper;
    private MediaMuxer mMuxer;
    private int mTrackIndex;
    private boolean mMuxerStarted;

    private int index = 0;

    private long mStartRecordTime = 0;

    // allocate one of these up front so we don't need to do it every time
    private MediaCodec.BufferInfo mBufferInfo;

    public EncodeOpenGLES(String fileName) {
        this.fileName = fileName;
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 1) { // 录制 record
                    sendEmptyMessageDelayed(1, 1000 / FRAME_RATE);
                    recordFrame();
                } else { // 停止 stop
                    // send end-of-stream to encoder, and drain remaining output
                    drainEncoder(true);
                    // release encoder, muxer, and input Surface
                    releaseEncoder();
                    //
                    Log.e(TAG, "RecordingEnd: " + System.currentTimeMillis());
                }
            }
        };
    }

    private void recordFrame() {
        // Feed any pending encoder output into the muxer.
        drainEncoder(false);
        // 根据索引绘制OpenGL
        drawSurfaceFrame(index++);
        Log.e(TAG, "record: " + index);
        // 设置时间timestamp给EGL。MediaMuxer将用这个timestamp作为转码视频的time stamp
        mEGLHelper.setPresentationTime(System.nanoTime() - mStartRecordTime);

        // Submit it to the encoder.  The eglSwapBuffers call will block if the input
        // is full, which would be bad if it stayed full until we dequeued an output
        // buffer (which we can't do, since we're stuck here).  So long as we fully drain
        // the encoder before supplying additional input, the system guarantees that we
        // can supply another frame without blocking.
        Log.d(TAG, "sending frame " + index + " to encoder");
        mEGLHelper.swapBuffers();
    }

    /**
     * Tests encoding of AVC video from a Surface.  The output is saved as an MP4 file.
     */
    public void startRecording() throws IOException {
        // QVGA at 2Mbps
        mWidth = 320;
        mHeight = 240;
        mBitRate = 2000000;

        try {
            // 准备编码器
            prepareEncoder();
            //
            mEGLHelper.makeCurrent();
            Log.e(TAG, "RecordingBegin: " + System.currentTimeMillis());
            mStartRecordTime = System.nanoTime();
            mHandler.sendEmptyMessage(1);
        } catch (Exception e) {
        }
        // To test the result, open the file with MediaExtractor, and get the format.  Pass
        // that into the MediaCodec decoder configuration, along with a SurfaceTexture surface,
        // and examine the output with glReadPixels.
    }

    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    private void prepareEncoder() throws IOException {
        mBufferInfo = new MediaCodec.BufferInfo();
        // 创建Format
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);

        // Set some properties.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface); // 颜色
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE); // 帧率，每秒视频帧数。
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL); // 关键帧
        Log.d(TAG, "format: " + format);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        //
        // If you want to have two EGL contexts -- one for display, one for recording --
        // you will likely want to defer instantiation of EGLHelper until after the
        // "display" EGL context is created, then modify the eglCreateContext call to
        // take eglGetCurrentContext() as the share_context argument.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEGLHelper = new EGLHelper(mEncoder.createInputSurface());
        mEncoder.start();

        // 输出文件地址.
        String outputPath = new File(OUTPUT_DIR, fileName + "." + mWidth + "x" + mHeight + ".mp4").toString();

        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
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
     * Releases encoder resources.  May be called after partial / failed initialization.
     */
    private void releaseEncoder() {
        Log.d(TAG, "releasing encoder objects");
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
     * Extracts all pending data from the encoder.
     * <p>
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
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
                    break;// out of while
                }
            }
        }
    }

    /**
     * Generates a frame of data using GL commands.  We have an 8-frame animation
     * sequence that wraps around.  It looks like this:
     * <pre>
     *   0 1 2 3
     *   7 6 5 4
     * </pre>
     * We draw one of the eight rectangles and leave the rest set to the clear color.
     */
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

    /**
     * stop recording
     */
    public void stop() {
        mHandler.removeMessages(1);
        mHandler.sendEmptyMessage(0);
    }
}
