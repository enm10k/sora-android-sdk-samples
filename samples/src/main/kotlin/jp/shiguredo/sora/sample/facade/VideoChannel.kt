package jp.shiguredo.sora.sample.facade

import android.content.Context
import android.os.Handler
import jp.shiguredo.sora.sample.camera.CameraVideoCapturerFactory
import jp.shiguredo.sora.sample.camera.DefaultCameraVideoCapturerFactory
import jp.shiguredo.sora.sample.stats.VideoUpstreamLatencyStatsCollector
import jp.shiguredo.sora.sample.ui.util.SoraRemoteRendererSlot
import jp.shiguredo.sora.sdk.channel.SoraMediaChannel
import jp.shiguredo.sora.sdk.channel.data.ChannelAttendeesCount
import jp.shiguredo.sora.sdk.channel.option.*
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import jp.shiguredo.sora.sdk2.*
import org.webrtc.*

class VideoChannel(
        private val context: Context,
        private val handler: Handler,
        private val configuration: Configuration,
        private val fixedResolution: Boolean = false,
        private val cameraFacing: Boolean = true,
        private val capturerFactory: CameraVideoCapturerFactory =
                DefaultCameraVideoCapturerFactory(context, fixedResolution, cameraFacing),
        private var listener: Listener?
) {

    companion object {
        private val TAG = VideoChannel::class.simpleName
    }

    private val egl: EglBase?
        get() = EglBase.create()

    interface Listener {
        fun onConnect(channel: VideoChannel) {}
        fun onClose(channel: VideoChannel) {}
        fun onError(channel: VideoChannel, reason: SoraErrorReason) {}
        fun onWarning(channel: VideoChannel, reason: SoraErrorReason) {}
        fun onAddRemoteRenderer(channel: VideoChannel, renderer: SurfaceViewRenderer) {}
        fun onRemoveRemoteRenderer(channel: VideoChannel, renderer: SurfaceViewRenderer) {}
        fun onAddLocalRenderer(channel: VideoChannel, renderer: SurfaceViewRenderer) {}
        fun onAttendeesCountUpdated(channel: VideoChannel, attendees: ChannelAttendeesCount) {}
    }

    private val statsCollector = VideoUpstreamLatencyStatsCollector()

    // TODO: SoraMediaChannel は捨てるので不要
    /*
    private val channelListener = object : SoraMediaChannel.Listener {

        override fun onConnect(mediaChannel: SoraMediaChannel) {
            SoraLogger.d(TAG, "[video_channel] @onConnected")
            handler.post {
                listener?.onConnect(this@SoraVideoChannel)
            }
        }

        override fun onClose(mediaChannel: SoraMediaChannel) {
            SoraLogger.d(TAG, "[video_channel] @onClose")
            disconnect()
        }

        override fun onError(mediaChannel: SoraMediaChannel, reason: SoraErrorReason) {
            SoraLogger.d(TAG, "[video_channel] @onError $reason")
            handler.post {
                listener?.onError(this@SoraVideoChannel, reason)
            }
        }

        override fun onError(mediaChannel: SoraMediaChannel, reason: SoraErrorReason, message: String) {
            SoraLogger.d(TAG, "[video_channel] @onError $reason: $message")
            handler.post {
                listener?.onError(this@SoraVideoChannel, reason)
            }
        }

        override fun onWarning(mediaChannel: SoraMediaChannel, reason: SoraErrorReason) {
            SoraLogger.d(TAG, "[video_channel] @onWarning $reason")
            handler.post {
                listener?.onWarning(this@SoraVideoChannel, reason)
            }
        }

        override fun onWarning(mediaChannel: SoraMediaChannel, reason: SoraErrorReason, message: String) {
            SoraLogger.d(TAG, "[video_channel] @onWarning $reason: $message")
            handler.post {
                listener?.onWarning(this@SoraVideoChannel, reason)
            }
        }

        override fun onSenderEncodings(mediaChannel: SoraMediaChannel, encodings: List<RtpParameters.Encoding>) {
            SoraLogger.d(TAG, "[video_channel] @onSenderEncodings: encodings=${encodings}")
        }

        override fun onAddRemoteStream(mediaChannel: SoraMediaChannel, ms: MediaStream) {
            SoraLogger.d(TAG, "[video_channel] @onAddRemoteStream:${ms.id}")
            handler.post {
                remoteRenderersSlot?.onAddRemoteStream(ms)
            }
        }

        override fun onRemoveRemoteStream(mediaChannel: SoraMediaChannel, label: String) {
            SoraLogger.d(TAG, "[video_channel] @onRemoveRemoteStream:${label}")
            handler.post {
                remoteRenderersSlot?.onRemoveRemoteStream(label)
            }
        }

        override fun onAddLocalStream(mediaChannel: SoraMediaChannel, ms: MediaStream, videoSource: VideoSource?) {
            SoraLogger.d(TAG, "[video_channel] @onAddLocalStream")

            if (ms.audioTracks.size > 0) {
                localAudioTrack = ms.audioTracks[0]
            }

            if (needLocalRenderer) {
                handler.post {
                    if (ms.videoTracks.size > 0) {
                        localRenderer = createSurfaceViewRenderer()
                        ms.videoTracks[0].addSink(localRenderer!!)
                        listener?.onAddLocalRenderer(this@SoraVideoChannel, localRenderer!!)
                    }
                }
            }
            handler.post {
                startCapturer()
            }

        }

        override fun onAttendeesCountUpdated(mediaChannel: SoraMediaChannel, attendees: ChannelAttendeesCount) {
            SoraLogger.d(TAG, "[video_channel] @onAttendeesCountUpdated")
            handler.post {
                listener?.onAttendeesCountUpdated(this@SoraVideoChannel, attendees)
            }
        }

        override fun onNotificationMessage(mediaChannel: SoraMediaChannel, notification: NotificationMessage) {
            SoraLogger.d(TAG, "[video_channel] @onNotificationmessage ${notification.eventType} ${notification}")
        }

        override fun onPushMessage(mediaChannel: SoraMediaChannel, push: PushMessage) {
            SoraLogger.d(TAG, "[video_channel] @onPushMessage ${push}")
        }

        override fun onPeerConnectionStatsReady(mediaChannel: SoraMediaChannel, statsReport: RTCStatsReport) {
            // statsReport.statsMap.entries.forEach {
            //     SoraLogger.d(TAG, "${it.key}=${it.value}")
            // }
            statsCollector.newStatsReport(statsReport)
        }
    }

     */

    // TODO: SDK2 の MediaChannel にする
    var mediaChannel:  SoraMediaChannel? = null
    private var capturer: CameraVideoCapturer? = null

    private var capturing = false

    private var closed    = false

    // TODO: MediaChannel から取得すべき
    private var localAudioTrack:     AudioTrack? = null

    private val rendererSlotListener =  object : SoraRemoteRendererSlot.Listener {

        override fun onAddRenderer(renderer: SurfaceViewRenderer) {
            handler.post {
                listener?.onAddRemoteRenderer(this@VideoChannel, renderer)
            }
        }

        override fun onRemoveRenderer(renderer: SurfaceViewRenderer) {
            handler.post {
                listener?.onRemoveRemoteRenderer(this@VideoChannel, renderer)
            }
        }
    }

    private fun createSurfaceViewRenderer(): SurfaceViewRenderer {
        val renderer = SurfaceViewRenderer(context)
        renderer.init(egl?.eglBaseContext, null)
        return renderer
    }

    fun connect() {
        SoraLogger.d(TAG, "[video_channel] try connecting")

        /*
        val mediaOption = Configuration(context, channelId, role).apply {

            if (role.hasUpstream()) {
                if (audioEnabled) {
                    enableAudioUpstream()
                }
                if (videoEnabled) {
                    capturer = capturerFactory.createCapturer()
                    enableVideoUpstream(capturer!!, egl!!.eglBaseContext)
                }
            }

            if (role.hasDownstream()) {
                if (audioEnabled) {
                    enableAudioDownstream()
                }
                enableVideoDownstream(egl!!.eglBaseContext)
            }

            if (multistream) {
                enableMultistream()
            }

            if(this@SoraVideoChannel.simulcast) {
                enableSimulcast()
                // hardware encoder では動かせていない、ソフトウェアを指定する
                videoEncoderFactory = SoftwareVideoEncoderFactory()
            }
            spotlightEnabled    = this@SoraVideoChannel.spotlight
            videoCodec   = this@SoraVideoChannel.videoCodec
            videoBitrate = this@SoraVideoChannel.videoBitRate

            audioCodec   = this@SoraVideoChannel.audioCodec
            audioBitrate = this@SoraVideoChannel.audioBitRate

            audioOption = SoraAudioOption().apply {
                // 全部デフォルト値なので、実際には指定する必要はない
                useHardwareAcousticEchoCanceler = true
                useHardwareNoiseSuppressor      = true

                audioProcessingEchoCancellation = true
                audioProcessingAutoGainControl  = true
                audioProcessingHighpassFilter   = true
                audioProcessingNoiseSuppression = true

                // 配信にステレオを使う場合の設定。
                // AndroidManifest で portrait 固定だが、ステレオで配信するときは landscape に
                // 変更したほうが良い。
                if (audioStereo) {
                    // libwebrtc の AGC が有効のときはステレオで出ないため無効化する
                    audioProcessingAutoGainControl  = false

                    // MediaRecorder.AudioSource.MIC の場合、両側のマイクの真ん中あたりで
                    // 不連続に音量が下がるように聞こえる (Pixel 3 XL -> Sora で録音)。
                    // マイクから離れることに依る近接センサーの影響か??
                    // CAMCORDER にすると端末のマイクから離れたときの影響が小さい。
                    audioSource = MediaRecorder.AudioSource.CAMCORDER
                    useStereoInput = true
                }
            }
        }

        val peerConnectionOption = PeerConnectionOption().apply {
            getStatsIntervalMSec = 5000
        }

        mediaChannel = SoraMediaChannel(
                context                 = context,
                signalingEndpoint       = signalingEndpoint,
                channelId               = channelId,
                signalingMetadata       = signalingMetadata,
                signalingNotifyMetadata = signalingNotifyMetadata,
                mediaOption             = mediaOption,
                listener                = channelListener,
                clientId                = clientId,
                peerConnectionOption    = peerConnectionOption
        )
        mediaChannel!!.connect()
         */

        Sora.connect(configuration) { result ->
            SoraLogger.d(TAG, "[video_channel] connecting => $result")
        }
    }

    fun startCapturer() {
        capturer?.let {
            if (!capturing) {
                capturing = true
                it.startCapture(configuration.videoFrameSize.width,
                        configuration.videoFrameSize.height,
                        configuration.videoFps)
            }
        }
    }

    fun stopCapturer() {
        capturer?.let {
            if (capturing) {
                capturing = false
                it.stopCapture()
            }
        }
    }

    private val cameraSwitchHandler = object : CameraVideoCapturer.CameraSwitchHandler {

        override fun onCameraSwitchDone(isFront: Boolean) {
            SoraLogger.d(TAG, "camera switched.")
        }

        override fun onCameraSwitchError(msg: String?) {
            SoraLogger.w(TAG, "failed to switch camera ${msg}")
        }
    }

    fun changeCameraFormat(width: Int, height: Int, fps: Int) {
        capturer?.let {
            it.changeCaptureFormat(width, height, fps)
        }
    }

    fun switchCamera() {
        capturer?.let {
            it.switchCamera(cameraSwitchHandler)
        }
    }

    fun mute(mute: Boolean) {
        localAudioTrack?.setEnabled(!mute)
    }

    fun disconnect() {
        stopCapturer()
        mediaChannel?.disconnect()
        mediaChannel = null
        capturer = null

        if (!closed) {
            closed = true

            handler.post {
                listener?.onClose(this@VideoChannel)
                localAudioTrack = null
            }

        }
    }

    fun dispose() {
        disconnect()
        listener = null
    }
}

