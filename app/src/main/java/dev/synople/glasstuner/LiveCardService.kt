package dev.synople.glasstuner

import com.google.android.glass.timeline.LiveCard
import com.google.android.glass.timeline.LiveCard.PublishMode

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews

class LiveCardService : Service() {
    private val SAMPLING_RATE = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC, SAMPLING_RATE,
        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
    )
    private var audioData: ShortArray = ShortArray(bufferSize / 2)


    private var liveCard: LiveCard? = null
    private lateinit var remoteViews: RemoteViews
    private var isRecording = false
    private var recordingThread: Thread? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (liveCard == null) {
            liveCard = LiveCard(this, LIVE_CARD_TAG)

            remoteViews = RemoteViews(packageName, R.layout.live_card)
            liveCard!!.setViews(remoteViews)

            // TODO: Start recording and displaying info
            startRecording()


            // Display the options menu when the live card is tapped.
            val menuIntent = Intent(this, LiveCardMenuActivity::class.java)
            liveCard!!.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0))
            liveCard!!.publish(PublishMode.REVEAL)
        } else {
            liveCard!!.navigate()
        }
        return START_STICKY
    }

    private fun startRecording() {
        isRecording = true
        recorder.startRecording()

        recordingThread = Thread(Runnable {
            while (isRecording) {
                recorder.read(audioData, 0, bufferSize / 2)
                val floatData = convert(audioData)
                val yinResult = YINPitchDetector(recorder.sampleRate.toDouble(), FloatArray(bufferSize / 4)).detect(floatData)
                val note = ArrayNoteFinder()
                note.setFrequency(yinResult)
                Log.v("YINResult", "${note.noteName}: $yinResult")

                remoteViews.setTextViewText(R.id.tvNote, note.noteName)
                liveCard?.setViews(remoteViews)

                Thread.sleep(250)
            }
        })
        recordingThread?.start()
    }

    override fun onDestroy() {
        if (liveCard != null && liveCard!!.isPublished) {
            liveCard!!.unpublish()
            liveCard = null
        }
        recorder.stop()
        recordingThread?.interrupt()
        super.onDestroy()
    }

    companion object {

        private const val LIVE_CARD_TAG = "LiveCardService"
    }
}
