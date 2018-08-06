package com.ccflying.decodevideo;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

public class VideoDecodeWrapper {
    //
    private final static String TAG = "VideoDecodeWrapper";

    private MediaExtractor mExtractor;

    private MediaCodec mVideoCodec;

    private Object mLock = new Object();

    private boolean isEos = false;

    private int mWidth, mHeight;

    private int mAvailableVideoIndex = -1;
    private MediaCodec.BufferInfo mAvailableVideoBufferInfo = null;

    //------------------------------------------------------------

    public VideoDecodeWrapper(String videoSource, Surface surface) {
        //
        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(videoSource);
            int trackCount = mExtractor.getTrackCount();
            Log.e(TAG, "trackCount = " + trackCount);
            for (int i = 0; i < trackCount; i++) {
                mExtractor.unselectTrack(i);
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
    }

    private void writeVideoSample(long sampleTime, int flags) {
        int index = 0;
        // video
        while ((index = mVideoCodec.dequeueInputBuffer(0)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
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

    /**
     * 停止解码
     */
    public void stopDecode() {
        if (null != mVideoCodec) {
            mVideoCodec.stop();
            mVideoCodec.release();
            mVideoCodec = null;
        }
        if (null != mExtractor) {
            mExtractor.release();
            mExtractor = null;
        }
    }

    public boolean isEos() {
        return isEos;
    }
}

// https://blog.csdn.net/u012521570/article/details/78783294
// https://www.cnblogs.com/CoderTian/p/6220332.html
// https://blog.csdn.net/leilu000/article/details/80365082
