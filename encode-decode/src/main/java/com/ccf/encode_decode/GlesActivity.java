package com.ccf.encode_decode;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GlesActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private int mProgram;

    private FloatBuffer mPositionBuffer;

    private static float[] mCoordinates = { //
            0f, 0.5f, 0f, //
            -0.5f, -0.5f, 0f, //
            0.5f, -0.5f, 0f
    };

    private float[] mColors = {1.0f, 1.0f, 1.0f, 1.0f};

    private Random mRandom = new Random();

    private GLSurfaceView mSurfaceView;

    private int mGLPositionHandle, mGLColorHandle;
    // 每个顶点坐标个数
    private static final int COORDS_PER_VERTEX = 3;
    // 顶点偏移量，即每个顶点在Buffer中所占的byte位数。
    private int vertexStride = COORDS_PER_VERTEX * 4;
    // 顶点个数
    private int vertexCount = mCoordinates.length / COORDS_PER_VERTEX;

    private String vertex_shader = "attribute vec4 vPosition;\n" +
            "void main() {\n" +
            "    gl_Position = vPosition;\n" +
            "}";

    private String fragment_shader = "precision mediump float;\n" +
            "uniform vec4 vColor;\n" +
            "void main() {\n" +
            "    gl_FragColor = vColor;\n" +
            "}";

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1) {
                sendEmptyMessageDelayed(1, 500);
                mSurfaceView.requestRender();
            } else {
                removeMessages(1);
                removeMessages(1);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gles);
        mSurfaceView = findViewById(R.id.glSurfaceView);

        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setRenderer(this);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.sendEmptyMessageDelayed(1, 500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.sendEmptyMessage(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1);

        mProgram = GLES20.glCreateProgram();
        int fShaderId = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fShaderId, fragment_shader);
        GLES20.glCompileShader(fShaderId);
        //
        int vShaderId = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vShaderId, vertex_shader);
        GLES20.glCompileShader(vShaderId);
        //
        GLES20.glAttachShader(mProgram, vShaderId);
        GLES20.glAttachShader(mProgram, fShaderId);
        //
        GLES20.glLinkProgram(mProgram);
        //
        mPositionBuffer = allocateFloatBuffer(mCoordinates);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        // 将程序加入到OpenGLES2.0环境
        GLES20.glUseProgram(mProgram);
        //
        mGLPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        GLES20.glEnableVertexAttribArray(mGLPositionHandle);
        GLES20.glVertexAttribPointer(mGLPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, vertexStride, mPositionBuffer);
        //
        mGLColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");
        //
        mColors[1] = mRandom.nextFloat();
        mColors[2] = mRandom.nextFloat();
        //
        GLES20.glUniform4fv(mGLColorHandle, 1, mColors, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
        //
        GLES20.glDisableVertexAttribArray(mGLPositionHandle);
    }

    public static FloatBuffer allocateFloatBuffer(float[] input) {
        // float占4个字节，所以是length * 4，
        ByteBuffer floatBB = ByteBuffer.allocateDirect(input.length * 4);
        floatBB.order(ByteOrder.nativeOrder());
        FloatBuffer floatBuffer = floatBB.asFloatBuffer();
        floatBuffer.put(input);
        floatBuffer.position(0);
        return floatBuffer;
    }

    public static ShortBuffer allocateShortBuffer(short[] input) {
        // short占2个字节，所以是length * 2，
        ByteBuffer floatBB = ByteBuffer.allocateDirect(input.length * 2);
        floatBB.order(ByteOrder.nativeOrder());
        ShortBuffer shortBuffer = floatBB.asShortBuffer();
        shortBuffer.put(input);
        shortBuffer.position(0);
        return shortBuffer;
    }
}
