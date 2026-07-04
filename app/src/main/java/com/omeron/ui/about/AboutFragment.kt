package com.omeron.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.omeron.BuildConfig
import com.omeron.R
import com.omeron.databinding.FragmentAboutBinding
import com.omeron.ui.base.BaseFragment

class AboutFragment : BaseFragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    private val githubLink by lazy { getString(R.string.github_link) }

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
            buttonGithub.setOnClickListener { linkHandler.openBrowser(githubLink) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
