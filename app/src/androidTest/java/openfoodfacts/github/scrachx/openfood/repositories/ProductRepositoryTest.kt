package openfoodfacts.github.scrachx.openfood.repositories

import android.content.Context
import android.util.Log
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import openfoodfacts.github.scrachx.openfood.models.DaoSession
import openfoodfacts.github.scrachx.openfood.models.entities.allergen.Allergen
import openfoodfacts.github.scrachx.openfood.models.entities.allergen.AllergenName
import openfoodfacts.github.scrachx.openfood.utils.getAppPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import javax.inject.Inject

/**
 * Created by Lobster on 05.03.18.
 */
@ExperimentalCoroutinesApi
@SmallTest
@HiltAndroidTest
class ProductRepositoryTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)


    @Inject
    lateinit var productRepository: ProductRepository

    @Inject
    @ApplicationContext
    lateinit var instance: Context

    @Inject
    lateinit var daoSession: DaoSession


    @Before
    fun cleanAllergens() {
        clearDatabase(daoSession)
        productRepository.saveAllergens(createAllergens())
    }

    @Test
    fun testGetAllergens() = runBlockingTest {
        val appPrefs = instance.getAppPreferences()

        val isDownloadActivated = appPrefs.getBoolean(Taxonomy.Allergens.getDownloadActivatePreferencesId(), false)
        val allergens = productRepository.reloadAllergensFromServer()
        assertThat(allergens).isNotNull()
        if (!isDownloadActivated) {
            assertThat(allergens).hasSize(0)
        } else {
            assertThat(allergens).hasSize(2)

            val allergen = allergens[0]
            assertThat(allergen.tag).isEqualTo(TEST_ALLERGEN_TAG)

            val allergenNames = allergen.names
            assertThat(allergenNames).hasSize(3)

            val allergenName = allergenNames[0]
            assertThat(allergen.tag).isEqualTo(allergenName.allergenTag)
            assertThat(allergenName.languageCode).isEqualTo(TEST_LANGUAGE_CODE)
            assertThat(allergenName.name).isEqualTo(TEST_ALLERGEN_NAME)
        }
    }

    @Test
    fun testGetEnabledAllergens() = runBlockingTest {
        val allergens = productRepository.getEnabledAllergens()
        assertThat(allergens).isNotNull()
        assertThat(allergens).hasSize(1)
        assertThat(allergens[0].tag).isEqualTo(TEST_ALLERGEN_TAG)
    }

    @Test
    fun testGetAllergensByEnabledAndLanguageCode() = runBlockingTest {
        val enabledAllergenNames = productRepository.getAllergenNames(true, TEST_LANGUAGE_CODE)
        val notEnabledAllergenNames = productRepository.getAllergenNames(false, TEST_LANGUAGE_CODE)
        assertNotNull(enabledAllergenNames)
        assertNotNull(notEnabledAllergenNames)
        assertEquals(1, enabledAllergenNames.size.toLong())
        assertEquals(1, notEnabledAllergenNames.size.toLong())
        assertEquals(TEST_ALLERGEN_NAME, enabledAllergenNames[0].name)
        assertEquals("Molluschi", notEnabledAllergenNames[0].name)
    }

    @Test
    fun testGetAllergensByLanguageCode() = runBlockingTest {
        val allergenNames = productRepository.getAllergensByLanguageCode(TEST_LANGUAGE_CODE)
        assertThat(allergenNames).isNotNull()
        assertThat(allergenNames).hasSize(2)
    }


    @After
    fun close() = clearDatabase(daoSession)

    companion object {
        private const val TEST_ALLERGEN_TAG = "en:lupin"
        private const val TEST_LANGUAGE_CODE = "es"
        private const val TEST_ALLERGEN_NAME = "Altramuces"

        private fun clearDatabase(daoSession: DaoSession) {
            val db = daoSession.database
            db.beginTransaction()
            try {
                daoSession.allergenDao.deleteAll()
                db.setTransactionSuccessful()
            } catch (e: Exception) {
                Log.e(this::class.simpleName, "Error in transaction.", e)
            } finally {
                db.endTransaction()
            }
        }

        private fun createAllergens(): List<Allergen> {
            val allergen1 = Allergen(TEST_ALLERGEN_TAG, ArrayList()).apply {
                enabled = true
                names += AllergenName(tag, TEST_LANGUAGE_CODE, TEST_ALLERGEN_NAME)
                names += AllergenName(tag, "bg", "Лупина")
                names += AllergenName(tag, "fr", "Lupin")
            }

            val allergen2 = Allergen("en:molluscs", ArrayList()).apply {
                names += AllergenName(tag, TEST_LANGUAGE_CODE, "Molluschi")
                names += AllergenName(tag, "en", "Mollusques")
            }

            return listOf(allergen1, allergen2)
        }
    }
}