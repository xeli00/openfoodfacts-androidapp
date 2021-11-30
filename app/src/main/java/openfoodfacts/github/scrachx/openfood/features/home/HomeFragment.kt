/*
 * Copyright 2016-2020 Open Food Facts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package openfoodfacts.github.scrachx.openfood.features.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import openfoodfacts.github.scrachx.openfood.R
import openfoodfacts.github.scrachx.openfood.customtabs.CustomTabActivityHelper
import openfoodfacts.github.scrachx.openfood.customtabs.CustomTabsHelper
import openfoodfacts.github.scrachx.openfood.customtabs.WebViewFallback
import openfoodfacts.github.scrachx.openfood.databinding.FragmentHomeBinding
import openfoodfacts.github.scrachx.openfood.features.login.LoginActivity.Companion.LoginContract
import openfoodfacts.github.scrachx.openfood.features.shared.NavigationBaseFragment
import openfoodfacts.github.scrachx.openfood.models.TagLine
import openfoodfacts.github.scrachx.openfood.utils.NavigationDrawerListener
import openfoodfacts.github.scrachx.openfood.utils.NavigationDrawerListener.NavigationDrawerType
import java.text.NumberFormat

/**
 * @see R.layout.fragment_home
 */
@AndroidEntryPoint
class HomeFragment : NavigationBaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTagLine.setOnClickListener { openDailyFoodFacts() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.tagline.collectLatest { setTagline(it) } }
                launch { viewModel.productCount.collectLatest { setProductCount(it) } }
                launch { viewModel.credentialCheck.collectLatest { if (it) logoutUser() } }
            }
        }

        viewModel.checkUserCredentials()
        viewModel.refreshTagline()
        viewModel.refreshProductCount()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openDailyFoodFacts() {
        // chrome custom tab init
        val dailyFoodFactUri = viewModel.tagline.value.url?.toUri() ?: return

        val customTabActivityHelper = CustomTabActivityHelper().apply {
            mayLaunchUrl(dailyFoodFactUri, null, null)
        }
        val customTabsIntent = CustomTabsHelper.getCustomTabsIntent(
            requireActivity(),
            customTabActivityHelper.session,
        )
        CustomTabActivityHelper.openCustomTab(
            requireActivity(),
            customTabsIntent,
            dailyFoodFactUri,
            WebViewFallback()
        )
    }

    @NavigationDrawerType
    override fun getNavigationDrawerType() = NavigationDrawerListener.ITEM_HOME

    private val loginLauncher = registerForActivityResult(LoginContract()) { }
    private fun logoutUser() {
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.alert_dialog_warning_title)
            .setMessage(R.string.alert_dialog_warning_msg_user)
            .setPositiveButton(android.R.string.ok) { _, _ -> loginLauncher.launch(Unit) }
            .show()
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.let { it.title = "" }
    }

    /**
     * Set text displayed on Home based on build variant
     *
     * @param count count of total products available on the apps database
     */
    private fun setProductCount(count: Int) {
        binding.textHome.text = if (count == 0) getString(R.string.txtHome)
        else getString(R.string.txtHomeOnline, NumberFormat.getInstance().format(count))
    }

    private fun setTagline(tagline: TagLine) {
        binding.tvTagLine.text = tagline.message
        binding.tvTagLine.isGone = tagline.message.isNullOrEmpty()
    }

    companion object {
        fun newInstance() = HomeFragment().apply { arguments = Bundle() }
    }
}
