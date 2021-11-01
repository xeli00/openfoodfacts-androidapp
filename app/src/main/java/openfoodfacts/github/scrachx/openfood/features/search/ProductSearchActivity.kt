package openfoodfacts.github.scrachx.openfood.features.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import openfoodfacts.github.scrachx.openfood.R
import openfoodfacts.github.scrachx.openfood.analytics.AnalyticsEvent
import openfoodfacts.github.scrachx.openfood.analytics.MatomoAnalytics
import openfoodfacts.github.scrachx.openfood.customtabs.CustomTabActivityHelper
import openfoodfacts.github.scrachx.openfood.databinding.ActivityProductBrowsingListBinding
import openfoodfacts.github.scrachx.openfood.features.adapters.ProductSearchAdapter
import openfoodfacts.github.scrachx.openfood.features.product.view.ProductViewActivityStarter
import openfoodfacts.github.scrachx.openfood.features.shared.BaseActivity
import openfoodfacts.github.scrachx.openfood.listeners.CommonBottomListenerInstaller.installBottomNavigation
import openfoodfacts.github.scrachx.openfood.listeners.CommonBottomListenerInstaller.selectNavigationItem
import openfoodfacts.github.scrachx.openfood.listeners.EndlessRecyclerViewScrollListener
import openfoodfacts.github.scrachx.openfood.listeners.RecyclerItemClickListener
import openfoodfacts.github.scrachx.openfood.models.Search
import openfoodfacts.github.scrachx.openfood.models.SearchInfo
import openfoodfacts.github.scrachx.openfood.models.SearchProduct
import openfoodfacts.github.scrachx.openfood.network.OpenFoodAPIClient
import openfoodfacts.github.scrachx.openfood.repositories.ProductRepository
import openfoodfacts.github.scrachx.openfood.utils.*
import openfoodfacts.github.scrachx.openfood.utils.SearchType.*
import java.text.NumberFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ProductSearchActivity : BaseActivity() {
    private var _binding: ActivityProductBrowsingListBinding? = null
    private val binding get() = _binding!!

    val viewModel: ProductSearchViewModel by viewModels()

    @Inject
    lateinit var client: OpenFoodAPIClient

    @Inject
    lateinit var productViewActivityStarter: ProductViewActivityStarter

    @Inject
    lateinit var analytics: MatomoAnalytics

    @Inject
    lateinit var productRepository: ProductRepository

    @Inject
    lateinit var picasso: Picasso

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var localeManager: LocaleManager

    private lateinit var adapter: ProductSearchAdapter

    private var contributionType = 0
    private val lowBatteryMode by lazy { isDisableImageLoad() && isBatteryLevelLow() }

    /**
     * boolean to determine if image should be loaded or not
     */
    private var setupDone = false
    private var mCountProducts = 0
    private var pageAddress = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityProductBrowsingListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarInclude.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        // Track search without details
        analytics.trackEvent(AnalyticsEvent.ProductSearch)

        // OnClick
        binding.buttonTryAgain.setOnClickListener { viewModel.reloadSearch() }
        binding.addProduct.setOnClickListener { addProduct() }

        binding.textCountProduct.visibility = View.INVISIBLE

        // Get the search information (query, title, type) that we will use in this activity
        val extras = intent.extras
        if (extras != null) {
            val searchInfo = extras.getParcelable(SEARCH_INFO) ?: SearchInfo.emptySearchInfo()
            viewModel.setSearchInfo(searchInfo)

        } else if (Intent.ACTION_VIEW == intent.action) {
            // the user has entered the activity via a url
            val data = intent.data
            if (data != null) {
                // TODO: If we open advanced search from app it redirects here
                val paths = data.toString().split("/")
                val searchInfo = SearchInfo.emptySearchInfo()

                if (paths[3] == "cgi" && "search.pl" in paths[4]) {
                    searchInfo.searchTitle = data.getQueryParameter("search_terms") ?: ""
                    searchInfo.searchQuery = data.getQueryParameter("search_terms") ?: ""
                    searchInfo.searchType = SEARCH
                } else {
                    searchInfo.searchTitle = paths[4]
                    searchInfo.searchQuery = paths[4]
                    searchInfo.searchType = SearchType.fromUrl(paths[3]) ?: SEARCH
                }

                viewModel.setSearchInfo(searchInfo)

            } else {
                Log.i(LOG_TAG, "No data was passed in with URL. Exiting.")
                finish()
            }
        } else {
            Log.e(LOG_TAG, "No data passed to the activity. Exiting.")
            finish()
        }
        binding.navigationBottom.bottomNavigation.selectNavigationItem(0)
        binding.navigationBottom.bottomNavigation.installBottomNavigation(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.products.collectLatest { setUpRecyclerView(it) } }
                launch { viewModel.searchInfo.collectLatest { loadSearch(it) } }
                launch {
                    viewModel.searchResult.collectLatest {
                        when (it) {
                            ProductSearchViewModel.SearchResult.Offline ->
                                showOfflineCloud()
                            is ProductSearchViewModel.SearchResult.Empty ->
                                showEmptyResponse(it.emptyMessage, it.extendedMessage)
                            is ProductSearchViewModel.SearchResult.Success ->
                                showSuccessfulSearch(it.search)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchMenuItem = menu.findItem(R.id.action_search)
        val searchView = searchMenuItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {

                viewModel.setSearchInfo(viewModel.searchInfo.value.apply {
                    searchQuery = query
                    searchType = SEARCH
                })
                return true
            }

            override fun onQueryTextChange(newText: String) = true
        })
        searchMenuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                supportActionBar?.title = ""
                finish()
                return true
            }
        })
        if (viewModel.searchInfo.value.searchType == CONTRIBUTOR) {
            menu.findItem(R.id.action_set_type).isVisible = true
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_set_type -> {
                val contributionTypes = arrayOf(
                    getString(R.string.products_added),
                    getString(R.string.products_incomplete),
                    getString(R.string.product_pictures_contributed),
                    getString(R.string.picture_contributed_incomplete),
                    getString(R.string.product_info_added),
                    getString(R.string.product_info_tocomplete)
                )
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.show_by)
                    .setItems(contributionTypes) { _, position ->
                        contributionType = when (position) {
                            in 1..5 -> position
                            else -> 0
                        }
                        viewModel.reloadSearch()
                    }.show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupHungerGames() {
        val actualCountryTag = sharedPreferences.getString(getString(R.string.pref_country_key), "")
        if (actualCountryTag.isNullOrBlank()) {
            lifecycleScope.launch {
                val url = productRepository
                    .getCountryByCC2OrWorld(localeManager.getLocale().country)
                    ?.tag ?: "en:world"
                withContext(Main) { setupUrlHungerGames(url) }
            }
        } else {
            setupUrlHungerGames(actualCountryTag)
        }
    }

    private fun setupUrlHungerGames(countryTag: String?) {
        val searchInfo = viewModel.searchInfo.value
        val url = "https://hunger.openfoodfacts.org/questions".toUri()
            .buildUpon()
            .appendQueryParameter("type", searchInfo.searchType.url)
            .appendQueryParameter("value_tag", searchInfo.searchQuery)
            .appendQueryParameter("country", countryTag)
            .build()

        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        binding.btnHungerGames.visibility = View.VISIBLE
        binding.btnHungerGames.text = resources.getString(R.string.hunger_game_call_to_action, searchInfo.searchTitle)
        binding.btnHungerGames.setOnClickListener {
            CustomTabActivityHelper.openCustomTab(this, customTabsIntent, url, null)
        }
    }

    private fun loadSearch(searchInfo: SearchInfo) {
        val bar = supportActionBar ?: error("Support action bar not set.")

        bar.title = searchInfo.searchTitle
        when (searchInfo.searchType) {
            BRAND -> {
                bar.setSubtitle(R.string.brand_string)
                setupHungerGames()
            }
            LABEL -> {
                bar.setSubtitle(R.string.label_string)
                setupHungerGames()
            }
            CATEGORY -> {
                bar.setSubtitle(R.string.category_string)
                setupHungerGames()
            }
            COUNTRY -> bar.setSubtitle(R.string.country_string)
            ORIGIN -> bar.setSubtitle(R.string.origin_of_ingredients)
            MANUFACTURING_PLACE -> bar.setSubtitle(R.string.manufacturing_place)
            ADDITIVE -> bar.setSubtitle(R.string.additive_string)
            SEARCH -> bar.setSubtitle(R.string.search_string)
            STORE -> bar.setSubtitle(R.string.store_subtitle)
            PACKAGING -> bar.setSubtitle(R.string.packaging_subtitle)
            CONTRIBUTOR -> bar.subtitle = getString(R.string.contributor_string)
            ALLERGEN -> bar.subtitle = getString(R.string.allergen_string)
            INCOMPLETE_PRODUCT -> bar.title = getString(R.string.products_to_be_completed)
            STATE -> bar.setSubtitle(R.string.state_subtitle)
            TRACE -> bar.setSubtitle(R.string.traces)
            EMB -> bar.setSubtitle(R.string.emb_code)
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.offlineCloudLinearLayout.visibility = View.INVISIBLE
        binding.textCountProduct.visibility = View.INVISIBLE
        binding.noResultsLayout.visibility = View.INVISIBLE
        pageAddress = 1
        loadDataFromAPI(searchInfo)
    }

    /**
     * When no matching products are found in the database then noResultsLayout is displayed.
     * This method is called when the user clicks on the add photo button in the noResultsLayout.
     */
    private fun addProduct() {
        when {
            checkSelfPermission(this, Manifest.permission.CAMERA) == PERMISSION_GRANTED ->
                startScanActivity()
            shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.action_about)
                    .setMessage(R.string.permission_camera)
                    .setNeutralButton(android.R.string.ok) { _, _ ->
                        requestCameraThenOpenScan.launch(Manifest.permission.CAMERA)
                    }.show()
            else -> requestCameraThenOpenScan.launch(Manifest.permission.CAMERA)
        }
    }


    fun loadDataFromAPI(searchInfo: SearchInfo) {
        val searchQuery = searchInfo.searchQuery
        when (searchInfo.searchType) {
            BRAND -> client.getProductsByBrand(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_brand_products)

            COUNTRY -> client.getProductsByCountry(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_country_products)

            ORIGIN -> client.getProductsByOrigin(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_country_products)

            MANUFACTURING_PLACE -> client.getProductsByManufacturingPlace(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_country_products)

            ADDITIVE -> client.getProductsByAdditive(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_additive_products)

            STORE -> client.getProductsByStore(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_store_products)

            PACKAGING -> client.getProductsByPackaging(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_packaging_products)

            SEARCH -> {
                if (isBarcodeValid(searchQuery)) {
                    productViewActivityStarter.openProduct(searchQuery, this@ProductSearchActivity)
                } else {
                    client.searchProductsByName(searchQuery, pageAddress)
                        .startSearch(R.string.txt_no_matching_products, R.string.txt_broaden_search)
                }
            }
            LABEL -> client.getProductsByLabel(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_label_products)

            CATEGORY -> client.getProductsByCategory(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching__category_products)

            ALLERGEN -> client.getProductsByAllergen(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_allergen_products)

            CONTRIBUTOR -> loadDataForContributor(searchQuery)

            STATE -> client.getProductsByStates(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_allergen_products)

            // Get Products to be completed data and input it to loadData function
            INCOMPLETE_PRODUCT -> client.getIncompleteProducts(pageAddress)
                .startSearch(R.string.txt_no_matching_incomplete_products)

            else -> Log.e("Products Browsing", "No match case found for ${searchInfo.searchType}")
        }
    }


    private fun Single<Search>.startSearch(
        @StringRes noMatchMsg: Int,
        @StringRes extendedMsg: Int = -1
    ) = viewModel.startSearch(this, noMatchMsg, extendedMsg)

    private fun loadDataForContributor(searchQuery: String) {
        when (contributionType) {
            1 -> client.getToBeCompletedProductsByContributor(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_contributor_products)

            2 -> client.getPicturesContributedProducts(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_contributor_products)

            3 -> client.getPicturesContributedIncompleteProducts(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_contributor_products)

            4 -> client.getInfoAddedProducts(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_contributor_products)

            5 -> client.getInfoAddedIncompleteProductsSingle(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_contributor_products)

            else -> client.getProductsByContributor(searchQuery, pageAddress)
                .startSearch(R.string.txt_no_matching_contributor_products)
        }
    }

    private fun showSuccessfulSearch(search: Search) {
        mCountProducts = search.count.toInt()
        if (pageAddress == 1) {
            val number = NumberFormat.getInstance(Locale.getDefault()).format(search.count.toLong())
            binding.textCountProduct.text = "${resources.getString(R.string.number_of_results)} $number"

            // Hacky thing to make sure the count is right
            val products: MutableList<SearchProduct?> = search.products.toMutableList()
            if (products.size < mCountProducts) {
                products += null
            }

            if (setupDone) {
                adapter = ProductSearchAdapter(products, lowBatteryMode, this, picasso, client, localeManager)
                binding.productsRecyclerView.adapter = adapter
            }

            viewModel.setSearchProducts(products)

        } else if (adapter.products.size - 1 < mCountProducts + 1) {
            val posStart = adapter.itemCount

            adapter.products.removeAt(posStart - 1)
            adapter.products += search.products

            // Hacky thing to make sure the count is right
            if (adapter.products.size < mCountProducts) {
                adapter.products += null
            }

            adapter.notifyItemRangeChanged(posStart - 1, adapter.products.size - 1)
        }
    }

    private fun showOfflineCloud() {
        binding.swipeRefresh.isRefreshing = false
        binding.textCountProduct.visibility = View.GONE
        binding.productsRecyclerView.visibility = View.INVISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.offlineCloudLinearLayout.visibility = View.VISIBLE
    }

    /**
     * Shows UI indicating that no matching products were found.
     *
     * Called by [displaySearch].
     *
     * @param message message to display when there are no results for given search
     * @param extendedMessage additional message to display, -1 if no message is displayed
     */
    private fun showEmptyResponse(@StringRes message: Int, @StringRes extendedMessage: Int) {
        binding.swipeRefresh.isRefreshing = false

        binding.productsRecyclerView.visibility = View.INVISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.offlineCloudLinearLayout.visibility = View.INVISIBLE
        binding.textCountProduct.visibility = View.GONE

        binding.textNoResults.setText(message)
        if (extendedMessage != -1) binding.textExtendSearch.setText(extendedMessage)
        binding.noResultsLayout.bringToFront()
        binding.noResultsLayout.visibility = View.VISIBLE

    }


    private fun setUpRecyclerView(mProducts: List<SearchProduct?>) {
        binding.swipeRefresh.isRefreshing = false

        binding.progressBar.visibility = View.INVISIBLE
        binding.offlineCloudLinearLayout.visibility = View.INVISIBLE

        binding.textCountProduct.visibility = View.VISIBLE
        binding.productsRecyclerView.visibility = View.VISIBLE

        if (!setupDone) {
            binding.productsRecyclerView.setHasFixedSize(true)
            val mLayoutManager = LinearLayoutManager(this@ProductSearchActivity, LinearLayoutManager.VERTICAL, false)
            binding.productsRecyclerView.layoutManager = mLayoutManager
            adapter = ProductSearchAdapter(mProducts.toMutableList(), lowBatteryMode, this, picasso, client, localeManager)
            binding.productsRecyclerView.adapter = adapter
            val dividerItemDecoration = DividerItemDecoration(binding.productsRecyclerView.context, DividerItemDecoration.VERTICAL)
            binding.productsRecyclerView.addItemDecoration(dividerItemDecoration)

            // Retain an instance so that you can call `resetState()` for fresh searches
            // Adds the scroll listener to RecyclerView
            binding.productsRecyclerView.addOnScrollListener(object : EndlessRecyclerViewScrollListener(mLayoutManager) {
                override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView) {
                    if (mProducts.size < mCountProducts) {
                        pageAddress = page
                        viewModel.reloadSearch()
                    }
                }
            })

            binding.productsRecyclerView.addOnItemTouchListener(RecyclerItemClickListener(this) { _, position ->
                adapter.getProduct(position)?.let {
                    productViewActivityStarter.openProduct(it.code, this)
                }
                return@RecyclerItemClickListener
            })

            binding.swipeRefresh.setOnRefreshListener {
                adapter.products.clear()
                adapter.notifyDataSetChanged()
                binding.textCountProduct.text = resources.getString(R.string.number_of_results)
                pageAddress = 1
                viewModel.reloadSearch()
                if (binding.swipeRefresh.isRefreshing) {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }

        setupDone = true
        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = true
            pageAddress = 1
            viewModel.reloadSearch()
        }
    }

    companion object {
        /**
         * Must be public to be visible by TakeScreenshotIncompleteProductsTest class.
         */
        const val SEARCH_INFO = "search_info"

        /**
         * Start a new [ProductSearchActivity] given a search information
         *
         * @param context the context to use to start this activity
         * @param searchQuery the search query
         * @param searchTitle the title used in the activity for this search query
         * @param type the type of search
         */
        fun start(context: Context, type: SearchType, searchQuery: String, searchTitle: String = searchQuery) =
            start(context, SearchInfo(type, searchQuery, searchTitle))

        /**
         * @see [start]
         */
        private fun start(context: Context, searchInfo: SearchInfo) {
            context.startActivity(Intent(context, ProductSearchActivity::class.java).apply {
                putExtra(SEARCH_INFO, searchInfo)
            })
        }

        private val LOG_TAG: String = ProductSearchActivity::class.simpleName!!
    }
}
