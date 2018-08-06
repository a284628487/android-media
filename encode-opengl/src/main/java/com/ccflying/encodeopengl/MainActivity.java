package com.ccflying.encodeopengl;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.ccflying.encodeaudio.AudioRecordActivity;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
    }

    public void beginEncode(View v) {
        if (v.getId() == R.id.record_video) {
            EncodeOpenGLES eog = new EncodeOpenGLES();
            try {
                eog.encodeToMP4();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            startActivity(new Intent(this, AudioRecordActivity.class));
        }
    }

}
