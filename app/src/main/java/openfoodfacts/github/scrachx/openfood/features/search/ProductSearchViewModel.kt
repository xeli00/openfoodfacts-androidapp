package openfoodfacts.github.scrachx.openfood.features.search

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import openfoodfacts.github.scrachx.openfood.models.Search
import openfoodfacts.github.scrachx.openfood.models.SearchInfo
import openfoodfacts.github.scrachx.openfood.models.SearchProduct
import javax.inject.Inject

@HiltViewModel
class ProductSearchViewModel @Inject constructor() : ViewModel() {


    private val _searchInfo = MutableStateFlow(SearchInfo.emptySearchInfo())
    val searchInfo = _searchInfo.asStateFlow()
    fun setSearchInfo(searchInfo: SearchInfo) {
        viewModelScope.launch { _searchInfo.emit(searchInfo) }
    }

    fun reloadSearch() = setSearchInfo(searchInfo.value)


    private val _products = MutableSharedFlow<List<SearchProduct?>>()
    val products = _products.asSharedFlow()

    fun setSearchProducts(products: List<SearchProduct?>) {
        viewModelScope.launch { _products.emit(products) }
    }

    private val _searchResult = MutableSharedFlow<SearchResult>()
    val searchResult = _searchResult.asSharedFlow()


    fun startSearch(
        single: Single<Search>,
        @StringRes noMatchMsg: Int,
        @StringRes extendedMsg: Int = -1
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            val response = try {
                withContext(Dispatchers.IO) { single.await() }
            } catch (err: Exception) {
                null
            }

            if (response == null) {
                _searchResult.emit(SearchResult.Offline)
                return@launch
            }

            val count = try {
                response.count.toInt()
            } catch (e: NumberFormatException) {
                throw NumberFormatException("Cannot parse ${response.count}.")
            }

            if (count == 0) {
                _searchResult.emit(SearchResult.Empty(noMatchMsg, extendedMsg))
            } else {
                _searchResult.emit(SearchResult.Success(response))
            }
        }
    }


    sealed class SearchResult {
        object Offline : SearchResult()
        class Empty(@StringRes val emptyMessage: Int, @StringRes val extendedMessage: Int) : SearchResult()
        class Success(val search: Search) : SearchResult()
    }


}