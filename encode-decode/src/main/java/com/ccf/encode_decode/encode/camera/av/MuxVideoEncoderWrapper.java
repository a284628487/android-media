package com.ccf.encode_decode.encode.camera.av;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import com.ccf.encode_decode.utils.NVUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MuxVideoEncoderWrapper {

    private final String TAG = "VideoEncoderWrapper";

    // encoder / muxer state
    private MediaCodec mEncoder;

    //
    private MediaCodec.BufferInfo mBufferInfo;

    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int I_FRAME_INTERVAL = 5;          // 5 seconds between I-frames

    private boolean isRecording = false;

    private long mRecordStartTime = 0;

    private MuxAVEncoderWrapper mAVWrapper;

    private LinkedBlockingQueue<ByteData> mBytes = new LinkedBlockingQueue<>();

    private int mCameraOutWidth;
    private int mCameraOutHeight;

    public MuxVideoEncoderWrapper(MuxAVEncoderWrapper wrapper, int width, int height) {
        this.mAVWrapper = wrapper;
        this.mCameraOutWidth = width;
        this.mCameraOutHeight = height;
        Log.e(TAG, "VideoByteEncoder: " + mCameraOutWidth + ", " + mCameraOutHeight);
        int encBitRate = 16000000; // 16Mbps
        prepareEncoder(encBitRate);
    }

    public void startRecording() {
        isRecording = true;
        mRecordStartTime = System.nanoTime();
        Log.e(TAG, "startRecording: ");
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    drainEncoder(false);
                    try {
                        ByteData data = mBytes.poll(500, TimeUnit.MILLISECONDS);
                        if (null != data) {
                            sendBufferToCodec(data.mData, data.mNanoTime);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (!isRecording) {
                        break;
                    }
                }
                drainEncoder(true);
                release();
            }
        }.start();
    }

    public void stop() {
        Log.e(TAG, "stop: ");
        isRecording = false;
    }

    private void release() {
        if (mEncoder != null) {
            Log.e(TAG, "release: ");
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (null != mAVWrapper) {
            mAVWrapper.releaseVideo();
        }
    }

    protected void sendDataFrame(byte[] data, long nanoTime) {
        mBytes.offer(new ByteData(data, nanoTime));
    }

    private void sendBufferToCodec(byte[] input, long presentationTimeNs) {
        try {
            int inputBufferIndex = mEncoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mEncoder.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(input);
                //
                long presentationTimeUs = (presentationTimeNs - mRecordStartTime) / 1000;
                mEncoder.queueInputBuffer(inputBufferIndex, 0, input.length, presentationTimeUs, 0);
            } else {
                Log.e(TAG, "sendBufferToCodec: ??? " + inputBufferIndex);
            }
        } catch (Throwable t) {
            Log.e(TAG, "sendBufferToCodec: " + t);
        }
    }

    /**
     * 配置MediaCodec 和 MediaMuxer, 准备input Surface，初始化编码器MediaCodec, MediaMuxer,
     * InputSurface, BufferInfo, 获取 mTrackIndex, 设置 MediaMuxer 的状态 mMuxerStarted。
     */
    private void prepareEncoder(int bitRate) {
        // 创建BufferInfo
        mBufferInfo = new MediaCodec.BufferInfo();
        // 配置Format
        int fileWidth = mCameraOutHeight;
        int fileHeight = mCameraOutWidth;
        //
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, fileWidth, fileHeight);
        // 设置format，COLOR_FormatSurface indicates that the data will be a GraphicBuffer metadata reference.
        // COLOR_FormatYUV420Flexible
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        Log.e(TAG, "format: " + format);

        // 根据 MIME_TYPE 创建 MediaCodec，并且根据format进行配置.
        // Get a Surface we can use for input and wrap it with a class that handles the EGL work.
        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 配置编码参数，flags为编码 ENCODE
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // 开启编码器
        mEncoder.start();

    }

    private class ByteData {
        byte[] mData;
        long mNanoTime;

        public ByteData(byte[] data, long mNanoTime) {
            this.mNanoTime = mNanoTime;
            // 旋转角度
            byte[] yuv420sp = NVUtils.NV21_rotate_to_90(data, mCameraOutWidth, mCameraOutHeight);
            // 必须要转格式，否则录制的内容播放出来为绿屏
            this.mData = NVUtils.NV21ToNV12(yuv420sp, mCameraOutWidth, mCameraOutHeight);
        }
    }

    private void drainEncoder(boolean endOfStream) {
        final int TIMEOUT_USEC = 10000;

        if (endOfStream) {
            Log.e(TAG, "sending EOS to encoder");
        }

        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) {
            // 获取已经被成功编码的帧的buffer索引。
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet, 没有可用的output输入，跳出循环。
                break;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mAVWrapper.isMuxerStarted()) {
                    throw new RuntimeException("format changed twice");
                }
                // 获取输入格式。
                MediaFormat newFormat = mEncoder.getOutputFormat();
                Log.e(TAG, "encoder output format changed: " + newFormat);
                // now that we have the Magic Goodies, start the muxer
                mAVWrapper.addVideoTrack(newFormat);
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
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    // Writes an encoded sample into the muxer.
                    mAVWrapper.writeVideoSampleData(encodedData, mBufferInfo);
                    Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer");
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
