package cn.rexih.android.testmediacodec

import android.media.*
import android.util.Log
import android.view.Surface
import java.lang.ref.WeakReference
import java.nio.ByteBuffer


/**
 *
 * @package cn.rexih.android.testmediacodec
 * @file Player
 * @date 2018/11/22
 * @author huangwr
 * @version %I%, %G%
 */
object Player {

    var surface: Surface? = null
    var filepath: String? = null
    var isPlaying = false
    var isPaused = false
    val TIMEOUT_US: Long = (0.01 * 1000000).toLong()
    val TAG = "Player"

    var refThreadVideo: WeakReference<Thread>? = null
    var refThreadAudio: WeakReference<Thread>? = null


    init {
        println("Player inited")

    }

    fun initMedia(surface: Surface?, filepath: String) {
        this.surface = surface
        this.filepath = filepath
        isPlaying = false
        isPaused = false
    }

    fun destroyMedia() {
        stop()
        surface = null
        filepath = null
        refThreadVideo?.get()?.interrupt()
        refThreadAudio?.get()?.interrupt()
        clearRef()
    }

    fun clearRef() {
        refThreadVideo?.clear()
        refThreadAudio?.clear()
    }

    @Synchronized
    fun play() {
        if (!isPlaying) {
            surface ?: return
            filepath ?: return

            val threadVideo = Thread(VideoDecode(surface!!, filepath!!))
            val threadAudio = Thread(AudioDecode(filepath!!))
            threadVideo.start()
            threadAudio.start()

            clearRef()
            refThreadVideo = WeakReference<Thread>(threadVideo)
            refThreadAudio = WeakReference<Thread>(threadAudio)

            isPlaying = true
        }
    }

    @Synchronized
    fun stop() {
        isPlaying = false
    }

    @Synchronized
    fun pause() {
        isPaused = true
    }

    @Synchronized
    fun resume() {
        isPaused = false
    }

    data class AudioDecode(val filepath: String) : Runnable {

        private var inputBufferSize = 1

        override fun run() {
            val extractor = MediaExtractor()
            // 1. MediaExtractor设置数据源
            extractor.setDataSource(filepath)

            // 2. 获取特定的track的Id
            val index = getTargetTrackIndex(extractor, "audio/")
            if (index < 0) {
                return
            }

            // 3. 获取此track的信息
            val trackFormat = extractor.getTrackFormat(index)
            // 3.1 track的mime
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            // 4. 根据track信息构建audioTrack并开始播放
            var audioTrack = buildAudioTrack(trackFormat)
            audioTrack.play()

            // 5. 根据track的信息创建decoder
            extractor.selectTrack(index)
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(trackFormat, null, null, 0)

            // 6. decoder开始
            decoder.start()
            // 缓冲信息
            val bufferInfo = MediaCodec.BufferInfo()
            // 解码器的所有输出缓冲区
            val outputBuffers = decoder.outputBuffers
            // 解码器的所有输入缓冲区
            val inputBuffers = decoder.inputBuffers

            // 7. 初始化解码后音频流数据的buf
            var sz = outputBuffers[0].capacity()
            if (sz <= 0) {
                sz = inputBufferSize
            }
            var tempBuf = ByteArray(sz)

            var isAudioEOS = false
            val startTimeMillis = System.currentTimeMillis()

            while (!Thread.interrupted() && // 线程未中断
                isPlaying // 正在播放
            ) {
                if (pauseDecoding()) {
                    continue
                }
                if (!isAudioEOS) {
                    isAudioEOS = decodeMediaData(extractor, decoder, inputBuffers)
                }
                // 11. decoder获取一段待处理的输出缓冲
                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when (outputBufferIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED")
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Log.d(TAG, "INFO_TRY_AGAIN_LATER")
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED")
                    else -> {
                        // 12. 同步延迟
                        decodeDelay(bufferInfo, startTimeMillis)
                        val bufSize = bufferInfo.size
                        if (bufSize > 0) {
                            // 13. 从byteBuffer中获取PCM流媒体数据，写入到audioTrack中
                            val byteBuffer = outputBuffers[outputBufferIndex]

                            if (tempBuf.size < bufSize) {
                                tempBuf = ByteArray(bufSize)
                            }
                            byteBuffer.position(0)
                            byteBuffer.get(tempBuf, 0, bufSize)
                            byteBuffer.clear()
                            audioTrack.write(tempBuf, 0, bufSize)

                        }
                        // 13. 将输出的流媒体数据释放
                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                    }
                }
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }

            }

            // 14. 停止解码，释放资源，关闭audioTrack
            decoder.stop()
            decoder.release()
            extractor.release()
            audioTrack.stop()
            audioTrack.release()
        }

        private fun buildAudioTrack(trackFormat: MediaFormat): AudioTrack {

            // 声道数，单声道还是双声道，决定声道配置是CHANNEL_OUT_MONO还是CHANNEL_OUT_STEREO
            val channelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            // 采样率
            val sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val maxInputSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)


