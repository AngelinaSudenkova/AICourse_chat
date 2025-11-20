import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import NewsViewModel
import models.NewsArticle

@Composable
fun NewsNotificationPanel(
    viewModel: NewsViewModel,
    isOpen: Boolean,
    onClose: () -> Unit
) {
    if (!isOpen) return
    
    // Load latest news when panel opens
    LaunchedEffect(isOpen) {
        if (isOpen) {
            viewModel.loadLatest()
        }
    }
    
    // Backdrop
    Div(attrs = {
        style {
            position(Position.Fixed)
            top(0.px)
            left(0.px)
            right(0.px)
            bottom(0.px)
            backgroundColor(Color("rgba(0, 0, 0, 0.5)"))
            property("z-index", "1000")
            display(DisplayStyle.Flex)
            alignItems(AlignItems.Center)
            justifyContent(JustifyContent.Center)
        }
        onClick { onClose() }
    }) {
        // Panel content
        Div(attrs = {
            style {
                backgroundColor(Color("var(--surface)"))
                borderRadius(12.px)
                padding(24.px)
                maxWidth(600.px)
                width(90.percent)
                maxHeight(80.vh)
                property("overflow-y", "auto")
                property("box-shadow", "0px 8px 24px rgba(0, 0, 0, 0.2)")
                position(Position.Relative)
            }
            onClick { ev ->
                ev.stopPropagation()
            }
        }) {
            // Close button
            Button(attrs = {
                style {
                    position(Position.Absolute)
                    top(8.px)
                    right(8.px)
                    padding(8.px)
                    fontSize(20.px)
                    border(0.px, LineStyle.None, Color.transparent)
                    backgroundColor(Color.transparent)
                    cursor("pointer")
                    color(Color("var(--text)"))
                    opacity(0.6)
                }
                onClick { onClose() }
            }) {
                Text("✖")
            }
            
            // Header
            H2(attrs = {
                style {
                    fontSize(20.px)
                    fontWeight(700)
                    color(Color("var(--text)"))
                    margin(0.px, 0.px, 16.px, 0.px)
                }
            }) {
                Text("📰 News Summary")
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
                        fontSize(14.px)
                        marginBottom(16.px)
                    }
                }) {
                    Text(viewModel.error!!)
                }
            }
            
            // Last updated
            if (viewModel.fetchedAt.isNotEmpty()) {
                Div(attrs = {
                    style {
                        fontSize(12.px)
                        color(Color("var(--text)"))
                        opacity(0.6)
                        marginBottom(16.px)
                    }
                }) {
                    Text("Last updated: ${formatDateForNotification(viewModel.fetchedAt)}")
                }
            }
            
            // Refresh button
            Button(attrs = {
                classes(AppStylesheet.button)
                style {
                    marginBottom(20.px)
                    padding(10.px, 20.px)
                    fontSize(14.px)
                }
                onClick { viewModel.refresh() }
                if (viewModel.isRefreshing) disabled()
            }) {
                Text(if (viewModel.isRefreshing) "🔄 Refreshing..." else "🔄 Refresh now")
            }
            
            // AI Summary
            if (viewModel.aiSummary != null) {
                Div(attrs = {
                    style {
                        padding(16.px)
                        backgroundColor(Color("var(--background)"))
                        border(1.px, LineStyle.Solid, Color("var(--border)"))
                        borderRadius(8.px)
                        marginBottom(20.px)
                    }
                }) {
                    H3(attrs = {
                        style {
                            fontSize(16.px)
                            fontWeight(600)
                            color(Color("var(--text)"))
                            margin(0.px, 0.px, 12.px, 0.px)
                        }
                    }) {
                        Text("🤖 AI Summary")
                    }
                    Div(attrs = {
                        style {
                            fontSize(14.px)
                            color(Color("var(--text)"))
                            property("line-height", "1.6")
                            whiteSpace("pre-wrap")
                        }
                    }) {
                        Text(viewModel.aiSummary!!)
                    }
                }
            } else if (viewModel.isLoading) {
                Div(attrs = {
                    style {
                        padding(20.px)
                        textAlign("center")
                        color(Color("var(--text)"))
                        opacity(0.6)
                    }
                }) {
                    Text("Loading news summary...")
                }
            } else {
                Div(attrs = {
                    style {
                        padding(20.px)
                        textAlign("center")
                        color(Color("var(--text)"))
                        opacity(0.6)
                    }
                }) {
                    Text("No summary available. Click refresh to generate one.")
                }
            }
            
            // Top headlines (3-5 items)
            if (viewModel.articles.isNotEmpty()) {
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        gap(12.px)
                    }
                }) {
                    H3(attrs = {
                        style {
                            fontSize(16.px)
                            fontWeight(600)
                            color(Color("var(--text)"))
                            margin(0.px, 0.px, 8.px, 0.px)
                        }
                    }) {
                        Text("Top Headlines")
                    }
                    
                    viewModel.articles.take(5).forEach { article ->
                        CompactNewsItem(article)
                    }
                }
            }
        }
    }
}

@Composable
fun CompactNewsItem(article: NewsArticle) {
    Div(attrs = {
        style {
            padding(12.px)
            backgroundColor(Color("var(--background)"))
            border(1.px, LineStyle.Solid, Color("var(--border)"))
            borderRadius(6.px)
            cursor("pointer")
        }
        onClick {
            if (article.url.isNotEmpty()) {
                js("window.open(arguments[0], '_blank')").invoke(article.url)
            }
        }
    }) {
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                gap(4.px)
            }
        }) {
            // Title
            Span(attrs = {
                style {
                    fontSize(14.px)
                    fontWeight(600)
                    color(Color("var(--text)"))
                }
            }) {
                Text(article.title)
            }
            
            // Source and time
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    gap(8.px)
                    fontSize(12.px)
                    color(Color("var(--text)"))
                    opacity(0.6)
                }
            }) {
                Span {
                    Text("• ${article.source}")
                }
                Span {
                    Text(formatTime(article.publishedAt))
                }
            }
        }
    }
}

fun formatTime(dateString: String): String {
    return try {
        val date = kotlin.js.Date(dateString)
        val hours = date.getHours()
        val minutes = date.getMinutes()
        val hoursStr = if (hours < 10) "0$hours" else hours.toString()
        val minutesStr = if (minutes < 10) "0$minutes" else minutes.toString()
        "$hoursStr:$minutesStr"
    } catch (e: Exception) {
        ""
    }
}

fun formatDateForNotification(dateString: String): String {
    return try {
        val date = kotlin.js.Date(dateString)
        date.toLocaleString()
    } catch (e: Exception) {
        dateString
    }
}

