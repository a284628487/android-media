package com.ccf.encode_decode.encode.camera.nopreview;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.ccf.encode_decode.R;

public class EncodeCameraActivity extends AppCompatActivity {

    private CameraToMp4 mCameraToMp4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_encode_camera);
        mCameraToMp4 = new CameraToMp4(this);
    }

    public void onClick(View v) {
        Toast.makeText(this, "Begin", Toast.LENGTH_SHORT).show();
        try {
            // 开启录制
            mCameraToMp4.startRecording();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
}
// https://stackoverflow.com/questions/22457623/surfacetextures-onframeavailable-method-always-called-too-late/22461014#22461014
// https://stackoverflow.com/questions/26487832/cameratompegtest-java-is-not-working-ends-with-illegalstateexception-cant-sto
