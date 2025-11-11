import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import ReasoningLabViewModel
import ReasonRun
import AppStylesheet

@Composable
fun ReasoningLabView(viewModel: ReasoningLabViewModel) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            height(100.percent)
            padding(24.px)
            property("overflow-y", "auto")
        }
    }) {
        // Challenge textarea
        Div(attrs = {
            style {
                marginBottom(20.px)
            }
        }) {
            Label(attrs = {
                style {
                    display(DisplayStyle.Block)
                    marginBottom(8.px)
                    fontSize(14.px)
                    fontWeight(600)
                    color(Color("var(--text)"))
                }
            }) {
                Text("Challenge:")
            }
            TextArea(attrs = {
                style {
                    width(100.percent)
                    minHeight(120.px)
                    padding(12.px)
                    fontSize(14.px)
                    borderRadius(8.px)
                    border(1.px, LineStyle.Solid, Color("var(--border)"))
                    backgroundColor(Color("var(--surface)"))
                    color(Color("var(--text)"))
                    fontFamily("monospace")
                    property("resize", "vertical")
                }
                value(viewModel.challenge)
                onInput { viewModel.challenge = it.value }
            })
        }
        
        // Button row
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexWrap(FlexWrap.Wrap)
                gap(12.px)
                marginBottom(24.px)
            }
        }) {
            ReasoningButton(
                label = "Direct",
                onClick = { viewModel.runDirect() },
                isLoading = viewModel.isLoading && viewModel.loadingMethod == "direct",
                disabled = viewModel.isLoading || viewModel.challenge.trim().isEmpty()
            )
            ReasoningButton(
                label = "Step by Step",
                onClick = { viewModel.runStep() },
                isLoading = viewModel.isLoading && viewModel.loadingMethod == "step",
                disabled = viewModel.isLoading || viewModel.challenge.trim().isEmpty()
            )
            ReasoningButton(
                label = "Best Prompt",
                onClick = { viewModel.runMeta() },
                isLoading = viewModel.isLoading && viewModel.loadingMethod == "meta",
                disabled = viewModel.isLoading || viewModel.challenge.trim().isEmpty()
            )
            ReasoningButton(
                label = "Expert Panel",
                onClick = { viewModel.runExperts() },
                isLoading = viewModel.isLoading && viewModel.loadingMethod == "experts",
                disabled = viewModel.isLoading || viewModel.challenge.trim().isEmpty()
            )
            ReasoningButton(
                label = "Reset",
                onClick = { viewModel.reset() },
                isLoading = false,
                disabled = viewModel.runs.isEmpty() && viewModel.error == null
            )
        }
        
        // Error display
        viewModel.error?.let { error ->
            Div(attrs = {
                style {
                    padding(12.px, 16.px)
                    backgroundColor(Color("#ffebee"))
                    color(Color("#c62828"))
                    borderRadius(8.px)
                    marginBottom(16.px)
                    border(1.px, LineStyle.Solid, Color("#e57373"))
                }
            }) {
                Text(error)
                Button(attrs = {
                    style {
                        marginTop(8.px)
                        padding(4.px, 8.px)
                        fontSize(12.px)
                        backgroundColor(Color("transparent"))
                        color(Color("#c62828"))
                        border(1.px, LineStyle.Solid, Color("#e57373"))
                        borderRadius(4.px)
                        cursor("pointer")
                    }
                    onClick { viewModel.clearError() }
                }) {
                    Text("Dismiss")
                }
            }
        }
        
        // Compare view (if we have multiple results)
        if (viewModel.runs.size > 1) {
            var showCompare by remember { mutableStateOf(false) }
            
            Button(attrs = {
                classes(AppStylesheet.button, AppStylesheet.reasoningButton)
                onClick { showCompare = !showCompare }
            }) {
                Text(if (showCompare) "Hide Compare" else "Show Compare")
            }
            
            if (showCompare) {
                CompareView(viewModel.runs)
            }
        }
        
        // Results area
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                gap(16.px)
            }
        }) {
            viewModel.runs.forEach { run ->
                ReasonResultCard(run)
            }
            
            if (viewModel.runs.isEmpty() && !viewModel.isLoading) {
                Div(attrs = {
                    style {
                        padding(32.px)
                        textAlign("center")
                        color(Color("var(--text)"))
                        opacity(0.6)
                    }
                }) {
                    Text("No results yet. Run a method to see results here.")
                }
            }
        }
    }
}

@Composable
fun ReasoningButton(
    label: String,
    onClick: () -> Unit,
    isLoading: Boolean,
    disabled: Boolean
) {
    Button(attrs = {
        classes(AppStylesheet.button, AppStylesheet.reasoningButton)
        onClick { onClick() }
        if (disabled || isLoading) {
            disabled()
        }
    }) {
        if (isLoading) {
            Text("⏳ $label...")
        } else {
            Text(label)
        }
    }
}

