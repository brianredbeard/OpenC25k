package se.wmuth.openc25k

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.recyclerview.widget.LinearLayoutManager
import se.wmuth.openc25k.audio.AudioFocusManager
import se.wmuth.openc25k.both.Beeper
import se.wmuth.openc25k.data.Run
import se.wmuth.openc25k.databinding.ActivityMainBinding
import se.wmuth.openc25k.data.model.SoundType
import se.wmuth.openc25k.main.DataHandler
import se.wmuth.openc25k.main.RunAdapter
import se.wmuth.openc25k.main.SettingsMenu
import se.wmuth.openc25k.main.SoundSelectionDialog
import se.wmuth.openc25k.main.VolumeDialog

// Get the datastore for the app
val Context.datastore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * The main activity, ties together everything on the apps 'home' page
 */
class MainActivity : AppCompatActivity(), RunAdapter.RunAdapterClickListener,
    SettingsMenu.SettingsMenuListener, VolumeDialog.VolumeDialogListener,
    SoundSelectionDialog.SoundSelectionDialogListener {
    private lateinit var audioFocusManager: AudioFocusManager
    private lateinit var menu: SettingsMenu
    private lateinit var runs: Array<Run>
    private lateinit var volDialog: VolumeDialog
    private lateinit var soundSelectionDialog: SoundSelectionDialog
    private lateinit var handler: DataHandler
    private lateinit var adapter: RunAdapter
    private lateinit var launcher: ActivityResultLauncher<Intent>
    private var beeper: Beeper? = null
    private var announcer: RunAnnouncer? = null
    private var sound: Boolean = true
    private var vibrate: Boolean = true
    private var tts: Boolean = true
    private var volume: Float = 0.5f
    private var soundType: SoundType = SoundType.BEEP

    override fun onCreate(savedInstanceState: Bundle?) {
        handler = DataHandler(this, datastore)
        sound = handler.getSound()
        vibrate = handler.getVibrate()
        tts = handler.getTTS()
        volume = handler.getVolume()
        soundType = handler.getSoundType()
        runs = handler.getRuns()

        audioFocusManager = AudioFocusManager(this)

        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        volDialog = VolumeDialog(this, this, layoutInflater)
        soundSelectionDialog = SoundSelectionDialog(this, this)
        menu = SettingsMenu(this, binding.materialToolbar.menu)

        val runRV = binding.recyclerView
        adapter = RunAdapter(this, runs, this)
        runRV.adapter = adapter
        runRV.layoutManager = LinearLayoutManager(this)

        binding.materialToolbar.setOnMenuItemClickListener(menu)
        binding.materialToolbar.menu.findItem(R.id.vibrate).isChecked = vibrate
        binding.materialToolbar.menu.findItem(R.id.sound).isChecked = sound
        binding.materialToolbar.menu.findItem(R.id.tts).isChecked = tts

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
        // Run held, toggle isComplete
        runs[position].isComplete = !runs[position].isComplete
        adapter.notifyItemChanged(position)
        handler.setRuns(runs)
    }

    /**
     * Handle the result of the TrackActivity
     *
     * @param resultCode if RESULT_OK, run was finished
     * @param data the data sent back from the activity, which run was completed
     */
    private fun handleActivityResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            runs[data.getIntExtra("id", 0)].isComplete = true
            adapter.notifyItemChanged(data.getIntExtra("id", 0))
            handler.setRuns(runs)
        }
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