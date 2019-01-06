package com.ccf.encode_decode.encode.camera.av;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MuxAVEncoderWrapper {

    private final String TAG = "AVEncoderWrapper";

    private MediaMuxer mMuxer;

    private String mFileName;

    private MuxAudioEncoderWrapper mAudioWrapper;

    private MuxVideoEncoderWrapper mVideoWrapper;

    private boolean mMuxerIsStarted = false;

    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;

    public MuxAVEncoderWrapper(String fileName, int width, int height) {
        //
        this.mFileName = fileName;
        try {
            mMuxer = new MediaMuxer(mFileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }
        //
        mAudioWrapper = new MuxAudioEncoderWrapper(this);
        mVideoWrapper = new MuxVideoEncoderWrapper(this, width, height);
    }

    private void releaseIfNeeded() {
        if (mMuxerIsStarted && mAudioTrackIndex == -1 && mVideoTrackIndex == -1) {
            if (mMuxer != null) {
                mMuxer.stop();
                mMuxer.release();
                mMuxer = null;
            }
        }
    }

    protected void releaseVideo() {
        mVideoTrackIndex = -1;
        releaseIfNeeded();
    }

    protected void releaseAudio() {
        mAudioTrackIndex = -1;
        releaseIfNeeded();
    }

    public void addVideoTrack(MediaFormat videoFormat) {
        mVideoTrackIndex = mMuxer.addTrack(videoFormat);
        startMuxerIfAudioAndVideoAdded();
    }

    public void addAudioTrack(MediaFormat audioFormat) {
        mAudioTrackIndex = mMuxer.addTrack(audioFormat);
        startMuxerIfAudioAndVideoAdded();
    }

    private void startMuxerIfAudioAndVideoAdded() {
        if (mVideoTrackIndex != -1 && mAudioTrackIndex != -1) {
            mMuxer.start();
            mMuxerIsStarted = true;
        }
    }

    public void writeVideoSampleData(ByteBuffer encodedData, MediaCodec.BufferInfo bufferInfo) {
        if (mMuxerIsStarted) {
            mMuxer.writeSampleData(mVideoTrackIndex, encodedData, bufferInfo);
        } else {
            Log.e(TAG, "writeVideoSampleData: ");
        }
    }

    public void writeAudioSampleData(ByteBuffer encodedData, MediaCodec.BufferInfo bufferInfo) {
        if (mMuxerIsStarted) {
            mMuxer.writeSampleData(mAudioTrackIndex, encodedData, bufferInfo);
        } else {
            Log.e(TAG, "writeAudioSampleData: ");
        }
    }

    private MediaCodec.BufferInfo cloneBufferInfo(MediaCodec.BufferInfo buffer) {
        MediaCodec.BufferInfo newInfo = new MediaCodec.BufferInfo();
        newInfo.set(buffer.offset, buffer.size, buffer.presentationTimeUs, buffer.flags);
        return newInfo;
    }

    private ByteBuffer cloneByteBuffer(ByteBuffer encodedBuffer) {
        ByteBuffer buffer = ByteBuffer.allocate(encodedBuffer.capacity());
        buffer.wrap(encodedBuffer.array());
        return buffer;
    }

    public boolean isMuxerStarted() {
        return mMuxerIsStarted;
    }

    public void startRecording() {
        mVideoWrapper.startRecording();
        mAudioWrapper.startRecording();
    }

    public void stop() {
        mVideoWrapper.stop();
        mAudioWrapper.stopRecord();
    }

    public void sendDataFrame(byte[] data, long nanoTime) {
        mVideoWrapper.sendDataFrame(data, nanoTime);
    }
}