            val frameSizeInBytes = channelCount * 2
            val channelConfig = if (channelCount == 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else {
                AudioFormat.CHANNEL_OUT_STEREO
            }
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, 1, AudioFormat.ENCODING_PCM_16BIT)
            inputBufferSize = if (minBufferSize > 0) {
                minBufferSize * 4
            } else {
                maxInputSize
            }
            inputBufferSize = (inputBufferSize / frameSizeInBytes) * frameSizeInBytes

            return AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                inputBufferSize,
                AudioTrack.MODE_STREAM
            )
        }

    }

    data class VideoDecode(val surface: Surface, val filepath: String) : Runnable {


        override fun run() {

            val extractor = MediaExtractor()
            // 1. MediaExtractor设置数据源
            extractor.setDataSource(filepath)

            // 2. 获取特定的track的Id
            val index = getTargetTrackIndex(extractor, "video/")
            if (index < 0) {
                return
            }

            // 3. 获取此track的信息
            val trackFormat = extractor.getTrackFormat(index)
            // 3.1 track的宽度
            val width = trackFormat.getInteger(MediaFormat.KEY_WIDTH)
            // 3.2 track的高度
            val height = trackFormat.getInteger(MediaFormat.KEY_HEIGHT)
            // 3.3 track的时间
            val time = trackFormat.getLong(MediaFormat.KEY_DURATION) / 1000000
            // 3.4 track的mime
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)

            // 4. 根据track的信息创建decoder并关联播放的surface
            extractor.selectTrack(index)
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(trackFormat, surface, null, 0)

            // 5. decoder开始
            decoder.start()
            // 缓冲信息
            val bufferInfo = MediaCodec.BufferInfo()
            // 解码器的所有输入缓冲区
            var inputBuffers: Array<ByteBuffer> = decoder.inputBuffers

            // 视频结尾
            var isVideoEOS = false
            // 开始时间（延时同步用）
            val startTimeMillis = System.currentTimeMillis()

            while (!Thread.interrupted() && // 线程未中断
                isPlaying // 正在播放
            ) {
                if (pauseDecoding()) {
                    continue
                }

                //可能输入已完成，解码输出还未完成，这种情况下不需要再进行解码的操作
                if (!isVideoEOS) {
                    isVideoEOS = decodeMediaData(extractor, decoder, inputBuffers)
                }

                // 从输出buffer取出数据渲染surface
                renderSurface(decoder, bufferInfo, startTimeMillis)
                // 结尾
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM !== 0) {
                    Log.v(TAG, "buffer stream end")
                    break
                }
            }
            // 12. 停止解码，释放资源
            decoder.stop()
            decoder.release()
            extractor.release()
        }

        private fun renderSurface(
            decoder: MediaCodec,
            bufferInfo: MediaCodec.BufferInfo,
            startTimeMillis: Long
        ) {
            // 9. decoder获取一段待处理的输出缓冲
            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when (outputBufferIndex) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED")
                MediaCodec.INFO_TRY_AGAIN_LATER -> Log.d(TAG, "INFO_TRY_AGAIN_LATER")
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED")
                else -> {
                    // 10. 同步延迟
                    decodeDelay(bufferInfo, startTimeMillis)
                    // 11. 将输出的流媒体数据渲染到Surface上
                    decoder.releaseOutputBuffer(outputBufferIndex, true)
                }
            }
        }


    }

    private fun decodeMediaData(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        inputBuffers: Array<ByteBuffer>
    ): Boolean {
        var isMediaEOS = false
        // 6. 从decoder获取一个输入缓冲的索引
        val inputBufferIndex: Int = decoder.dequeueInputBuffer(TIMEOUT_US)
        if (inputBufferIndex >= 0) {
            val byteBuffer = inputBuffers[inputBufferIndex]
            // 7. 从extractor获取一段视频缓冲数据
            val sampleSize = extractor.readSampleData(byteBuffer, 0)

            // 8. 将缓冲数据放入输入索引指向的缓冲区
            if (sampleSize < 0) {
                // extractor中已经没有待解码的数据
                // 解码器输入缓冲标记为结尾
                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                isMediaEOS = true
            } else {
                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                extractor.advance()
            }
        }
        return isMediaEOS
    }

    private fun getTargetTrackIndex(extractor: MediaExtractor, mediaType: String): Int {
        val trackCount = extractor.trackCount
        var trackFormat: MediaFormat
        for (i in 0 until trackCount) {
            trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mime.startsWith(mediaType)) {
                return i
            }
        }
        return -1
    }

    private fun decodeDelay(bufferInfo: MediaCodec.BufferInfo, startTimeMillis: Long) {
        while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startTimeMillis) {
            try {
                Thread.sleep(10)
            } catch (e: InterruptedException) {
                e.printStackTrace()
                break
            }
        }
    }

    private fun pauseDecoding(): Boolean {
        if (isPaused) {
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            return true
        }
        return false
    }

}