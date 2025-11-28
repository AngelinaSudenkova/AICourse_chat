import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import models.*

@Composable
fun RagCitedView(viewModel: RagCitedViewModel) {
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
            Text("📎 RAG with Sources")
        }
        
        P(attrs = {
            style {
                marginBottom(24.px)
                color(Color("#666"))
            }
        }) {
            Text("Ask questions and get answers with mandatory citations to sources from the wiki index. Each answer includes inline citations [1], [2], etc. and a Sources section.")
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
            
            // Filtering controls
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
            
            // Fallback controls
            Div(attrs = {
                style {
                    marginBottom(16.px)
                    padding(16.px)
                    backgroundColor(Color("#fff3e0"))
                    borderRadius(4.px)
                    border(1.px, LineStyle.Solid, Color("#ff9800"))
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
                        checked(viewModel.allowModelFallback)
                        onChange { ev ->
                            viewModel.allowModelFallback = (ev.target as? org.w3c.dom.HTMLInputElement)?.checked ?: false
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
                        Text("Allow fallback if wiki has no relevant matches")
                    }
                }
                
                if (viewModel.allowModelFallback) {
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
                            Text("Minimum similarity score to use RAG: ${(kotlin.math.round(viewModel.minBestScoreForRag * 1000) / 1000.0).toString()}")
                        }
                        Input(type = InputType.Range, attrs = {
                            value(viewModel.minBestScoreForRag.toString())
                            attr("min", "0.0")
                            attr("max", "1.0")
                            attr("step", "0.05")
                            onInput { ev ->
                                val value = (ev.target as? org.w3c.dom.HTMLInputElement)?.value?.toDoubleOrNull() ?: 0.25
                                viewModel.minBestScoreForRag = value.coerceIn(0.0, 1.0)
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
                        P(attrs = {
                            style {
                                fontSize(12.px)
                                color(Color("#666"))
                                marginTop(8.px)
                                marginBottom(0.px)
                            }
                        }) {
                            Text("If the best matching chunk score is below this threshold, the system will use the base model without RAG.")
                        }
                    }
                }
            }
            
            // Auto-fetch Wikipedia option
            Div(attrs = {
                style {
                    marginBottom(16.px)
                    padding(16.px)
                    backgroundColor(Color("#e3f2fd"))
                    borderRadius(4.px)
                    border(1.px, LineStyle.Solid, Color("#2196f3"))
                }
            }) {
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        alignItems(AlignItems.Center)
                        marginBottom(8.px)
                    }
                }) {
                    Input(type = InputType.Checkbox, attrs = {
                        checked(viewModel.autoFetchWiki)
                        onChange { ev ->
                            viewModel.autoFetchWiki = (ev.target as? org.w3c.dom.HTMLInputElement)?.checked ?: false
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
                        Text("Auto-fetch Wikipedia articles when local index has no matches")
                    }
                }
                P(attrs = {
                    style {
                        fontSize(12.px)
                        color(Color("#666"))
                        marginTop(4.px)
                        marginBottom(0.px)
                        marginLeft(28.px)
                    }
                }) {
                    Text("If enabled, when the local wiki index doesn't have relevant articles, the system will automatically fetch and index relevant Wikipedia articles based on your question before answering.")
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
                Text(if (viewModel.loading) "Generating answer..." else "Ask with Sources")
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
                
                // Answer with citations
                Div(attrs = {
                    style {
                        padding(24.px)
                        backgroundColor(Color.white)
                        borderRadius(8.px)
                        border(2.px, LineStyle.Solid, if (result.labeledSources.isEmpty()) Color("#ff9800") else Color("#4caf50"))
                        property("box-shadow", if (result.labeledSources.isEmpty()) "0px 2px 8px rgba(255,152,0,0.2)" else "0px 2px 8px rgba(76,175,80,0.2)")
                    }
                }) {
                    Div(attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            justifyContent(JustifyContent.SpaceBetween)
                            alignItems(AlignItems.Center)
                            marginBottom(16.px)
                        }
                    }) {
                        H3(attrs = {
                            style {
                                marginTop(0.px)
                                marginBottom(0.px)
                                fontSize(20.px)
                                color(if (result.labeledSources.isEmpty()) Color("#ff9800") else Color("#4caf50"))
                            }
                        }) {
                            Text(if (result.labeledSources.isEmpty()) "Answer (Fallback Mode)" else "Answer with Citations")
                        }
                        if (result.labeledSources.isEmpty()) {
                            Span(attrs = {
                                style {
                                    fontSize(12.px)
                                    color(Color("#fff"))
                                    backgroundColor(Color("#ff9800"))
                                    padding(6.px, 12.px)
                                    borderRadius(4.px)
                                    fontWeight("bold")
                                }
                            }) {
                                Text("⚠ Answered without local sources (fallback mode)")
                            }
                        }
                    }
                    Div(attrs = {
                        style {
                            padding(16.px)
                            backgroundColor(if (result.labeledSources.isEmpty()) Color("#fff3e0") else Color("#f1f8e9"))
                            borderRadius(4.px)
                            property("line-height", "1.6")
                            whiteSpace("pre-wrap")
                            fontSize(15.px)
                        }
                    }) {
                        Text(result.answerWithCitations)
                    }
                }
                
                // Sources section
                if (result.labeledSources.isEmpty()) {
                    Div(attrs = {
                        style {
                            padding(16.px)
                            backgroundColor(Color("#fff3e0"))
                            borderRadius(8.px)
                            border(1.px, LineStyle.Solid, Color("#ff9800"))
                            marginTop(24.px)
                        }
                    }) {
                        P(attrs = {
                            style {
                                margin(0.px)
                                fontSize(14.px)
                                color(Color("#e65100"))
                                fontWeight("bold")
                            }
                        }) {
                            Text("⚠ No sources used — Answer generated using base model fallback")
                        }
                        P(attrs = {
                            style {
                                marginTop(8.px)
                                marginBottom(0.px)
                                fontSize(13.px)
                                color(Color("#666"))
                            }
                        }) {
                            Text("The wiki index did not contain relevant information for this question, so the system used the base language model to generate an answer without citations.")
                        }
                    }
                } else if (result.labeledSources.isNotEmpty()) {
                    Div(attrs = {
                        style {
                            marginTop(24.px)
                        }
                    }) {
                        H3(attrs = {
                            style {
                                marginBottom(16.px)
                                fontSize(20.px)
                                color(Color("#666"))
                            }
                        }) {
                            Text("Sources (${result.labeledSources.size})")
                        }
                        
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                flexDirection(FlexDirection.Column)
                                gap(12.px)
                            }
                        }) {
                            result.labeledSources.forEach { source ->
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
                                            Text("[${source.label}] ${source.title}")
                                        }
                                        Span(attrs = {
                                            style {
                                                fontSize(12.px)
                                                color(Color("#666"))
                                                backgroundColor(Color("#e8f5e9"))
                                                padding(4.px, 8.px)
                                                borderRadius(4.px)
                                            }
                                        }) {
                                            Text("Score: ${(kotlin.math.round(source.score * 1000) / 1000.0).toString()}")
                                        }
                                    }
                                    Div(attrs = {
                                        style {
                                            fontSize(14.px)
                                            color(Color("#555"))
                                            property("line-height", "1.5")
                                            fontFamily("monospace")
                                            whiteSpace("pre-wrap")
                                            paddingTop(8.px)
                                            paddingLeft(8.px)
                                            property("border-left", "3px solid #4caf50")
                                        }
                                    }) {
                                        Text(source.snippet)
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
                        Text("No sources found. Make sure you have indexed wiki articles first.")
                    }
                }
            }
        }
    }
}

