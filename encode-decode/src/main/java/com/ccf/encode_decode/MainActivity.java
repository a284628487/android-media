package com.ccf.encode_decode;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.ccf.encode_decode.decode.AVPlayActivity;
import com.ccf.encode_decode.encode.audio.AudioRecordActivity;
import com.ccf.encode_decode.encode.camera.nopreview.EncodeCameraActivity;
import com.ccf.encode_decode.encode.camera.preview.EncodeCameraPreviewActivity;
import com.ccf.encode_decode.encode.fbocamera.CameraPreviewGLESEncodeActivity;
import com.ccf.encode_decode.encode.glesdraw.GlesEncodeActivity;
import com.ccf.encode_decode.encode.surfaceview.SurfaceViewEncodeActivity;

public class MainActivity extends AppCompatActivity {

    final String TAG = "MainActivity";

    private String[] permissions = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions(permissions, 0);

    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.record_audio:
                startActivity(new Intent(this, AudioRecordActivity.class));
                break;
            case R.id.encode_gles_to_mp4:
                startActivity(new Intent(this, GlesEncodeActivity.class));
                break;
            case R.id.encode_surfaceview:
                startActivity(new Intent(this, SurfaceViewEncodeActivity.class));
                break;
            case R.id.encode_camera:
                startActivity(new Intent(this, EncodeCameraActivity.class));
                break;
            case R.id.encode_camera_preview: // 只录制视频
                startActivity(new Intent(this, EncodeCameraPreviewActivity.class));
                break;
            case R.id.encode_camera_preview_audio: // 同时录制视频音频
                startActivity(new Intent(this, EncodeCameraPreviewActivity.class)
                        .putExtra("flag", 1));
                break;
            case R.id.encode_camera_with_gles:
                startActivity(new Intent(this, CameraPreviewGLESEncodeActivity.class));
                break;
            case R.id.play_video:
                startActivity(new Intent(this, AVPlayActivity.class));
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        int index = 0;
        for (String permission : permissions) {
            Log.e(TAG, "onRequestPermissionsResult: " + permission + " - " + grantResults[index++]);
        }
    }
}
