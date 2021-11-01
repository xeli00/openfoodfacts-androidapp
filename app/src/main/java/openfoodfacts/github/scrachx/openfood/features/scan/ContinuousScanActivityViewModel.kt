package openfoodfacts.github.scrachx.openfood.features.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import openfoodfacts.github.scrachx.openfood.models.Product
import openfoodfacts.github.scrachx.openfood.models.ProductState
import openfoodfacts.github.scrachx.openfood.network.OpenFoodAPIClient
import javax.inject.Inject

@HiltViewModel
class ContinuousScanActivityViewModel @Inject constructor(
    private val client: OpenFoodAPIClient
) : ViewModel() {

    private val _productState = MutableSharedFlow<ProductState>()
    val productState = _productState.asSharedFlow()
    fun refreshProductState(productState: ProductState) {
        viewModelScope.launch { _productState.emit(productState) }
    }

    val product = productState.map { it.product!! }



    fun addToHistory(product: Product) {
        viewModelScope.launch { client.addToHistory(product) }
    }

}