package br.com.helpdev.lapscounter

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.preference.PreferenceManager
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import br.com.helpdev.chronometerlib.Chronometer
import br.com.helpdev.lapscounter.headset.HeadsetButtonControl
import br.com.helpdev.lapscounter.injector.InjectorUtils
import br.com.helpdev.lapscounter.ui.ActivitiesActivity
import br.com.helpdev.lapscounter.ui.SettingsActivity
import br.com.helpdev.lapscounter.ui.adapter.LapsAdapter
import br.com.helpdev.lapscounter.ui.dialog.SaveActivityDialog
import br.com.helpdev.lapscounter.ui.viewmodel.ChronometerViewModel
import br.com.helpdev.lapscounter.utils.*
import br.com.helpdev.lapscounter.utils.AlarmUtils.alarmAsync
import kotlinx.android.synthetic.main.distances_layout.*
import kotlinx.android.synthetic.main.include_buttons.*
import kotlinx.android.synthetic.main.include_chronometer.*
import kotlinx.android.synthetic.main.include_lap_log.*
import kotlinx.android.synthetic.main.item_lap_log.*
import kotlinx.android.synthetic.main.lap_log_chronometers.*
import kotlinx.android.synthetic.main.main_toolbar.*
import kotlin.concurrent.thread

abstract class ChronometerActivity : AppCompatActivity(), HeadsetButtonControl.HeadsetButtonControlListener {
    companion object {
        private const val REQUEST_SETTINGS = 15
    }

    private lateinit var viewModel: ChronometerViewModel

    private var headsetButtonReceiver: HeadsetButtonControl? = null

    private var dialog: AlertDialog? = null
    private var saveActivityDialog: SaveActivityDialog? = null

    private var audioEnable = true
    private var countLastLap: Boolean = true
    private var lapDistance: Float = 50.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        configureOnClicks()
        viewModel = loadViewModel()

        registerForContextMenu(numberOfLap_fix)

