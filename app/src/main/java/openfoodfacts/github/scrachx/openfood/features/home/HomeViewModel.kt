package openfoodfacts.github.scrachx.openfood.features.home

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import openfoodfacts.github.scrachx.openfood.features.login.LoginActivity
import openfoodfacts.github.scrachx.openfood.hilt.qualifiers.LoginPreferences
import openfoodfacts.github.scrachx.openfood.models.TagLine
import openfoodfacts.github.scrachx.openfood.network.services.ProductsAPI
import openfoodfacts.github.scrachx.openfood.utils.LocaleManager
import openfoodfacts.github.scrachx.openfood.utils.getUserAgent
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val productsAPI: ProductsAPI,
    private val localeManager: LocaleManager,
    private val sharedPrefs: SharedPreferences,
    @LoginPreferences private val loginPrefs: SharedPreferences
) : ViewModel() {

    private val _tagline = MutableStateFlow(TagLine())
    val tagline = _tagline.asStateFlow()

    fun refreshTagline() {
        viewModelScope.launch {
            val languages = try {
                withContext(Dispatchers.IO) { productsAPI.getTagline(getUserAgent()) }
            } catch (err: Exception) {
                Log.w(LOG_TAG, "Could not retrieve tag-line from server.", err)
                return@launch
            }

            val appLanguage = localeManager.getLanguage()
            var isLanguageFound = false

            var tagline: TagLine? = null
            for (language in languages) {
                if (appLanguage !in language.language) continue
                isLanguageFound = true
                tagline = language.tagLine

                if (language.language == appLanguage) break
            }

            if (!isLanguageFound) {
                tagline = languages.last().tagLine
            }

            tagline?.let { this@HomeViewModel._tagline.emit(tagline) }
        }
    }

    private val _productCount = MutableSharedFlow<Int>()
    val productCount = _productCount.asSharedFlow()

    fun refreshProductCount() {
        viewModelScope.launch {
            Log.d(LOG_TAG, "Refreshing total product count...")

            val count = try {
                withContext(Dispatchers.IO) {
                    productsAPI.getTotalProductCount(getUserAgent()).count.toInt()
                }.also {
                    sharedPrefs.edit { putInt(PRODUCT_COUNT_KEY, it) }
                }
            } catch (err: Exception) {
                Log.e(LOG_TAG, "Could not retrieve product count from server.", err)
                sharedPrefs.getInt(PRODUCT_COUNT_KEY, 0)
            }

            Log.d(LOG_TAG, "Refreshed total product count. There are $count products on the database.")
            _productCount.emit(count)
        }
    }

    val _credentialCheck = MutableSharedFlow<Boolean>()
    val credentialCheck = _credentialCheck.asSharedFlow()

    fun checkUserCredentials() {
        val settings = loginPrefs

        val login = settings.getString("user", null)
        val password = settings.getString("pass", null)

        Log.d(LOG_TAG, "Checking user saved credentials...")
        if (login.isNullOrEmpty() || password.isNullOrEmpty()) {
            Log.d(LOG_TAG, "User is not logged in.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val htmlBody = try {
                val response = productsAPI.signIn(login, password, "Sign-in")
                response.body()!!.string()
            } catch (err: IOException) {
                Log.e(LOG_TAG, "I/O exception while checking credentials.", err)
                return@launch // Do not logout the user. We're just offline.
            }

            if (LoginActivity.isHtmlNotValid(htmlBody)) {
                Log.w(LOG_TAG, "Cannot validate login!")

                // deleting saved credentials and asking the user to log back in.
                settings.edit {
                    putString("user", "")
                    putString("pass", "")
                }
                _credentialCheck.emit(false)
            } else {
                _credentialCheck.emit(true)
            }
        }
    }

    companion object {
        private val LOG_TAG = HomeViewModel::class.simpleName
        private const val PRODUCT_COUNT_KEY = "productCount"
    }

}