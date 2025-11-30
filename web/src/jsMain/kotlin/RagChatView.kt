import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import models.*
import org.w3c.dom.HTMLDivElement

@Composable
fun RagChatView(viewModel: RagChatViewModel) {
    LaunchedEffect(Unit) {
        viewModel.loadConversations()
    }
    
    // Full layout with sidebar - work within parent container
    // Parent container already has TopBar, so we use flex to take remaining space
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
            flex(1)
            minHeight(0.px)
            width(100.percent)
            overflow("hidden")
        }
    }) {
        // Sidebar
        Sidebar(
            conversations = viewModel.conversations,
            currentConversationId = viewModel.currentConversationId,
            onSelectConversation = { id -> viewModel.loadConversation(id) },
            onNewChat = { viewModel.createNewChat() },
            onDeleteConversation = { id -> viewModel.deleteConversation(id) },
            isLoading = viewModel.isLoadingConversations
        )
        
        // Main content area - flex column
        // This must work within the parent container that has TopBar
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                flex(1)
                minHeight(0.px)
                overflow("hidden")
            }
        }) {
            // Scrollable chat area - takes available space
            RagChatThread(
                messages = viewModel.messages,
                sourcesByMessage = viewModel.sourcesByMessage,
                isLoading = viewModel.isLoading,
                isEmpty = viewModel.messages.isEmpty() && !viewModel.isLoading && viewModel.currentConversationId == null
            )
            
            // Error display - fixed at bottom, above input
            viewModel.error?.let { error ->
                Div(attrs = {
                    style {
                        padding(12.px, 16.px)
                        backgroundColor(Color("#fee"))
                        color(Color("#c33"))
                        property("border-top", "1px solid #fcc")
                        flexShrink(0)
                    }
                }) {
                    Text("Error: $error")
                }
            }
            
            // Input - ALWAYS VISIBLE at bottom
            // This must be outside the scrollable area and always visible
            Div(attrs = {
                style {
                    flexShrink(0)
                    width(100.percent)
                }
            }) {
                RagChatInput(
                    onSend = { text -> viewModel.sendMessage(text) },
                    isLoading = viewModel.isLoading,
                    placeholder = "Ask a question with RAG..."
                )
            }
        }
    }
}

@Composable
fun RagChatThread(
    messages: List<ChatMessage>,
    sourcesByMessage: Map<String, List<LabeledSource>>,
    isLoading: Boolean,
    isEmpty: Boolean = false
) {
    var scrollContainer by remember { mutableStateOf<HTMLDivElement?>(null) }
    
    // Auto-scroll to bottom when messages change or loading state changes
    LaunchedEffect(messages.size, isLoading) {
        scrollContainer?.let { container ->
            kotlinx.coroutines.delay(50)
            container.scrollTop = container.scrollHeight.toDouble()
        }
    }
    
    // Scrollable container - this is the key: flex(1) with overflowY("auto")
    Div(attrs = {
        style {
            flex(1)
            minHeight(0.px)
            overflowY("auto")
            overflowX("hidden")
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
        }
        ref { element ->
            scrollContainer = element?.unsafeCast<HTMLDivElement>()
            onDispose { }
        }
    }) {
        if (isEmpty) {
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    alignItems(AlignItems.Center)
                    justifyContent(JustifyContent.Center)
                    padding(24.px)
                    flex(1)
                }
            }) {
                Text("Welcome! Click 'New Chat' to start a RAG-powered conversation.")
            }
        } else {
            Div(attrs = {
                style {
                    padding(24.px)
                }
            }) {
                messages.forEach { message ->
                    RagMessageBubble(
                        message = message,
                        sources = sourcesByMessage[message.timestamp.toString()] ?: emptyList()
                    )
                    
                    if (isLoading && message == messages.lastOrNull()) {
                        TypingIndicator()
                    }
                }
                
                if (isLoading && messages.isEmpty()) {
                    TypingIndicator()
                }
            }
        }
    }
}

