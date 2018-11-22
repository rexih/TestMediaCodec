[TOC]
## 说明
用kotlin编写的java层音视频解码Demo代码

将测试用的mp4文件放置在SD的Download/test.mp4下

- MediaExtractor
- MediaCodec
- 权限
- SurfaceView
- 弱引用
- kotlin特性：数组声明；null处理；object；data class；SAM；when；for in until


## 视频解码

1. MediaExtractor设置数据源
2. 获取特定的track的Id
3. 获取此track的信息(mime)
4. 根据track的信息创建decoder并关联播放的surface
5. decoder开始
6. 从decoder获取一个输入缓冲的索引
7. 从extractor获取一段视频缓冲数据
8. 将缓冲数据放入输入索引指向的缓冲区
9. decoder获取一段待处理的输出缓冲
10. 同步延迟
11. 将输出的流媒体数据渲染到Surface上
12. 停止解码，释放资源

## 音频解码

与视频差不多，差异的地方：

- 根据track信息构建audioTrack并开始播放
- 从解码器的输出Buffer中获取PCM流媒体数据，写入到audioTrack中

参考:
[Android MediaExtractor + MediaCodec 实现简易播放器](https://www.jianshu.com/p/ec5fd369c518)