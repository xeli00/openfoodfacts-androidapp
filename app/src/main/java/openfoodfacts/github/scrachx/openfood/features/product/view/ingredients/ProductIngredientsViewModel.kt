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
package openfoodfacts.github.scrachx.openfood.features.product.view.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import openfoodfacts.github.scrachx.openfood.models.Product
import openfoodfacts.github.scrachx.openfood.models.ProductState
import openfoodfacts.github.scrachx.openfood.repositories.ProductRepository
import openfoodfacts.github.scrachx.openfood.utils.InfoState
import openfoodfacts.github.scrachx.openfood.utils.LocaleManager
import javax.inject.Inject

/**
 * Created by Lobster on 17.03.18.
 */
@HiltViewModel
class ProductIngredientsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    localeManager: LocaleManager
) : ViewModel() {

    val languageCode = localeManager.getLanguage()

    private val _productState = MutableSharedFlow<ProductState>()
    val productState = _productState.asSharedFlow()

    val product = productState.map { it.product!! }

    fun refreshProduct(productState: ProductState) {
        viewModelScope.launch { _productState.emit(productState) }
    }

    val additives = product.transform { product ->
        emit(InfoState.Loading)
        val additivesTags = product.additivesTags

        if (additivesTags.isEmpty()) {
            emit(InfoState.Empty)
        } else {
            additivesTags.map { tag ->
                productRepository.getAdditive(tag, languageCode).takeUnless { it.isNull }
                    ?: productRepository.getAdditive(tag)
            }.filter { it.isNotNull }.let { emit(InfoState.Data(it)) }
        }
    }

    val allergensNames = product.transform { product ->
        emit(InfoState.Loading)
        val allergenTags = product.allergensTags

        if (allergenTags.isEmpty()) {
            emit(InfoState.Empty)
        } else {
            allergenTags.map { tag ->
                productRepository.getAllergenByTagAndLanguageCode(tag, languageCode).takeUnless { it.isNull }
                    ?: productRepository.getAllergenByTagAndDefaultLanguageCode(tag)
            }.filter { it.isNotNull }.let { emit(InfoState.Data(it)) }
        }
    }


    val allergens = product.map { product ->
        product.allergensTags.takeUnless { it.isEmpty() } ?: emptyList()
    }


    val vitaminsTags = product.map { it.vitaminTags }
    val mineralTags = product.map { it.mineralTags }.filterNot { it.isEmpty() }
    val otherNutritionTags = product.map { it.otherNutritionTags }
    val aminoAcidTagsList = product.map { it.aminoAcidTags }

    val traces = product.map { product ->
        val traces = product.traces

        if (traces.isNullOrBlank()) InfoState.Empty
        else InfoState.Data(traces.split(","))
    }

    val ingredientsImageUrl = product.map { product ->
        val url = product.getImageIngredientsUrl(languageCode)

        if (url.isNullOrBlank()) InfoState.Empty
        else InfoState.Data(url)
    }

    val ingredientsText = product.map { product ->
        val ingredients = product.getIngredientsText(languageCode)

        if (ingredients.isNullOrEmpty()) InfoState.Empty
        else InfoState.Data(ingredients.replace("_", ""))
    }

    val extractPromptVisibility = product.map {
        val url = it.getImageIngredientsUrl(languageCode)
        val ingredients = it.getIngredientsText(languageCode)

        return@map ingredients.isNullOrEmpty() && !url.isNullOrEmpty()
    }

    val novaGroups = product.map { it.novaGroups }

}