        if (null != savedInstanceState) {
            restoreInstanceVisibilityViews(savedInstanceState)
            onRestoreChronometer()
        }
        updateParameters()
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.row_log_long_press, menu)
    }

    override fun onContextItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId){
            R.id.logOption1 -> {
                Toast.makeText(this, "Option 1 Selected at ${item.tooltipText}", Toast.LENGTH_SHORT).show()
            }
            R.id.logOption2 -> {
                Toast.makeText(this, "Option 2 Selected at ${item.tooltipText}", Toast.LENGTH_SHORT).show()
            }
            R.id.logOption3 -> {
                Toast.makeText(this, "Option 3 Selected at ${item.tooltipText}", Toast.LENGTH_SHORT).show()
            }
            R.id.logOption4 -> {
                Toast.makeText(this, "Option 4 Selected at ${item.tooltipText}", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "Invalid Option", Toast.LENGTH_SHORT).show()
            }
        }
        return super.onContextItemSelected(item)
    }

    private fun loadViewModel() = ViewModelProviders.of(this, InjectorUtils.provideActivityViewModelFactory())
        .get(ChronometerViewModel::class.java)

    private fun configureOnClicks() {
        bt_start.setOnClickListener { btStartPressed().run { alarm(R.raw.beep) } }
        bt_resume.setOnClickListener { btResumePressed().run { alarm(R.raw.beep) } }
        bt_lap.setOnClickListener { btLapPressed().run { alarm(R.raw.beep_2) } }
        bt_pause.setOnClickListener { btPausePressed().run { alarm(R.raw.beep) } }
        bt_share.setOnClickListener { btSharePressed() }
        bt_save.setOnClickListener { btSavePressed() }
        bt_restart.setOnClickListener { btRestartPressed() }
        bt_stop.setOnClickListener { btStopPressed() }
    }

    private fun rowChronometerLogClick() {

    }

    private fun onRestoreChronometer() {
        refreshBasesTotal()
        checkAndPerformChronometerStatus()
        refreshChronometerLog()
        createAdapter()
    }

    private fun checkAndPerformChronometerStatus() {
        when {
            Chronometer.STATUS_STARTED == viewModel.chronometer.status -> {
                startChronometersWidget()
                checkHavePauseTime()
            }
            Chronometer.STATUS_PAUSED == viewModel.chronometer.status -> startChronometersPaused()
            Chronometer.STATUS_STOPPED == viewModel.chronometer.status -> refreshBasesPaused()
        }
    }

    private fun checkHavePauseTime() {
        if ((SystemClock.elapsedRealtime() - viewModel.chronometer.getLastLapBasePause()) > 1) {
            chronometerLogPause.setTextColor(Color.RED)
            chronometerLogPause.base = viewModel.chronometer.getLastLapBasePause()
        }
    }

    private fun restoreInstanceVisibilityViews(savedInstanceState: Bundle) {
        buttons_frame_2.visibility = savedInstanceState.getInt(buttons_frame_2.id.toString())
        buttons_lay_restart_save.visibility = savedInstanceState.getInt(buttons_lay_restart_save.id.toString())
        bt_start.visibility = savedInstanceState.getInt(bt_start.id.toString())
        bt_lap.visibility = savedInstanceState.getInt(bt_lap.id.toString())
        bt_pause.visibility = savedInstanceState.getInt(bt_pause.id.toString())
        bt_resume.visibility = savedInstanceState.getInt(bt_resume.id.toString())
        bt_stop.visibility = savedInstanceState.getInt(bt_stop.id.toString())
        layout_chronometer_pause.visibility = savedInstanceState.getInt(layout_chronometer_pause.id.toString())
        chronometer_lap_log.visibility = savedInstanceState.getInt(chronometer_lap_log.id.toString())
        text_view_empty.visibility = savedInstanceState.getInt(text_view_empty.id.toString())
    }

    private fun updateParameters() {
        lapDistance = getLapDistance(this)
        countLastLap = countLastLap(this)
        audioEnable = isAudioEnable(this)
        info_lap.text = getStringLapDistance(this)
        updateInfoTravelled()
    }


    private fun updateInfoTravelled() {
        val distance = ChronometerUtils.getDistanceTravelled(viewModel.chronometer, lapDistance, countLastLap)
        info_travelled.text = getString(R.string.info_travelled, distance)
        val pace = ChronometerUtils.getPace(viewModel.chronometer, lapDistance, countLastLap)
        info_pace.text = getString(R.string.info_pace, pace.first, pace.second)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (REQUEST_SETTINGS == requestCode) {
            updateParameters()
        }
    }

    private fun createAdapter() {
        recycler_view.adapter = LapsAdapter(this, viewModel.chronometer.getObChronometer().laps)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveInstanceVisibilityViews(outState)
        super.onSaveInstanceState(outState)
    }

    private fun saveInstanceVisibilityViews(outState: Bundle) {
        outState.putInt(buttons_frame_2.id.toString(), buttons_frame_2.visibility)
        outState.putInt(buttons_lay_restart_save.id.toString(), buttons_lay_restart_save.visibility)
        outState.putInt(bt_start.id.toString(), bt_start.visibility)
        outState.putInt(bt_stop.id.toString(), bt_stop.visibility)
        outState.putInt(bt_lap.id.toString(), bt_lap.visibility)
        outState.putInt(bt_pause.id.toString(), bt_pause.visibility)
        outState.putInt(bt_resume.id.toString(), bt_resume.visibility)
        outState.putInt(layout_chronometer_pause.id.toString(), layout_chronometer_pause.visibility)
        outState.putInt(chronometer_lap_log.id.toString(), chronometer_lap_log.visibility)
        outState.putInt(text_view_empty.id.toString(), text_view_empty.visibility)
    }

    private fun btStopPressed() {
        bt_stop.visibility = View.GONE
        bt_resume.visibility = View.GONE
        layout_chronometer_pause.visibility = View.GONE
        buttons_lay_restart_save.visibility = View.VISIBLE
        viewModel.chronometer.stop()
        chronometerWidgetPause.stop()
        chronometerLogPause.stop()
        refreshBasesPaused()
        updateInfoTravelled()
    }

    private fun btStartPressed() {
        createAdapter()
        btStartResumePressed()
        buttons_frame_2.visibility = View.VISIBLE
        bt_start.visibility = View.GONE
        bt_pause.visibility = View.VISIBLE
        chronometer_lap_log.visibility = View.VISIBLE
        text_view_empty.visibility = View.GONE
        refreshChronometerLog()
    }

    private fun btResumePressed() {
        btStartResumePressed()
        changeLayoutPauseResume(false)
        chronometerLogPause.stop()
        chronometerWidgetPause.stop()
    }

    private fun btStartResumePressed() {
        viewModel.chronometer.start()
        refreshBasesTotal()
        startChronometersWidget()
        refreshChronometerLog()
    }

    private fun btPausePressed() {
        viewModel.chronometer.pause()
        chronometerWidget.stop()
        chronometerLogWidget.stop()
        chronometerLogCurrent.stop()
        startChronometersPaused()
        changeLayoutPauseResume(true)
    }

    private fun btLapPressed() {
        viewModel.chronometer.lap()
        chronometerLogPause.base = SystemClock.elapsedRealtime()
        chronometerLogPause.setTextColor(ContextCompat.getColor(this, R.color.colorSecondaryText))
        refreshChronometerLog()
        recycler_view.smoothScrollToPosition()
        updateInfoTravelled()
    }

    private fun RecyclerView.smoothScrollToPosition() {
        adapter?.let {
            it.notifyDataSetChanged()
            smoothScrollToPosition(it.itemCount)
        }
    }

    private fun alarm(rawId: Int) {
        if (!audioEnable) return
        alarmAsync(this, rawId)
    }

    private fun refreshBasesTotal() {
        chronometerWidget.base = viewModel.chronometer.getCurrentBase()
        chronometerLogWidget.base = viewModel.chronometer.getCurrentBase()
    }

    private fun refreshBasesPaused() {
        chronometerWidgetPause.base = viewModel.chronometer.getPauseBaseTime()
        chronometerLogPause.base = viewModel.chronometer.getLastLapBasePause()
    }

    private fun startChronometersWidget() {
        chronometerWidget.start()
        chronometerLogWidget.start()
        chronometerLogCurrent.start()
    }

    private fun refreshChronometerLog() {
        numberOfLap.text =
            getString(R.string.num_lap, String.format("%02d", viewModel.chronometer.getObChronometer().laps.size))
        chronometerLogCurrent.base = viewModel.chronometer.getBaseLastLap()
    }

    private fun startChronometersPaused() {
        refreshBasesPaused()
        chronometerLogPause.setTextColor(Color.RED)
        chronometerWidgetPause.start()
        chronometerLogPause.start()
    }

    private fun changeLayoutPauseResume(paused: Boolean) {
        buttons_lay_restart_save.visibility = View.GONE
        bt_stop.visibility = if (paused) View.VISIBLE else View.GONE
        bt_lap.visibility = if (paused) View.GONE else View.VISIBLE
        bt_pause.visibility = if (paused) View.GONE else View.VISIBLE
        bt_resume.visibility = if (paused) View.VISIBLE else View.GONE
        layout_chronometer_pause.visibility = if (paused) View.VISIBLE else View.INVISIBLE
    }

    private fun btSavePressed() {
        showDialogSaveActivity()
    }

    private fun showDialogSaveActivity() {
        SaveActivityDialog(this).also {
            saveActivityDialog = it
        }.show { name, description ->
            saveActivity(name, description)
        }
    }

    private fun saveActivity(name: String, description: String) {
        viewModel.saveActivity(name, description, lapDistance, countLastLap)
        showDialogActivitySaved()
    }

    private fun showDialogActivitySaved() {
        dialog = createDialogActivitySaved()
        configureDialogAnimation(dialog!!)
        dialog!!.show()
        runDismissDelayDialog(1500)
    }

    private fun runDismissDelayDialog(timeDelay: Long) {
        thread {
            Thread.sleep(timeDelay)
            dismissDialog()
        }
    }

    private fun createDialogActivitySaved(): AlertDialog? {
        return AlertDialog.Builder(this).apply {
            setView(R.layout.dialog_activity_saved)
        }.create()
    }

    private fun configureDialogAnimation(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.attributes?.windowAnimations = R.style.DialogActivitySavedAnimation
    }

    private fun btSharePressed() {
        ChronometerShareUtils.shareText(this, viewModel.chronometer, lapDistance, countLastLap)
    }

    private fun btRestartPressed() {
        resetChronometerWidgets()
        resetVisibleViews()
        updateInfoTravelled()
    }

    private fun resetChronometerWidgets() {
        viewModel.chronometer.reset()

        chronometerWidget.base = SystemClock.elapsedRealtime()
        chronometerLogWidget.base = SystemClock.elapsedRealtime()
        chronometerLogCurrent.base = SystemClock.elapsedRealtime()
        chronometerLogPause.base = SystemClock.elapsedRealtime()

        chronometerLogCurrent.stop()
        chronometerWidget.stop()
        chronometerLogWidget.stop()
        chronometerLogPause.stop()
    }

    private fun resetVisibleViews() {
        buttons_frame_2.visibility = View.GONE
        bt_start.visibility = View.VISIBLE
        buttons_lay_restart_save.visibility = View.GONE
        bt_lap.visibility = View.VISIBLE
        bt_pause.visibility = View.GONE
        bt_resume.visibility = View.GONE
        layout_chronometer_pause.visibility = View.INVISIBLE
        chronometer_lap_log.visibility = View.GONE
        text_view_empty.visibility = View.VISIBLE
        chronometerLogPause.setTextColor(ContextCompat.getColor(this, R.color.colorSecondaryText))
        recycler_view.adapter = null
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_settings -> startActivityForResult(Intent(this, SettingsActivity::class.java), REQUEST_SETTINGS)
            R.id.menu_activities -> startActivity(Intent(this, ActivitiesActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        configureHeadsetButtons()
    }

    private fun configureHeadsetButtons() {
        if (headsetButtonsEnable()) {
            if (null == headsetButtonReceiver) headsetButtonReceiver = HeadsetButtonControl()
            headsetButtonReceiver!!.registerHeadsetButton(this, this)
        }
    }

    private fun headsetButtonsEnable() = PreferenceManager.getDefaultSharedPreferences(this)
        .getBoolean(
            getString(R.string.pref_headset_buttons_name),
            resources.getBoolean(R.bool.pref_headset_buttons_default_value)
        )


    override fun onPause() {
        headsetButtonReceiver?.unregisterHeadsetButton(this)
        dismissDialog()
        saveActivityDialog?.dismiss()
        super.onPause()
    }

    private fun dismissDialog() {
        dialog?.let {
            it.dismiss()
            dialog = null
        }
    }

    override fun btHeadsetHookPressed(keyEvent: KeyEvent?) {
        when {
            View.VISIBLE == bt_start.visibility -> btStartPressed()
            View.VISIBLE == bt_resume.visibility -> btResumePressed()
            View.VISIBLE == bt_lap.visibility -> btLapPressed()
        }
    }

    override fun btHeadsetHookDoubleClickPressed(keyEvent: KeyEvent?) {
        if (View.VISIBLE == bt_pause.visibility) btPausePressed()
    }

    override fun btHeadsetMediaNextPressed(keyEvent: KeyEvent?) {
    }

    override fun btHeadsetMediaPreviousPressed(keyEvent: KeyEvent?) {
    }

    override fun btHeadsetUndefinedPressed(keyEvent: KeyEvent?) {
    }

    override fun onBackPressed() {
        showDialogExit()
    }

    private fun showDialogExit() {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.app_name)
            setMessage(R.string.confirm_exit)
            setPositiveButton(android.R.string.ok) { _, _ -> super.finish() }
            setNegativeButton(android.R.string.cancel, null)
        }.let { dialog = it.create(); dialog!! }.let { it.show() }
    }

}
