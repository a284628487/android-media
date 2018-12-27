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

import com.ccf.encode_decode.audio.AudioRecordActivity;

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