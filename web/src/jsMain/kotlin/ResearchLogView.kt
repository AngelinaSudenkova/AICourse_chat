import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import ResearchLogViewModel
import models.ResearchLogEntry

@Composable
fun ResearchLogView(viewModel: ResearchLogViewModel) {
    // Load log on first render
    LaunchedEffect(Unit) {
        viewModel.loadLog()
    }
    
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
            height(100.percent)
            property("overflow", "hidden")
        }
    }) {
        // Left sidebar - list of entries
        Div(attrs = {
            style {
                width(300.px)
                property("border-right", "1px solid var(--border)")
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                property("overflow-y", "auto")
                backgroundColor(Color("var(--surface)"))
            }
        }) {
            // Header
            Div(attrs = {
                style {
                    padding(16.px)
                    property("border-bottom", "1px solid var(--border)")
                }
            }) {
                H2(attrs = {
                    style {
                        fontSize(18.px)
                        fontWeight(600)
                        color(Color("var(--text)"))
                        margin(0.px, 0.px, 8.px, 0.px)
                    }
                }) {
                    Text("📚 Research Log")
                }
                Button(attrs = {
                    classes(AppStylesheet.button)
                    style {
                        padding(8.px, 16.px)
                        fontSize(12.px)
                    }
                    onClick { viewModel.loadLog() }
                    if (viewModel.isLoading) disabled()
                }) {
                    Text(if (viewModel.isLoading) "Loading..." else "Refresh")
                }
            }
            
            // Error display
            if (viewModel.error != null) {
                Div(attrs = {
                    style {
                        padding(12.px, 16.px)
                        backgroundColor(Color("#fee"))
                        border(1.px, LineStyle.Solid, Color("#fcc"))
                        borderRadius(8.px)
                        color(Color("#c33"))
                        fontSize(12.px)
                        margin(8.px)
                    }
                }) {
                    Text(viewModel.error!!)
                }
            }
            
            // Entries list
            if (viewModel.entries.isEmpty() && !viewModel.isLoading) {
                Div(attrs = {
                    style {
                        padding(24.px)
                        textAlign("center")
                        color(Color("var(--text)"))
                        opacity(0.6)
                        fontSize(14.px)
                    }
                }) {
                    Text("No research entries yet. Use /research command in chat to create one.")
                }
            } else {
                viewModel.entries.forEach { entry ->
                    ResearchLogEntryItem(
                        entry = entry,
                        isSelected = viewModel.selectedEntry?.filename == entry.filename,
                        onClick = { viewModel.loadFileContent(entry) }
                    )
                }
            }
        }
        
        // Right panel - content view
        Div(attrs = {
            style {
                flex(1)
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                property("overflow-y", "auto")
                padding(24.px)
            }
        }) {
            if (viewModel.selectedEntry == null) {
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        alignItems(AlignItems.Center)
                        justifyContent(JustifyContent.Center)
                        height(100.percent)
                        color(Color("var(--text)"))
                        opacity(0.6)
                    }
                }) {
                    Text("Select a research entry to view its content")
                }
            } else {
                val entry = viewModel.selectedEntry!!
                
                // Header
                Div(attrs = {
                    style {
                        marginBottom(20.px)
                    }
                }) {
                    H2(attrs = {
                        style {
                            fontSize(24.px)
                            fontWeight(700)
                            color(Color("var(--text)"))
                            margin(0.px, 0.px, 8.px, 0.px)
                        }
                    }) {
                        Text(entry.title)
                    }
                    Div(attrs = {
                        style {
                            fontSize(12.px)
                            color(Color("var(--text)"))
                            opacity(0.6)
                            marginBottom(8.px)
                        }
                    }) {
                        Text("Query: ${entry.query}")
                    }
                    Div(attrs = {
                        style {
                            fontSize(12.px)
                            color(Color("var(--text)"))
                            opacity(0.6)
                        }
                    }) {
                        Text("Created: ${formatDateForResearchLog(entry.createdAt)}")
                    }
                }
                
                // Content
                if (viewModel.isLoadingContent) {
                    Div(attrs = {
                        style {
                            padding(24.px)
                            textAlign("center")
                            color(Color("var(--text)"))
                            opacity(0.6)
                        }
                    }) {
                        Text("Loading content...")
                    }
                } else if (viewModel.selectedContent != null) {
                    Div(attrs = {
                        style {
                            padding(20.px)
                            backgroundColor(Color("var(--surface)"))
                            border(1.px, LineStyle.Solid, Color("var(--border)"))
                            borderRadius(8.px)
                            fontSize(14.px)
                            color(Color("var(--text)"))
                            property("line-height", "1.6")
                            whiteSpace("pre-wrap")
                        }
                        classes("markdown-content")
                    }) {
                        MarkdownText(viewModel.selectedContent!!)
                    }
                } else if (viewModel.error != null) {
                    Div(attrs = {
                        style {
                            padding(12.px, 16.px)
                            backgroundColor(Color("#fee"))
                            border(1.px, LineStyle.Solid, Color("#fcc"))
                            borderRadius(8.px)
                            color(Color("#c33"))
                            fontSize(14.px)
                        }
                    }) {
                        Text(viewModel.error!!)
                    }
                }
            }
        }
    }
}

@Composable
fun ResearchLogEntryItem(
    entry: ResearchLogEntry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Div(attrs = {
        style {
            padding(12.px, 16.px)
            backgroundColor(if (isSelected) Color("var(--background)") else Color.transparent)
            property("border-bottom", "1px solid var(--border)")
            cursor("pointer")
        }
        onClick { onClick() }
    }) {
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                gap(4.px)
            }
        }) {
            Span(attrs = {
                style {
                    fontSize(14.px)
                    fontWeight(if (isSelected) 600 else 400)
                    color(Color("var(--text)"))
                }
            }) {
                Text(entry.title)
            }
            Span(attrs = {
                style {
                    fontSize(12.px)
                    color(Color("var(--text)"))
                    opacity(0.6)
                }
            }) {
                Text(formatDateForResearchLog(entry.createdAt))
            }
        }
    }
}

fun formatDateForResearchLog(timestamp: Long): String {
    return try {
        val date = kotlin.js.Date(timestamp.toDouble())
        date.toLocaleString()
    } catch (e: Exception) {
        timestamp.toString()
    }
}

