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
            Text("Compare answers with and without RAG (Retrieval-Augmented Generation). Enter a question to see how wiki context improves the answer.")
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
                Text(if (viewModel.loading) "Comparing..." else "Compare RAG vs Baseline")
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
        
        // Results display
        viewModel.result?.let { result ->
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
                
                // Two-column comparison
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Row)
                        gap(24.px)
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
                                fontSize(20.px)
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
                            }
                        }) {
                            Text(result.baselineAnswer)
                        }
                    }
                    
                    // RAG answer
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
                                fontSize(20.px)
                                color(Color("#4caf50"))
                            }
                        }) {
                            Text("With RAG (Wiki context)")
                        }
                        Div(attrs = {
                            style {
                                padding(16.px)
                                backgroundColor(Color("#f1f8e9"))
                                borderRadius(4.px)
                                property("line-height", "1.6")
                                whiteSpace("pre-wrap")
                            }
                        }) {
                            Text(result.ragAnswer)
                        }
                    }
                }
                
                // Used chunks
                if (result.usedChunks.isNotEmpty()) {
                    Div(attrs = {
                        style {
                            marginTop(24.px)
                        }
                    }) {
                        H3(attrs = {
                            style {
                                marginBottom(16.px)
                                fontSize(20.px)
                            }
                        }) {
                            Text("Used Context Chunks (${result.usedChunks.size})")
                        }
                        
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                flexDirection(FlexDirection.Column)
                                gap(12.px)
                            }
                        }) {
                            result.usedChunks.forEachIndexed { index, chunk ->
                                Div(attrs = {
                                    style {
                                        padding(16.px)
                                        backgroundColor(Color.white)
                                        borderRadius(8.px)
                                        border(1.px, LineStyle.Solid, Color("#ddd"))
                                        property("box-shadow", "0px 1px 4px rgba(0,0,0,0.05)")
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
                                                backgroundColor(Color("#f0f0f0"))
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
                } else {
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

