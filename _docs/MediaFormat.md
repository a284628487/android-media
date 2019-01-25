## **Audio**

`MediaFormat.createAudioFormat`

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

## **Video**

`MediaFormat.createVideoFormat`

```java
MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
// 设置format，COLOR_FormatSurface indicates that the data will be a GraphicBuffer metadata reference.
format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
```

## **MediaCodec**

- createEncoderByType
- createDecoderByType

