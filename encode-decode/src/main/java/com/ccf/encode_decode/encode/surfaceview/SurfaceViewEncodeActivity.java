package com.ccf.encode_decode.encode.surfaceview;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.EGLContext;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import com.ccf.encode_decode.R;

import java.io.IOException;

public class SurfaceViewEncodeActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "SVEncodeActivity";

    private Button mStart, mStop;

    private MySurfaceView mSurfaceView;

    private SurfaceHolder mHolder;

    private int mWidth, mHeight;

    private Paint mPaint;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_encode_surfaceview);

        mStart = findViewById(R.id.startRecord);
        mStop = findViewById(R.id.stopRecord);
        mStop.setEnabled(false);
        //
        mSurfaceView = findViewById(R.id.surfaceView);
        //
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void onClick(View view) {
        if (view == mStart) {
            mStart.setEnabled(false);
            mStop.setEnabled(true);
            EGLContext mShareContext = mSurfaceView.getShareContext();
//            mEncoder = new EncodeSurfaceView("testSurfaceView2.mp4", mShareContext);
//            try {
//                mEncoder.startRecording(mSurfaceView.getEGLSurface());
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
        } else {
            mStop.setEnabled(false);
            mStart.setEnabled(true);
//            mEncoder.stop();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mHolder = holder;
        drawSomething();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mHolder = holder;
        mWidth = width;
        mHeight = height;
        drawSomething();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mHolder = null;
    }


    // nothing
    private void drawSomething() {
        if (mWidth == 0) {
            return;
        }

        Canvas canvas = null;
        try {
            canvas = mHolder.lockCanvas();
            if (null == canvas)
                return;
            canvas.drawColor(Color.WHITE);
            mPaint.setColor(0xffff4400);
            canvas.drawRect(0, 0, mWidth / 2, mHeight / 2, mPaint);
        } finally {
            if (null != canvas) {
                mHolder.unlockCanvasAndPost(canvas);
            }
        }
    }
}
