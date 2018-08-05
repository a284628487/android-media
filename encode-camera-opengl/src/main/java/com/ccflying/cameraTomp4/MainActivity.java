package com.ccflying.cameraTomp4;

import android.media.MediaCodec;
import android.opengl.GLSurfaceView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private CameraToMp4 mCameraToMp4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCameraToMp4 = new CameraToMp4();
    }

    public void beginEncode(View v) {
        Toast.makeText(this, "Begin", Toast.LENGTH_SHORT).show();
        try {
            mCameraToMp4.encodeCameraPreviewToMp4();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        // MediaCodec mc;
        // GLSurfaceView sv;
    }
}
// https://stackoverflow.com/questions/22457623/surfacetextures-onframeavailable-method-always-called-too-late/22461014#22461014
// https://stackoverflow.com/questions/26487832/cameratompegtest-java-is-not-working-ends-with-illegalstateexception-cant-sto
