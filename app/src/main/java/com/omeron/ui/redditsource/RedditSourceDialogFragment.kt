package com.omeron.ui.redditsource

import android.app.Dialog
import android.content.DialogInterface
import android.content.DialogInterface.OnShowListener
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import com.omeron.R
import com.omeron.data.model.preferences.DataPreferences
import com.omeron.databinding.FragmentRedditSourceBinding
import com.omeron.util.extension.doAndDismiss
import com.omeron.util.extension.serializable
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RedditSourceDialogFragment : DialogFragment(), OnShowListener {

    private var _binding: FragmentRedditSourceBinding? = null
    private val binding get() = _binding!!

    private lateinit var source: DataPreferences.RedditSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.run {
            source = serializable(KEY_SOURCE) ?: DataPreferences.RedditSource.REDDIT
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = FragmentRedditSourceBinding.inflate(requireActivity().layoutInflater)

        initView()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_reddit_source_title)
            .setView(binding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                // Ignore
            }
            .setNeutralButton(R.string.dialog_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .create()
            .apply {
                setOnShowListener(this@RedditSourceDialogFragment)
            }
    }

    private fun initView() {
        binding.run {
            radioReddit.isChecked = source == DataPreferences.RedditSource.REDDIT
            radioRedditScrap.isChecked = source == DataPreferences.RedditSource.REDDIT_SCRAP
        }
    }

    private fun save() {
        val source = when (binding.radioGroup.checkedRadioButtonId) {
            R.id.radio_reddit -> DataPreferences.RedditSource.REDDIT
            R.id.radio_reddit_scrap -> DataPreferences.RedditSource.REDDIT_SCRAP
            else -> DataPreferences.RedditSource.REDDIT
        }

        doAndDismiss {
            setFragmentResult(REQUEST_KEY_SOURCE, bundleOf(KEY_SOURCE to source))
        }
    }

    override fun onShow(dialog: DialogInterface?) {
        (dialog as AlertDialog?)
            ?.getButton(DialogInterface.BUTTON_POSITIVE)
            ?.setOnClickListener {
                save()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "RedditSourceDialogFragment"

        const val REQUEST_KEY_SOURCE = "REQUEST_KEY_SOURCE"

        const val KEY_SOURCE = "KEY_SOURCE"

        fun show(
            fragmentManager: FragmentManager,
            source: DataPreferences.RedditSource
        ) {
            RedditSourceDialogFragment().apply {
                arguments = bundleOf(KEY_SOURCE to source)
            }.show(fragmentManager, TAG)
        }
    }
}
