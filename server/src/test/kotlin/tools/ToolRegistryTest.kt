package tools

import database.DatabaseFactory
import models.ToolCall
import kotlin.test.*

class ToolRegistryTest {
    private lateinit var toolRegistry: ToolRegistry
    
    @BeforeTest
    fun setup() {
        toolRegistry = ToolRegistry(DatabaseFactory())
    }
    
    @Test
    fun testDecideCalculator() {
        val toolCall = toolRegistry.decide("/calc 2+2")
        assertNotNull(toolCall)
        assertEquals("calculator", toolCall.name)
        assertEquals("2+2", toolCall.input)
    }
    
    @Test
    fun testDecideTime() {
        val toolCall = toolRegistry.decide("/time")
        assertNotNull(toolCall)
        assertEquals("timeZone", toolCall.name)
    }
    
    @Test
    fun testDecideDbSearch() {
        val toolCall = toolRegistry.decide("/search kotlin")
        assertNotNull(toolCall)
        assertEquals("dbSearch", toolCall.name)
        assertEquals("kotlin", toolCall.input)
    }
    
    @Test
    fun testInvokeCalculator() {
        val toolCall = ToolCall("calculator", "2+2")
        val result = toolRegistry.invokeSync(toolCall)
        assertEquals("4.0", result)
    }
    
    @Test
    fun testInvokeTimeZone() {
        val toolCall = ToolCall("timeZone", "")
        val result = toolRegistry.invokeSync(toolCall)
        assertTrue(result.isNotEmpty())
    }
}

