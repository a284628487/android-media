package com.ccf.encode_decode.encode.camera.preview;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import com.ccf.encode_decode.R;
import com.ccf.encode_decode.encode.camera.ICameraRecordInterface;
import com.ccf.encode_decode.encode.camera.av.MuxCameraUtils;

public class EncodeCameraPreviewActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = "EncodeCameraPreview";

    private Button mStart, mStop;

    private SurfaceView mSurfaceView;

    private ICameraRecordInterface mUtils;

    private int flag = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_encode_camera_preview);

        mStart = findViewById(R.id.startRecord);
        mStop = findViewById(R.id.stopRecord);
        mStop.setEnabled(false);

        mSurfaceView = findViewById(R.id.surfaceView);
        mSurfaceView.getHolder().addCallback(this);

        flag = getIntent().getIntExtra("flag", 0);
    }

    public void onClick(View view) {
        if (view == mStart) {
            mStart.setEnabled(false);
            mStop.setEnabled(true);

            mUtils.startRecording();
        } else if (view == mStop) {
            mStop.setEnabled(false);
            mStart.setEnabled(true);
            if (null != mUtils) {
                mUtils.stop();
            }
        } else {
            mUtils.autoFocus();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (flag == 1) {
            mUtils = new MuxCameraUtils(holder, width, height);
        } else {
            mUtils = new CameraUtils(holder, width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (null != mUtils) {
            mUtils.release();
        }
    }
}

// https://blog.csdn.net/bxjie/article/details/46046415
// https://blog.csdn.net/a360940265a/article/details/80447547
