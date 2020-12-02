package openfoodfacts.github.scrachx.openfood.models.entities.allergen

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import openfoodfacts.github.scrachx.openfood.utils.DeserializerHelper
import openfoodfacts.github.scrachx.openfood.utils.DeserializerHelper.extractMapFromJsonNode
import java.io.IOException

/**
 * Custom deserializer for [AllergensWrapper]
 *
 * @author Lobster 2018-03-04
 * @author ross-holloway94 2018-03-14
 */
class AllergensWrapperDeserializer : StdDeserializer<AllergensWrapper>(AllergensWrapper::class.java) {
    @Throws(IOException::class)
    override fun deserialize(jp: JsonParser, ctxt: DeserializationContext): AllergensWrapper {
        val allergens = arrayListOf<AllergenResponse>()
        val mainNode = jp.codec.readTree<JsonNode>(jp)
        val mainNodeIterator = mainNode.fields()
        while (mainNodeIterator.hasNext()) {
            val subNode = mainNodeIterator.next()
            val namesNode = subNode.value[DeserializerHelper.NAMES_KEY]
            if (namesNode != null) {
                val names = extractMapFromJsonNode(namesNode)
                if (subNode.value.has(DeserializerHelper.WIKIDATA_KEY)) {
                    allergens.add(AllergenResponse(subNode.key, names, subNode.value[DeserializerHelper.WIKIDATA_KEY].toString()))
                } else {
                    allergens.add(AllergenResponse(subNode.key, names))
                }
            }
        }
        return AllergensWrapper(allergens)
    }
}