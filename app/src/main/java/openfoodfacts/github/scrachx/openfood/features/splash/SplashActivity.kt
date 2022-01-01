package openfoodfacts.github.scrachx.openfood.features.splash

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import openfoodfacts.github.scrachx.openfood.R
import openfoodfacts.github.scrachx.openfood.databinding.ActivitySplashBinding
import openfoodfacts.github.scrachx.openfood.features.shared.BaseActivity
import openfoodfacts.github.scrachx.openfood.features.welcome.WelcomeActivity
import openfoodfacts.github.scrachx.openfood.utils.getAppPreferences
import pl.aprilapps.easyphotopicker.EasyImage
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@AndroidEntryPoint
class SplashActivity : BaseActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModels()

    @SuppressLint("SourceLockedOrientationActivity")
    @ExperimentalTime
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (resources.getBoolean(R.bool.portrait_only)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        viewModel.tagLine.observe(this, binding.tagline::setText)
        viewModel.isLoading.observe(this, ::updateLoading)

        viewModel.setupTagLines(resources.getStringArray(R.array.taglines_array))
        viewModel.refreshData()

        lifecycleScope.launch {
            val settings = getAppPreferences()
            //first run ever off this application, whatever the version
            val firstRun = settings.getBoolean(FIRST_RUN_KEY, true)
            if (firstRun) settings.edit { putBoolean(FIRST_RUN_KEY, false) }


            // The 6000 delay is to show one loop of the multilingual logo. I asked for it ~ Pierre
            if (firstRun) {
                delay(6.seconds)
                navigateToMainActivity()
            } else {
                navigateToMainActivity()
            }
        }
    }

    private fun navigateToMainActivity() {
        EasyImage.configuration(this@SplashActivity).apply {
            setImagesFolderName("OFF_Images")
            saveInAppExternalFilesDir()
            setCopyExistingPicturesToPublicLocation(true)
        }
        WelcomeActivity.start(this@SplashActivity)
        finish()
    }

    private fun updateLoading(state: WorkInfo.State) {
        when (state) {
            WorkInfo.State.RUNNING -> {
                binding.loadingTxt.isVisible = true
                binding.loadingProgress.isVisible = true
            }

            WorkInfo.State.SUCCEEDED -> {
                binding.loadingTxt.isVisible = false
                binding.loadingProgress.isVisible = false
            }

            else -> {
                binding.loadingTxt.isVisible = false
                binding.loadingProgress.isVisible = false

                Snackbar.make(
                    binding.root,
                    R.string.errorWeb,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val FIRST_RUN_KEY = "firstRun"
    }
}
