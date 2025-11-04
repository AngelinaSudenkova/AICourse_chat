package tools

import database.DatabaseFactory
import database.tables.Documents
import models.ToolCall
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.regex.Pattern

class ToolRegistry(private val databaseFactory: DatabaseFactory) {
    fun decide(input: String): ToolCall? {
        return when {
            input.startsWith("/calc") || input.startsWith("/calculate") -> {
                val expr = input.removePrefix("/calc").removePrefix("/calculate").trim()
                ToolCall("calculator", expr)
            }
            input.startsWith("/time") || input.startsWith("/now") -> {
                ToolCall("timeZone", "")
            }
            input.startsWith("/search") || input.startsWith("/db") -> {
                val query = input.removePrefix("/search").removePrefix("/db").trim()
                ToolCall("dbSearch", query)
            }
            else -> null
        }
    }
    
    fun invokeSync(toolCall: ToolCall): String {
        return when (toolCall.name) {
            "calculator" -> {
                try {
                    val result = evaluateExpression(toolCall.input)
                    result.toString()
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            "timeZone" -> {
                val now = System.currentTimeMillis()
                java.time.Instant.ofEpochMilli(now).toString()
            }
            "dbSearch" -> {
                searchDatabase(toolCall.input)
            }
            else -> "Unknown tool: ${toolCall.name}"
        }
    }
    
    private fun evaluateExpression(expr: String): Double {
        val cleanExpr = expr.replace(" ", "").replace("^", "**")
        val context = java.util.concurrent.ConcurrentHashMap<String, Double>()
        
        return try {
            val tokens = tokenize(cleanExpr)
            val rpn = shuntingYard(tokens)
            evaluateRPN(rpn)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid expression: $expr")
        }
    }
    
    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        val pattern = Pattern.compile("\\d+\\.?\\d*|[+\\-*/()]|\\*\\*")
        val matcher = pattern.matcher(expr)
        
        while (matcher.find()) {
            tokens.add(matcher.group())
        }
        return tokens
    }
    
    private fun shuntingYard(tokens: List<String>): List<String> {
        val output = mutableListOf<String>()
        val operators = mutableListOf<String>()
        
        val precedence = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2, "**" to 3)
        
        for (token in tokens) {
            when {
                token.matches(Regex("\\d+\\.?\\d*")) -> output.add(token)
                token == "(" -> operators.add(token)
                token == ")" -> {
                    while (operators.isNotEmpty() && operators.last() != "(") {
                        output.add(operators.removeAt(operators.size - 1))
                    }
                    if (operators.isNotEmpty()) operators.removeAt(operators.size - 1)
                }
                else -> {
                    while (operators.isNotEmpty() && 
                           operators.last() != "(" &&
                           (precedence[operators.last()] ?: 0) >= (precedence[token] ?: 0)) {
                        output.add(operators.removeAt(operators.size - 1))
                    }
                    operators.add(token)
                }
            }
        }
        
        while (operators.isNotEmpty()) {
            output.add(operators.removeAt(operators.size - 1))
        }
        
        return output
    }
    
    private fun evaluateRPN(rpn: List<String>): Double {
        val stack = mutableListOf<Double>()
        
        for (token in rpn) {
            when {
                token.matches(Regex("\\d+\\.?\\d*")) -> stack.add(token.toDouble())
                token == "+" -> {
                    val b = stack.removeAt(stack.size - 1)
                    val a = stack.removeAt(stack.size - 1)
                    stack.add(a + b)
                }
                token == "-" -> {
                    val b = stack.removeAt(stack.size - 1)
                    val a = stack.removeAt(stack.size - 1)
                    stack.add(a - b)
                }
                token == "*" -> {
                    val b = stack.removeAt(stack.size - 1)
                    val a = stack.removeAt(stack.size - 1)
                    stack.add(a * b)
                }
                token == "/" -> {
                    val b = stack.removeAt(stack.size - 1)
                    val a = stack.removeAt(stack.size - 1)
                    stack.add(a / b)
                }
                token == "**" -> {
                    val b = stack.removeAt(stack.size - 1)
                    val a = stack.removeAt(stack.size - 1)
                    stack.add(Math.pow(a, b))
                }
            }
        }
        
        return stack.firstOrNull() ?: throw IllegalArgumentException("Invalid expression")
    }
    
    private fun searchDatabase(query: String): String {
        return transaction {
            val results = Documents.select {
                (Documents.title like "%$query%") or (Documents.content like "%$query%")
            }.limit(10).map {
                "${it[Documents.title]}: ${it[Documents.content]}"
            }
            
            if (results.isEmpty()) {
                "No results found for: $query"
            } else {
                results.joinToString("\n")
            }
        }
    }
}

