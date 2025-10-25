package se.wmuth.openc25k

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import se.wmuth.openc25k.data.Interval
import se.wmuth.openc25k.data.Run
import se.wmuth.openc25k.data.model.SoundType
import se.wmuth.openc25k.databinding.ActivityTrackBinding
import se.wmuth.openc25k.service.RunTrackingService
import timber.log.Timber

/**
 * Activity for tracking a run in progress
 *
 * This activity binds to RunTrackingService and displays the current state.
 * The service handles all timer, audio, and state management.
 * The activity is just a UI observer that can be destroyed and recreated.
 */
class TrackActivity : AppCompatActivity(), RunTrackingService.RunStateListener {

    private lateinit var binding: ActivityTrackBinding
    private var service: RunTrackingService? = null
    private var bound = false
    private var runIndex: Int = -1

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val serviceBinder = binder as RunTrackingService.RunTrackingBinder
            service = serviceBinder.getService()
            bound = true

            // Register as state listener
            service?.addStateListener(this@TrackActivity)

            // Update UI with current state
            updateUIFromService()

            Timber.d("TrackActivity bound to service")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.removeStateListener(this@TrackActivity)
            service = null
            bound = false
            Timber.d("TrackActivity unbound from service")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get run data from intent
        val run = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("run", Run::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("run")
        }

        runIndex = intent.getIntExtra("id", -1)

        if (run == null) {
            Timber.e("No run provided to TrackActivity")
            finish()
            return
        }

        // Check if service is already running
        val serviceIntent = Intent(this, RunTrackingService::class.java)
        val serviceRunning = isServiceRunning()

        if (!serviceRunning) {
            // Start new service with run data
            serviceIntent.apply {
                putExtra(RunTrackingService.EXTRA_RUN, run)
                putExtra(RunTrackingService.EXTRA_RUN_INDEX, runIndex)
                putExtra(RunTrackingService.EXTRA_SOUND_ENABLED, intent.getBooleanExtra("sound", true))
                putExtra(RunTrackingService.EXTRA_VIBRATE_ENABLED, intent.getBooleanExtra("vibrate", true))
                putExtra(RunTrackingService.EXTRA_TTS_ENABLED, intent.getBooleanExtra("tts", true))
                putExtra(RunTrackingService.EXTRA_VOLUME, intent.getFloatExtra("volume", 0.5f))
                putExtra(RunTrackingService.EXTRA_SOUND_TYPE, intent.getStringExtra("soundType") ?: SoundType.BEEP.name)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Timber.d("Started RunTrackingService")
        }

        // Bind to service
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Set up button listeners
        binding.btnStart.setOnClickListener {
            service?.start()
        }

        binding.btnPause.setOnClickListener {
            service?.pause()
        }

        binding.btnSkip.setOnClickListener {
            service?.skip()
        }

        // Initialize UI with run data
        binding.twTitle.text = run.name
        binding.twSummary.text = run.description

        Timber.d("TrackActivity created for run: ${run.name}")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle notification tap returning to this activity
        Timber.d("TrackActivity onNewIntent")
        updateUIFromService()
    }

    /**
     * Update UI based on current service state
     */
    private fun updateUIFromService() {
        service?.let { svc ->
            binding.twRemainTimer.text = svc.getTotalRemaining()
            binding.twTimer.text = svc.getIntervalRemaining()
            binding.twStatus.text = svc.getCurrentInterval()?.title ?: ""

            // Update button states based on running state
            binding.btnStart.isEnabled = !svc.isRunning()
            binding.btnPause.isEnabled = svc.isRunning()
        }
    }

    /**
     * Check if RunTrackingService is currently running
     */
    private fun isServiceRunning(): Boolean {
        // Simple check - try to bind and see if we get a service
        // In a production app, you might use ActivityManager
        return false // For now, always start fresh
    }

    // RunStateListener implementation

    override fun onTick(intervalRemaining: String, totalRemaining: String) {
        runOnUiThread {
            binding.twTimer.text = intervalRemaining
            binding.twRemainTimer.text = totalRemaining
        }
    }

    override fun onIntervalChange(interval: Interval) {
        runOnUiThread {
            binding.twStatus.text = interval.title
            binding.twTimer.text = interval.time.toString()
        }
    }

    override fun onHalfway() {
        runOnUiThread {
            // Announce halfway point to TalkBack
            binding.root.announceForAccessibility(getString(R.string.halfway_announcement))
        }
        Timber.d("Halfway point reached!")
    }

    override fun onRunComplete() {
        runOnUiThread {
            binding.twStatus.text = getString(R.string.runComplete)
        }

        // Return success result
        val resultIntent = Intent().apply {
            putExtra("id", runIndex)
        }
        setResult(RESULT_OK, resultIntent)

        Timber.d("Run completed!")

        // Finish activity after short delay
        binding.root.postDelayed({
            finish()
        }, 2000)
    }

    override fun onRunStateChanged(isRunning: Boolean) {
        runOnUiThread {
            binding.btnStart.isEnabled = !isRunning
            binding.btnPause.isEnabled = isRunning
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unbind from service
        if (bound) {
            service?.removeStateListener(this)
            unbindService(serviceConnection)
            bound = false
        }
        Timber.d("TrackActivity destroyed")
    }
}
