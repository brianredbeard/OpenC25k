package se.wmuth.openc25k

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.recyclerview.widget.LinearLayoutManager
import se.wmuth.openc25k.audio.AudioFocusManager
import se.wmuth.openc25k.both.Beeper
import se.wmuth.openc25k.data.Interval
import se.wmuth.openc25k.data.Run
import se.wmuth.openc25k.databinding.ActivityMainBinding
import se.wmuth.openc25k.data.model.RunProgress
import se.wmuth.openc25k.data.model.SoundType
import se.wmuth.openc25k.data.repository.ProgressRepository
import se.wmuth.openc25k.main.DataHandler
import se.wmuth.openc25k.main.RunAdapter
import se.wmuth.openc25k.main.SettingsMenu
import se.wmuth.openc25k.main.SoundSelectionDialog
import se.wmuth.openc25k.main.VolumeDialog
import se.wmuth.openc25k.service.RunTrackingService
import se.wmuth.openc25k.track.RunAnnouncer

// Get the datastore for the app
val Context.datastore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * The main activity, ties together everything on the apps 'home' page
 */
class MainActivity : AppCompatActivity(), RunAdapter.RunAdapterClickListener,
    SettingsMenu.SettingsMenuListener, VolumeDialog.VolumeDialogListener,
    SoundSelectionDialog.SoundSelectionDialogListener, RunTrackingService.RunStateListener {
    private lateinit var audioFocusManager: AudioFocusManager
    private lateinit var menu: SettingsMenu
    private lateinit var runs: Array<Run>
    private lateinit var volDialog: VolumeDialog
    private lateinit var soundSelectionDialog: SoundSelectionDialog
    private lateinit var handler: DataHandler
    private lateinit var progressRepository: ProgressRepository
    private lateinit var adapter: RunAdapter
    private lateinit var launcher: ActivityResultLauncher<Intent>
    private lateinit var binding: ActivityMainBinding
    private var beeper: Beeper? = null
    private var announcer: RunAnnouncer? = null
    private var sound: Boolean = true
    private var vibrate: Boolean = true
    private var tts: Boolean = true
    private var volume: Float = 0.5f
    private var soundType: SoundType = SoundType.BEEP

    // Service binding for active run tracking
    private var trackingService: RunTrackingService? = null
    private var boundToService = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val serviceBinder = binder as RunTrackingService.RunTrackingBinder
            trackingService = serviceBinder.getService()
            boundToService = true
            trackingService?.addStateListener(this@MainActivity)
            updateActiveRunBanner()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService?.removeStateListener(this@MainActivity)
            trackingService = null
            boundToService = false
            hideActiveRunBanner()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        handler = DataHandler(this, datastore)
        progressRepository = ProgressRepository(handler)
        sound = handler.getSound()
        vibrate = handler.getVibrate()
        tts = handler.getTTS()
        volume = handler.getVolume()
        soundType = handler.getSoundType()
        runs = handler.getRuns()

        audioFocusManager = AudioFocusManager(this)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        volDialog = VolumeDialog(this, this, layoutInflater)
        soundSelectionDialog = SoundSelectionDialog(this, this)
        menu = SettingsMenu(this, binding.materialToolbar.menu)

        val runRV = binding.recyclerView
        adapter = RunAdapter(this, runs, this, progressRepository)
        runRV.adapter = adapter
        runRV.layoutManager = LinearLayoutManager(this)

        binding.materialToolbar.setOnMenuItemClickListener(menu)
        binding.materialToolbar.menu.findItem(R.id.vibrate).isChecked = vibrate
        binding.materialToolbar.menu.findItem(R.id.sound).isChecked = sound
        binding.materialToolbar.menu.findItem(R.id.tts).isChecked = tts

        // Setup progress header
        updateProgressHeader()

        // Setup quick resume FAB
        setupQuickResumeFAB()

        // Setup active run banner
        setupActiveRunBanner()

        launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            handleActivityResult(it.resultCode, it.data)
        }
    }

    override fun onRunItemClick(position: Int) {
        // Run was clicked, launch TrackActivity with extras
        val intent = Intent(this, TrackActivity::class.java)
        intent.putExtra("run", runs[position])
        intent.putExtra("id", position)
        intent.putExtra("sound", sound)
        intent.putExtra("vibrate", vibrate)
        intent.putExtra("tts", tts)
        intent.putExtra("volume", volume)
        intent.putExtra("soundType", soundType.name)
        launcher.launch(intent)
    }

    override fun onRunItemLongClick(position: Int) {
        // Run held, toggle isComplete and increment progress
        val wasComplete = runs[position].isComplete
        runs[position].isComplete = !runs[position].isComplete

        // If marking as complete, record it in progress tracking
        if (!wasComplete && runs[position].isComplete) {
            progressRepository.recordCompletion(position)
        }

        handler.setRuns(runs)
        adapter.notifyItemChanged(position)
        updateProgressHeader()
    }

    /**
     * Update the progress summary header
     */
    private fun updateProgressHeader() {
        val summary = progressRepository.getProgressSummary(runs)

        binding.root.findViewById<TextView>(R.id.tvCurrentWeekDay)?.text =
            getString(R.string.progress_week_day, summary.currentWeek, summary.currentDay)

        binding.root.findViewById<TextView>(R.id.tvProgressStats)?.text =
            getString(R.string.progress_runs_completed, summary.totalCompleted, summary.totalRuns)

        val lastRunText = if (summary.lastRunDate != null) {
            val progress = RunProgress(lastCompletedDate = summary.lastRunDate)
            getString(R.string.progress_last_run_with_date, progress.getLastCompletedText())
        } else {
            getString(R.string.progress_last_run_never)
        }
        binding.root.findViewById<TextView>(R.id.tvLastRun)?.text = lastRunText
    }

    /**
     * Setup quick resume FAB to start recommended run
     */
    private fun setupQuickResumeFAB() {
        binding.fabQuickResume.setOnClickListener {
            val recommendedIndex = progressRepository.getNextRecommendedRun(runs)
            onRunItemClick(recommendedIndex)
        }
    }

    /**
     * Setup active run banner click to return to TrackActivity
     */
    private fun setupActiveRunBanner() {
        binding.root.findViewById<View>(R.id.activeRunBanner)?.setOnClickListener {
            // Open TrackActivity to return to active run
            val intent = Intent(this, TrackActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
    }

    /**
     * Show and update the active run banner with current service state
     */
    private fun updateActiveRunBanner() {
        val service = trackingService ?: return

        // Only show banner if service has an active run
        if (!service.hasActiveRun()) {
            hideActiveRunBanner()
            return
        }

        binding.root.findViewById<View>(R.id.activeRunBanner)?.visibility = View.VISIBLE

        val run = service.getRun()
        if (run != null) {
            binding.root.findViewById<TextView>(R.id.tvBannerRunName)?.text = run.name
        }

        val interval = service.getCurrentInterval()
        val intervalRemaining = service.getIntervalRemaining()
        val totalRemaining = service.getTotalRemaining()

        if (interval != null) {
            binding.root.findViewById<TextView>(R.id.tvBannerInterval)?.text =
                getString(R.string.banner_interval_remaining, interval.title, intervalRemaining)
        }

        binding.root.findViewById<TextView>(R.id.tvBannerTotalRemaining)?.text =
            getString(R.string.banner_total_remaining, totalRemaining)
    }

    /**
     * Hide the active run banner
     */
    private fun hideActiveRunBanner() {
        binding.root.findViewById<View>(R.id.activeRunBanner)?.visibility = View.GONE
    }

    /**
     * Try to bind to RunTrackingService if it's running
     */
    private fun tryBindToService() {
        val intent = Intent(this, RunTrackingService::class.java)
        try {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (_: Exception) {
            // Service not running, that's okay
            hideActiveRunBanner()
        }
    }

    /**
     * Handle the result of the TrackActivity
     *
     * @param resultCode if RESULT_OK, run was finished
     * @param data the data sent back from the activity, which run was completed
     */
    private fun handleActivityResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            val runIndex = data.getIntExtra("id", 0)

            // Record completion in progress repository
            progressRepository.recordCompletion(runIndex)

            // Keep old isComplete flag for backward compatibility
            runs[runIndex].isComplete = true
            handler.setRuns(runs)

            // Refresh UI
            adapter.notifyItemChanged(runIndex)
            updateProgressHeader()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        // Refresh UI when returning to activity - we don't know which items changed
        // notifyDataSetChanged() is appropriate here as we could have multiple changes
        // from background service or settings modifications
        adapter.notifyDataSetChanged()
        updateProgressHeader()

        // Try to bind to service if it's running
        tryBindToService()
    }

    override fun onPause() {
        super.onPause()
        // Unbind from service when leaving activity
        if (boundToService) {
            trackingService?.removeStateListener(this)
            unbindService(serviceConnection)
            boundToService = false
        }
    }

    // RunTrackingService.RunStateListener implementation

    override fun onTick(intervalRemaining: String, totalRemaining: String) {
        runOnUiThread {
            binding.root.findViewById<TextView>(R.id.tvBannerTotalRemaining)?.text =
                getString(R.string.banner_total_remaining, totalRemaining)

            val interval = trackingService?.getCurrentInterval()
            if (interval != null) {
                binding.root.findViewById<TextView>(R.id.tvBannerInterval)?.text =
                    getString(R.string.banner_interval_remaining, interval.title, intervalRemaining)
            }
        }
    }

    override fun onIntervalChange(interval: Interval) {
        runOnUiThread {
            updateActiveRunBanner()
        }
    }

    override fun onHalfway() {
        // No special handling needed in MainActivity
    }

    override fun onRunComplete() {
        runOnUiThread {
            hideActiveRunBanner()
        }
    }

    override fun onRunStateChanged(isRunning: Boolean) {
        // No special handling needed in MainActivity
    }

    override fun createVolumeDialog() {
        volDialog.createAlertDialog(volume)
    }

    override fun createSoundSelectionDialog() {
        soundSelectionDialog.createAlertDialog(soundType)
    }

    override fun shouldMakeSound(): Boolean {
        return sound
    }

    override fun shouldVibrate(): Boolean {
        return vibrate
    }

    override fun shouldUseTTS(): Boolean {
        return tts
    }

    override fun testVolume() {
        if (beeper == null) {
            beeper = Beeper(applicationContext, volume, audioFocusManager, soundType)
        }
        if (announcer == null) {
            announcer = RunAnnouncer(this, audioFocusManager)
        }
        if (sound) {
            beeper?.beep()
        }
        if (tts) {
            announcer?.announce(getString(R.string.tts_test_message))
        }
    }

    override fun toggleSound() {
        sound = !sound
        handler.setSound(sound)
    }

    override fun toggleVibration() {
        vibrate = !vibrate
        handler.setVibrate(vibrate)
    }

    override fun toggleTTS() {
        tts = !tts
        handler.setTTS(tts)
    }

    override fun setVolume(nV: Float) {
        volume = nV
        handler.setVolume(volume)
    }

    override fun setSoundType(soundType: SoundType) {
        this.soundType = soundType
        handler.setSoundType(soundType)
        // Recreate beeper with new sound type if it exists
        beeper?.release()
        beeper = null
    }
}