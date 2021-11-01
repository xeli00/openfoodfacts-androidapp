package openfoodfacts.github.scrachx.openfood.features.product.view.summary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import openfoodfacts.github.scrachx.openfood.AppFlavors
import openfoodfacts.github.scrachx.openfood.AppFlavors.isFlavors
import openfoodfacts.github.scrachx.openfood.models.AnnotationAnswer
import openfoodfacts.github.scrachx.openfood.models.AnnotationResponse
import openfoodfacts.github.scrachx.openfood.models.Product
import openfoodfacts.github.scrachx.openfood.models.Question
import openfoodfacts.github.scrachx.openfood.models.entities.additive.AdditiveName
import openfoodfacts.github.scrachx.openfood.models.entities.allergen.AllergenData
import openfoodfacts.github.scrachx.openfood.models.entities.analysistagconfig.AnalysisTagConfig
import openfoodfacts.github.scrachx.openfood.models.entities.category.CategoryName
import openfoodfacts.github.scrachx.openfood.models.entities.label.LabelName
import openfoodfacts.github.scrachx.openfood.repositories.ProductRepository
import openfoodfacts.github.scrachx.openfood.utils.InfoState
import openfoodfacts.github.scrachx.openfood.utils.InfoState.*
import openfoodfacts.github.scrachx.openfood.utils.LocaleManager
import javax.inject.Inject


@HiltViewModel
class SummaryProductViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    localeManager: LocaleManager
) : ViewModel() {

    private val languageCode = localeManager.getLanguage()


    private val _categoriesState = MutableStateFlow<InfoState<List<CategoryName>>>(Loading)
    val categoriesState = _categoriesState.asStateFlow()

    fun refreshCategories(product: Product) {
        viewModelScope.launch {
            _categoriesState.value = Loading

            val categoriesTags = product.categoriesTags

            if (categoriesTags.isNullOrEmpty()) {
                _categoriesState.value = Empty
                return@launch
            }

            val categories = try {
                categoriesTags.map { tag ->
                    val categoryName = productRepository.getCategoryByTagAndLanguageCode(tag, languageCode)

                    if (categoryName.isNotNull) categoryName
                    else productRepository.getCategoryByTagAndLanguageCode(tag)
                }
            } catch (err: Exception) {
                Log.e(LOG_TAG, "loadCategories", err)
                _categoriesState.value = Empty
                return@launch
            }

            _categoriesState.value = if (categories.isEmpty()) Empty else Data(categories)
        }
    }


    private val _labelsState = MutableStateFlow<InfoState<List<LabelName>>>(Loading)
    val labelsState = _labelsState.asStateFlow()

    fun refreshLabels(product: Product) {
        viewModelScope.launch {
            _labelsState.value = Loading

            val labelsTags = product.labelsTags

            if (labelsTags == null || labelsTags.isEmpty()) {
                _labelsState.value = Empty
                return@launch
            }

            val labels = try {
                labelsTags.map { tag ->
                    val labelName = productRepository.getLabel(tag, languageCode)

                    if (labelName.isNotNull) labelName
                    else productRepository.getLabel(tag)
                }.filter { it.isNotNull }
            } catch (err: Exception) {
                Log.e(LOG_TAG, "loadLabels", err)
                _labelsState.value = Empty
                return@launch
            }

            _labelsState.value = if (labels.isEmpty()) Empty else Data(labels)

        }
    }


    private val _allergens = MutableStateFlow(AllergenData.createEmpty())
    val allergens = _allergens.asStateFlow()

    fun refreshAllergens(product: Product) {
        viewModelScope.launch {
            val productAllergens = productRepository.getAllergenNames(true, languageCode)
            val matchingAllergens = AllergenData.computeMatchingAllergens(product, productAllergens)
            _allergens.value = matchingAllergens
        }
    }


    private val _analysisTagsState = MutableStateFlow<InfoState<List<AnalysisTagConfig>>>(Loading)
    val analysisTagsState = _analysisTagsState.asStateFlow()

    fun refreshAnalysisTags(product: Product) {
        viewModelScope.launch {
            if (!isFlavors(AppFlavors.OFF, AppFlavors.OBF, AppFlavors.OPFF)) {
                _analysisTagsState.value = Empty
                return@launch
            }

            _analysisTagsState.value = Loading

            val knownTags = product.ingredientsAnalysisTags
            if (knownTags.isNotEmpty()) {
                val configs = try {
                    knownTags.mapNotNull {
                        productRepository.getAnalysisTagConfig(it, languageCode)
                    }
                } catch (err: Exception) {
                    Log.e(LOG_TAG, "loadAnalysisTags", err)
                    _analysisTagsState.value = Empty
                    return@launch
                }

                _analysisTagsState.value = if (configs.isEmpty()) Empty else Data(configs)

            } else {
                val configs = try {
                    productRepository.getUnknownAnalysisTagConfigs(languageCode)
                } catch (err: Exception) {
                    Log.e(LOG_TAG, "loadAnalysisTags", err)
                    _analysisTagsState.value = Empty
                    return@launch
                }

                _analysisTagsState.value = if (configs.isEmpty()) Empty else Data(configs)

            }
        }
    }


    private val _additivesState = MutableStateFlow<InfoState<List<AdditiveName>>>(Loading)
    val additivesState = _additivesState.asStateFlow()

    fun refreshAdditives(product: Product) {
        viewModelScope.launch {
            _additivesState.value = Loading

            val additivesTags = product.additivesTags
            if (additivesTags.isEmpty()) {
                _additivesState.value = Empty
                return@launch
            }

            val additives = try {
                additivesTags.map { tag ->
                    val categoryName = productRepository.getAdditive(tag, languageCode)

                    if (categoryName.isNotNull) categoryName
                    else productRepository.getAdditive(tag)
                }.filter { it.isNotNull }
            } catch (err: Exception) {
                Log.e(LOG_TAG, "loadAdditives", err)
                _additivesState.value = Empty
                return@launch
            }
            _additivesState.value = if (additives.isEmpty()) Empty else Data(additives)
        }
    }


    private val _productQuestion = MutableStateFlow<InfoState<Question>>(Loading)
    val productQuestion = _productQuestion.asStateFlow()

    fun loadProductQuestion(product: Product) {
        viewModelScope.launch {
            _productQuestion.value = Loading
            val question = productRepository.getProductQuestion(product.code, languageCode)
            _productQuestion.value = if (question != null) Data(question) else Empty
        }
    }


    private val _annotationFlow = MutableSharedFlow<AnnotationResponse>()
    val annotationFlow = _annotationFlow.asSharedFlow()

    fun annotateInsight(insightId: String, annotation: AnnotationAnswer) {
        viewModelScope.launch {
            _annotationFlow.emit(productRepository.annotateInsight(insightId, annotation))
        }
    }


    companion object {
        val LOG_TAG = SummaryProductViewModel::class.simpleName
    }
}

