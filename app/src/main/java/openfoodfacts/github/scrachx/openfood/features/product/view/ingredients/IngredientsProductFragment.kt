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

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spanned
import android.text.SpannedString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import openfoodfacts.github.scrachx.openfood.AppFlavors
import openfoodfacts.github.scrachx.openfood.AppFlavors.OBF
import openfoodfacts.github.scrachx.openfood.AppFlavors.OPF
import openfoodfacts.github.scrachx.openfood.AppFlavors.OPFF
import openfoodfacts.github.scrachx.openfood.AppFlavors.isFlavors
import openfoodfacts.github.scrachx.openfood.R
import openfoodfacts.github.scrachx.openfood.customtabs.CustomTabActivityHelper
import openfoodfacts.github.scrachx.openfood.customtabs.CustomTabsHelper
import openfoodfacts.github.scrachx.openfood.customtabs.WebViewFallback
import openfoodfacts.github.scrachx.openfood.databinding.FragmentIngredientsProductBinding
import openfoodfacts.github.scrachx.openfood.features.FullScreenActivityOpener
import openfoodfacts.github.scrachx.openfood.features.ImagesManageActivity
import openfoodfacts.github.scrachx.openfood.features.additives.AdditiveFragmentHelper.showAdditives
import openfoodfacts.github.scrachx.openfood.features.login.LoginActivity.Companion.LoginContract
import openfoodfacts.github.scrachx.openfood.features.product.edit.ProductEditActivity
import openfoodfacts.github.scrachx.openfood.features.product.edit.ProductEditActivity.Companion.KEY_STATE
import openfoodfacts.github.scrachx.openfood.features.product.edit.ProductEditActivity.PerformOCRContract
import openfoodfacts.github.scrachx.openfood.features.product.edit.ProductEditActivity.SendUpdatedImgContract
import openfoodfacts.github.scrachx.openfood.features.shared.BaseFragment
import openfoodfacts.github.scrachx.openfood.images.ProductImage
import openfoodfacts.github.scrachx.openfood.models.DaoSession
import openfoodfacts.github.scrachx.openfood.models.ProductImageField
import openfoodfacts.github.scrachx.openfood.models.ProductState
import openfoodfacts.github.scrachx.openfood.models.entities.SendProduct
import openfoodfacts.github.scrachx.openfood.models.entities.additive.AdditiveName
import openfoodfacts.github.scrachx.openfood.models.entities.allergen.AllergenName
import openfoodfacts.github.scrachx.openfood.models.entities.allergen.AllergenNameDao
import openfoodfacts.github.scrachx.openfood.network.OpenFoodAPIClient
import openfoodfacts.github.scrachx.openfood.network.WikiDataApiClient
import openfoodfacts.github.scrachx.openfood.repositories.ProductRepository
import openfoodfacts.github.scrachx.openfood.utils.*
import openfoodfacts.github.scrachx.openfood.utils.InfoState.*
import java.io.File
import javax.inject.Inject
import openfoodfacts.github.scrachx.openfood.features.search.ProductSearchActivity.Companion.start as startSearch

@AndroidEntryPoint
class IngredientsProductFragment : BaseFragment() {
    private var _binding: FragmentIngredientsProductBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var client: OpenFoodAPIClient

    @Inject
    lateinit var productRepository: ProductRepository

    @Inject
    lateinit var daoSession: DaoSession

    @Inject
    lateinit var wikidataClient: WikiDataApiClient

    @Inject
    lateinit var picasso: Picasso

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var localeManager: LocaleManager

    private val viewModel: ProductIngredientsViewModel by viewModels()

    private val loginPref by lazy { requireActivity().getLoginPreferences() }

    private val performOCRLauncher = registerForActivityResult(PerformOCRContract())
    { result ->
        if (result) {
            ingredientExtracted = true
            onRefresh()
        }
    }
    private val updateImagesLauncher = registerForActivityResult(SendUpdatedImgContract())
    { result -> if (result) onRefresh() }

    private val loginLauncher = registerForActivityResult(LoginContract())
    {
        ProductEditActivity.start(
            requireContext(),
            productState.product!!,
            sendUpdatedIngredientsImage,
            ingredientExtracted
        )
    }

    private lateinit var productState: ProductState
    private lateinit var customTabActivityHelper: CustomTabActivityHelper
    private lateinit var customTabsIntent: CustomTabsIntent
    private val photoReceiverHandler by lazy {
        PhotoReceiverHandler(sharedPreferences) { onPhotoReturned(it) }
    }

    private var ingredientExtracted = false

    private var mSendProduct: SendProduct? = null

    private var ingredientsImgUrl: String? = null

