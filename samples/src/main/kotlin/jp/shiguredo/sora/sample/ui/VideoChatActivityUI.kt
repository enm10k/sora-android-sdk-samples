package jp.shiguredo.sora.sample.ui

import android.content.res.Resources
import android.graphics.Color
import android.widget.RelativeLayout
import androidx.core.content.res.ResourcesCompat
import jp.shiguredo.sora.sample.R
import jp.shiguredo.sora.sample.ui.util.RendererLayoutCalculator
import jp.shiguredo.sora.sample.ui.util.SoraScreenUtil
import kotlinx.android.synthetic.main.activity_video_chat_room.*
import org.webrtc.SurfaceViewRenderer

class VideoChatActivityUI(
        val activity:        SampleAppActivity,
        val layout: Int,
        val channelName:     String,
        val resources: Resources,
        val videoViewWidth:  Int,
        val videoViewHeight: Int,
        val videoViewMargin: Int,
        val density:         Float
) {

    private val renderersLayoutCalculator: RendererLayoutCalculator

    init {
        activity.setContentView(layout)
        activity.channelNameText.text = channelName
        this.renderersLayoutCalculator = RendererLayoutCalculator(
                width = SoraScreenUtil.size(activity).x - dp2px(20 * 2),
                height = SoraScreenUtil.size(activity).y - dp2px(20 * 2 + 100)
        )
        activity.toggleMuteButton.setOnClickListener { activity.toggleMuted() }
        activity.switchCameraButton.setOnClickListener { activity.switchCamera() }
        activity.closeButton.setOnClickListener { activity.close() }
    }

    internal fun changeState(colorCode: String) {
        activity.channelNameText.setBackgroundColor(Color.parseColor(colorCode))
    }

    internal fun addLocalRenderer(renderer: SurfaceViewRenderer) {
        /*
        renderer.layoutParams =
                FrameLayout.LayoutParams(dp2px(100), dp2px(100))
        activity.localRendererContainer.addView(renderer)
        renderer.setMirror(true)

         */
    }

    internal fun addRenderer(renderer: SurfaceViewRenderer) {
        renderer.layoutParams = rendererLayoutParams()
        activity.rendererContainer.addView(renderer)
        renderersLayoutCalculator.add(renderer)
    }

    internal fun removeRenderer(renderer: SurfaceViewRenderer) {
        activity.rendererContainer.removeView(renderer)
        renderersLayoutCalculator.remove(renderer)
    }

    internal fun showUnmuteButton() {
        activity.toggleMuteButton.setImageDrawable(
                ResourcesCompat.getDrawable(resources, R.drawable.ic_mic_white_48dp, null))
    }

    internal fun showMuteButton() {
        activity.toggleMuteButton.setImageDrawable(
                ResourcesCompat.getDrawable(resources, R.drawable.ic_mic_off_black_48dp, null))
    }

    private fun dp2px(d: Int): Int = (density * d).toInt()

    private fun rendererLayoutParams(): RelativeLayout.LayoutParams {
        val params = RelativeLayout.LayoutParams(dp2px(videoViewWidth), dp2px(videoViewHeight))
        val margin = dp2px(videoViewMargin)
        params.setMargins(margin, margin, margin, margin)
        return params
    }
}