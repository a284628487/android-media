package com.ccflying.decodevideo;

import android.animation.TimeAnimator;
import android.graphics.SurfaceTexture;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";
    private TextureView mTextureView;
    private AVDecodeWrapper mWrapper;
    private TimeAnimator mTimeAnimator = new TimeAnimator();
    private String videoPath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.textureView);
        videoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/video.mp4";
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mTextureView.isAvailable()) {
            startPlay(new Surface(mTextureView.getSurfaceTexture()), mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    startPlay(new Surface(surface), width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            });
        }
    }

    private void startPlay(Surface surface, int viewWidth, int viewHeight) {
        mWrapper = new AVDecodeWrapper(videoPath, surface);
        //
        int videoWidth = mWrapper.getVideoWidth();
        int videoHeight = mWrapper.getVideoHeight();
        //
        computeWidthHeight(viewWidth, viewHeight, videoWidth, videoHeight);
        //
        mTimeAnimator.setTimeListener(new TimeAnimator.TimeListener() {
            @Override
            public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
                if (mWrapper.isEos()) {
                    mTimeAnimator.end();
                    return;
                }
                mWrapper.updateDecode(totalTime);
            }
        });
        mTimeAnimator.start();
    }

    private void computeWidthHeight(int viewWidth, int viewHeight, int videoWidth, int videoHeight) {
        float viewRatio = viewWidth * 1f / viewHeight;
        float videoRatio = videoWidth * 1f / videoHeight;
        ViewGroup.LayoutParams params = mTextureView.getLayoutParams();
        // view宽高比 小于 视频宽高比
        if (viewRatio < videoRatio) {
            params.width = viewWidth;
            params.height = (int) (viewWidth / videoRatio);
            mTextureView.setLayoutParams(params);
        } else {
            params.height = viewHeight;
            params.width = (int) (viewHeight * videoRatio);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mTimeAnimator.end();
        if (null != mWrapper)
            mWrapper.stopDecode();
    }
}
