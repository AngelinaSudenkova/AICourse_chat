import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import models.*

@Composable
fun WikiSearchView(viewModel: WikiSearchViewModel) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            padding(24.px)
            gap(24.px)
            property("overflow-y", "auto")
            height(100.percent)
            maxWidth(1200.px)
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
            Text("📚 Wiki Search")
        }
        
        P(attrs = {
            style {
                marginBottom(24.px)
                color(Color("#666"))
            }
        }) {
            Text("Fetch Wikipedia articles, index them with embeddings, and search semantically across the indexed content.")
        }
        
        // Fetch & Index section
        Div(attrs = {
            style {
                padding(24.px)
                backgroundColor(Color("#f5f5f5"))
                borderRadius(8.px)
            }
        }) {
            H2(attrs = {
                style {
                    fontSize(20.px)
                    marginBottom(16.px)
                }
            }) {
                Text("Fetch & Index")
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
                    Text("Topic to fetch (e.g. Quantum computing)")
                }
                Input(type = InputType.Text, attrs = {
                    value(viewModel.topicToFetch)
                    onInput { ev ->
                        viewModel.topicToFetch = (ev.target as? org.w3c.dom.HTMLInputElement)?.value ?: ""
                    }
                    placeholder("e.g., Quantum computing")
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
                if (viewModel.isLoadingFetch || viewModel.isLoadingIndex) {
                    disabled()
                }
                onClick { viewModel.fetchAndIndex() }
                style {
                    padding(12.px, 24.px)
                    fontSize(16.px)
                    backgroundColor(Color("#007bff"))
                    color(Color.white)
                    border(0.px)
                    borderRadius(4.px)
                    cursor("pointer")
                }
            }) {
                Text(if (viewModel.isLoadingFetch || viewModel.isLoadingIndex) "Fetching & Indexing..." else "Fetch & Index")
            }
            
            if (viewModel.fetchStatus != null) {
                Div(attrs = {
                    style {
                        marginTop(16.px)
                        padding(12.px)
                        backgroundColor(Color("#d4edda"))
                        borderRadius(4.px)
                        color(Color("#155724"))
                    }
                }) {
                    Text(viewModel.fetchStatus ?: "")
                }
            }
        }
        
        // Search section
        Div(attrs = {
            style {
                padding(24.px)
                backgroundColor(Color("#f5f5f5"))
                borderRadius(8.px)
            }
        }) {
            H2(attrs = {
                style {
                    fontSize(20.px)
                    marginBottom(16.px)
                }
            }) {
                Text("Search")
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
                    Text("Search query")
                }
                Input(type = InputType.Text, attrs = {
                    value(viewModel.searchQuery)
                    onInput { ev ->
                        viewModel.searchQuery = (ev.target as? org.w3c.dom.HTMLInputElement)?.value ?: ""
                    }
                    placeholder("e.g., What is quantum entanglement?")
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
                if (viewModel.isLoadingSearch) {
                    disabled()
                }
                onClick { viewModel.search() }
                style {
                    padding(12.px, 24.px)
                    fontSize(16.px)
                    backgroundColor(Color("#28a745"))
                    color(Color.white)
                    border(0.px)
                    borderRadius(4.px)
                    cursor("pointer")
                }
            }) {
                Text(if (viewModel.isLoadingSearch) "Searching..." else "Search")
            }
        }
        
        // Error display
        if (viewModel.error != null) {
            Div(attrs = {
                style {
                    padding(16.px)
                    backgroundColor(Color("#f8d7da"))
                    borderRadius(4.px)
                    color(Color("#721c24"))
                }
            }) {
                Text(viewModel.error ?: "")
            }
        }
        
        // Search results
        if (viewModel.searchResults != null) {
            Div(attrs = {
                style {
                    marginTop(24.px)
                }
            }) {
                H2(attrs = {
                    style {
                        fontSize(24.px)
                        marginBottom(16.px)
                    }
                }) {
                    Text("Search Results")
                }
                
                if (viewModel.searchResults!!.results.isEmpty()) {
                    P(attrs = {
                        style {
                            color(Color("#666"))
                        }
                    }) {
                        Text("No results found.")
                    }
                } else {
                    viewModel.searchResults!!.results.forEach { result ->
                        Div(attrs = {
                            style {
                                padding(16.px)
                                marginBottom(16.px)
                                backgroundColor(Color.white)
                                borderRadius(8.px)
                                border(1.px, LineStyle.Solid, Color("#ddd"))
                            }
                        }) {
                            Div(attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    justifyContent(JustifyContent.SpaceBetween)
                                    alignItems(AlignItems.Center)
                                    marginBottom(8.px)
                                }
                            }) {
                                H3(attrs = {
                                    style {
                                        fontSize(18.px)
                                        margin(0.px)
                                    }
                                }) {
                                    Text(result.title)
                                }
                                Span(attrs = {
                                    style {
                                        fontSize(12.px)
                                        color(Color("#666"))
                                    }
                                }) {
                                    Text("Score: ${(result.score * 1000).toInt() / 1000.0}")
                                }
                            }
                            
                            P(attrs = {
                                style {
                                    margin(0.px)
                                    color(Color("#333"))
                                    lineHeight(1.6.cssRem)
                                }
                            }) {
                                Text(result.snippet)
                            }
                            
                            Span(attrs = {
                                style {
                                    fontSize(12.px)
                                    color(Color("#666"))
                                    marginTop(8.px)
                                    display(DisplayStyle.Block)
                                }
                            }) {
                                Text("Chunk ID: ${result.chunkId} | Article ID: ${result.articleId}")
                            }
                        }
                    }
                }
            }
        }
    }
}