@Composable
fun RagMessageBubble(
    message: ChatMessage,
    sources: List<LabeledSource>
) {
    val isUser = message.role == "user"
    val isAssistant = message.role == "assistant"
    
    Div(attrs = {
        style {
            marginBottom(16.px)
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            alignItems(if (isUser) AlignItems.FlexEnd else AlignItems.FlexStart)
        }
    }) {
        // Message bubble
        Div(attrs = {
            style {
                maxWidth(70.percent)
                padding(12.px, 16.px)
                borderRadius(12.px)
                backgroundColor(if (isUser) Color("#007bff") else Color("var(--surface)"))
                color(if (isUser) Color.white else Color("var(--text)"))
                property("box-shadow", "0 1px 2px rgba(0,0,0,0.1)")
            }
        }) {
            if (isAssistant) {
                MarkdownText(message.content)
            } else {
                Text(message.content)
            }
        }
        
        // Sources section (only for assistant messages with sources)
        if (isAssistant && sources.isNotEmpty()) {
            Div(attrs = {
                style {
                    marginTop(8.px)
                    maxWidth(70.percent)
                    padding(12.px, 16.px)
                    backgroundColor(Color("#f5f5f5"))
                    borderRadius(8.px)
                    border(1.px, LineStyle.Solid, Color("#ddd"))
                }
            }) {
                Div(attrs = {
                    style {
                        fontSize(12.px)
                        fontWeight("bold")
                        color(Color("#666"))
                        marginBottom(8.px)
                    }
                }) {
                    Text("Источники (${sources.size}):")
                }
                
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        gap(8.px)
                    }
                }) {
                    sources.forEach { source ->
                        Div(attrs = {
                            style {
                                padding(8.px, 12.px)
                                backgroundColor(Color.white)
                                borderRadius(4.px)
                                border(1.px, LineStyle.Solid, Color("#e0e0e0"))
                            }
                        }) {
                            Div(attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    justifyContent(JustifyContent.SpaceBetween)
                                    alignItems(AlignItems.Center)
                                    marginBottom(4.px)
                                }
                            }) {
                                Span(attrs = {
                                    style {
                                        fontSize(13.px)
                                        fontWeight("bold")
                                        color(Color("#333"))
                                    }
                                }) {
                                    Text("[${source.label}] ${source.title}")
                                }
                                Span(attrs = {
                                    style {
                                        fontSize(11.px)
                                        color(Color("#666"))
                                        backgroundColor(Color("#e8f5e9"))
                                        padding(2.px, 6.px)
                                        borderRadius(3.px)
                                    }
                                }) {
                                    Text("Score: ${(kotlin.math.round(source.score * 1000) / 1000.0).toString()}")
                                }
                            }
                            Div(attrs = {
                                style {
                                    fontSize(12.px)
                                    color(Color("#555"))
                                    property("line-height", "1.4")
                                    whiteSpace("pre-wrap")
                                    marginTop(4.px)
                                    paddingLeft(8.px)
                                    property("border-left", "2px solid #4caf50")
                                }
                            }) {
                                Text(source.snippet.take(200) + if (source.snippet.length > 200) "..." else "")
                            }
                        }
                    }
                }
            }
        }
        
        // Timestamp
        Div(attrs = {
            style {
                fontSize(11.px)
                color(Color("#999"))
                marginTop(4.px)
                paddingLeft(if (isUser) 0.px else 8.px)
                paddingRight(if (isUser) 8.px else 0.px)
            }
        }) {
            Text(formatTimestamp(message.timestamp))
        }
    }
}

// TypingIndicator is reused from Main.kt

// formatTimestamp is reused from Main.kt

// MarkdownText and markdownToHtml are reused from Main.kt

@Composable
fun RagChatInput(
    onSend: (String) -> Unit,
    isLoading: Boolean = false,
    placeholder: String = "Type a message..."
) {
    var inputValue by remember { mutableStateOf("") }
    
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
            gap(8.px)
            padding(16.px)
            backgroundColor(Color("#ffffff"))
            property("border-top", "1px solid #ddd")
            width(100.percent)
            boxSizing("border-box")
            alignItems(AlignItems.Center)
        }
    }) {
        Input(type = InputType.Text, attrs = {
            style {
                flex(1)
                padding(12.px, 16.px)
                fontSize(16.px)
                borderRadius(8.px)
                border(2.px, LineStyle.Solid, Color("#007bff"))
                backgroundColor(Color.white)
                color(Color("#000000"))
                outline("none")
                minHeight(40.px)
            }
            placeholder(placeholder)
            value(inputValue)
            if (isLoading) disabled()
            onInput {
                inputValue = (it.target as? org.w3c.dom.HTMLInputElement)?.value ?: ""
            }
            onKeyDown {
                if (it.key == "Enter" && !it.shiftKey && !isLoading) {
                    it.preventDefault()
                    val text = inputValue.trim()
                    if (text.isNotEmpty()) {
                        onSend(text)
                        inputValue = ""
                    }
                }
            }
        })
        
        Button(attrs = {
            style {
                padding(12.px, 24.px)
                fontSize(16.px)
                backgroundColor(if (isLoading) Color("#999999") else Color("#007bff"))
                color(Color.white)
                border(0.px)
                borderRadius(8.px)
                cursor(if (isLoading) "not-allowed" else "pointer")
                fontWeight("bold")
                whiteSpace("nowrap")
                minHeight(40.px)
                minWidth(80.px)
            }
            onClick {
                if (!isLoading) {
                    val text = inputValue.trim()
                    if (text.isNotEmpty()) {
                        onSend(text)
                        inputValue = ""
                    }
                }
            }
            if (isLoading) disabled()
        }) {
            Text(if (isLoading) "Sending..." else "Send")
        }
    }
}