@Composable
fun ReasonResultCard(run: ReasonRun) {
    var showPrompt by remember { mutableStateOf(false) }
    
    Div(attrs = {
        style {
            borderRadius(12.px)
            border(1.px, LineStyle.Solid, Color("var(--border)"))
            backgroundColor(Color("var(--surface)"))
            padding(20.px)
            property("box-shadow", "0 2px 4px rgba(0, 0, 0, 0.1)")
        }
    }) {
        // Header with method name
        H3(attrs = {
            style {
                fontSize(18.px)
                fontWeight(600)
                marginBottom(12.px)
                color(Color("var(--text)"))
                marginTop(0.px)
            }
        }) {
            Text(getMethodDisplayName(run.method))
        }
        
        // Prompt used (collapsible)
        Div(attrs = {
            style {
                marginBottom(12.px)
            }
        }) {
            Button(attrs = {
                style {
                    padding(4.px, 8.px)
                    fontSize(12.px)
                    backgroundColor(Color("transparent"))
                    color(Color("var(--text)"))
                    border(1.px, LineStyle.Solid, Color("var(--border)"))
                    borderRadius(4.px)
                    cursor("pointer")
                    opacity(0.7)
                }
                onClick { showPrompt = !showPrompt }
            }) {
                Text(if (showPrompt) "▼ Hide Prompt" else "▶ Show Prompt")
            }
            
            if (showPrompt) {
                Div(attrs = {
                    style {
                        marginTop(8.px)
                        padding(12.px)
                        backgroundColor(Color("var(--background)"))
                        borderRadius(6.px)
                        fontSize(12.px)
                        fontFamily("monospace")
                        color(Color("var(--text)"))
                        whiteSpace("pre-wrap")
                        property("word-break", "break-word")
                    }
                }) {
                    Text(run.promptUsed)
                }
            }
        }
        
        // Answer in bordered box
        Div(attrs = {
            style {
                padding(16.px)
                backgroundColor(Color("var(--background)"))
                borderRadius(8.px)
                border(1.px, LineStyle.Solid, Color("var(--border)"))
                fontSize(14.px)
                color(Color("var(--text)"))
                whiteSpace("pre-wrap")
                property("word-break", "break-word")
                lineHeight("1.6")
            }
        }) {
            if (run.answer.startsWith("Error:")) {
                Span(attrs = {
                    style {
                        color(Color("#c62828"))
                        fontWeight(600)
                    }
                }) {
                    Text(run.answer)
                }
            } else {
                Text(run.answer)
            }
        }
    }
}

@Composable
fun CompareView(runs: List<ReasonRun>) {
    Div(attrs = {
        style {
            borderRadius(12.px)
            border(2.px, LineStyle.Solid, Color("var(--primary)"))
            backgroundColor(Color("var(--surface)"))
            padding(20.px)
            marginBottom(20.px)
        }
    }) {
        H3(attrs = {
            style {
                fontSize(18.px)
                fontWeight(600)
                marginBottom(16.px)
                color(Color("var(--text)"))
                marginTop(0.px)
            }
        }) {
            Text("Comparison")
        }
        
        Table(attrs = {
            style {
                width(100.percent)
                property("border-collapse", "collapse")
            }
        }) {
            Thead {
                Tr {
                    Th(attrs = {
                        style {
                            padding(12.px)
                            textAlign("left")
                            property("border-bottom", "2px solid var(--border)")
                            fontWeight(600)
                            color(Color("var(--text)"))
                            width(20.percent)
                        }
                    }) {
                        Text("Method")
                    }
                    Th(attrs = {
                        style {
                            padding(12.px)
                            textAlign("left")
                            property("border-bottom", "2px solid var(--border)")
                            fontWeight(600)
                            color(Color("var(--text)"))
                            width(30.percent)
                        }
                    }) {
                        Text("Key Numbers")
                    }
                    Th(attrs = {
                        style {
                            padding(12.px)
                            textAlign("left")
                            property("border-bottom", "2px solid var(--border)")
                            fontWeight(600)
                            color(Color("var(--text)"))
                            width(50.percent)
                        }
                    }) {
                        Text("Conclusion")
                    }
                }
            }
            Tbody {
                runs.forEach { run ->
                    val extracted = extractKeyInfo(run.answer)
                    Tr {
                        Td(attrs = {
                            style {
                                padding(12.px)
                                property("border-bottom", "1px solid var(--border)")
                                fontWeight(600)
                                color(Color("var(--text)"))
                                property("vertical-align", "top")
                            }
                        }) {
                            Text(getMethodDisplayName(run.method))
                        }
                        Td(attrs = {
                            style {
                                padding(12.px)
                                property("border-bottom", "1px solid var(--border)")
                                color(Color("var(--primary)"))
                                fontSize(14.px)
                                fontWeight(600)
                                fontFamily("monospace")
                                property("vertical-align", "top")
                            }
                        }) {
                            if (extracted.numbers.isNotEmpty()) {
                                Div {
                                    extracted.numbers.forEach { num ->
                                        Div(attrs = {
                                            style {
                                                marginBottom(4.px)
                                            }
                                        }) {
                                            Text(num)
                                        }
                                    }
                                }
                            } else {
                                Span(attrs = {
                                    style {
                                        opacity(0.5)
                                    }
                                }) {
                                    Text("-")
                                }
                            }
                        }
                        Td(attrs = {
                            style {
                                padding(12.px)
                                property("border-bottom", "1px solid var(--border)")
                                color(Color("var(--text)"))
                                fontSize(13.px)
                                lineHeight("1.5")
                                property("vertical-align", "top")
                            }
                        }) {
                            Text(extracted.summary)
                        }
                    }
                }
            }
        }
    }
}

