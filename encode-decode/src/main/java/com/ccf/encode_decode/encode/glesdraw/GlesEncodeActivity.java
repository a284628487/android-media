package com.ccf.encode_decode.encode.glesdraw;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.ccf.encode_decode.R;

import java.io.IOException;

public class GlesEncodeActivity extends AppCompatActivity {

    private static final String TAG = "AudioRecordActivity";

    private Button mStart, mStop;

    private EncodeOpenGLES mEncoder;

    private int index = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_encode_gles_to_mp4);
        //
        mStart = findViewById(R.id.startRecord);
        mStop = findViewById(R.id.stopRecord);
        mStop.setEnabled(false);
    }

    public void onClick(View view) {
        if (view == mStart) {
            mStart.setEnabled(false);
            mStop.setEnabled(true);
            mEncoder = new EncodeOpenGLES("testMp4_" + (index++));
            try {
                mEncoder.startRecording();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            mStop.setEnabled(false);
            mStart.setEnabled(true);
            mEncoder.stop();
        }
    }
}
