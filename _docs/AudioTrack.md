AudioTrack
==========

## 缓冲区

`AudioTrack.getMinBufferSize()`接口返回最小数据缓冲区的大小，它是声音能正常播放的最低保障，从函数参数来看，返回值取决于采样率、采样深度、声道数这三个属性。**MODE_STREAM**模式下，应用程序重点参考其返回值然后确定分配多大的数据缓冲区。
如果数据缓冲区分配得过小，那么播放声音会频繁遭遇 underrun，指生产者`AudioTrack`提供数据的速度跟不上消费者`AudioFlinger::PlaybackThread`消耗数据的速度，反映到播放效果就是声音断续卡顿，严重影响听觉体验。

> 最小缓冲区的大小 = `最低帧数*声道数*采样深度`，(采样深度以字节为单位)，在视频中，如果帧数过低那么画面会有卡顿感，同理音频也是一样。

## 音频领域相关概念

1. 帧（frame）：帧表示一个完整的声音单元，所谓的声音单元是指一个采样样本；如果是双声道，那么一个完整的声音单元就是2个样本，如果是5.1声道，那么一个完整的声音单元就是6个样本了。帧的大小（一个完整的声音单元的数据量）等于声道数乘以采样深度，即`frameSize = channelCount * bytesPerSample`。帧的概念非常重要，无论是框架层还是内核层，都是以帧为单位去管理音频数据缓冲区的。

2. 传输延迟（latency）：传输延迟表示一个周期的音频数据的传输时间。我们再引入周期（period）的概念：Linux ALSA 把数据缓冲区划分为若干个块，dma 每传输完一个块上的数据即发出一个硬件中断，cpu 收到中断信号后，再配置 dma 去传输下一个块上的数据；一个块即是一个周期，
周期大小（periodSize)即是一个数据块的帧数。再回到传输延迟（latency），传输延迟等于周期大小除以采样率，即`latency = periodSize / sampleRate`。

3. 音频重采样：音频重采样是指把一个采样率的数据转换为另一个采样率的数据。Android 原生系统上，音频硬件设备一般都工作在一个固定的采样率上（如 48 KHz），因此所有音轨数据都需要重采样到这个固定的采样率上，然后再输出。为什么要这么做？系统中可能存在多个音轨同时播放，而每个音轨的采样率可能是不一致的。比如在播放音乐的过程中，来了一个提示音，这时需要把音乐和提示音混音并输出到硬件设备，而音乐的采样率和提示音的采样率不一致，问题来了，如果硬件设备工作的采样率设置为音乐的采样率的话，那么提示音就会失真。因此最简单见效的解决方法是：硬件设备工作的采样率固定一个值，所有音轨在 AudioFlinger 都重采样到这个采样率上，混音后输出到硬件设备，保证所有音轨听起来都不失真。

## 最低帧数

`AudioTrack::getMinFrameCount()`根据硬件设备的配置信息（采样率、周期大小、传输延迟）和音轨的采样率，计算出一个最低帧数（应用程序至少设置多少个帧才能保证声音正常播放）。


- [HWEncoderExperiments](https://github.com/OnlyInAmerica/HWEncoderExperiments/tree/audioonly/HWEncoderExperiments/src/main/java/net/openwatch/hwencoderexperiments)
- [CTS1](http://androidxref.com/4.4.2_r2/xref/cts/tests/tests/media/src/android/media/cts/)
- [CameraRecordingStream](http://androidxref.com/4.4.2_r2/xref/pdk/apps/TestingCamera2/src/com/android/testingcamera2/CameraRecordingStream.java)