data class ExtractedInfo(
    val numbers: List<String>,
    val summary: String
)

fun extractKeyInfo(answer: String): ExtractedInfo {
    val numbers = mutableListOf<String>()
    
    // Extract fractions (like 1/3, 2/3) - prioritize these
    val fractionRegex = Regex("(\\d+/\\d+)")
    val fractions = fractionRegex.findAll(answer).map { it.value }.distinct().toList()
    numbers.addAll(fractions)
    
    // Extract percentages
    val percentRegex = Regex("(\\d+(?:\\.\\d+)?%)")
    val percentages = percentRegex.findAll(answer).map { it.value }.distinct().toList()
    numbers.addAll(percentages)
    
    // Extract decimal probabilities (like 0.33, 0.67, 0.333)
    val decimalRegex = Regex("\\b(0\\.\\d{1,4})\\b")
    val decimals = decimalRegex.findAll(answer).map { it.value }.distinct().toList()
    numbers.addAll(decimals.filter { it.toDoubleOrNull()?.let { d -> d in 0.0..1.0 } == true })
    
    // Clean answer text for processing
    val cleanAnswer = answer
        .replace(Regex("\\$"), "") // Remove LaTeX $ markers
        .replace(Regex("#+\\s*"), "") // Remove markdown headers
        .replace(Regex("\\*+"), "") // Remove markdown bold/italic
        .replace(Regex("`+"), "") // Remove code blocks
        .replace(Regex("\\s+"), " ") // Normalize spaces
        .trim()
    
    // Split into sentences (rough approximation)
    val sentences = cleanAnswer.split(Regex("[.!?]\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    
    // Priority 1: Look for sentences with conclusion keywords AND numbers
    val priorityKeywords = listOf("should", "switch", "stay", "conclusion", "final", "answer", "result", "therefore", "thus")
    val prioritySentences = sentences.filter { sentence ->
        priorityKeywords.any { keyword -> sentence.contains(keyword, ignoreCase = true) } &&
        (fractionRegex.containsMatchIn(sentence) || percentRegex.containsMatchIn(sentence) || decimalRegex.containsMatchIn(sentence)) &&
        sentence.length in 30..180
    }
    
    // Priority 2: Look for sentences with conclusion keywords (without numbers)
    val conclusionSentences = if (prioritySentences.isEmpty()) {
        sentences.filter { sentence ->
            priorityKeywords.any { keyword -> sentence.contains(keyword, ignoreCase = true) } &&
            sentence.length in 25..160
        }
    } else {
        emptyList()
    }
    
    // Priority 3: Look for sentences containing "probability" with numbers
    val probabilitySentences = sentences.filter { sentence ->
        sentence.contains("probability", ignoreCase = true) &&
        (fractionRegex.containsMatchIn(sentence) || percentRegex.containsMatchIn(sentence) || decimalRegex.containsMatchIn(sentence)) &&
        sentence.length in 20..150
    }
    
    // Select best summary
    val summary = when {
        prioritySentences.isNotEmpty() -> {
            // Take the shortest priority sentence (usually the clearest)
            prioritySentences.minByOrNull { it.length } ?: prioritySentences.first()
        }
        conclusionSentences.isNotEmpty() -> {
            conclusionSentences.first()
        }
        probabilitySentences.isNotEmpty() -> {
            probabilitySentences.first()
        }
        else -> {
            // Fallback: find first sentence with a number or first meaningful sentence
            sentences.firstOrNull { 
                fractionRegex.containsMatchIn(it) || 
                percentRegex.containsMatchIn(it) || 
                decimalRegex.containsMatchIn(it) ||
                it.length in 40..120
            } ?: cleanAnswer.take(120).trim() + if (cleanAnswer.length > 120) "..." else ""
        }
    }
    
    // Clean up the summary one more time
    val finalSummary = summary
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(180)
        .ifEmpty { cleanAnswer.take(150) + "..." }
    
    return ExtractedInfo(
        numbers = numbers.distinct().take(5),
        summary = finalSummary
    )
}

fun getMethodDisplayName(method: String): String {
    return when (method) {
        "direct" -> "Direct"
        "step" -> "Step by Step"
        "meta" -> "Best Prompt"
        "experts" -> "Expert Panel"
        else -> method.replaceFirstChar { it.uppercaseChar() }
    }
}

