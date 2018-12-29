package com.ccf.encode_decode.decode;

import android.view.Surface;

import com.ccf.encode_decode.decode.audio.AudioDecodeWrapper;
import com.ccf.encode_decode.decode.video.VideoDecodeWrapper;

public class AVDecodeWrapper {
    protected final Surface surface;
    protected final String videoSource;

    private AudioDecodeWrapper mAudioDecodeWrapper;
    private VideoDecodeWrapper mVideoDecodeWrapper;

    public AVDecodeWrapper(String videoSource, Surface surface) {
        this.videoSource = videoSource;
        this.surface = surface;

        mAudioDecodeWrapper = new AudioDecodeWrapper(videoSource);
        mVideoDecodeWrapper = new VideoDecodeWrapper(videoSource, surface);
    }

    public void updateDecode(long totalTime) {
        if (null != mAudioDecodeWrapper && !mAudioDecodeWrapper.isEos())
            mAudioDecodeWrapper.updateDecode(totalTime);
        if (null != mVideoDecodeWrapper && !mVideoDecodeWrapper.isEos())
            mVideoDecodeWrapper.updateDecode(totalTime);
    }

    public boolean isEos() {
        if (null != mAudioDecodeWrapper && null != mVideoDecodeWrapper) {
            return mAudioDecodeWrapper.isEos() && mVideoDecodeWrapper.isEos();
        }
        return (null != mAudioDecodeWrapper) ? mAudioDecodeWrapper.isEos() :
                ((null != mVideoDecodeWrapper) ? mVideoDecodeWrapper.isEos() : true);
    }

    public int getVideoWidth() {
        return (null != mVideoDecodeWrapper) ? mVideoDecodeWrapper.getVideoWidth() : 0;
    }

    public int getVideoHeight() {
        return (null != mVideoDecodeWrapper) ? mVideoDecodeWrapper.getVideoHeight() : 0;
    }

    public void stopDecode() {
        if (null != mAudioDecodeWrapper) mAudioDecodeWrapper.stopDecode();
        if (null != mVideoDecodeWrapper) mVideoDecodeWrapper.stopDecode();
    }
}