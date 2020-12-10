package jp.shiguredo.sora.sample.facade

import android.content.Context
import android.os.Handler
import jp.shiguredo.sora.sample.option.SoraRoleType
import jp.shiguredo.sora.sdk.channel.SoraMediaChannel
import jp.shiguredo.sora.sdk.channel.data.ChannelAttendeesCount
import jp.shiguredo.sora.sdk.channel.option.SoraAudioOption
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.AudioTrack
import org.webrtc.MediaStream
import org.webrtc.VideoSource

class AudioChannel(
        private val context:           Context,
        private val handler:           Handler,
        private val signalingEndpoint: String,
        private val channelId:         String,
        private val signalingMetadata: String = "",
        private var role:              SoraRoleType,
        private var multistream:       Boolean = true,
        private var audioCodec:        SoraAudioOption.Codec = SoraAudioOption.Codec.OPUS,
        private val audioBitRate:      Int? = null,
        private var listener:          Listener?
) {

    companion object {
        private val TAG = AudioChannel::class.simpleName
    }

    interface Listener {
        fun onConnect(channel: AudioChannel) {}
        fun onClose(channel: AudioChannel) {}
        fun onError(channel: AudioChannel, reason: SoraErrorReason) {}
        fun onAttendeesCountUpdated(channel: AudioChannel, attendees: ChannelAttendeesCount) {}
    }

    private val channelListener = object : SoraMediaChannel.Listener {

        override fun onConnect(mediaChannel: SoraMediaChannel) {
            SoraLogger.d(TAG, "[audio_channel] @onConnected")
            handler.post { listener?.onConnect(this@AudioChannel) }
        }

        override fun onClose(mediaChannel: SoraMediaChannel) {
            SoraLogger.d(TAG, "[audio_channel] @onClose")
            disconnect()
        }

        override fun onError(mediaChannel: SoraMediaChannel, reason: SoraErrorReason) {
            SoraLogger.d(TAG, "[audio_channel] @onError")
            handler.post { listener?.onError(this@AudioChannel, reason) }
            disconnect()
        }

        override fun onAddLocalStream(mediaChannel: SoraMediaChannel, ms: MediaStream, videoSource: VideoSource?) {
            SoraLogger.d(TAG, "[audio_channel] @onAddLocalStream")
            if (ms.audioTracks.size > 0) {
                localAudioTrack = ms.audioTracks[0]
            }
        }

        override fun onAttendeesCountUpdated(mediaChannel: SoraMediaChannel, attendees: ChannelAttendeesCount) {
            SoraLogger.d(TAG, "[audio_channel] @onAttendeesCountUpdated")
            handler.post { listener?.onAttendeesCountUpdated(this@AudioChannel, attendees) }
        }

    }

    private var mediaChannel:    SoraMediaChannel? = null
    private var localAudioTrack: AudioTrack?  = null

    private var closed = false

    fun connect() {

        val mediaOption = SoraMediaOption().apply {

            if (role.hasUpstream()) {
                enableAudioUpstream()
            }

            if (role.hasDownstream()) {
                enableAudioDownstream()
            }

            if (multistream) {
                enableMultistream()
            }

            audioCodec = this@AudioChannel.audioCodec
            audioBitrate = this@AudioChannel.audioBitRate
        }

        mediaChannel = SoraMediaChannel(
                context           = context,
                signalingEndpoint = signalingEndpoint,
                channelId         = channelId,
                signalingMetadata = signalingMetadata,
                mediaOption       = mediaOption,
                listener          = channelListener
        )

        mediaChannel!!.connect()
    }

    fun disconnect() {
        mediaChannel?.disconnect()
        mediaChannel = null
        if (!closed) {
            closed = true
            handler.post {
                listener?.onClose(this@AudioChannel)
                localAudioTrack = null
            }
        }
    }

    fun dispose() {
        disconnect()
        listener = null
    }
}
