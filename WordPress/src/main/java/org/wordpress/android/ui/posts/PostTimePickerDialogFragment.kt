package org.wordpress.android.ui.posts

import android.app.Dialog
import android.app.TimePickerDialog
import android.app.TimePickerDialog.OnTimeSetListener
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.ContextThemeWrapper
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import org.wordpress.android.R.style
import org.wordpress.android.WordPress
import javax.inject.Inject

class PostTimePickerDialogFragment : DialogFragment() {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: EditPostPublishedSettingsViewModel

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewModel = ViewModelProviders.of(activity!!, viewModelFactory)
                .get(EditPostPublishedSettingsViewModel::class.java)

        val is24HrFormat = DateFormat.is24HourFormat(activity)
        val timePickerDialog = TimePickerDialog(
                ContextThemeWrapper(activity, style.Calypso_Dialog_Alert),
                OnTimeSetListener { _, selectedHour, selectedMinute ->
                    Log.d("vojta", "Time set: $selectedHour:$selectedMinute")
                    viewModel.hour = selectedHour
                    viewModel.minute = selectedMinute
                    viewModel.onTimeSelected()
                },
                viewModel.hour ?: 0,
                viewModel.minute ?: 0,
                is24HrFormat
        )
        return timePickerDialog
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        (activity!!.applicationContext as WordPress).component().inject(this)
    }

    companion object {
        const val TAG = "post_time_picker_dialog_fragment"

        fun newInstance(): PostTimePickerDialogFragment {
            return PostTimePickerDialogFragment()
        }
    }
}