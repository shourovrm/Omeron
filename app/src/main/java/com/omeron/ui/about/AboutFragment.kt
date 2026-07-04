package com.omeron.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.omeron.BuildConfig
import com.omeron.R
import com.omeron.databinding.FragmentAboutBinding
import com.omeron.ui.base.BaseFragment

class AboutFragment : BaseFragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    private val gitlabLink by lazy { getString(R.string.gitlab_link) }
    private val matrixLink by lazy { getString(R.string.matrix_link) }
    private val email by lazy { getString(R.string.email) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initAppBar()
        binding.appVersion.text = BuildConfig.VERSION_NAME
    }

    private fun initAppBar() {
        binding.appBar.run {
            backCard.setOnClickListener { onBackPressed() }
            buttonGitlab.setOnClickListener { linkHandler.openBrowser(gitlabLink) }
            buttonMatrix.setOnClickListener { linkHandler.openBrowser(matrixLink) }
            buttonMail.setOnClickListener { sendEmail() }
        }
    }

    private fun sendEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        }

        val packageManager = activity?.packageManager ?: return

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            val clipboard =
                activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?

            if (clipboard != null) {
                val clip = ClipData.newPlainText("Omeron email", email)
                clipboard.setPrimaryClip(clip)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    Toast.makeText(
                        requireContext(),
                        R.string.toast_clipboard_copied_email,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
