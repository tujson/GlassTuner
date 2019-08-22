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
import dev.synople.glasstuner.calculators.AudioCalculator

class LiveCardService : Service() {
    private val samplingRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        samplingRate, AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC, samplingRate,
        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
    )
    private var audioData: ByteArray = ByteArray(bufferSize)


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
                recorder.read(audioData, 0, bufferSize)

                val note = process(audioData)
                Log.v("Result", note)

                remoteViews.setTextViewText(R.id.tvNote, note)
                liveCard?.setViews(remoteViews)

                Thread.sleep(250)
            }
        })
        recordingThread?.start()
    }

    private fun process(byteArray: ByteArray): String {
        val audioCalculator = AudioCalculator()
        audioCalculator.setBytes(byteArray)

        val frequency = audioCalculator.frequency
        val note = ArrayNoteFinder()
        note.setFrequency(frequency)

        return if (frequency > 10) {
            "${note.noteName}: $frequency"
        } else {
            "?"
        }
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
