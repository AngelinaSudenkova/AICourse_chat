import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import models.*

@Composable
fun TutorView(viewModel: TutorViewModel) {
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
            Text("🎓 Learning Tutor")
        }
        
        P(attrs = {
            style {
                marginBottom(24.px)
                color(Color("#666"))
            }
        }) {
            Text("Enter a topic and get a comprehensive learning guide with Wikipedia summary, YouTube videos, AI explanation, and a saved Notion study note.")
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
                    Text("Topic")
                }
                Input(type = InputType.Text, attrs = {
                    value(viewModel.topic)
                    onInput { ev ->
                        viewModel.topic = (ev.target as? org.w3c.dom.HTMLInputElement)?.value ?: ""
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
                    Text("Level (optional)")
                }
                Select(attrs = {
                    attr("value", viewModel.level ?: "")
                    onChange { ev ->
                        val value = (ev.target as? org.w3c.dom.HTMLSelectElement)?.value ?: ""
                        viewModel.level = if (value.isBlank()) null else value
                    }
                    style {
                        width(100.percent)
                        padding(12.px)
                        fontSize(16.px)
                        borderRadius(4.px)
                        border(1.px, LineStyle.Solid, Color("#ddd"))
                    }
                }) {
                    Option(value = "", attrs = {}) { Text("Select level...") }
                    Option(value = "beginner", attrs = {}) { Text("Beginner") }
                    Option(value = "intermediate", attrs = {}) { Text("Intermediate") }
                    Option(value = "advanced", attrs = {}) { Text("Advanced") }
                }
            }
            
            Button(attrs = {
                onClick { viewModel.teach() }
                if (viewModel.isLoading || viewModel.topic.isBlank()) {
                    disabled()
                }
                style {
                    padding(12.px, 24.px)
                    fontSize(16.px)
                    backgroundColor(if (viewModel.isLoading || viewModel.topic.isBlank()) Color("#ccc") else Color("#007bff"))
                    color(Color.white)
                    border(0.px)
                    borderRadius(4.px)
                    cursor("pointer")
                    if (viewModel.isLoading || viewModel.topic.isBlank()) {
                        cursor("not-allowed")
                    }
                }
            }) {
                Text(if (viewModel.isLoading) "Teaching..." else "Teach me")
            }
        }
        
        // Error display
        viewModel.error?.let { error ->
            Div(attrs = {
                style {
                    padding(16.px)
                    backgroundColor(Color("#fee"))
                    color(Color("#c33"))
                    borderRadius(4.px)
                }
            }) {
                Text(error)
            }
        }
        
        // Response display
        viewModel.response?.let { response ->
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    gap(24.px)
                }
            }) {
                // Topic header
                H2(attrs = {
                    style {
                        fontSize(24.px)
                        marginBottom(16.px)
                    }
                }) {
                    Text("Learning Guide: ${response.topic}")
                }
                
                // Wikipedia summary
                Div(attrs = {
                    style {
                        padding(20.px)
                        backgroundColor(Color.white)
                        borderRadius(8.px)
                        border(1.px, LineStyle.Solid, Color("#ddd"))
                    }
                }) {
                    H3(attrs = {
                        style {
                            fontSize(20.px)
                            marginBottom(12.px)
                        }
                    }) {
                        Text("📚 Wikipedia Summary")
                    }
                    A(attrs = {
                        href(response.wikipedia.url)
                        attr("target", "_blank")
                        attr("rel", "noopener noreferrer")
                        style {
                            color(Color("#007bff"))
                            textDecoration("none")
                            fontSize(18.px)
                            fontWeight("bold")
                            marginBottom(8.px)
                            display(DisplayStyle.Block)
                        }
                    }) {
                        Text(response.wikipedia.title)
                    }
                    P(attrs = {
                        style {
                            marginTop(8.px)
                            property("line-height", "1.6")
                            color(Color("#333"))
                        }
                    }) {
                        Text(response.wikipedia.summary)
                    }
                }
                
                // YouTube videos
                if (response.videos.isNotEmpty()) {
                    Div(attrs = {
                        style {
                            padding(20.px)
                            backgroundColor(Color.white)
                            borderRadius(8.px)
                            border(1.px, LineStyle.Solid, Color("#ddd"))
                        }
                    }) {
                        H3(attrs = {
                            style {
                                fontSize(20.px)
                                marginBottom(12.px)
                            }
                        }) {
                            Text("🎥 YouTube Videos")
                        }
                        response.videos.forEach { video ->
                            Div(attrs = {
                                style {
                                    marginBottom(16.px)
                                    padding(12.px)
                                    backgroundColor(Color("#f9f9f9"))
                                    borderRadius(4.px)
                                }
                            }) {
                                A(attrs = {
                                    href(video.url)
                                    attr("target", "_blank")
                                    attr("rel", "noopener noreferrer")
                                    style {
                                        color(Color("#007bff"))
                                        textDecoration("none")
                                        fontSize(16.px)
                                        fontWeight("bold")
                                        display(DisplayStyle.Block)
                                        marginBottom(4.px)
                                    }
                                }) {
                                    Text(video.title)
                                }
                                video.channel?.let { channel ->
                                    Span(attrs = {
                                        style {
                                            fontSize(14.px)
                                            color(Color("#666"))
                                        }
                                    }) {
                                        Text("Channel: $channel")
                                    }
                                }
                                video.duration?.let { duration ->
                                    Span(attrs = {
                                        style {
                                            fontSize(14.px)
                                            color(Color("#666"))
                                            marginLeft(8.px)
                                        }
                                    }) {
                                        Text("Duration: $duration")
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Simple explanation
                Div(attrs = {
                    style {
                        padding(20.px)
                        backgroundColor(Color("#f0f8ff"))
                        borderRadius(8.px)
                        border(1.px, LineStyle.Solid, Color("#b3d9ff"))
                    }
                }) {
                    H3(attrs = {
                        style {
                            fontSize(20.px)
                            marginBottom(12.px)
                        }
                    }) {
                        Text("💡 Simple Explanation")
                    }
                    P(attrs = {
                        style {
                            property("line-height", "1.8")
                            color(Color("#333"))
                            whiteSpace("pre-wrap")
                        }
                    }) {
                        Text(response.simpleExplanation)
                    }
                }
                
                // Key points
                Div(attrs = {
                    style {
                        padding(20.px)
                        backgroundColor(Color.white)
                        borderRadius(8.px)
                        border(1.px, LineStyle.Solid, Color("#ddd"))
                    }
                }) {
                    H3(attrs = {
                        style {
                            fontSize(20.px)
                            marginBottom(12.px)
                        }
                    }) {
                        Text("🔑 Key Points")
                    }
                    Ul(attrs = {
                        style {
                            marginLeft(20.px)
                        }
                    }) {
                        response.studyNote.keyPoints.forEach { point ->
                            Li(attrs = {
                                style {
                                    marginBottom(8.px)
                                    property("line-height", "1.6")
                                }
                            }) {
                                Text(point)
                            }
                        }
                    }
                }
                
                // Notion note status
                response.studyNote.notionPageId?.let { pageId ->
                    Div(attrs = {
                        style {
                            padding(16.px)
                            backgroundColor(Color("#e8f5e9"))
                            borderRadius(8.px)
                            border(1.px, LineStyle.Solid, Color("#4caf50"))
                        }
                    }) {
                        Span(attrs = {
                            style {
                                fontSize(16.px)
                                fontWeight("bold")
                                color(Color("#2e7d32"))
                            }
                        }) {
                            Text("✅ Saved to Notion")
                        }
                        Br()
                        A(attrs = {
                            href("https://www.notion.so/${pageId.replace("-", "")}")
                            attr("target", "_blank")
                            attr("rel", "noopener noreferrer")
                            style {
                                color(Color("#007bff"))
                                textDecoration("none")
                                marginTop(8.px)
                                display(DisplayStyle.Block)
                            }
                        }) {
                            Text("Open study note in Notion →")
                        }
                    }
                }
            }
        }
    }
}

