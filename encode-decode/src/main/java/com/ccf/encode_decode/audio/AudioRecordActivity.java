package com.ccf.encode_decode.audio;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.ccf.encode_decode.R;

public class AudioRecordActivity extends AppCompatActivity {

    private static final String TAG = "AudioRecordActivity";

    private Button mStart, mStop;

    private AudioEncoderWrapper mWrapper;

    private int index = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_audio);
        mStart = findViewById(R.id.startRecord);
        mStop = findViewById(R.id.stopRecord);
        mStop.setEnabled(false);


        // test MediaCodec capabilities
        for (int i = MediaCodecList.getCodecCount() - 1; i >= 0; i--) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (codecInfo.isEncoder()) {
                for (String t : codecInfo.getSupportedTypes()) {
                    try {
                        Log.w("CodecCapability", t);
                        Log.w("CodecCapability", codecInfo.getCapabilitiesForType(t).toString());
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void onClick(View view) {
        if (view == mStart) {
            mStart.setEnabled(false);
            mStop.setEnabled(true);

            String path = Environment.getExternalStorageDirectory().getPath()
                    + "/testWrapper" + (index++) + ".m4a";
            mWrapper = new AudioEncoderWrapper(path);
            mWrapper.startRecording();
        } else {
            mStop.setEnabled(false);
            mStart.setEnabled(true);
            mWrapper.stopRecord();
        }
    }

    public void onRecordButtonClick(View v) {
//        recording = !recording;
//        Log.e(TAG, "Record Start: " + String.valueOf(recording));
//        if (recording) {
//            recordButton.setText("Stop");
//            mEncoder = new AudioEncoder(getApplicationContext());
//            audioPoller = new AudioSoftwarePoller();
//            audioPoller.setAudioEncoder(mEncoder);
//            mEncoder.setAudioSoftwarePoller(audioPoller);
//            audioPoller.startPolling();
//        } else {
//            recordButton.setText("Start");
//            if (mEncoder != null) {
//                audioPoller.stopPolling();
//                mEncoder.stop();
//            }
//        }
    }

    static byte[] audioData;

    private static byte[] getSimulatedAudioInput() {
        int magnitude = 10;
        if (audioData == null) {
            //audioData = new byte[1024];
            audioData = new byte[1470]; // this is roughly equal to the audio expected between 30 fps frames
            for (int x = 0; x < audioData.length - 1; x++) {
                audioData[x] = (byte) (magnitude * Math.sin(x));
            }
            Log.i(TAG, "generated simulated audio data");
        }
        return audioData;

    }
}
