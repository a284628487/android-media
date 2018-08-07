AudioRecord
===========

**AudioRecord**，用于从硬件设备录制音频，有三个方法用于从AudioRecord中获取音频数据，`read(byte[], int, int)`, `read(short[], int, int)` or `read(ByteBuffer, int)`。根据音频格式，选择使用合适的方法来实现。

## Constants

```Java
    /**
     * The read mode indicating the read operation will block until all data
     * requested has been read.
     */
    public final static int READ_BLOCKING = 0;

    /**
     * The read mode indicating the read operation will return immediately after
     * reading as much audio data as possible without blocking.
     */
    public final static int READ_NON_BLOCKING = 1;
			
```

## Constructor

初始化实例时，`AudioRecord`类内部创建一个`audio buffer`，用于填充audio数据，该buffer的size由构造函数参数指定。
通俗的讲就是里面有个缓冲区，`AudioRecord`不停的从硬件获取数据并填充进去(生产者)，`read`方法从该缓冲区中读取数据(消费者)。

```Java
/**
 * @param audioSource 参考MediaRecorder.AudioSource。
 * @param sampleRateInHz 采样率. 44100Hz 可以保证在任何设备上被识别, 但是22050, 16000, 11025 在一些设备上也被支持。
 * @param channelConfig 声道, AudioFormat#CHANNEL_IN_MONO/CHANNEL_IN_STEREO，其中{CHANNEL_IN_MONO}在所有设备上都被支持。
 * @param audioFormat 音频数据格式, AudioFormat#ENCODING_PCM_8BIT/ENCODING_PCM_16BIT/ENCODING_PCM_FLOAT。
 * @param bufferSizeInBytes 缓冲区大小, 参见 getMinBufferSize(int, int, int) 获取最小的缓冲区大小。
 */
public AudioRecord(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes) {}

/** 
 * 获取最小的缓冲区大小，如果传递给AudioRecord的bufferSizeInBytes小于该方法返回的数值，则该AudioRecord创建失败。
 * @param sampleRateInHz 采样率. 44100Hz 可以保证在任何设备上被识别, 但是22050, 16000, 11025 在一些设备上也被支持。
 * @param channelConfig 声道, AudioFormat#CHANNEL_IN_MONO/CHANNEL_IN_STEREO，其中{CHANNEL_IN_MONO}在所有设备上都被支持。
 * @param audioFormat 音频数据格式, AudioFormat#ENCODING_PCM_8BIT/ENCODING_PCM_16BIT。
 */
static public int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat) {}
```

```Java
package com.ccflying.encodeaudio;

import java.nio.ByteBuffer;

import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.AudioRecord.OnRecordPositionUpdateListener;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.test.AndroidTestCase;

public class AudioRecordTest extends AndroidTestCase {

    private AudioRecord mAudioRecord;
    private int mHz = 44100;
    private boolean mIsOnMarkerReachedCalled;
    private boolean mIsOnPeriodicNotificationCalled;
    private boolean mIsHandleMessageCalled;
    private Looper mLooper;

    @Override
    protected void setUp() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        Thread t = new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mLooper = Looper.myLooper();
                synchronized (this) {
                    mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, mHz,
                            AudioFormat.CHANNEL_CONFIGURATION_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            AudioRecord.getMinBufferSize(mHz,
                                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                                    AudioFormat.ENCODING_PCM_16BIT) * 10);
                    this.notify();
                }
                Looper.loop();
            }
        };
        synchronized (t) {
            t.start(); // will block until we wait
            t.wait();
        }
        assertNotNull(mAudioRecord);
    }

    public void release() {
        mAudioRecord.release();
        mLooper.quit();
    }

    private void reset() {
        mIsOnMarkerReachedCalled = false;
        mIsOnPeriodicNotificationCalled = false;
        mIsHandleMessageCalled = false;
    }

    public void testAudioRecordOP() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        final int SLEEP_TIME = 10;
        final int RECORD_TIME = 10000; // 录制10s的音频
        assertEquals(AudioRecord.STATE_INITIALIZED, mAudioRecord.getState());

        int markerInFrames = mAudioRecord.getSampleRate() / 2;
        assertEquals(AudioRecord.SUCCESS,
                mAudioRecord.setNotificationMarkerPosition(markerInFrames));
        assertEquals(markerInFrames, mAudioRecord.getNotificationMarkerPosition());
        //
        int periodInFrames = mAudioRecord.getSampleRate();
        assertEquals(AudioRecord.SUCCESS,
                mAudioRecord.setPositionNotificationPeriod(periodInFrames));
        assertEquals(periodInFrames, mAudioRecord.getPositionNotificationPeriod());
        //
        final OnRecordPositionUpdateListener listener = new OnRecordPositionUpdateListener() {
            public void onMarkerReached(AudioRecord recorder) {
                mIsOnMarkerReachedCalled = true;
            }

            public void onPeriodicNotification(AudioRecord recorder) {
                mIsOnPeriodicNotificationCalled = true;
            }
        };
        final Handler handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                mIsHandleMessageCalled = true;
                super.handleMessage(msg);
            }
        };
        // use handler
        mAudioRecord.setRecordPositionUpdateListener(listener, handler);

        // use byte array as buffer
        final int BUFFER_SIZE = 102400;
        byte[] byteData = new byte[BUFFER_SIZE];
        // or sort array as buffer
        short[] shortData = new short[BUFFER_SIZE];
        // or use ByteBuffer as buffer
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        // -> mAudioRecord.read(byteBuffer, BUFFER_SIZE);

        long time = System.currentTimeMillis();
        // 开始录制
        mAudioRecord.startRecording();
        assertEquals(AudioRecord.RECORDSTATE_RECORDING, mAudioRecord.getRecordingState());
        // 录制10s钟的音频.
        while (System.currentTimeMillis() - time < RECORD_TIME) {
            Thread.sleep(SLEEP_TIME);
            mAudioRecord.read(byteData, 0, BUFFER_SIZE);
        }
        // mAudioRecord.release();
        mAudioRecord.stop();
        // 判断状态
        assertEquals(AudioRecord.RECORDSTATE_STOPPED, mAudioRecord.getRecordingState());
        assertTrue(mIsOnMarkerReachedCalled);
        assertTrue(mIsOnPeriodicNotificationCalled);
        // 重置
        reset();
        MediaMuxer muxer;
    }

    private boolean hasMicrophone() {
        return getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
    }
}

```
> Src: `http://androidxref.com/4.4.2_r2/xref/cts/tests/tests/media/src/android/media/cts/AudioRecordTest.java`

