package openfoodfacts.github.scrachx.openfood.models.entities.allergen

import openfoodfacts.github.scrachx.openfood.models.Product
import openfoodfacts.github.scrachx.openfood.network.ApiFields
import org.jetbrains.annotations.Contract


data class AllergenData(val incomplete: Boolean, val allergens: List<String>) {
    fun isEmpty() = !incomplete && allergens.isEmpty()

    companion object {
        @Contract(" -> new")
        fun createEmpty() = AllergenData(false, emptyList())

        fun computeMatchingAllergens(product: Product, userAllergens: List<AllergenName>): AllergenData {
            if (userAllergens.isEmpty())
                return createEmpty()

            if (ApiFields.StateTags.INGREDIENTS_COMPLETED !in product.statesTags)
                return AllergenData(true, emptyList())

            val productAllergens = (product.allergensHierarchy + product.tracesTags).toSet()

            val matchingAllergens = userAllergens
                .filter { it.allergenTag in productAllergens }
                .map { it.name }
                .toSet()

            return AllergenData(false, matchingAllergens.toList())
        }
    }
}


