package com.ccflying.decodevideo;

import android.view.Surface;

public class AVDecodeWrapper {
    private final Surface surface;
    private final String videoSource;

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