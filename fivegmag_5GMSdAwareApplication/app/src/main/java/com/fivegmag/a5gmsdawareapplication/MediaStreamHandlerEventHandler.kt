package com.fivegmag.a5gmsdawareapplication

import android.content.Context
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@UnstableApi
/**
 * This class handles messages that are dispatched by the Media Stream Handler.
 * Implements a pub/sub pattern using the eventbus library.
 *
 */
class MediaStreamHandlerEventHandler {

    private lateinit var representationInfoTextView: TextView
    private lateinit var context: Context

    fun initialize(repInfoTextView: TextView, ctxt: Context) {
        representationInfoTextView = repInfoTextView
        context = ctxt
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownstreamFormatChangedEvent(event: DownstreamFormatChangedEvent) {
        // Handle the event
        if (event.mediaLoadData.trackFormat?.containerMimeType?.contains(
                "video",
                ignoreCase = true
            ) == true
        ) {
            val kbitsPerSecond =
                event.mediaLoadData.trackFormat?.peakBitrate?.div(1000).toString()
            val id = event.mediaLoadData.trackFormat?.id.toString()
            val text = context.getString(R.string.representationInfo, kbitsPerSecond, id)
            representationInfoTextView.text = text
        }
    }
}