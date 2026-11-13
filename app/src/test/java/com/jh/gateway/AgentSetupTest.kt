package com.jh.gateway

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The agent setup snippets must point at the local gateway and carry the gateway key,
 * because that is what makes an agent use the routing chain instead of a provider.
 */
class AgentSetupTest {

    @Test
    fun snippetsCarryTheGatewayKeyAndAddress() {
        val snippets = AgentSetup.snippets(port = 8787, gatewayKey = "gw-testkey", lan = false)
        assertTrue(snippets.isNotEmpty())
        snippets.forEach { snippet ->
            assertTrue("${snippet.id} is missing the key", snippet.content.contains("gw-testkey"))
            assertTrue("${snippet.id} is missing the port", snippet.content.contains("8787"))
        }
    }

    @Test
    fun openCodeSnippetIsValidJsonWithTheProviderAndModel() {
        val snippet = AgentSetup.snippets(8787, "gw-testkey", lan = false).first { it.id == "opencode" }
        val json = JSONObject(snippet.content)
        assertEquals("jh-getway/auto", json.getString("model"))
        val provider = json.getJSONObject("provider").getJSONObject("jh-getway")
        assertTrue(provider.getJSONObject("options").getString("baseURL").startsWith("http://127.0.0.1:8787/v1"))
        assertEquals("gw-testkey", provider.getJSONObject("options").getString("apiKey"))
    }

    @Test
    fun clineSnippetIsValidJson() {
        val snippet = AgentSetup.snippets(8787, "gw-testkey", lan = false).first { it.id == "cline" }
        val json = JSONObject(snippet.content)
        assertEquals("openai", json.getString("cline.apiProvider"))
        assertEquals("gw-testkey", json.getString("cline.openAiApiKey"))
    }

    @Test
    fun envSnippetsExportTheExpectedVariables() {
        val snippets = AgentSetup.snippets(8787, "gw-testkey", lan = false)
        val openAi = snippets.first { it.id == "openai_env" }.content
        assertTrue(openAi.contains("OPENAI_BASE_URL"))
        assertTrue(openAi.contains("OPENAI_API_KEY"))
        val claude = snippets.first { it.id == "claude_code" }.content
        assertTrue(claude.contains("ANTHROPIC_BASE_URL"))
        assertTrue(claude.contains("ANTHROPIC_AUTH_TOKEN"))
    }

    @Test
    fun modelEndpointFollowsTheProviderBaseUrl() {
        assertEquals("https://api.openai.com/v1/models", ModelDiscovery.modelsEndpoint("https://api.openai.com/v1"))
        assertEquals("https://api.openai.com/v1/models", ModelDiscovery.modelsEndpoint("https://api.openai.com"))
        assertEquals("https://api.openai.com/v1/models", ModelDiscovery.modelsEndpoint("https://api.openai.com/"))
    }
}
