package com.ccf.encode_decode.encode.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioEncoderWrapper {

    private final static String TAG = "AudioEncoderWrapper";

    private static final int SAMPLE_RATE = 44100;
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecorder;
    private MediaCodec mediaCodec;
    private MediaMuxer mediaMuxer;
    private MediaFormat audioFormat;

    private boolean isRecording = false;

    private MediaCodec.BufferInfo bufferInfo;

    private boolean mMuxerStarted = false;
    private int trackIndex = 0;

    private static final int BUFFER_SIZE = 1024 * 2;
    // 录制开启的时间
    private long recordStartTime = 0;
    // 结束录制
    private boolean eosReceived = false;

    public AudioEncoderWrapper(String filePath) {
        // int sampleRateInHz, int channelConfig, int audioFormat
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

        bufferInfo = new MediaCodec.BufferInfo();
        // int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes
        audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG,
                AUDIO_FORMAT, bufferSize * 4);

        // String mime, int sampleRate, int channelCount
        audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, SAMPLE_RATE, 1);
        // audioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        // audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRateInHz);
        // audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 128000);
        // 创建audio编解码器
        try {
            mediaCodec = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 配置编码器
        mediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // 开启
        mediaCodec.start();
        //
        try {
            mediaMuxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }
    }

    /**
     * 开启Recorder录音
     */
    public void startRecording() {
        audioRecorder.startRecording();

        isRecording = true;

        recordStartTime = System.nanoTime();

        new Thread() {
            @Override
            public void run() {
                // 创建缓冲区
                byte[] buffer = new byte[BUFFER_SIZE];
                long audioPresentationTimeNs;
                while (isRecording) {
                    audioPresentationTimeNs = System.nanoTime();
                    // 从Recorder中读取数据并填充到buffer中。
                    int readResult = audioRecorder.read(buffer, 0, BUFFER_SIZE);
                    if (readResult == AudioRecord.ERROR_BAD_VALUE || readResult == AudioRecord.ERROR_INVALID_OPERATION)
                        Log.e(TAG, "Read error");
                    // transfer previously encoded data to muxer
                    Log.w(TAG, "offerAudioEncoder");
                    readEncodedData(false);
                    // send current frame data to encoder
                    Log.w(TAG, "sendCurrentFrame");
                    sendBufferToCodec(buffer, audioPresentationTimeNs);
                }
            }
        }.start();
    }

    public void stopRecord() {
        eosReceived = true;
        isRecording = false;
        if (null != audioRecorder) {
            audioRecorder.stop();
            audioRecorder.release();
        }
    }

    private void closeEncoderAndMuxer() {
        closeEncoder();
        closeMuxer();
    }

    private void closeEncoder() {
        if (null != mediaCodec) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
    }

    private void closeMuxer() {
        if (null != mediaMuxer) {
            mMuxerStarted = false;
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
        }
    }

    /**
     * 将输入的byte传递给编码器进行编码
     *
     * @param input
     * @param presentationTimeNs
     */
    private void sendBufferToCodec(byte[] input, long presentationTimeNs) {
        try {
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(input);
                //
                long presentationTimeUs = (presentationTimeNs - recordStartTime) / 1000;
                // 结束
                if (eosReceived) {
                    Log.e(TAG, "EOS received in offerEncoder");
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, presentationTimeUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    // 读取数据
                    readEncodedData(true);
                    // 关闭Encoder和Muxer
                    closeEncoderAndMuxer();
                } else {
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, presentationTimeUs, 0);
                }
            } else {
                Log.e(TAG, "sendCurrentFrame: ??? " + inputBufferIndex);
            }
        } catch (Throwable t) {
            Log.e(TAG, "sendCurrentFrame exception");
            t.printStackTrace();
        }
    }

    private void readEncodedData(boolean endOfStream) {
        final int TIMEOUT_USEC = 100;
        int encoderStatus = 0;
        while ((encoderStatus = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
            if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // TODO ... ignore
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { // 开启编码器
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed after muxer start");
                }
                MediaFormat newFormat = mediaCodec.getOutputFormat();

                // now that we have the Magic Goodies, start the muxer
                trackIndex = mediaMuxer.addTrack(newFormat);
                mediaMuxer.start();
                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = mediaCodec.getOutputBuffer(encoderStatus);
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Log.e(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    bufferInfo.size = 0;
                }

                if (bufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(bufferInfo.offset);
                    encodedData.limit(bufferInfo.offset + bufferInfo.size);
                    mediaMuxer.writeSampleData(trackIndex, encodedData, bufferInfo);
                }
                // 释放encoderStatus对应的Buffer.
                mediaCodec.releaseOutputBuffer(encoderStatus, false);
                // 录制结束
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.e(TAG, "reached end of stream unexpectedly");
                    } else {
                        Log.e(TAG, "end of stream reached");
                    }
                    break;// out of while
                }
            }
        }
    }
}
