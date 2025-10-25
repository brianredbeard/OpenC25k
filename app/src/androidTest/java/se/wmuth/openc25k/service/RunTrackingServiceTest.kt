package se.wmuth.openc25k.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.wmuth.openc25k.data.Interval
import se.wmuth.openc25k.data.Run
import se.wmuth.openc25k.data.model.SoundType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests for RunTrackingService
 */
@RunWith(AndroidJUnit4::class)
class RunTrackingServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var context: Context
    private lateinit var testRun: Run
    private var service: RunTrackingService? = null
    private var bound = false

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        testRun = Run(
            name = "Test Run",
            description = "Test",
            isComplete = false,
            intervals = arrayOf(
                Interval(5, "Warmup"),
                Interval(3, "Jog"),
                Interval(3, "Walk")
            )
        )
    }

    @After
    fun tearDown() {
        if (bound && service != null) {
            service = null
            bound = false
        }
    }

    @Test
    fun serviceStartsAndBinds() {
        val intent = createServiceIntent()
        val latch = CountDownLatch(1)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as RunTrackingService.RunTrackingBinder
                service = serviceBinder.getService()
                bound = true
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                bound = false
            }
        }

        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        // Wait for service to bind
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bound).isTrue()
        assertThat(service).isNotNull()
    }

    @Test
    fun serviceHasActiveRunAfterInitialization() {
        val intent = createServiceIntent()
        val latch = CountDownLatch(1)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as RunTrackingService.RunTrackingBinder
                service = serviceBinder.getService()
                bound = true
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                bound = false
            }
        }

        // Start service first (this initializes the run)
        context.startService(intent)
        Thread.sleep(500) // Give service time to initialize

        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        latch.await(5, TimeUnit.SECONDS)

        assertThat(service?.hasActiveRun()).isTrue()
        assertThat(service?.getRun()?.name).isEqualTo("Test Run")
        assertThat(service?.getRunIndex()).isEqualTo(0)
    }

    @Test
    fun serviceHasNoActiveRunBeforeInitialization() {
        val intent = Intent(context, RunTrackingService::class.java)
        val latch = CountDownLatch(1)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as RunTrackingService.RunTrackingBinder
                service = serviceBinder.getService()
                bound = true
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                bound = false
            }
        }

        // Bind without starting (no run initialized)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        latch.await(5, TimeUnit.SECONDS)

        assertThat(service?.hasActiveRun()).isFalse()
        assertThat(service?.getRun()).isNull()
    }

    @Test
    fun servicePauseAndResumeWorks() {
        val intent = createServiceIntent()
        val latch = CountDownLatch(1)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as RunTrackingService.RunTrackingBinder
                service = serviceBinder.getService()
                bound = true
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                bound = false
            }
        }

        context.startService(intent)
        Thread.sleep(500)

        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        latch.await(5, TimeUnit.SECONDS)

        // Start the run
        service?.start()
        Thread.sleep(100)
        assertThat(service?.isRunning()).isTrue()

        // Pause
        service?.pause()
        Thread.sleep(100)
        assertThat(service?.isRunning()).isFalse()

        // Resume
        service?.resume()
        Thread.sleep(100)
        assertThat(service?.isRunning()).isTrue()
    }

    @Test
    fun serviceSkipAdvancesInterval() {
        val intent = createServiceIntent()
        val latch = CountDownLatch(1)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as RunTrackingService.RunTrackingBinder
                service = serviceBinder.getService()
                bound = true
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                bound = false
            }
        }

        context.startService(intent)
        Thread.sleep(500)

        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        latch.await(5, TimeUnit.SECONDS)

        val initialInterval = service?.getCurrentInterval()
        assertThat(initialInterval?.title).isEqualTo("Warmup")

        // Skip to next interval
        service?.skip()
        Thread.sleep(200) // Wait for skip to process

        val nextInterval = service?.getCurrentInterval()
        assertThat(nextInterval?.title).isEqualTo("Jog")
    }

    private fun createServiceIntent(): Intent {
        return Intent(context, RunTrackingService::class.java).apply {
            putExtra(RunTrackingService.EXTRA_RUN, testRun)
            putExtra(RunTrackingService.EXTRA_RUN_INDEX, 0)
            putExtra(RunTrackingService.EXTRA_SOUND_ENABLED, false)
            putExtra(RunTrackingService.EXTRA_VIBRATE_ENABLED, false)
            putExtra(RunTrackingService.EXTRA_TTS_ENABLED, false)
            putExtra(RunTrackingService.EXTRA_VOLUME, 0.5f)
            putExtra(RunTrackingService.EXTRA_SOUND_TYPE, SoundType.BEEP.name)
        }
    }
}