    private var sendUpdatedIngredientsImage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        productState = requireProductState()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIngredientsProductBinding.inflate(inflater)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.extractIngredientsPrompt.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_add_box_blue_18dp, 0, 0, 0)
        binding.changeIngImg.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_add_a_photo_blue_18dp, 0, 0, 0)

        binding.changeIngImg.setOnClickListener { changeIngImage() }
        binding.novaMethodLink.setOnClickListener { novaMethodLinkDisplay() }
        binding.extractIngredientsPrompt.setOnClickListener { extractIngredients() }
        binding.imageViewIngredients.setOnClickListener { openFullScreen() }

        lifecycleScope.launchWhenCreated {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.vitaminsTags.collectLatest { setVitaminTags(it) } }
                launch { viewModel.aminoAcidTagsList.collectLatest { setAminoAcids(it) } }
                launch { viewModel.mineralTags.collectLatest { setMineralTags(it) } }
                launch { viewModel.mineralTags.collectLatest { setOtherNutritionTags(it) } }
                launch { viewModel.additives.collectLatest { setAdditivesState(it) } }
                launch { viewModel.allergensNames.collectLatest { setAllergensState(it) } }
                launch { viewModel.traces.collectLatest { setTracesState(it) } }
                launch { viewModel.ingredientsImageUrl.collectLatest { setIngredientImage(it) } }
                launch { viewModel.extractPromptVisibility.collectLatest { setExtractPromptVisibility(it) } }
                launch {
                    viewModel.ingredientsText
                        .combine(viewModel.allergens) { txt, all -> txt to all }
                        .collectLatest { (txt, all) -> setIngredients(txt, all) }
                }
                if (!isFlavors(OBF)) {
                    launch { viewModel.novaGroups.collectLatest { setNovaGroup(it) } }
                }
            }
        }


        refreshView(productState)
    }

    private fun setVitaminTags(it: List<String>) {
        if (it.isEmpty()) binding.cvVitaminsTagsText.visibility = View.GONE
        else {
            binding.cvVitaminsTagsText.visibility = View.VISIBLE
            binding.vitaminsTagsText.text = buildSpannedString {
                bold { append(getString(R.string.vitamin_tags_text)) }
                append(tagListToString(it))
            }
        }
    }

    private fun setAminoAcids(aminoAcids: List<String>) {
        if (aminoAcids.isEmpty()) binding.cvAminoAcidTagsText.visibility = View.GONE
        else {
            binding.cvAminoAcidTagsText.visibility = View.VISIBLE
            binding.aminoAcidTagsText.text = buildSpannedString {
                bold { append(getString(R.string.amino_acid_tags_text)) }
                append(tagListToString(aminoAcids))
            }
        }
    }

    private fun setMineralTags(mineralTags: List<String>) {
        if (mineralTags.isEmpty()) binding.cvMineralTagsText.visibility = View.GONE
        else {
            binding.cvMineralTagsText.visibility = View.VISIBLE
            binding.mineralTagsText.text = buildSpannedString {
                bold { append(getString(R.string.mineral_tags_text)) }
                append(tagListToString(mineralTags))
            }
        }
    }

    private fun setOtherNutritionTags(minerals: List<String>) {
        binding.otherNutritionTags.visibility = View.VISIBLE
        binding.otherNutritionTags.text = buildSpannedString {
            bold { append(getString(R.string.other_tags_text)) }
            append(tagListToString(minerals))
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        customTabActivityHelper = CustomTabActivityHelper()
        customTabsIntent = CustomTabsHelper.getCustomTabsIntent(requireContext(), customTabActivityHelper.session)
        productState = requireProductState()
    }

    override fun refreshView(productState: ProductState) {
        super.refreshView(productState)
        this.productState = productState

        if (arguments != null) mSendProduct = getSendProduct()

        viewModel.refreshProduct(productState)


        // Useful when this fragment is used in offline saving
        if (mSendProduct != null && !mSendProduct!!.imgUploadIngredients.isNullOrBlank()) {
            binding.addPhotoLabel.visibility = View.GONE
            ingredientsImgUrl = mSendProduct!!.imgUploadIngredients
            picasso.load(LOCALE_FILE_SCHEME + ingredientsImgUrl).config(Bitmap.Config.RGB_565).into(binding.imageViewIngredients)
        }

    }

    private fun setExtractPromptVisibility(isVisible: Boolean) {
        binding.extractIngredientsPrompt.isVisible = isVisible
    }

    private fun setIngredients(ingredientsState: InfoState<String>, allergens: List<String>) {
        // If ingredients are empty, return empty
        when (ingredientsState) {
            is Data -> {
                val ingredients = ingredientsState.data

                binding.cvTextIngredientProduct.visibility = View.VISIBLE

                val txtIngredients = boldAllergens(ingredients, allergens)

                val ingredientsListAt = txtIngredients.toString().indexOf(":").coerceAtLeast(0)
                if (txtIngredients.toString().substring(ingredientsListAt).trim { it <= ' ' }.isNotEmpty()) {
                    binding.textIngredientProduct.text = txtIngredients
                }
            }
            else -> {
                binding.cvTextIngredientProduct.visibility = View.GONE
            }
        }
    }

    private fun setNovaGroup(novaGroups: String?) {
        if (novaGroups == null) return

        binding.novaLayout.visibility = View.VISIBLE
        binding.novaExplanation.text = getNovaGroupExplanation(novaGroups, requireContext()) ?: ""
        binding.novaGroup.setImageResource(getResourceFromNova(novaGroups))
        binding.novaGroup.setOnClickListener {
            val uri = getString(R.string.url_nova_groups).toUri()
            val tabsIntent = CustomTabsHelper.getCustomTabsIntent(requireContext(), customTabActivityHelper.session)
            CustomTabActivityHelper.openCustomTab(requireActivity(), tabsIntent, uri, WebViewFallback())
        }
    }

    private fun setIngredientImage(state: InfoState<String>) {
        when (state) {
            is Data -> {
                binding.ingredientImagetipBox.setTipMessage(getString(R.string.onboarding_hint_msg, getString(R.string.image_edit_tip)))
                binding.ingredientImagetipBox.loadToolTip()
                binding.addPhotoLabel.visibility = View.GONE
                binding.changeIngImg.visibility = View.VISIBLE

                // Load Image if isLowBatteryMode is false
                val url = state.data
                if (!requireContext().isBatteryLevelLow()) {
                    picasso.load(url).into(binding.imageViewIngredients)
                } else {
                    binding.imageViewIngredients.visibility = View.GONE
                }
                ingredientsImgUrl = url
            }
            else -> {
                // Nothing
            }
        }
    }

    private fun setTracesState(state: InfoState<List<String>>) {
        when (state) {
            is Data -> {
                val language = localeManager.getLanguage()
                binding.cvTextTraceProduct.visibility = View.VISIBLE

                binding.textTraceProduct.movementMethod = LinkMovementMethod.getInstance()
                binding.textTraceProduct.text = buildSpannedString {
                    bold { append(getString(R.string.txtTraces)) }
                    append(" ")

                    state.data.map {
                        getSearchLinkText(
                            getTracesName(language, it),
                            SearchType.TRACE,
                            requireActivity()
                        )
                    }.forEachIndexed { i, el ->
                        if (i > 0) append(", ")
                        append(el)
                    }
                }

            }
            else -> binding.cvTextTraceProduct.visibility = View.GONE
        }
    }

    private fun getTracesName(languageCode: String, tag: String): String {
        val allergenName = daoSession.allergenNameDao.unique {
            where(AllergenNameDao.Properties.AllergenTag.eq(tag))
            where(AllergenNameDao.Properties.LanguageCode.eq(languageCode))
        }

        return if (allergenName != null) allergenName.name else tag
    }

    private fun tagListToString(tagList: List<String>) =
        tagList.joinToString(", ", " ") { it.substring(3) }


    private fun getAllergensTag(allergen: AllergenName): SpannedString {
        val clickableSpan: ClickableSpan = object : ClickableSpan() {
            override fun onClick(view: View) {
                if (allergen.isWikiDataIdPresent) {
                    lifecycleScope.launch {
                        val result = wikidataClient.getEntityData(
                            allergen.wikiDataId
                        )
                        val activity = activity
                        if (activity?.isFinishing == false) {
                            showBottomSheet(result, allergen, activity.supportFragmentManager)
                        }
                    }
                } else {
                    startSearch(requireContext(), SearchType.ALLERGEN, allergen.allergenTag, allergen.name)
                }
            }
        }
        return buildSpannedString {
            append(allergen.name)
            setSpan(clickableSpan, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // If allergen is not in the taxonomy list then italicize it
            if (!allergen.isNotNull) {
                setSpan(StyleSpan(Typeface.ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun boldAllergens(ingredientsText: CharSequence, allergenTags: List<String>): SpannedString {
        return buildSpannedString {
            append(ingredientsText)
            INGREDIENT_REGEX.findAll(ingredientsText).forEach { match ->

                val allergenTxt = match.value
                    .replace("[()]+".toRegex(), "")
                    .replace("[,.-]".toRegex(), " ")

                allergenTags.find { tag -> tag.contains(allergenTxt, true) }?.let {
                    var start = match.range.first
                    var end = match.range.last + 1
                    if ("(" in match.value) {
                        start += 1
                    }
                    if (")" in match.value) {
                        end -= 1
                    }
                    setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                }
            }
            insert(0, buildSpannedString {
                bold { append(getString(R.string.txtIngredients) + ' ') }
            })
        }
    }


    private fun setAdditivesState(state: InfoState<List<AdditiveName>>) {
        when (state) {
            Loading -> {
                binding.cvTextAdditiveProduct.visibility = View.VISIBLE
                binding.textAdditiveProduct.text = getString(R.string.txtLoading)
            }
            Empty -> binding.cvTextAdditiveProduct.visibility = View.GONE
            is Data -> {
                showAdditives(state.data, binding.textAdditiveProduct, wikidataClient, this)
            }
        }
    }


    fun changeIngImage() {
        sendUpdatedIngredientsImage = true
        if (activity == null) return
        val viewPager = requireActivity().findViewById<ViewPager2>(R.id.pager)
        if (isFlavors(AppFlavors.OFF)) {
            if (loginPref.getString("user", "").isNullOrEmpty()) {
                showSignInDialog()
            } else {
                productState = requireProductState()
                updateImagesLauncher.launch(productState.product!!)
            }
        }
        when {
            isFlavors(OPFF) -> viewPager.currentItem = 4
            isFlavors(OBF) -> viewPager.currentItem = 1
            isFlavors(OPF) -> viewPager.currentItem = 0
        }
    }

    private fun setAllergensState(state: InfoState<List<AllergenName>>) {
        when (state) {
            Loading -> {
                binding.textSubstanceProduct.visibility = View.VISIBLE
                binding.textSubstanceProduct.append(getString(R.string.txtLoading))
            }
            Empty -> binding.textSubstanceProduct.visibility = View.GONE

            is Data -> {
                binding.textSubstanceProduct.movementMethod = LinkMovementMethod.getInstance()
                binding.textSubstanceProduct.text = buildSpannedString {
                    bold { append(getString(R.string.txtSubstances)) }
                    append(" ")
                    state.data.map(::getAllergensTag).forEachIndexed { i, el ->
                        if (i > 0) append(", ")
                        append(el)
                    }
                }
            }
        }
    }


    private fun novaMethodLinkDisplay() {
        if (productState.product != null && productState.product!!.novaGroups != null) {
            val uri = Uri.parse(getString(R.string.url_nova_groups))
            val tabsIntent = CustomTabsHelper.getCustomTabsIntent(requireContext(), customTabActivityHelper.session)
            CustomTabActivityHelper.openCustomTab(requireActivity(), tabsIntent, uri, WebViewFallback())
        }
    }

    fun extractIngredients() {
        if (!isAdded) return

        val settings = requireContext().getLoginPreferences()
        if (settings.getString("user", "")!!.isEmpty()) {
            showSignInDialog()
        } else {
            productState = requireProductState()
            performOCRLauncher.launch(productState.product)
        }
    }

    private fun showSignInDialog() {
        buildSignInDialog(requireContext(),
            onPositive = { dialog, _ ->
                loginLauncher.launch(Unit)
                dialog.dismiss()
            },
            onNegative = { d, _ -> d.dismiss() }
        ).show()
    }

    private fun openFullScreen() {
        if (ingredientsImgUrl != null && productState.product != null) {
            FullScreenActivityOpener.openForUrl(
                this,
                client,
                productState.product!!,
                ProductImageField.INGREDIENTS,
                ingredientsImgUrl!!,
                binding.imageViewIngredients,
                localeManager.getLanguage()
            )
        } else {
            newIngredientImage()
        }
    }

    private fun newIngredientImage() = doChooseOrTakePhotos()

    override fun doOnPhotosPermissionGranted() = newIngredientImage()

    private fun onPhotoReturned(newPhotoFile: File) {
        val image = ProductImage(
            productState.code!!,
            ProductImageField.INGREDIENTS,
            newPhotoFile,
            localeManager.getLanguage()
        ).apply { filePath = newPhotoFile.absolutePath }

        lifecycleScope.launch { client.postImg(image).await() }

        binding.addPhotoLabel.visibility = View.GONE
        ingredientsImgUrl = newPhotoFile.absolutePath

        picasso.load(newPhotoFile)
            .fit()
            .into(binding.imageViewIngredients)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (ImagesManageActivity.isImageModified(requestCode, resultCode)) {
            onRefresh()
        }
        photoReceiverHandler.onActivityResult(this, requestCode, resultCode, data)
    }

    companion object {
        val INGREDIENT_REGEX = Regex("[\\p{L}\\p{Nd}(),.-]+")

        fun newInstance(productState: ProductState) = IngredientsProductFragment().apply {
            arguments = Bundle().apply {
                putSerializable(KEY_STATE, productState)
            }
        }
    }
}
