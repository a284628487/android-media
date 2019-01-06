package com.ccf.encode_decode.encode.camera.preview;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;

import com.ccf.encode_decode.encode.camera.ICameraRecordInterface;

import java.io.IOException;

public class CameraUtils implements Camera.PreviewCallback, Camera.AutoFocusCallback, ICameraRecordInterface {

    private String TAG = "CameraUtils";

    protected int width;
    protected int height;

    private int previewWidth;
    private int previewHeight;
    //
    private Camera mCamera;

    private SurfaceHolder mSurfaceHolder;
    //
    private VideoEncoderWrapper mEncoder;

    public CameraUtils(SurfaceHolder holder, int width, int height) {
        this.mSurfaceHolder = holder;
        this.width = width;
        this.height = height;
        //
        configCamera();
    }

    private void configCamera() {
        mCamera = Camera.open();
        //
        Camera.Parameters parameters = mCamera.getParameters();
        Camera.Size size = parameters.getPreferredPreviewSizeForVideo();
        parameters.setPreviewFormat(ImageFormat.NV21);
        parameters.setPreviewSize(size.width, size.height);
        previewWidth = size.width;
        previewHeight = size.height;
        //
        mCamera.setParameters(parameters);
        //
        mCamera.setDisplayOrientation(90);
        mCamera.setPreviewCallback(this);
        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
    }

    @Override
    public void startRecording() {
        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.videoOnly.mp4";
        mEncoder = new VideoEncoderWrapper(filePath, previewWidth, previewHeight);
        mEncoder.startRecording();
    }

    @Override
    public void stop() {
        if (null != mEncoder) {
            mEncoder.stop();
            mEncoder = null;
        }
    }

    @Override
    public void release() {
        if (null != mEncoder) {
            mEncoder.stop();
            mEncoder = null;
        }
        if (null != mCamera) {
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (null == mCamera)
            return;
        if (null == mEncoder) {
            mCamera.addCallbackBuffer(data);
            return;
        }
        mEncoder.sendDataFrame(data, System.nanoTime());
        mCamera.addCallbackBuffer(data);
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        Log.d(TAG, "onAutoFocus: " + success);
    }

    @Override
    public void autoFocus() {
        mCamera.autoFocus(this);
    }
}
