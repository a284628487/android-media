package com.ccf.encode_decode.encode.fbocamera;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.ccf.encode_decode.R;
import com.ccf.encode_decode.encode.fbocamera.camera.CameraGLSurfaceView;
import com.ccf.encode_decode.encode.fbocamera.encodec.BaseVideoEncoder;

import java.io.File;

public class CameraPreviewGLESEncodeActivity extends AppCompatActivity {

    private CameraGLSurfaceView cameraEglSurfaceView;

    private Button button;

    private BaseVideoEncoder videoEncoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_encode);
        cameraEglSurfaceView = findViewById(R.id.camera_view);
        button = findViewById(R.id.recode);
    }

    public void recode1(View view) {
        if (videoEncoder == null) {
            startRecode(44100, 16, 2);
            button.setText("正在录制");
        } else {
            videoEncoder.stopRecode();
            button.setText("开始录制");
            videoEncoder = null;
        }
    }

    private void startRecode(int samplerate, int bit, int channels) {
        videoEncoder = new BaseVideoEncoder(this, cameraEglSurfaceView.getTextureId());
        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() +
                File.separator + "testEncode.mp4";
        videoEncoder.initEncoder(cameraEglSurfaceView.getEglContext(), filePath,
                cameraEglSurfaceView.getCameraPrivewHeight(), // 相机的宽和高是相反的
                cameraEglSurfaceView.getCameraPrivewWidth());

        videoEncoder.startRecode();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        cameraEglSurfaceView.previewAngle(this);
    }

}
