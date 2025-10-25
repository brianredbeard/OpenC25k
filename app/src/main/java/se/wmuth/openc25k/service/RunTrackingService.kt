package se.wmuth.openc25k.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import se.wmuth.openc25k.R
import se.wmuth.openc25k.TrackActivity
import se.wmuth.openc25k.audio.AudioFocusManager
import se.wmuth.openc25k.both.Beeper
import se.wmuth.openc25k.data.Interval
import se.wmuth.openc25k.data.Run
import se.wmuth.openc25k.data.model.SoundType
import se.wmuth.openc25k.track.RunAnnouncer
import se.wmuth.openc25k.track.RunTimer
import se.wmuth.openc25k.track.Shaker
import timber.log.Timber

/**
 * Foreground service that manages run tracking in the background
 *
 * This service survives activity destruction, allowing runs to continue
 * even when the user navigates away or receives a phone call.
 *
 * Features:
 * - Foreground service with persistent notification
 * - Timer, audio, and vibration management
 * - Notification with pause/skip/stop controls
 * - Survives activity lifecycle changes
 * - Auto-pause on phone calls
 */
class RunTrackingService : Service(), RunTimer.RunTimerListener {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "run_tracking_channel"
        private const val CHANNEL_NAME = "Run Tracking"

        const val ACTION_PAUSE = "se.wmuth.openc25k.ACTION_PAUSE"
        const val ACTION_RESUME = "se.wmuth.openc25k.ACTION_RESUME"
        const val ACTION_SKIP = "se.wmuth.openc25k.ACTION_SKIP"
        const val ACTION_STOP = "se.wmuth.openc25k.ACTION_STOP"

