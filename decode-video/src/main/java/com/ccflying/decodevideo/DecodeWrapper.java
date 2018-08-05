package com.ccflying.decodevideo;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DecodeWrapper {
    //
    private final static String TAG = "DecodeWrapper";

    private MediaExtractor mExtractor;
    private MediaExtractor mAudioExtractor;

    private MediaCodec mVideoCodec;

    private Object mLock = new Object();

    private boolean isEos = false;

    private int mWidth, mHeight;

    private int mAvailableVideoIndex = -1;
    private MediaCodec.BufferInfo mAvailableVideoBufferInfo = null;

    //------------------------------------------------------------

    private AudioTrack mAudioTrack;
    // 采样率
    private int mSampleRate;
    //声道数
    private int channelCount;
    //
    private MediaCodec mAudioCodec;

    private int mAvailableAudioIndex = -1;
    private MediaCodec.BufferInfo mAvailableAudioBufferInfo = null;


    public DecodeWrapper(String videoSource, Surface surface) {
        //
        mExtractor = new MediaExtractor();
        mAudioExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(videoSource);
            mAudioExtractor.setDataSource(videoSource);
            int trackCount = mExtractor.getTrackCount();
            Log.e(TAG, "trackCount = " + trackCount);
            for (int i = 0; i < trackCount; i++) {
                mExtractor.unselectTrack(i);
                mAudioExtractor.unselectTrack(i);
            }
            MediaFormat format = null;
            for (int i = 0; i < trackCount; i++) {
                format = mExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                // find video
                if (mime.contains("video/")) {
                    mExtractor.selectTrack(i);
                    mVideoCodec = MediaCodec.createDecoderByType(mime);
                    //
                    mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                    if (format.containsKey("crop-left") && format.containsKey("crop-right")) {
                        mWidth = format.getInteger("crop-right") + 1 - format.getInteger("crop-left");
                    }
                    mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                    if (format.containsKey("crop-top") && format.containsKey("crop-bottom")) {
                        mHeight = format.getInteger("crop-bottom") + 1 - format.getInteger("crop-top");
                    }
                    //
                    mVideoCodec.configure(format, surface, null, 0);
                    mVideoCodec.start();
                    break;
                }
            }
            for (int i = 0; i < trackCount; i++) {
                format = mAudioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.contains("audio/")) {
                    mAudioExtractor.selectTrack(i);
                    mAudioCodec = MediaCodec.createDecoderByType(mime);

                    //获取当前帧的采样率
                    mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    //获取当前帧的通道数
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

                    // Audio
                    initAudioTrack();

                    mAudioCodec.configure(format, null, null, 0);
                    mAudioCodec.start();
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public int getVideoWidth() {
        return mWidth;
    }

    public int getVideoHeight() {
        return mHeight;
    }

    public void updateDecode(long totalTime) {
        // INCLUDE(VIDEO-BEGIN)
        int flags = mExtractor.getSampleFlags();
        boolean eos = (flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        if (!eos) {
            long sampleTime = mExtractor.getSampleTime();
            int sampleFlags = mExtractor.getSampleFlags();
            //
            writeVideoSample(sampleTime, sampleFlags);
        } else {
            synchronized (mLock) {
                isEos = true;
            }
        }
        readVideoSample(totalTime);
        // INCLUDE(VIDEO-END)

        // INCLUDE(AUDIO-BEGIN)
        flags = mAudioExtractor.getSampleFlags();
        boolean eosAudio = (flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        if (!eosAudio) {
            long sampleTime = mAudioExtractor.getSampleTime();
            int sampleFlags = mAudioExtractor.getSampleFlags();
            //
            writeAudioSample(sampleTime, sampleFlags);
        } else {

        }
        readAudioSample(totalTime);
        // INCLUDE(AUDIO-END)
    }

    private void writeVideoSample(long sampleTime, int flags) {
        int index = 0;
        // video
        if ((index = mVideoCodec.dequeueInputBuffer(0)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
            // Extractor#readSampleData
            int bufferSize = mExtractor.readSampleData(mVideoCodec.getInputBuffer(index), 0);
            if (bufferSize <= 0) {
                bufferSize |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
            }
            // 提交解码buffer对应的index.
            mVideoCodec.queueInputBuffer(index, 0, bufferSize, sampleTime, flags);
            // 向前前进视频帧
            mExtractor.advance();
        }
    }

    private void writeAudioSample(long sampleTime, int flags) {
        int index = 0;
        // audio
        if ((index = mAudioCodec.dequeueInputBuffer(0)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
            // Extractor#readSampleData
            int bufferSize = mAudioExtractor.readSampleData(mAudioCodec.getInputBuffer(index), 0);
            if (bufferSize <= 0) {
                bufferSize |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
            }
            Log.e(TAG, "audio size = " + bufferSize);
            // 提交解码buffer对应的index.
            mAudioCodec.queueInputBuffer(index, 0, bufferSize, sampleTime, flags);
            // 向前前进音频
            mAudioExtractor.advance();
        }
    }

    private void readVideoSample(long totalTime) {
        if (mAvailableVideoIndex != -1) {
            if (mAvailableVideoBufferInfo.presentationTimeUs / 1000 < totalTime) {
                mVideoCodec.releaseOutputBuffer(mAvailableVideoIndex, true);
                mAvailableVideoIndex = -1;
                mAvailableVideoBufferInfo = null;
            }
            return;
        }
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int index = 0;
        //
        if ((index = mVideoCodec.dequeueOutputBuffer(bufferInfo, 0)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
            switch (index) {
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    // TODO ... ignore
                    break;
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    // TODO ... ignore
                    break;
                default:
                    if (index >= 0) {
                        if (bufferInfo.presentationTimeUs / 1000 < totalTime) {
                            // ByteBuffer outputBuffer = mVideoCodec.getOutputBuffer(index);
                            mVideoCodec.releaseOutputBuffer(index, true);
                        } else {
                            mAvailableVideoBufferInfo = bufferInfo;
                            mAvailableVideoIndex = index;
                        }
                        // EOS
                        if (bufferInfo.size <= 0 && isEos) {
                            stopDecode();
                        }
                    }
            }
        } else {
            if (isEos) {
                stopDecode();
            }
        }
    }

    private void readAudioSample(long totalTime) {
        if (mAvailableAudioIndex != -1) {
            if (mAvailableAudioBufferInfo.presentationTimeUs / 1000 < totalTime) {
                // TODO
                ByteBuffer outBuffer = mAudioCodec.getOutputBuffer(mAvailableAudioIndex);
                byte[] outData = new byte[mAvailableAudioBufferInfo.size];
                outBuffer.get(outData);
                outBuffer.clear();
                playAudioTrack(outData, mAvailableAudioBufferInfo.offset,
                        mAvailableAudioBufferInfo.size);
                mAudioCodec.releaseOutputBuffer(mAvailableAudioIndex, false);
                //
                mAvailableAudioIndex = -1;
                mAvailableAudioBufferInfo = null;
            }
            return;
        }
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int index = 0;
        //
        if ((index = mAudioCodec.dequeueOutputBuffer(bufferInfo, 0)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
            switch (index) {
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    // TODO ... ignore
                    break;
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    // TODO ... ignore
                    break;
                default:
                    Log.e(TAG, "audio: " + index);
                    if (index >= 0) {
                        if (bufferInfo.presentationTimeUs / 1000 < totalTime) {
                            ByteBuffer outBuffer = mAudioCodec.getOutputBuffer(index);
                            byte[] outData = new byte[bufferInfo.size];
                            outBuffer.get(outData);
                            outBuffer.clear();
                            playAudioTrack(outData, bufferInfo.offset, bufferInfo.size);
                            mAudioCodec.releaseOutputBuffer(index, false);
                        } else {
                            mAvailableAudioBufferInfo = bufferInfo;
                            mAvailableAudioIndex = index;
                        }
                        // EOS
                        if (bufferInfo.size <= 0 && isEos) {
                            stopDecode();
                        }
                    }
            }
        } else {
            Log.e(TAG, "audio: " + index);
        }
    }

    /**
     * 停止解码
     */
    public void stopDecode() {
        if (null != mVideoCodec) {
            mVideoCodec.stop();
            mVideoCodec.release();
        }
        if (null != mAudioCodec) {
            mAudioCodec.stop();
            mAudioCodec.release();
        }
        if (null != mExtractor) {
            mExtractor.release();
            mAudioExtractor.release();
        }
        if (null != mAudioTrack) {
            mAudioTrack.stop();
            mAudioTrack.release();
        }
    }

    public boolean isEos() {
        return isEos;
    }

    private void initAudioTrack() {
        int minBufferSize = AudioTrack.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        // 创建AudioTrack对象
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize,
                AudioTrack.MODE_STREAM);
        //
        mAudioTrack.play();
    }

    private void playAudioTrack(byte[] data, int offset, int length) {
        if (null == mAudioTrack || data == null || data.length == 0) {
            return;
        }
        Log.e(TAG, "audio: playAudioTrack");
        mAudioTrack.write(data, offset, length);
    }
}

// https://blog.csdn.net/u012521570/article/details/78783294
// https://www.cnblogs.com/CoderTian/p/6220332.html
// https://blog.csdn.net/leilu000/article/details/80365082
