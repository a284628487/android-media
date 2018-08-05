package com.ccflying.endoceopengl;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
    }

    public void beginEocnde(View v) {
        EncodeOpenGLES eog = new EncodeOpenGLES();
        try {
            eog.encodeToMP4();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
