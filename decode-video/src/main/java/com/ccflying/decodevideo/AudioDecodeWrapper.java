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

public class AudioDecodeWrapper {
    //
    private final static String TAG = "AudioDecodeWrapper";

    private MediaExtractor mAudioExtractor;

    private Object mLock = new Object();

    private boolean isEos = false;

    private AudioTrack mAudioTrack;
    // 采样率
    private int mSampleRate;
    //声道数
    private int channelCount;
    //
    private MediaCodec mAudioCodec;

    private int mAvailableAudioIndex = -1;

    private MediaCodec.BufferInfo mAvailableAudioBufferInfo = null;


    public AudioDecodeWrapper(String videoSource) {
        //
        mAudioExtractor = new MediaExtractor();
        try {
            mAudioExtractor.setDataSource(videoSource);
            int trackCount = mAudioExtractor.getTrackCount();
            Log.e(TAG, "trackCount = " + trackCount);
            for (int i = 0; i < trackCount; i++) {
                mAudioExtractor.unselectTrack(i);
            }
            MediaFormat format = null;
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

    public void updateDecode(long totalTime) {
        // INCLUDE(AUDIO-BEGIN)
        int flags = mAudioExtractor.getSampleFlags();
        boolean eosAudio = (flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        if (!eosAudio) {
            long sampleTime = mAudioExtractor.getSampleTime();
            int sampleFlags = mAudioExtractor.getSampleFlags();
            //
            writeAudioSample(sampleTime, sampleFlags);
        } else {
            synchronized (mLock) {
                isEos = true;
            }
        }
        readAudioSample(totalTime);
        // INCLUDE(AUDIO-END)
    }

    private void writeAudioSample(long sampleTime, int flags) {
        int index = 0;
        // audio
        while ((index = mAudioCodec.dequeueInputBuffer(0)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
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

    private void readAudioSample(long totalTime) {
        if (mAvailableAudioIndex != -1) {
            if (mAvailableAudioBufferInfo.presentationTimeUs / 1000 < totalTime) {
                // TODO
                playAudioTrack(mAvailableAudioBufferInfo, mAvailableAudioIndex);
                // release
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
                            playAudioTrack(bufferInfo, index);
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
            if (isEos) {
                stopDecode();
            }
        }
    }

    /**
     * 停止解码
     */
    public void stopDecode() {
        if (null != mAudioCodec) {
            mAudioCodec.stop();
            mAudioCodec.release();
            mAudioCodec = null;
        }
        if (null != mAudioExtractor) {
            mAudioExtractor.release();
            mAudioExtractor = null;
        }
        if (null != mAudioTrack) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
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


    private void playAudioTrack(MediaCodec.BufferInfo bufferInfo, int index) {
        ByteBuffer outBuffer = mAudioCodec.getOutputBuffer(index);
        byte[] outData = new byte[bufferInfo.size];
        outBuffer.get(outData);
        outBuffer.clear();
        playAudioTrack(outData, bufferInfo.offset, bufferInfo.size);
    }

    private void playAudioTrack(byte[] data, int offset, int length) {
        if (null == mAudioTrack || data == null || data.length == 0) {
            return;
        }
        Log.w(TAG, "audio: playAudioTrack");
        mAudioTrack.write(data, offset, length);
    }
}

// https://blog.csdn.net/u012521570/article/details/78783294
// https://www.cnblogs.com/CoderTian/p/6220332.html
// https://blog.csdn.net/leilu000/article/details/80365082
