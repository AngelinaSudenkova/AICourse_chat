import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import NewsViewModel
import models.NewsArticle

@Composable
fun NewsView(viewModel: NewsViewModel) {
    // Load latest news on first render
    LaunchedEffect(Unit) {
        viewModel.loadLatest()
    }
    
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            padding(24.px)
            gap(24.px)
            property("overflow-y", "auto")
            height(100.percent)
        }
    }) {
        // Header
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                gap(12.px)
            }
        }) {
            H2(attrs = {
                style {
                    fontSize(24.px)
                    fontWeight(700)
                    color(Color("var(--text)"))
                    margin(0.px)
                }
            }) {
                Text("📰 News")
            }
            P(attrs = {
                style {
                    fontSize(14.px)
                    color(Color("var(--text)"))
                    opacity(0.7)
                    margin(0.px)
                }
            }) {
                Text("Latest news headlines and AI-powered summaries.")
            }
        }

        // Refresh button
        Button(attrs = {
            classes(AppStylesheet.button)
            style {
                alignSelf(AlignSelf.FlexStart)
                padding(12.px, 24.px)
                fontSize(14.px)
            }
            onClick { viewModel.refresh() }
            if (viewModel.isRefreshing) disabled()
        }) {
            Text(if (viewModel.isRefreshing) "🔄 Refreshing..." else "🔄 Refresh & Ask AI Now")
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
                }
            }) {
                Text(viewModel.error!!)
            }
        }

        // Last updated timestamp
        if (viewModel.fetchedAt.isNotEmpty()) {
            Div(attrs = {
                style {
                    fontSize(12.px)
                    color(Color("var(--text)"))
                    opacity(0.6)
                }
            }) {
                Text("Last updated: ${formatDate(viewModel.fetchedAt)}")
            }
        }

        // AI Summary Card
        if (viewModel.aiSummary != null) {
            Div(attrs = {
                style {
                    padding(20.px)
                    backgroundColor(Color("var(--surface)"))
                    border(1.px, LineStyle.Solid, Color("var(--border)"))
                    borderRadius(8.px)
                }
            }) {
                H3(attrs = {
                    style {
                        fontSize(18.px)
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
        } else if (!viewModel.isLoading && !viewModel.isRefreshing && viewModel.error == null) {
            Div(attrs = {
                style {
                    padding(20.px)
                    backgroundColor(Color("var(--surface)"))
                    border(1.px, LineStyle.Solid, Color("var(--border)"))
                    borderRadius(8.px)
                    textAlign("center")
                    color(Color("var(--text)"))
                    opacity(0.6)
                }
            }) {
                Text("No summary yet — click refresh to generate one.")
            }
        }

        // News Articles List
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
                        fontSize(18.px)
                        fontWeight(600)
                        color(Color("var(--text)"))
                        margin(0.px)
                    }
                }) {
                    Text("Headlines (${viewModel.articles.size})")
                }
                
                viewModel.articles.forEach { article ->
                    NewsArticleCard(article)
                }
            }
        } else if (viewModel.isLoading && viewModel.articles.isEmpty()) {
            Div(attrs = {
                style {
                    padding(24.px)
                    textAlign("center")
                    color(Color("var(--text)"))
                    opacity(0.6)
                }
            }) {
                Text("Loading news...")
            }
        } else if (!viewModel.isLoading && !viewModel.isRefreshing && viewModel.error == null) {
            Div(attrs = {
                style {
                    padding(24.px)
                    textAlign("center")
                    color(Color("var(--text)"))
                    opacity(0.6)
                }
            }) {
                Text("No news articles found. Click refresh to fetch latest headlines.")
            }
        }
    }
}

@Composable
fun NewsArticleCard(article: NewsArticle) {
    Div(attrs = {
        style {
            padding(16.px)
            backgroundColor(Color("var(--surface)"))
            border(1.px, LineStyle.Solid, Color("var(--border)"))
            borderRadius(8.px)
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
                gap(8.px)
            }
        }) {
            // Title
            H4(attrs = {
                style {
                    fontSize(16.px)
                    fontWeight(600)
                    color(Color("var(--text)"))
                    margin(0.px)
                }
            }) {
                Text(article.title)
            }
            
            // Description
            val description = article.description
            if (description != null && description.isNotEmpty()) {
                P(attrs = {
                    style {
                        fontSize(14.px)
                        color(Color("var(--text)"))
                        opacity(0.8)
                        margin(0.px)
                        property("line-height", "1.5")
                    }
                }) {
                    Text(description)
                }
            }
            
            // Metadata
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    justifyContent(JustifyContent.SpaceBetween)
                    alignItems(AlignItems.Center)
                    fontSize(12.px)
                    color(Color("var(--text)"))
                    opacity(0.6)
                    marginTop(8.px)
                }
            }) {
                Span {
                    Text("${article.source}${if (article.author != null) " • ${article.author}" else ""}")
                }
                Span {
                    Text(formatDate(article.publishedAt))
                }
            }
        }
    }
}

fun formatDate(dateString: String): String {
    return try {
        val date = kotlin.js.Date(dateString)
        date.toLocaleString()
    } catch (e: Exception) {
        dateString
    }
}

