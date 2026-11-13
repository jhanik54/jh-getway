package com.jh.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Playground classifies the models of a provider as free, paid or unknown.
 * These tests pin the rules so a provider with published pricing is never mislabelled.
 */
@RunWith(RobolectricTestRunner::class)
class ModelCatalogParserTest {

    @Test
    fun openRouterStylePricingMarksZeroPricedModelsAsFree() {
        val body = """
            {"data":[
              {"id":"vendor/model-a","pricing":{"prompt":"0","completion":"0"}},
              {"id":"vendor/model-b","pricing":{"prompt":"0.0000025","completion":"0.00001"}}
            ]}
        """.trimIndent()
        val catalog = ModelCatalogParser.parse(body, "Test")
        assertEquals(2, catalog.total)
        assertEquals(1, catalog.free.size)
        assertEquals("vendor/model-a", catalog.free.first().id)
        assertEquals(1, catalog.paid.size)
        assertEquals("vendor/model-b", catalog.paid.first().id)
    }

    @Test
    fun freeIdConventionIsRecognised() {
        val body = """{"data":[{"id":"vendor/model:free"},{"id":"vendor/other"}]}"""
        val catalog = ModelCatalogParser.parse(body, "Test")
        assertEquals(1, catalog.free.size)
        assertEquals("vendor/model:free", catalog.free.first().id)
        assertEquals(1, catalog.unknown.size)
    }

    @Test
    fun explicitFreeFlagIsHonoured() {
        val body = """{"data":[{"id":"a","free":true},{"id":"b","free":false}]}"""
        val catalog = ModelCatalogParser.parse(body, "Test")
        assertEquals(1, catalog.free.size)
        assertEquals(1, catalog.paid.size)
    }

    @Test
    fun providersWithoutPricingAreReportedAsUnknownNotGuessed() {
        val body = """{"data":[{"id":"a"},{"id":"b"}]}"""
        val catalog = ModelCatalogParser.parse(body, "Test")
        assertEquals(2, catalog.unknown.size)
        assertEquals(0, catalog.free.size)
        assertEquals(0, catalog.paid.size)
        assertEquals("id-convention", catalog.source)
    }

    @Test
    fun bareArrayResponseIsAccepted() {
        val body = """[{"id":"x","pricing":{"prompt":"0","completion":"0"}}]"""
        val catalog = ModelCatalogParser.parse(body, "Test")
        assertEquals(1, catalog.free.size)
    }

    @Test
    fun duplicateModelIdsAreCollapsed() {
        val body = """{"data":[{"id":"same"},{"id":"same"}]}"""
        val catalog = ModelCatalogParser.parse(body, "Test")
        assertEquals(1, catalog.total)
    }

    @Test
    fun capabilitiesAreDerivedWhenTheProviderDoesNotPublishThem() {
        val body = """{"data":[{"id":"gpt-4o"}]}"""
        val catalog = ModelCatalogParser.parse(body, "Test")
        assertTrue(catalog.models.first().capabilities.contains("vision"))
    }

    @Test
    fun catalogSurvivesAJsonRoundTrip() {
        val body = """{"data":[{"id":"a","pricing":{"prompt":"0","completion":"0"}},{"id":"b","pricing":{"prompt":"1","completion":"2"}}]}"""
        val catalog = ModelCatalogParser.parse(body, "Test")
        val restored = ModelCatalogParser.fromJson(ModelCatalogParser.toJson(catalog), "Test")
        assertEquals(catalog.total, restored.total)
        assertEquals(1, restored.free.size)
        assertEquals(1, restored.paid.size)
    }
}
