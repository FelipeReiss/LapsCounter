package br.com.helpdev.lapscounter.ui.fragment

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import br.com.helpdev.lapscounter.R
import br.com.helpdev.lapscounter.databinding.FragmentActivityBinding
import br.com.helpdev.lapscounter.injector.InjectorUtils
import br.com.helpdev.lapscounter.model.entity.ActivityEntity
import br.com.helpdev.lapscounter.model.entity.toObLaps
import br.com.helpdev.lapscounter.ui.ActivitiesActivity
import br.com.helpdev.lapscounter.ui.adapter.LapsAdapter
import br.com.helpdev.lapscounter.ui.viewmodel.ActivityViewModel
import br.com.helpdev.lapscounter.utils.ChronometerShareUtils
import com.google.android.gms.ads.AdRequest
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_activity.*
import kotlinx.android.synthetic.main.include_lap_log.view.*


class ActivityFragment : Fragment() {

    private var dialog: AlertDialog? = null
    private lateinit var viewModel: ActivityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPause() {
        super.onPause()
        dismissDialog()
    }

    private fun dismissDialog() {
        dialog?.let {
            it.dismiss()
            dialog = null
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return FragmentActivityBinding.inflate(layoutInflater, container, false).apply {
            subscribeUI(this)
        }.let { it.root }
    }

    private fun subscribeUI(binding: FragmentActivityBinding) {
        val activityEntityId = ActivityFragmentArgs.fromBundle(arguments!!).activityEntityId
        viewModel = loadViewModel()
        binding.viewModel = viewModel
        viewModel.init(context!!, activityEntityId)
        observerActivityEntity(viewModel, binding)
    }

    private fun observerActivityEntity(viewModel: ActivityViewModel, binding: FragmentActivityBinding) {
        viewModel.activityEntity.observe(this, Observer { activityEntity ->
            configureLapsLog(activityEntity, binding)
            setToolbarTitle(activityEntity.name)
        })
    }

    private fun loadViewModel() = ViewModelProviders.of(this, InjectorUtils.provideActivityViewModelFactory())
        .get(ActivityViewModel::class.java)

    private fun configureLapsLog(activityEntity: ActivityEntity, binding: FragmentActivityBinding) {
        val toObLaps = activityEntity.chronometer!!.toObLaps()
        binding.layoutLog.recycler_view.adapter = LapsAdapter(context!!, toObLaps, {_, _ -> },false)
        if (toObLaps.isNotEmpty()) binding.layoutLog.text_view_empty.visibility = View.GONE
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAds()
    }

    private fun setToolbarTitle(name: String) {
        (activity as ActivitiesActivity).supportActionBar?.title = name
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.activity_saved_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.isRemoveItem()) showDialogDelete()
        else if (item.isShareMenu()) shareActivity()
        return super.onOptionsItemSelected(item)
    }

    private fun MenuItem.isRemoveItem() = itemId == R.id.menu_remove
    private fun MenuItem.isShareMenu() = itemId == R.id.menu_share

    private fun showDialogDelete() {
        AlertDialog.Builder(context!!).apply {
            setTitle(R.string.app_name)
            setMessage(R.string.confirm_delete)
            setPositiveButton(android.R.string.ok) { _, _ -> deleteActivity() }
            setNegativeButton(android.R.string.cancel, null)
        }.let { dialog = it.create(); dialog!! }.let { it.show() }
    }

    private fun shareActivity() {
        viewModel.activityEntity.value?.let {
            ChronometerShareUtils.shareText(context!!, it)
        }
    }

    private fun deleteActivity() {
        viewModel.deleteActivity()
        Snackbar.make(view!!, getString(R.string.activity_removed), Snackbar.LENGTH_LONG).show()
        Navigation.findNavController(view!!).navigateUp()
    }


    private fun loadAds() {
        adView.loadAd(AdRequest.Builder().build())
    }

}