import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import models.*

@Composable
fun RagCompareView(viewModel: RagCompareViewModel) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            padding(24.px)
            gap(24.px)
            property("overflow-y", "auto")
            height(100.percent)
            maxWidth(1400.px)
            property("margin-left", "auto")
            property("margin-right", "auto")
        }
    }) {
        H1(attrs = {
            style {
                fontSize(32.px)
                marginBottom(24.px)
            }
        }) {
            Text("🧩 RAG Compare")
        }
        
        P(attrs = {
            style {
                marginBottom(24.px)
                color(Color("#666"))
            }
        }) {
            Text("Compare answers with and without RAG (Retrieval-Augmented Generation). Enter a question to see how wiki context improves the answer. Day 17: Now with relevance filtering to remove low-scoring chunks.")
        }
        
        // Input form
        Div(attrs = {
            style {
                padding(24.px)
                backgroundColor(Color("#f5f5f5"))
                borderRadius(8.px)
            }
        }) {
            Div(attrs = {
                style {
                    marginBottom(16.px)
                }
            }) {
                Label(attrs = {
                    style {
                        display(DisplayStyle.Block)
                        marginBottom(8.px)
                        fontWeight("bold")
                    }
                }) {
                    Text("Question")
                }
                Input(type = InputType.Text, attrs = {
                    value(viewModel.question)
                    onInput { ev ->
                        viewModel.question = (ev.target as? org.w3c.dom.HTMLInputElement)?.value ?: ""
                    }
                    placeholder("e.g., What are the key ideas of quantum computing?")
                    style {
                        width(100.percent)
                        padding(12.px)
                        fontSize(16.px)
                        borderRadius(4.px)
                        border(1.px, LineStyle.Solid, Color("#ddd"))
                    }
                })
            }
            
            Div(attrs = {
                style {
                    marginBottom(16.px)
                }
            }) {
                Label(attrs = {
                    style {
                        display(DisplayStyle.Block)
                        marginBottom(8.px)
                        fontWeight("bold")
                    }
                }) {
                    Text("Top K (number of chunks)")
                }
                Input(type = InputType.Number, attrs = {
                    value(viewModel.topK.toString())
                    onInput { ev ->
                        val value = (ev.target as? org.w3c.dom.HTMLInputElement)?.value?.toIntOrNull() ?: 5
                        viewModel.topK = value.coerceIn(1, 20)
                    }
                    style {
                        width(100.percent)
                        padding(12.px)
                        fontSize(16.px)
                        borderRadius(4.px)
                        border(1.px, LineStyle.Solid, Color("#ddd"))
                    }
                })
            }
            
            // Filtering controls (Day 17)
            Div(attrs = {
                style {
                    marginBottom(16.px)
                    padding(16.px)
                    backgroundColor(Color("#fff"))
                    borderRadius(4.px)
                    border(1.px, LineStyle.Solid, Color("#ddd"))
                }
            }) {
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        alignItems(AlignItems.Center)
                        marginBottom(12.px)
                    }
                }) {
                    Input(type = InputType.Checkbox, attrs = {
                        checked(viewModel.enableFilter)
                        onChange { ev ->
                            viewModel.enableFilter = (ev.target as? org.w3c.dom.HTMLInputElement)?.checked ?: false
                        }
                        style {
                            marginRight(8.px)
                            width(20.px)
                            height(20.px)
                            cursor("pointer")
                        }
                    })
                    Label(attrs = {
                        style {
                            fontWeight("bold")
                            cursor("pointer")
                        }
                    }) {
                        Text("Enable relevance filter")
                    }
                }
                
                if (viewModel.enableFilter) {
                    Div(attrs = {
                        style {
                            marginTop(12.px)
                        }
                    }) {
                        Label(attrs = {
                            style {
                                display(DisplayStyle.Block)
                                marginBottom(8.px)
                                fontWeight("bold")
                            }
                        }) {
                            Text("Min Similarity Threshold: ${(kotlin.math.round(viewModel.minSimilarity * 1000) / 1000.0).toString()}")
                        }
                        Input(type = InputType.Range, attrs = {
                            value(viewModel.minSimilarity.toString())
                            attr("min", "0.0")
                            attr("max", "1.0")
                            attr("step", "0.05")
                            onInput { ev ->
                                val value = (ev.target as? org.w3c.dom.HTMLInputElement)?.value?.toDoubleOrNull() ?: 0.3
                                viewModel.minSimilarity = value.coerceIn(0.0, 1.0)
                            }
                            style {
                                width(100.percent)
                                height(8.px)
                                cursor("pointer")
                            }
                        })
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                justifyContent(JustifyContent.SpaceBetween)
                                fontSize(12.px)
                                color(Color("#666"))
                                marginTop(4.px)
                            }
                        }) {
                            Text("0.0")
                            Text("1.0")
                        }
                    }
                }
            }
            
            Button(attrs = {
                onClick { viewModel.submit() }
                if (viewModel.loading) disabled()
                style {
                    padding(12.px, 24.px)
                    fontSize(16.px)
                    backgroundColor(if (viewModel.loading) Color("#ccc") else Color("#007bff"))
                    color(Color.white)
                    border(0.px)
                    borderRadius(4.px)
                    cursor("pointer")
                    fontWeight("bold")
                }
            }) {
                Text(if (viewModel.loading) "Comparing..." else "Compare (Baseline / Raw RAG / Filtered RAG)")
            }
        }
        
        // Error display
        viewModel.error?.let { error ->
            Div(attrs = {
                style {
                    padding(16.px)
                    backgroundColor(Color("#fee"))
                    color(Color("#c33"))
                    borderRadius(8.px)
                    border(1.px, LineStyle.Solid, Color("#fcc"))
                }
            }) {
                Text("Error: $error")
            }
        }
        
        // Results display - Day 17: filtering comparison
        viewModel.filteringResult?.let { result ->
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    gap(24.px)
                }
            }) {
                // Question display
                Div(attrs = {
                    style {
                        padding(16.px)
                        backgroundColor(Color("#e3f2fd"))
                        borderRadius(8.px)
                        border(1.px, LineStyle.Solid, Color("#90caf9"))
                    }
                }) {
                    H3(attrs = {
                        style {
                            marginTop(0.px)
                            marginBottom(8.px)
                            fontSize(18.px)
                        }
                    }) {
                        Text("Question:")
                    }
                    P(attrs = {
                        style {
                            margin(0.px)
                            fontSize(16.px)
                            fontWeight("bold")
                        }
                    }) {
                        Text(result.question)
                    }
                }
                
                // Three-column comparison
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Row)
                        gap(16.px)
                        alignItems(AlignItems.FlexStart)
                    }
                }) {
                    // Baseline answer
                    Div(attrs = {
                        style {
                            flex(1)
                            padding(20.px)
                            backgroundColor(Color.white)
                            borderRadius(8.px)
                            border(2.px, LineStyle.Solid, Color("#ddd"))
                            property("box-shadow", "0px 2px 8px rgba(0,0,0,0.1)")
                        }
                    }) {
                        H3(attrs = {
                            style {
                                marginTop(0.px)
                                marginBottom(16.px)
                                fontSize(18.px)
                                color(Color("#666"))
                            }
                        }) {
                            Text("Baseline (no RAG)")
                        }
                        Div(attrs = {
                            style {
                                padding(16.px)
                                backgroundColor(Color("#f9f9f9"))
                                borderRadius(4.px)
                                property("line-height", "1.6")
                                whiteSpace("pre-wrap")
                                fontSize(14.px)
                            }
                        }) {
                            Text(result.baselineAnswer)
                        }
                    }
                    
                    // RAG raw answer
                    Div(attrs = {
                        style {
                            flex(1)
                            padding(20.px)
                            backgroundColor(Color.white)
                            borderRadius(8.px)
                            border(2.px, LineStyle.Solid, Color("#ff9800"))
                            property("box-shadow", "0px 2px 8px rgba(255,152,0,0.2)")
                        }
                    }) {
                        H3(attrs = {
                            style {
                                marginTop(0.px)
                                marginBottom(16.px)
                                fontSize(18.px)
                                color(Color("#ff9800"))
                            }
                        }) {
                            Text("RAG Raw (topK=${result.usedChunksRaw.size})")
                        }
                        Div(attrs = {
                            style {
                                padding(16.px)
                                backgroundColor(Color("#fff3e0"))
                                borderRadius(4.px)
                                property("line-height", "1.6")
                                whiteSpace("pre-wrap")
                                fontSize(14.px)
                            }
                        }) {
                            Text(result.ragRawAnswer)
                        }
                    }
                    
                    // RAG filtered answer
                    Div(attrs = {
                        style {
                            flex(1)
                            padding(20.px)
                            backgroundColor(Color.white)
                            borderRadius(8.px)
                            border(2.px, LineStyle.Solid, Color("#4caf50"))
                            property("box-shadow", "0px 2px 8px rgba(76,175,80,0.2)")
                        }
                    }) {
                        H3(attrs = {
                            style {
                                marginTop(0.px)
                                marginBottom(16.px)
                                fontSize(18.px)
                                color(Color("#4caf50"))
                            }
                        }) {
                            Text("RAG Filtered (${result.usedChunksFiltered.size} chunks)")
                        }
                        if (result.filterEnabled) {
                            Span(attrs = {
                                style {
                                    fontSize(12.px)
                                    color(Color("#666"))
                                    backgroundColor(Color("#e8f5e9"))
                                    padding(4.px, 8.px)
                                    borderRadius(4.px)
                                    marginBottom(8.px)
                                    display(DisplayStyle.InlineBlock)
                                }
                            }) {
                                Text("Threshold: ${(kotlin.math.round(result.minSimilarity * 1000) / 1000.0).toString()}")
                            }
                        }
                        Div(attrs = {
                            style {
                                padding(16.px)
                                backgroundColor(Color("#f1f8e9"))
                                borderRadius(4.px)
                                property("line-height", "1.6")
                                whiteSpace("pre-wrap")
                                fontSize(14.px)
                                marginTop(8.px)
                            }
                        }) {
                            Text(result.ragFilteredAnswer)
                        }
                    }
                }
                
                // Used chunks - Raw
                if (result.usedChunksRaw.isNotEmpty()) {
                    Div(attrs = {
                        style {
                            marginTop(24.px)
                        }
                    }) {
                        H3(attrs = {
                            style {
                                marginBottom(16.px)
                                fontSize(20.px)
                                color(Color("#ff9800"))
                            }
                        }) {
                            Text("Context Chunks (Raw topK=${result.usedChunksRaw.size})")
                        }
                        
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                flexDirection(FlexDirection.Column)
                                gap(12.px)
                            }
                        }) {
                            result.usedChunksRaw.forEachIndexed { index, chunk ->
                                val isFiltered = !result.usedChunksFiltered.any { it.chunkId == chunk.chunkId }
                                Div(attrs = {
                                    style {
                                        padding(16.px)
                                        backgroundColor(if (isFiltered && result.filterEnabled) Color("#ffebee") else Color.white)
                                        borderRadius(8.px)
                                        border(1.px, LineStyle.Solid, if (isFiltered && result.filterEnabled) Color("#f44336") else Color("#ddd"))
                                        property("box-shadow", "0px 1px 4px rgba(0,0,0,0.05)")
                                        opacity(if (isFiltered && result.filterEnabled) 0.6 else 1.0)
                                    }
                                }) {
                                    Div(attrs = {
                                        style {
                                            display(DisplayStyle.Flex)
                                            flexDirection(FlexDirection.Row)
                                            justifyContent(JustifyContent.SpaceBetween)
                                            alignItems(AlignItems.Center)
                                            marginBottom(8.px)
                                        }
                                    }) {
                                        H4(attrs = {
                                            style {
                                                margin(0.px)
                                                fontSize(16.px)
                                                fontWeight("bold")
                                            }
                                        }) {
                                            Text("${index + 1}. ${chunk.title}")
                                        }
                                        Span(attrs = {
                                            style {
                                                fontSize(12.px)
                                                color(Color("#666"))
                                                backgroundColor(if (chunk.score >= result.minSimilarity) Color("#c8e6c9") else Color("#ffcdd2"))
                                                padding(4.px, 8.px)
                                                borderRadius(4.px)
                                            }
                                        }) {
                                            Text("Score: ${(kotlin.math.round(chunk.score * 1000) / 1000.0).toString()}")
                                        }
                                    }
                                    if (isFiltered && result.filterEnabled) {
                                        Span(attrs = {
                                            style {
                                                fontSize(12.px)
                                                color(Color("#d32f2f"))
                                                fontWeight("bold")
                                                marginBottom(4.px)
                                                display(DisplayStyle.Block)
                                            }
                                        }) {
                                            Text("⚠ Filtered out (score < ${(kotlin.math.round(result.minSimilarity * 1000) / 1000.0).toString()})")
                                        }
                                    }
                                    Div(attrs = {
                                        style {
                                            fontSize(14.px)
                                            color(Color("#555"))
                                            property("line-height", "1.5")
                                            fontFamily("monospace")
                                            whiteSpace("pre-wrap")
                                        }
                                    }) {
                                        Text(chunk.snippet)
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Used chunks - Filtered
                if (result.usedChunksFiltered.isNotEmpty()) {
                    Div(attrs = {
                        style {
                            marginTop(24.px)
                        }
                    }) {
                        H3(attrs = {
                            style {
                                marginBottom(16.px)
                                fontSize(20.px)
                                color(Color("#4caf50"))
                            }
                        }) {
                            Text("Context Chunks (After Filter=${result.usedChunksFiltered.size})")
                        }
                        
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                flexDirection(FlexDirection.Column)
                                gap(12.px)
                            }
                        }) {
                            result.usedChunksFiltered.forEachIndexed { index, chunk ->
                                Div(attrs = {
                                    style {
                                        padding(16.px)
                                        backgroundColor(Color.white)
                                        borderRadius(8.px)
                                        border(1.px, LineStyle.Solid, Color("#4caf50"))
                                        property("box-shadow", "0px 1px 4px rgba(76,175,80,0.2)")
                                    }
                                }) {
                                    Div(attrs = {
                                        style {
                                            display(DisplayStyle.Flex)
                                            flexDirection(FlexDirection.Row)
                                            justifyContent(JustifyContent.SpaceBetween)
                                            alignItems(AlignItems.Center)
                                            marginBottom(8.px)
                                        }
                                    }) {
                                        H4(attrs = {
                                            style {
                                                margin(0.px)
                                                fontSize(16.px)
                                                fontWeight("bold")
                                            }
                                        }) {
                                            Text("${index + 1}. ${chunk.title}")
                                        }
                                        Span(attrs = {
                                            style {
                                                fontSize(12.px)
                                                color(Color("#666"))
                                                backgroundColor(Color("#c8e6c9"))
                                                padding(4.px, 8.px)
                                                borderRadius(4.px)
                                            }
                                        }) {
                                            Text("Score: ${(kotlin.math.round(chunk.score * 1000) / 1000.0).toString()}")
                                        }
                                    }
                                    Div(attrs = {
                                        style {
                                            fontSize(14.px)
                                            color(Color("#555"))
                                            property("line-height", "1.5")
                                            fontFamily("monospace")
                                            whiteSpace("pre-wrap")
                                        }
                                    }) {
                                        Text(chunk.snippet)
                                    }
                                }
                            }
                        }
                    }
                } else if (result.usedChunksRaw.isEmpty()) {
                    Div(attrs = {
                        style {
                            padding(16.px)
                            backgroundColor(Color("#fff3cd"))
                            borderRadius(8.px)
                            border(1.px, LineStyle.Solid, Color("#ffc107"))
                            marginTop(24.px)
                        }
                    }) {
                        Text("No chunks found. Make sure you have indexed wiki articles first.")
                    }
                }
            }
        }
    }
}

