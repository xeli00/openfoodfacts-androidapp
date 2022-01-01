package openfoodfacts.github.scrachx.openfood.features.splash

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.*
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import openfoodfacts.github.scrachx.openfood.AppFlavors
import openfoodfacts.github.scrachx.openfood.AppFlavors.ANY
import openfoodfacts.github.scrachx.openfood.AppFlavors.OBF
import openfoodfacts.github.scrachx.openfood.AppFlavors.OFF
import openfoodfacts.github.scrachx.openfood.AppFlavors.OPFF
import openfoodfacts.github.scrachx.openfood.jobs.LoadTaxonomiesWorker
import openfoodfacts.github.scrachx.openfood.repositories.Taxonomy
import openfoodfacts.github.scrachx.openfood.utils.getAppPreferences
import javax.inject.Inject
import kotlin.time.ExperimentalTime

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val app: Application
) : AndroidViewModel(app) {

    private val settings = app.getAppPreferences()

    private val tagLines = MutableLiveData<Array<String>>()
    fun setupTagLines(items: Array<String>) = tagLines.postValue(items)

    val tagLine = tagLines.switchMap {
        liveData {
            var i = 0
            while (true) {
                emit(it[i++ % it.size])
                delay(1500)
            }
        }
    }

    private fun <T> Taxonomy<T>.activateDownload(vararg flavors: String) {
        if (AppFlavors.isFlavors(*flavors)) {
            settings.edit { putBoolean(getDownloadActivatePreferencesId(), true) }
        }
    }

    private val _isLoading = MutableSharedFlow<WorkInfo.State>()
    val isLoading = _isLoading.asLiveData(viewModelScope.coroutineContext)

    @ExperimentalTime
    fun refreshData() {
        viewModelScope.launch {
            Taxonomy.Categories.activateDownload(ANY)
            Taxonomy.Tags.activateDownload(ANY)
            Taxonomy.InvalidBarcodes.activateDownload(ANY)
            Taxonomy.Additives.activateDownload(OFF, OBF)
            Taxonomy.Countries.activateDownload(OFF, OBF)
            Taxonomy.Labels.activateDownload(OFF, OBF)
            Taxonomy.Allergens.activateDownload(OFF, OBF, OPFF)
            Taxonomy.AnalysisTags.activateDownload(OFF, OBF, OPFF)
            Taxonomy.AnalysisTagConfigs.activateDownload(OFF, OBF, OPFF)
            Taxonomy.ProductStates.activateDownload(OFF, OBF, OPFF)
            Taxonomy.Stores.activateDownload(OFF, OBF, OPFF)
            Taxonomy.Brands.activateDownload(OFF, OBF)

            // The service will load server resources only if newer than already downloaded...
            withContext(Dispatchers.Main) {
                val request = OneTimeWorkRequest.from(LoadTaxonomiesWorker::class.java)
                val workManager = WorkManager.getInstance(app).also { it.enqueue(request) }

                launch {
                    workManager.getWorkInfoByIdLiveData(request.id).asFlow()
                        .collectLatest { workInfo: WorkInfo? ->
                            if (workInfo != null) {
                                _isLoading.emit(workInfo.state)
                                _isLoading.emit(workInfo.state)
                            }
                        }
                }
            }
        }
    }
}