        const val EXTRA_RUN = "extra_run"
        const val EXTRA_RUN_INDEX = "extra_run_index"
        const val EXTRA_SOUND_ENABLED = "extra_sound_enabled"
        const val EXTRA_VIBRATE_ENABLED = "extra_vibrate_enabled"
        const val EXTRA_TTS_ENABLED = "extra_tts_enabled"
        const val EXTRA_VOLUME = "extra_volume"
        const val EXTRA_SOUND_TYPE = "extra_sound_type"
    }

    // Service binding
    private val binder = RunTrackingBinder()

    // Run tracking components
    private lateinit var timer: RunTimer
    private lateinit var audioFocusManager: AudioFocusManager
    private lateinit var beeper: Beeper
    private lateinit var announcer: RunAnnouncer
    private lateinit var shaker: Shaker

    // Run state
    private lateinit var run: Run
    private var runIndex: Int = -1
    private lateinit var intervals: Iterator<Interval>
    private var currentInterval: Interval? = null

    // Settings
    private var soundEnabled: Boolean = true
    private var vibrateEnabled: Boolean = true
    private var ttsEnabled: Boolean = true

    // State tracking
    private var isRunning = false
    private var isCompleted = false

    // State change listeners
    private val stateListeners = mutableListOf<RunStateListener>()

    /**
     * Interface for observing run state changes
     */
    interface RunStateListener {
        fun onTick(intervalRemaining: String, totalRemaining: String)
        fun onIntervalChange(interval: Interval)
        fun onHalfway()
        fun onRunComplete()
        fun onRunStateChanged(isRunning: Boolean)
    }

    /**
     * Binder for activity to bind to this service
     */
    inner class RunTrackingBinder : Binder() {
        fun getService(): RunTrackingService = this@RunTrackingService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("RunTrackingService created")

        // Initialize audio focus manager
        audioFocusManager = AudioFocusManager(this)

        // Create notification channel
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("RunTrackingService onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_SKIP -> skip()
            ACTION_STOP -> stopRun()
            else -> {
                // Initial start - extract run data
                if (intent != null && !::timer.isInitialized) {
                    initializeRun(intent)
                }
            }
        }

        return START_STICKY
    }

    /**
     * Initialize the run with data from intent
     */
    private fun initializeRun(intent: Intent) {
        // Extract run data
        run = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RUN, Run::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RUN)
        } ?: run {
            Timber.e("No run provided in intent")
            stopSelf()
            return
        }

        runIndex = intent.getIntExtra(EXTRA_RUN_INDEX, -1)
        soundEnabled = intent.getBooleanExtra(EXTRA_SOUND_ENABLED, true)
        vibrateEnabled = intent.getBooleanExtra(EXTRA_VIBRATE_ENABLED, true)
        ttsEnabled = intent.getBooleanExtra(EXTRA_TTS_ENABLED, true)
        val volume = intent.getFloatExtra(EXTRA_VOLUME, 0.5f)
        val soundTypeName = intent.getStringExtra(EXTRA_SOUND_TYPE) ?: SoundType.BEEP.name
        val soundType = SoundType.fromName(soundTypeName)

        Timber.d("Initializing run: ${run.name}, index: $runIndex")

        // Initialize components
        beeper = Beeper(this, volume, audioFocusManager, soundType)
        announcer = RunAnnouncer(this, audioFocusManager)
        shaker = Shaker(getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager)
        timer = RunTimer(run.intervals, this)

        intervals = run.intervals.iterator()
        currentInterval = intervals.next()

        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())

        Timber.d("Run initialized successfully")
    }

    /**
     * Create notification channel (required for Android 8+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance = no sound
            ).apply {
                description = "Shows ongoing run tracking progress"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create or update the foreground notification
     */
    private fun createNotification(): Notification {
        // Intent to open TrackActivity when notification is tapped
        val openIntent = Intent(this, TrackActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Pause/Resume
        val pauseResumeAction = if (isRunning) {
            val pauseIntent = Intent(this, RunTrackingService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePendingIntent = PendingIntent.getService(
                this, 1, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePendingIntent
            )
        } else {
            val resumeIntent = Intent(this, RunTrackingService::class.java).apply {
                action = ACTION_RESUME
            }
            val resumePendingIntent = PendingIntent.getService(
                this, 1, resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Resume",
                resumePendingIntent
            )
        }

        // Action: Skip
        val skipIntent = Intent(this, RunTrackingService::class.java).apply {
            action = ACTION_SKIP
        }
        val skipPendingIntent = PendingIntent.getService(
            this, 2, skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skipAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Skip",
            skipPendingIntent
        )

        // Action: Stop
        val stopIntent = Intent(this, RunTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_delete,
            "Stop",
            stopPendingIntent
        )

        // Build notification
        val intervalText = currentInterval?.title ?: "Starting..."
        val timeRemaining = if (::timer.isInitialized) timer.getIntervalRemaining() else "--:--"
        val totalRemaining = if (::timer.isInitialized) timer.getTotalRemaining() else "--:--"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(run.name)
            .setContentText("$intervalText • $timeRemaining remaining")
            .setSubText("Total: $totalRemaining")
            .setSmallIcon(R.mipmap.ic_green)
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeAction)
            .addAction(skipAction)
            .addAction(stopAction)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * Update the notification with current state
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    // Public control methods

    fun start() {
        if (!isRunning && ::timer.isInitialized) {
            timer.start()
            isRunning = true
            updateNotification()
            notifyStateChanged()
            Timber.d("Run started")
        }
    }

    fun pause() {
        if (isRunning && ::timer.isInitialized) {
            timer.pause()
            isRunning = false
            updateNotification()
            notifyStateChanged()
            Timber.d("Run paused")
        }
    }

    fun resume() {
        start() // Resume is same as start
    }

    fun skip() {
        if (::timer.isInitialized) {
            timer.skip()
            Timber.d("Interval skipped")
        }
    }

    private fun stopRun() {
        pause()
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.d("Run stopped by user")
    }

    // RunTimer.RunTimerListener implementation

    override fun tick() {
        stateListeners.forEach { it.onTick(timer.getIntervalRemaining(), timer.getTotalRemaining()) }
        updateNotification()
    }

    override fun nextInterval() {
        if (intervals.hasNext()) {
            currentInterval = intervals.next()

            // Stop any in-progress TTS to prevent focus leak
            announcer.stop()

            // Announce the interval change
            if (ttsEnabled) {
                announcer.announceIntervalWithDuration(
                    currentInterval!!.title,
                    currentInterval!!.time
                )
            }

            // Play appropriate sound/vibration
            if (currentInterval!!.title == getString(R.string.walk)) {
                if (soundEnabled) beeper.beep()
                if (vibrateEnabled) shaker.walkShake()
            } else {
                if (soundEnabled) beeper.beepMultiple(2u)
                if (vibrateEnabled) shaker.jogShake()
            }

            stateListeners.forEach { it.onIntervalChange(currentInterval!!) }
            updateNotification()
        }
    }

    override fun onHalfway() {
        // Stop any in-progress TTS
        announcer.stop()

        if (ttsEnabled) {
            announcer.announce(getString(R.string.halfway_announcement))
        }

        if (soundEnabled) beeper.beep()
        if (vibrateEnabled) shaker.walkShake()

        stateListeners.forEach { it.onHalfway() }
        Timber.d("Halfway point reached")
    }

    override fun finishRun() {
        isCompleted = true
        isRunning = false

        if (soundEnabled) beeper.beepMultiple(4u)
        if (vibrateEnabled) shaker.completeShake()

        stateListeners.forEach { it.onRunComplete() }

        Timber.d("Run completed!")

        // Clean up and stop service after a short delay
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // State listener management

    fun addStateListener(listener: RunStateListener) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener)
        }
    }

    fun removeStateListener(listener: RunStateListener) {
        stateListeners.remove(listener)
    }

    private fun notifyStateChanged() {
        stateListeners.forEach { it.onRunStateChanged(isRunning) }
    }

    // Getters for current state

    fun getRunIndex(): Int = runIndex
    fun isRunning(): Boolean = isRunning
    fun isCompleted(): Boolean = isCompleted
    fun getIntervalRemaining(): String = if (::timer.isInitialized) timer.getIntervalRemaining() else "--:--"
    fun getTotalRemaining(): String = if (::timer.isInitialized) timer.getTotalRemaining() else "--:--"
    fun getCurrentInterval(): Interval? = currentInterval
    fun getRun(): Run = run

    // Cleanup

    private fun cleanup() {
        if (::beeper.isInitialized) beeper.release()
        if (::announcer.isInitialized) announcer.release()
        Timber.d("Service cleanup complete")
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        Timber.d("RunTrackingService destroyed")
    }
}
