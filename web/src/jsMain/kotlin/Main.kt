import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import models.*
import transport.HttpTransport
import structured.ReadingSummary
import structured.JournalResponse
import structured.Journal
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.Element
import ReasoningLabViewModel
import ReasoningLabView
import TemperatureLabViewModel
import TemperatureLabView
import ModelComparisonViewModel
import ui.ModelComparisonTab
import McpLabViewModel
import NotionFinanceViewModel
import NewsViewModel
import NewsView
import NewsNotificationPanel
import RemindersViewModel
import RemindersView
import ResearchLogViewModel
import ResearchLogView
import TutorViewModel
import TutorView
import WikiSearchViewModel
import WikiSearchView

fun main() {
    renderComposable(rootElementId = "root") {
        App()
    }
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val viewModel = remember { ChatViewModel(scope) }
    
    // Load theme from localStorage on startup
    var theme by remember { 
        mutableStateOf(
            try {
                val stored = js("window.localStorage.getItem('theme')") as? String
                if (stored != null && stored.isNotEmpty() && (stored == "light" || stored == "dark")) stored else "light"
            } catch (e: Exception) {
                "light"
            }
        )
    }
    
    // Load mode from localStorage on startup
    var mode by remember { 
        mutableStateOf(
            try {
                val stored = js("window.localStorage.getItem('mode')") as? String
                val allowed = setOf("chat", "journal", "reasoning", "temperature", "modelComparison", "mcp", "notionFinance", "news", "reminders", "researchLog", "tutor", "wikiSearch")
                if (stored != null && stored.isNotEmpty() && stored in allowed) stored else "chat"
            } catch (e: Exception) {
                "chat"
            }
        )
    }
    
    val reasoningViewModel = remember { ReasoningLabViewModel(scope) }
    val temperatureViewModel = remember { TemperatureLabViewModel(scope) }
    val httpTransport = remember { HttpTransport("http://localhost:8081") }
    val modelComparisonViewModel = remember { ModelComparisonViewModel(scope, httpTransport) }
    val mcpViewModel = remember { McpLabViewModel(scope) }
    val notionFinanceViewModel = remember { NotionFinanceViewModel(scope) }
    val newsViewModel = remember { NewsViewModel(scope) }
    val remindersViewModel = remember { RemindersViewModel(scope) }
    val researchLogViewModel = remember { ResearchLogViewModel(scope) }
    val tutorViewModel = remember { TutorViewModel(scope, httpTransport) }
    val wikiSearchViewModel = remember { WikiSearchViewModel(scope, httpTransport) }
    
    // News notification panel state
    var isNewsNotificationOpen by remember { mutableStateOf(false) }
    var lastViewedNewsTimestamp by remember { mutableStateOf<String?>(null) }
    
    // Save theme to localStorage when it changes
    LaunchedEffect(theme) {
        try {
            val localStorage = js("window.localStorage")
            localStorage.setItem("theme", theme)
        } catch (e: Exception) {
            // Ignore localStorage errors
        }
    }
    
    // Save mode to localStorage when it changes
    LaunchedEffect(mode) {
        try {
            val localStorage = js("window.localStorage")
            localStorage.setItem("mode", mode)
        } catch (e: Exception) {
            // Ignore localStorage errors
        }
    }
    
    LaunchedEffect(Unit) {
        // Load conversations on startup (only for chat mode)
        if (mode == "chat") {
            viewModel.loadConversations()
        }
    }
    
    Style(AppStylesheet)
    
    Div(attrs = {
        attr("data-theme", theme)
        style {
            width(100.percent)
            height(100.vh)
            display(DisplayStyle.Flex)
        }
    }) {
        if (mode == "chat") {
            Sidebar(
                conversations = viewModel.conversations,
                currentConversationId = viewModel.currentConversationId,
                onSelectConversation = { id -> viewModel.loadConversation(id) },
                onNewChat = { viewModel.createNewChat() },
                onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                isLoading = viewModel.isLoadingConversations
            )
        }
        
        Div(attrs = {
            style {
                flex(1)
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                height(100.vh)
                property("overflow", "hidden")
            }
        }) {
            TopBar(
                theme = theme,
                mode = mode,
                onThemeToggle = { theme = if (theme == "light") "dark" else "light" },
                onModeToggle = { 
                    mode = when (mode) {
                        "chat" -> "journal"
                        else -> "chat"
                    }
                },
                onTemperatureToggle = {
                    mode = "temperature"
                },
                onReasoningToggle = {
                    mode = "reasoning"
                },
                onModelComparisonToggle = {
                    mode = "modelComparison"
                },
                onMcpToggle = {
                    mode = "mcp"
                },
                onNotionFinanceToggle = {
                    mode = "notionFinance"
                },
                onNewsToggle = {
                    mode = "news"
                },
                onRemindersToggle = {
                    mode = "reminders"
                },
                onResearchLogToggle = {
                    mode = "researchLog"
                },
                onTutorToggle = {
                    mode = "tutor"
                },
                onWikiSearchToggle = {
                    mode = "wikiSearch"
                },
                onExport = { viewModel.exportMessages() },
                onNewsNotificationClick = {
                    isNewsNotificationOpen = true
                    // Update last viewed timestamp when opening
                    if (newsViewModel.fetchedAt.isNotEmpty()) {
                        lastViewedNewsTimestamp = newsViewModel.fetchedAt
                    }
                },
                hasNewNews = newsViewModel.fetchedAt.isNotEmpty() && 
                    lastViewedNewsTimestamp != null && 
                    newsViewModel.fetchedAt != lastViewedNewsTimestamp
            )
            
            if (mode == "journal") {
                JournalView(viewModel)
            } else if (mode == "reasoning") {
                ReasoningLabView(reasoningViewModel)
            } else if (mode == "temperature") {
                TemperatureLabView(temperatureViewModel)
            } else if (mode == "modelComparison") {
                ModelComparisonTab(modelComparisonViewModel)
            } else if (mode == "mcp") {
                McpLabView(mcpViewModel)
            } else if (mode == "notionFinance") {
                NotionFinanceView(notionFinanceViewModel)
            } else if (mode == "news") {
                NewsView(newsViewModel)
            } else if (mode == "reminders") {
                RemindersView(remindersViewModel)
            } else if (mode == "researchLog") {
                ResearchLogView(researchLogViewModel)
            } else if (mode == "tutor") {
                TutorView(tutorViewModel)
            } else if (mode == "wikiSearch") {
                WikiSearchView(wikiSearchViewModel)
            } else {
                if (viewModel.messages.isEmpty() && !viewModel.isLoading && viewModel.currentConversationId == null) {
                    Div(attrs = {
                        classes(AppStylesheet.emptyState)
                    }) {
                        Text("Welcome! Click 'New Chat' to start a conversation.")
                    }
                } else {
                    ChatThread(
                        messages = viewModel.messages,
                        toolCalls = viewModel.toolCalls,
                        isLoading = viewModel.isLoading,
                        readingSummaries = viewModel.readingSummaries,
                        journalResponses = emptyMap() // Not used in chat mode
                    )
                }
            }
            
            // News notification panel (overlay)
            NewsNotificationPanel(
                viewModel = newsViewModel,
                isOpen = isNewsNotificationOpen,
                onClose = {
                    isNewsNotificationOpen = false
                    // Update last viewed timestamp when closing
                    if (newsViewModel.fetchedAt.isNotEmpty()) {
                        lastViewedNewsTimestamp = newsViewModel.fetchedAt
                    }
                }
            )
            
            if (mode == "chat" || mode == "journal") {
                if (mode == "chat") {
                    CompressionInsightPanel(viewModel)
                    ExternalMemoryPanel(viewModel)
                }
                ChatInput(
                    onSend = { text -> 
                        if (mode == "journal") {
                            viewModel.sendJournalMessage(text)
                        } else {
                            viewModel.sendMessage(text)
                        }
                    },
                    isLoading = viewModel.isLoading,
                    placeholder = if (mode == "journal") "Share your thoughts..." else "Type a message...",
                    viewModel = if (mode == "chat") viewModel else null
                )
            }
        }
    }
}

@Composable
fun ExternalMemoryPanel(viewModel: ChatViewModel) {
    Div(attrs = {
        style {
            padding(12.px, 16.px)
            backgroundColor(Color("var(--surface)"))
            property("border-top", "1px solid var(--border)")
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            gap(8.px)
        }
    }) {
        // Header with toggle and status
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                alignItems(AlignItems.Center)
                justifyContent(JustifyContent.SpaceBetween)
                gap(12.px)
                flexWrap(FlexWrap.Wrap)
            }
        }) {
            Div {
                Text("External Memory")
            }
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    alignItems(AlignItems.Center)
                    gap(8.px)
                }
            }) {
                if (viewModel.useMemory) {
                    Span(attrs = {
                        style {
                            fontSize(12.px)
                            color(Color("#4caf50"))
                        }
                    }) {
                        Text("💾 Memory active")
                    }
                }
                Label(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        alignItems(AlignItems.Center)
                        gap(4.px)
                        cursor("pointer")
                    }
                    onClick {
                        viewModel.useMemory = !viewModel.useMemory
                        // When turning off memory, clear local list
                        if (!viewModel.useMemory) {
                            viewModel.memories = emptyList()
                        } else {
                            // Reload memories for current conversation when turning on
                            val convId = viewModel.currentConversationId
                            if (convId != null) {
                                // Fire and forget; UI will update when loaded
                                // We don't have direct scope here, so rely on next send/load to refresh.
                            }
                        }
                    }
                }) {
                    Input(type = InputType.Checkbox, attrs = {
                        checked(viewModel.useMemory)
                        onChange { ev ->
                            viewModel.useMemory = ev.value
                            if (!viewModel.useMemory) {
                                viewModel.memories = emptyList()
                            }
                        }
                    })
                    Text("Use memory")
                }
            }
        }

        val entries = viewModel.memories
        if (entries.isEmpty()) {
            Span(attrs = {
                style {
                    fontSize(12.px)
                    opacity(0.7)
                }
            }) {
                Text("No memories stored yet for this conversation.")
            }
        } else {
            var expandedId by remember { mutableStateOf<String?>(null) }
            Div(attrs = {
                style {
                    maxHeight(200.px)
                    overflowY("auto")
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    gap(6.px)
                    fontSize(12.px)
                }
            }) {
                entries.forEach { entry ->
                    val createdAtText = remember(entry.createdAt) {
                        kotlin.js.Date(entry.createdAt.toDouble()).toLocaleString()
                    }
                    Div(attrs = {
                        style {
                            padding(8.px)
                            backgroundColor(Color("var(--background)"))
                            borderRadius(4.px)
                            border(1.px, LineStyle.Solid, Color("var(--border)"))
                            cursor("pointer")
                        }
                        onClick {
                            expandedId = if (expandedId == entry.id) null else entry.id
                        }
                    }) {
                        // Header line
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                justifyContent(JustifyContent.SpaceBetween)
                                gap(8.px)
                            }
                        }) {
                            Span {
                                Text("[${entry.kind}] ${entry.title}")
                            }
                            Span(attrs = {
                                style { fontSize(10.px); opacity(0.6) }
                            }) {
                                Text(createdAtText)
                            }
                        }
                        if (expandedId == entry.id) {
                            Div(attrs = {
                                style {
                                    marginTop(6.px)
                                    whiteSpace("pre-wrap")
                                }
                            }) {
                                Text(entry.content)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Sidebar(
    conversations: List<Conversation>,
    currentConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    isLoading: Boolean
) {
    Div(attrs = {
        classes(AppStylesheet.sidebar)
    }) {
        Div(attrs = {
            classes(AppStylesheet.sidebarHeader)
        }) {
            Button(attrs = {
                classes(AppStylesheet.button, AppStylesheet.newChatButton)
                onClick { onNewChat() }
            }) {
                Text("+ New Chat")
            }
        }
        
        Div(attrs = {
            classes(AppStylesheet.conversationList)
        }) {
            if (isLoading) {
                Div(attrs = {
                    classes(AppStylesheet.conversationItem)
                }) {
                    Text("Loading...")
                }
            } else if (conversations.isEmpty()) {
                Div(attrs = {
                    classes(AppStylesheet.conversationItem)
                }) {
                    Text("No conversations yet")
                }
            } else {
                conversations.forEach { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        isSelected = conversation.id == currentConversationId,
                        onSelect = { onSelectConversation(conversation.id) },
                        onDelete = { onDeleteConversation(conversation.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Div(attrs = {
        classes(
            AppStylesheet.conversationItem,
            *if (isSelected) arrayOf(AppStylesheet.conversationItemSelected) else emptyArray()
        )
        onClick { onSelect() }
    }) {
        Span(attrs = {
            classes(AppStylesheet.conversationTitle)
            onClick { onSelect() }
        }) {
            Text(conversation.title)
        }
        Button(attrs = {
            classes(AppStylesheet.deleteButton)
            onClick { 
                onDelete() 
            }
        }) {
            Text("×")
        }
    }
}

@Composable
fun TopBar(
    theme: String,
    mode: String,
    onThemeToggle: () -> Unit,
    onModeToggle: () -> Unit,
    onTemperatureToggle: () -> Unit,
    onReasoningToggle: () -> Unit,
    onModelComparisonToggle: () -> Unit,
    onMcpToggle: () -> Unit,
    onNotionFinanceToggle: () -> Unit,
    onNewsToggle: () -> Unit,
    onRemindersToggle: () -> Unit,
    onResearchLogToggle: () -> Unit,
    onTutorToggle: () -> Unit,
    onWikiSearchToggle: () -> Unit,
    onExport: () -> Unit,
    onNewsNotificationClick: () -> Unit,
    hasNewNews: Boolean = false
) {
    Div(attrs = {
        classes(AppStylesheet.topBar)
    }) {
        H1(attrs = {
            classes(AppStylesheet.title)
        }) {
            Text(
                when (mode) {
                    "journal" -> "Personal Journal"
                    "reasoning" -> "Reasoning Lab"
                    "temperature" -> "Temperature Lab"
                    "modelComparison" -> "Model Comparison"
                    "mcp" -> "MCP Tools"
                    "notionFinance" -> "💰 Notion Finance"
                    "news" -> "📰 News"
                    "reminders" -> "🔔 Reminders"
                    "researchLog" -> "📚 Research Log"
                    "tutor" -> "🎓 Learning Tutor"
                    "wikiSearch" -> "📚 Wiki Search"
                    else -> "KMP AI Chat"
                }
            )
        }
        
        Div(attrs = {
            classes(AppStylesheet.topBarActions)
        }) {
            Button(attrs = {
                classes(AppStylesheet.button, AppStylesheet.modeButton)
                onClick { onModeToggle() }
            }) {
                Text(if (mode == "chat") "📔 Journal" else "💬 Chat")
            }
            
            if (mode != "temperature") {
                Button(attrs = {
                    classes(AppStylesheet.button, AppStylesheet.modeButton)
                    onClick { onTemperatureToggle() }
                }) {
                    Text("🔥 Temperature Lab")
                }
            }

            if (mode != "reasoning") {
                Button(attrs = {
                    classes(AppStylesheet.button, AppStylesheet.modeButton)
                    onClick { onReasoningToggle() }
                }) {
                    Text("🧪 Reasoning Lab")
                }
            }

            if (mode != "modelComparison") {
                Button(attrs = {
                    classes(AppStylesheet.button, AppStylesheet.modeButton)
                    onClick { onModelComparisonToggle() }
                }) {
                    Text("🧪 Model Comparison")
                }
            }

            if (mode != "mcp") {
                Button(attrs = {
                    classes(AppStylesheet.button, AppStylesheet.modeButton)
                    onClick { onMcpToggle() }
                }) {
                    Text("🧩 MCP Tools")
                }
            }
            
            if (mode != "notionFinance") {
                Button(attrs = {
                    classes(AppStylesheet.button, AppStylesheet.modeButton)
                    onClick { onNotionFinanceToggle() }
                }) {
                    Text("💰 Notion Finance")
                }
            }
            
            if (mode != "news") {
                Button(attrs = {
                    classes(AppStylesheet.button, AppStylesheet.modeButton)
                    onClick { onNewsToggle() }
                }) {
                    Text("📰 News")
                }
            }
            
            if (mode != "reminders") {
                Button(attrs = {
                    classes(AppStylesheet.button, AppStylesheet.modeButton)
                    onClick { onRemindersToggle() }
                }) {
                    Text("🔔 Reminders")
                }
            }
            
            if (mode != "researchLog") {
                Button(attrs = {
                    classes(AppStylesheet.button, AppStylesheet.modeButton)
                    onClick { onResearchLogToggle() }
                }) {
                    Text("📚 Research Log")
                }
            }
            
            if (mode != "tutor") {
                Button(attrs = {
                    classes(AppStylesheet.button, AppStylesheet.modeButton)
                    onClick { onTutorToggle() }
                }) {
                    Text("🎓 Tutor")
                }
            }
            
            if (mode != "wikiSearch") {
                Button(attrs = {
                    classes(AppStylesheet.button, AppStylesheet.modeButton)
                    onClick { onWikiSearchToggle() }
                }) {
                    Text("📚 Wiki Search")
                }
            }
            
            // News notification button
            Div(attrs = {
                style {
                    position(Position.Relative)
                    display(DisplayStyle.InlineBlock)
                }
            }) {
                Button(attrs = {
                    classes(AppStylesheet.button, AppStylesheet.iconButton)
                    onClick { onNewsNotificationClick() }
                }) {
                    Text("🔔")
                }
                // Badge indicator
                if (hasNewNews) {
                    Span(attrs = {
                        style {
                            position(Position.Absolute)
                            top(0.px)
                            right(0.px)
                            width(8.px)
                            height(8.px)
                            backgroundColor(Color("#f44336"))
                            borderRadius(50.percent)
                            border(2.px, LineStyle.Solid, Color("var(--surface)"))
                        }
                    }) {
                        // Empty span for badge dot
                    }
                }
            }
            
            Button(attrs = {
                classes(AppStylesheet.button, AppStylesheet.iconButton)
                onClick { onThemeToggle() }
            }) {
                Text(if (theme == "light") "🌙" else "☀️")
            }
            
            if (mode == "chat") {
                Button(attrs = {
                    classes(AppStylesheet.button, AppStylesheet.iconButton)
                    onClick { onExport() }
                }) {
                    Text("📥")
                }
            }
        }
    }
}

@Composable
fun ChatThread(
    messages: List<ChatMessage>,
    toolCalls: Map<String, List<ToolCall>>,
    isLoading: Boolean,
    readingSummaries: Map<String, ReadingSummary> = emptyMap(),
    journalResponses: Map<String, JournalResponse> = emptyMap()
) {
    var scrollContainer by remember { mutableStateOf<org.w3c.dom.HTMLDivElement?>(null) }
    
    // Auto-scroll to bottom when messages change or loading state changes
    LaunchedEffect(messages.size, isLoading) {
        scrollContainer?.let { container ->
            kotlinx.coroutines.delay(50) // Small delay to ensure DOM is updated
            container.scrollTop = container.scrollHeight.toDouble()
        }
    }
    
    Div(attrs = {
        classes(AppStylesheet.chatThread)
        ref { element ->
            scrollContainer = element?.unsafeCast<org.w3c.dom.HTMLDivElement>()
            onDispose { }
        }
    }) {
        messages.forEach { message ->
            MessageBubble(
                message = message,
                toolCalls = toolCalls[message.timestamp.toString()] ?: emptyList()
            )
            
            // Show reading summary if available for this message
            readingSummaries[message.timestamp.toString()]?.let { summary ->
                ReadingSummaryCard(summary)
            }
            
            // Show journal response if available for this message
            journalResponses[message.timestamp.toString()]?.let { journalResponse ->
                if (journalResponse.status == "ready") {
                    journalResponse.journal?.let { journal ->
                        JournalCard(journal)
                    }
                } else if (journalResponse.status == "collecting" && journalResponse.missing.isNotEmpty()) {
                    // Note: The missing array should contain actual questions, which are already displayed
                    // as assistant messages. This section is kept for backward compatibility but shouldn't
                    // show redundant information.
                }
            }
        }
        
        if (isLoading) {
            TypingIndicator()
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    toolCalls: List<ToolCall>
) {
    val isUser = message.role == "user"
    val isAssistant = message.role == "assistant"
    
    Div(attrs = {
        classes(
            AppStylesheet.messageBubble,
            if (isUser) AppStylesheet.messageUser else AppStylesheet.messageAssistant
        )
    }) {
        Div(attrs = {
            classes(AppStylesheet.messageContent)
            if (isAssistant) {
                classes("markdown-content")
            }
        }) {
            if (isAssistant) {
                // Render markdown for assistant messages
                MarkdownText(message.content)
            } else {
                // Plain text for user messages
                Text(message.content)
            }
        }
        
        if (toolCalls.isNotEmpty()) {
            toolCalls.forEach { toolCall ->
                ToolCallCard(toolCall = toolCall)
            }
        }
        
        Div(attrs = {
            classes(AppStylesheet.messageTime)
        }) {
            Text(formatTimestamp(message.timestamp))
        }
    }
}

@Composable
fun ToolCallCard(toolCall: ToolCall) {
    Div(attrs = {
        classes(AppStylesheet.toolCallCard)
    }) {
        Div(attrs = {
            classes(AppStylesheet.toolCallHeader)
        }) {
            Text("🔧 ${toolCall.name}")
        }
        Div(attrs = {
            classes(AppStylesheet.toolCallInput)
        }) {
            Text("Input: ${toolCall.input}")
        }
        if (toolCall.result != null) {
            Div(attrs = {
                classes(AppStylesheet.toolCallResult)
            }) {
                Text("Result: ${toolCall.result}")
            }
        }
    }
}

@Composable
fun ReadingSummaryCard(data: ReadingSummary) {
    Div(attrs = {
        style {
            borderRadius(16.px)
            border(1.px, LineStyle.Solid, Color("var(--border)"))
            padding(20.px)
            backgroundColor(Color("var(--surface)"))
            marginTop(12.px)
            marginBottom(12.px)
            property("box-shadow", "0 2px 8px rgba(0, 0, 0, 0.1)")
            property("transition", "all 0.3s ease")
        }
        classes("reading-summary-card")
    }) {
        // Title (header) - prominent
        H2(attrs = {
            style {
                fontSize(24.px)
                fontWeight(700)
                color(Color("var(--text)"))
                marginBottom(12.px)
                marginTop(0.px)
                lineHeight("1.3")
            }
        }) { 
            Text(data.title) 
        }
        
        // Time to read + Source (same row, compact)
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.SpaceBetween)
                alignItems(AlignItems.Center)
                marginBottom(16.px)
                paddingBottom(12.px)
                property("border-bottom", "1px solid var(--border)")
            }
        }) {
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    alignItems(AlignItems.Center)
                    gap(8.px)
                }
            }) {
                Span(attrs = {
                    style {
                        fontSize(14.px)
                        color(Color("var(--text)"))
                        opacity(0.7)
                    }
                }) {
                    Text("⏱️")
                }
                B(attrs = {
                    style {
                        fontSize(14.px)
                        color(Color("var(--text)"))
                        opacity(0.9)
                    }
                }) { 
                    Text(data.timeOfReading) 
                }
            }
            Span(attrs = {
                style {
                    fontSize(13.px)
                    color(Color("var(--text)"))
                    opacity(0.6)
                    property("font-style", "italic")
                }
            }) {
                Text(data.theSourceOfTheText)
            }
        }
        
        // Summary with markdown/lists support
        Div(attrs = {
            style {
                borderRadius(12.px)
                padding(16.px, 18.px)
                backgroundColor(Color("var(--summary-bg)"))
                border(1.px, LineStyle.Solid, Color("var(--summary-border)"))
                property("color", "var(--text)")
                lineHeight("1.7")
            }
            classes("reading-summary-box", "summary-markdown")
        }) {
            Div(attrs = {
                style {
                    fontSize(14.px)
                    fontWeight(600)
                    marginBottom(10.px)
                    color(Color("var(--text)"))
                    opacity(0.9)
                }
            }) {
                Text("📝 Summary")
            }
            Div(attrs = {
                style {
                    fontSize(15.px)
                    color(Color("var(--text)"))
                    opacity(0.95)
                }
            }) {
                MarkdownText(data.summary)
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Div(attrs = {
        classes(AppStylesheet.typingIndicator)
    }) {
        Span(attrs = {
            classes("typing-dot")
            style {
                property("animation-delay", "0s")
            }
        }) {
            Text("●")
        }
        Span(attrs = {
            classes("typing-dot")
            style {
                property("animation-delay", "0.2s")
            }
        }) {
            Text("●")
        }
        Span(attrs = {
            classes("typing-dot")
            style {
                property("animation-delay", "0.4s")
            }
        }) {
            Text("●")
        }
    }
}

@Composable
fun McpLabView(viewModel: McpLabViewModel) {
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
                Text("🧩 MCP Tools")
            }
            P(attrs = {
                style {
                    fontSize(14.px)
                    color(Color("var(--text)"))
                    opacity(0.7)
                    margin(0.px)
                }
            }) {
                Text("Load and view tools from your MCP server. Set MCP_SERVER_PATH environment variable to configure the server.")
            }
        }

        // Load button
        Button(attrs = {
            classes(AppStylesheet.button)
            style {
                alignSelf(AlignSelf.FlexStart)
                padding(12.px, 24.px)
                fontSize(14.px)
            }
            onClick { viewModel.loadTools() }
            if (viewModel.isLoading) disabled()
        }) {
            Text(if (viewModel.isLoading) "Loading..." else "Load MCP Tools")
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

        // JSON Messages (Request/Response)
        if (viewModel.messages.isNotEmpty()) {
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
                    Text("JSON Request/Response Messages")
                }

                viewModel.messages.forEachIndexed { index, message ->
                    val isRequest = message.direction == "request"
                    val isResponse = message.direction == "response"
                    val isError = message.direction == "error"
                    
                    Div(attrs = {
                        style {
                            padding(12.px)
                            backgroundColor(
                                when {
                                    isRequest -> Color("rgba(66, 165, 245, 0.1)")
                                    isError -> Color("rgba(244, 67, 54, 0.1)")
                                    else -> Color("rgba(76, 175, 80, 0.1)")
                                }
                            )
                            border(
                                1.px, 
                                LineStyle.Solid, 
                                when {
                                    isRequest -> Color("rgba(66, 165, 245, 0.3)")
                                    isError -> Color("rgba(244, 67, 54, 0.3)")
                                    else -> Color("rgba(76, 175, 80, 0.3)")
                                }
                            )
                            borderRadius(8.px)
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
                            Span(attrs = {
                                style {
                                    fontSize(12.px)
                                    fontWeight(600)
                                    color(
                                        when {
                                            isRequest -> Color("#42a5f5")
                                            isError -> Color("#f44336")
                                            else -> Color("#4caf50")
                                        }
                                    )
                                    property("text-transform", "uppercase")
                                }
                            }) {
                                Text("${message.direction} #${index + 1}")
                            }
                        }
                        Pre(attrs = {
                            style {
                                margin(0.px)
                                padding(8.px)
                                backgroundColor(Color("var(--background)"))
                                borderRadius(4.px)
                                fontSize(11.px)
                                fontFamily("monospace")
                                whiteSpace("pre-wrap")
                                property("word-break", "break-all")
                                overflowX("auto")
                                maxHeight(300.px)
                                overflowY("auto")
                                color(Color("var(--text)"))
                            }
                        }) {
                            Code {
                                Text(message.content)
                            }
                        }
                    }
                }
            }
        }

        // Tools list
        if (viewModel.tools.isNotEmpty()) {
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
                    Text("Available Tools (${viewModel.tools.size})")
                }

                viewModel.tools.forEach { tool ->
                    Div(attrs = {
                        style {
                            padding(16.px)
                            backgroundColor(Color("var(--surface)"))
                            border(1.px, LineStyle.Solid, Color("var(--border)"))
                            borderRadius(8.px)
                        }
                    }) {
                        Div(attrs = {
                            style {
                                fontSize(16.px)
                                fontWeight(600)
                                color(Color("var(--text)"))
                                marginBottom(8.px)
                            }
                        }) {
                            Text(tool.name)
                        }
                        tool.description?.let { desc ->
                            Div(attrs = {
                                style {
                                    fontSize(14.px)
                                    color(Color("var(--text)"))
                                    opacity(0.8)
                                    lineHeight("1.5")
                                }
                            }) {
                                Text(desc)
                            }
                        }
                    }
                }
            }
        } else if (!viewModel.isLoading && viewModel.error == null) {
            Div(attrs = {
                style {
                    padding(24.px)
                    textAlign("center")
                    color(Color("var(--text)"))
                    opacity(0.6)
                }
            }) {
                Text("No tools loaded. Click 'Load MCP Tools' to fetch tools from your MCP server.")
            }
        }
    }
}

@Composable
fun NotionFinanceView(viewModel: NotionFinanceViewModel) {
    // Load snapshot on first render
    LaunchedEffect(Unit) {
        viewModel.loadSnapshot()
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
                Text("💰 Notion Finance")
            }
            P(attrs = {
                style {
                    fontSize(14.px)
                    color(Color("var(--text)"))
                    opacity(0.7)
                    margin(0.px)
                }
            }) {
                Text("View your expenses and ask AI questions about your spending.")
            }
        }

        // Load snapshot button
        Button(attrs = {
            classes(AppStylesheet.button)
            style {
                alignSelf(AlignSelf.FlexStart)
                padding(12.px, 24.px)
                fontSize(14.px)
            }
            onClick { viewModel.loadSnapshot() }
            if (viewModel.isLoadingSnapshot) disabled()
        }) {
            Text(if (viewModel.isLoadingSnapshot) "Loading..." else "Refresh Finance Data")
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

        // Finance entries table
        if (viewModel.entries.isNotEmpty()) {
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
                    Text("Expenses (${viewModel.entries.size})")
                }
                
                // Calculate total
                val totalAmount = viewModel.entries.sumOf { it.amount }
                
                Div(attrs = {
                    style {
                        padding(12.px, 16.px)
                        backgroundColor(Color("var(--surface)"))
                        border(1.px, LineStyle.Solid, Color("var(--border)"))
                        borderRadius(8.px)
                        marginBottom(8.px)
                    }
                }) {
                    Text("Total: ${(totalAmount.asDynamic().toFixed(2) as String)} PLN")
                }

                // Table
                Div(attrs = {
                    style {
                        overflowX("auto")
                        border(1.px, LineStyle.Solid, Color("var(--border)"))
                        borderRadius(8.px)
                    }
                }) {
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
                                        padding(12.px, 16.px)
                                        textAlign("left")
                                        backgroundColor(Color("var(--surface)"))
                                        property("border-bottom", "1px solid var(--border)")
                                        fontWeight(600)
                                        fontSize(14.px)
                                    }
                                }) { Text("Date") }
                                Th(attrs = {
                                    style {
                                        padding(12.px, 16.px)
                                        textAlign("left")
                                        backgroundColor(Color("var(--surface)"))
                                        property("border-bottom", "1px solid var(--border)")
                                        fontWeight(600)
                                        fontSize(14.px)
                                    }
                                }) { Text("Title") }
                                Th(attrs = {
                                    style {
                                        padding(12.px, 16.px)
                                        textAlign("right")
                                        backgroundColor(Color("var(--surface)"))
                                        property("border-bottom", "1px solid var(--border)")
                                        fontWeight(600)
                                        fontSize(14.px)
                                    }
                                }) { Text("Amount (PLN)") }
                            }
                        }
                        Tbody {
                            viewModel.entries.forEach { entry ->
                                Tr {
                                    Td(attrs = {
                                        style {
                                            padding(12.px, 16.px)
                                            property("border-bottom", "1px solid var(--border)")
                                            fontSize(14.px)
                                        }
                                    }) { Text(entry.date) }
                                    Td(attrs = {
                                        style {
                                            padding(12.px, 16.px)
                                            property("border-bottom", "1px solid var(--border)")
                                            fontSize(14.px)
                                        }
                                    }) { Text(entry.title) }
                                    Td(attrs = {
                                        style {
                                            padding(12.px, 16.px)
                                            property("border-bottom", "1px solid var(--border)")
                                            fontSize(14.px)
                                            textAlign("right")
                                        }
                                    }) { Text((entry.amount.asDynamic().toFixed(2) as String)) }
                                }
                            }
                        }
                    }
                }
            }
        } else if (!viewModel.isLoadingSnapshot && viewModel.error == null) {
            Div(attrs = {
                style {
                    padding(24.px)
                    textAlign("center")
                    color(Color("var(--text)"))
                    opacity(0.6)
                }
            }) {
                Text("No expenses found. Click 'Refresh Finance Data' to load entries.")
            }
        }

        // AI Analysis Section
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                gap(12.px)
                marginTop(24.px)
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
                Text("Ask AI About Your Spending")
            }

            // Default question button
            Button(attrs = {
                classes(AppStylesheet.button)
                style {
                    alignSelf(AlignSelf.FlexStart)
                    padding(12.px, 24.px)
                    fontSize(14.px)
                }
                onClick { viewModel.askDefaultQuestion() }
                if (viewModel.isLoadingAnalysis) disabled()
            }) {
                Text(if (viewModel.isLoadingAnalysis) "Analyzing..." else "Ask AI about my spending")
            }

            // Custom question input
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    gap(8.px)
                }
            }) {
                Input(type = InputType.Text, attrs = {
                    value(viewModel.customQuestion)
                    onInput { ev ->
                        viewModel.updateCustomQuestion((ev.target as? org.w3c.dom.HTMLInputElement)?.value ?: "")
                    }
                    placeholder("Ask anything about your spending...")
                    style {
                        padding(12.px, 16.px)
                        fontSize(14.px)
                        borderRadius(8.px)
                        border(1.px, LineStyle.Solid, Color("var(--border)"))
                        backgroundColor(Color("var(--background)"))
                        color(Color("var(--text)"))
                        width(100.percent)
                    }
                    onKeyDown { ev ->
                        if (ev.key == "Enter" && !ev.shiftKey) {
                            ev.preventDefault()
                            viewModel.askCustomQuestion()
                        }
                    }
                })
                Button(attrs = {
                    classes(AppStylesheet.button)
                    style {
                        alignSelf(AlignSelf.FlexStart)
                        padding(12.px, 24.px)
                        fontSize(14.px)
                    }
                    onClick { viewModel.askCustomQuestion() }
                    if (viewModel.isLoadingAnalysis || viewModel.customQuestion.isBlank()) disabled()
                }) {
                    Text(if (viewModel.isLoadingAnalysis) "Analyzing..." else "Send")
                }
            }

            // AI Answer display
            if (viewModel.aiAnswer != null) {
                Div(attrs = {
                    style {
                        padding(20.px)
                        backgroundColor(Color("var(--surface)"))
                        border(1.px, LineStyle.Solid, Color("var(--border)"))
                        borderRadius(8.px)
                        marginTop(12.px)
                    }
                }) {
                    Div(attrs = {
                        style {
                            fontSize(16.px)
                            fontWeight(600)
                            color(Color("var(--text)"))
                            marginBottom(12.px)
                        }
                    }) {
                        Text("AI Analysis")
                    }
                    Div(attrs = {
                        style {
                            fontSize(14.px)
                            color(Color("var(--text)"))
                            lineHeight("1.6")
                            whiteSpace("pre-wrap")
                        }
                    }) {
                        MarkdownText(viewModel.aiAnswer!!)
                    }
                }
            } else if (viewModel.isLoadingAnalysis) {
                Div(attrs = {
                    style {
                        padding(20.px)
                        backgroundColor(Color("var(--surface)"))
                        border(1.px, LineStyle.Solid, Color("var(--border)"))
                        borderRadius(8.px)
                        marginTop(12.px)
                        textAlign("center")
                        color(Color("var(--text)"))
                        opacity(0.7)
                    }
                }) {
                    Text("Analyzing your spending...")
                }
            }
        }
    }
}

@Composable
fun JournalView(viewModel: ChatViewModel) {
    var scrollContainer by remember { mutableStateOf<org.w3c.dom.HTMLDivElement?>(null) }
    
    // Auto-scroll to bottom when messages change or loading state changes
    LaunchedEffect(viewModel.journalMessages.size, viewModel.isLoading) {
        scrollContainer?.let { container ->
            kotlinx.coroutines.delay(50)
            container.scrollTop = container.scrollHeight.toDouble()
        }
    }
    
    Div(attrs = {
        classes(AppStylesheet.chatThread)
        ref { element ->
            scrollContainer = element?.unsafeCast<org.w3c.dom.HTMLDivElement>()
            onDispose { }
        }
    }) {
        if (viewModel.journalMessages.isEmpty() && !viewModel.isLoading) {
            Div(attrs = {
                classes(AppStylesheet.emptyState)
            }) {
                Text("Welcome to your Personal Journal! 🌙\n\nShare your thoughts and reflections about your day. I'll help you create a beautiful journal entry.")
            }
        }
        
        viewModel.journalMessages.forEach { message ->
            MessageBubble(
                message = message,
                toolCalls = emptyList()
            )
            
            // Show journal response if available for this message
            viewModel.journalResponses[message.timestamp.toString()]?.let { journalResponse ->
                if (journalResponse.status == "ready") {
                    journalResponse.journal?.let { journal ->
                        JournalCard(journal)
                    }
                } else if (journalResponse.status == "collecting" && journalResponse.missing.isNotEmpty()) {
                    // Note: The missing array should contain actual questions, which are already displayed
                    // as assistant messages. This section is kept for backward compatibility but shouldn't
                    // show redundant information.
                }
            }
        }
        
        if (viewModel.isLoading) {
            TypingIndicator()
        }
    }
}

@Composable
fun JournalCard(journal: Journal) {
    fun getMoodEmoji(mood: String): String {
        return when (mood) {
            "very_bad" -> "😞"
            "bad" -> "😟"
            "neutral" -> "😐"
            "good" -> "😊"
            "very_good" -> "😄"
            else -> "😊"
        }
    }
    
    Div(attrs = {
        style {
            borderRadius(16.px)
            border(1.px, LineStyle.Solid, Color("var(--border)"))
            padding(24.px)
            backgroundColor(Color("var(--surface)"))
            marginTop(12.px)
            marginBottom(12.px)
            property("box-shadow", "0 2px 8px rgba(0, 0, 0, 0.1)")
        }
        classes("journal-card")
    }) {
        // Title with date
        H2(attrs = {
            style {
                fontSize(26.px)
                fontWeight(700)
                color(Color("var(--text)"))
                marginBottom(16.px)
                marginTop(0.px)
                lineHeight("1.3")
            }
        }) {
            Text("${getMoodEmoji(journal.mood)} ${journal.title}")
        }
        
        // Date and Mood on same line
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.SpaceBetween)
                alignItems(AlignItems.Center)
                marginBottom(20.px)
                paddingBottom(12.px)
                property("border-bottom", "1px solid var(--border)")
            }
        }) {
            B(attrs = {
                style {
                    fontSize(14.px)
                    color(Color("var(--text)"))
                    opacity(0.9)
                }
            }) {
                Text(journal.date)
            }
            Span(attrs = {
                style {
                    fontSize(14.px)
                    color(Color("var(--text)"))
                    opacity(0.9)
                }
            }) {
                Text("${getMoodEmoji(journal.mood)} ${journal.mood.replace("_", " ").replaceFirstChar { it.uppercaseChar() }} (${journal.moodScore}/5)")
            }
        }
        
        // Key Moments
        if (journal.keyMoments.isNotEmpty()) {
            Div(attrs = {
                style {
                    marginBottom(20.px)
                }
            }) {
                H3(attrs = {
                    style {
                        fontSize(16.px)
                        fontWeight(600)
                        marginBottom(8.px)
                        color(Color("var(--text)"))
                    }
                }) {
                    Text("✨ Key Moments")
                }
                Ul(attrs = {
                    style {
                        marginLeft(20.px)
                    }
                }) {
                    journal.keyMoments.forEach { moment ->
                        Li(attrs = {
                            style {
                                marginBottom(4.px)
                                lineHeight("1.6")
                            }
                        }) {
                            Text(moment)
                        }
                    }
                }
            }
        }
        
        // Lessons
        if (journal.lessons.isNotEmpty()) {
            Div(attrs = {
                style {
                    marginBottom(20.px)
                }
            }) {
                H3(attrs = {
                    style {
                        fontSize(16.px)
                        fontWeight(600)
                        marginBottom(8.px)
                        color(Color("var(--text)"))
                    }
                }) {
                    Text("📚 Lessons")
                }
                Ul(attrs = {
                    style {
                        marginLeft(20.px)
                    }
                }) {
                    journal.lessons.forEach { lesson ->
                        Li(attrs = {
                            style {
                                marginBottom(4.px)
                                lineHeight("1.6")
                            }
                        }) {
                            Text(lesson)
                        }
                    }
                }
            }
        }
        
        // Gratitude
        if (journal.gratitude.isNotEmpty()) {
            Div(attrs = {
                style {
                    marginBottom(20.px)
                }
            }) {
                H3(attrs = {
                    style {
                        fontSize(16.px)
                        fontWeight(600)
                        marginBottom(8.px)
                        color(Color("var(--text)"))
                    }
                }) {
                    Text("🙏 Gratitude")
                }
                Ul(attrs = {
                    style {
                        marginLeft(20.px)
                    }
                }) {
                    journal.gratitude.forEach { item ->
                        Li(attrs = {
                            style {
                                marginBottom(4.px)
                                lineHeight("1.6")
                            }
                        }) {
                            Text(item)
                        }
                    }
                }
            }
        }
        
        // Next Steps
        if (journal.nextSteps.isNotEmpty()) {
            Div(attrs = {
                style {
                    marginBottom(20.px)
                }
            }) {
                H3(attrs = {
                    style {
                        fontSize(16.px)
                        fontWeight(600)
                        marginBottom(8.px)
                        color(Color("var(--text)"))
                    }
                }) {
                    Text("🚀 Next Steps")
                }
                Ul(attrs = {
                    style {
                        marginLeft(20.px)
                    }
                }) {
                    journal.nextSteps.forEach { step ->
                        Li(attrs = {
                            style {
                                marginBottom(4.px)
                                lineHeight("1.6")
                            }
                        }) {
                            Text(step)
                        }
                    }
                }
            }
        }
        
        // Reflection Summary in a box
        Div(attrs = {
            style {
                borderRadius(12.px)
                padding(16.px, 18.px)
                backgroundColor(Color("var(--summary-bg)"))
                border(1.px, LineStyle.Solid, Color("var(--summary-border)"))
                marginTop(16.px)
            }
        }) {
            Div(attrs = {
                style {
                    fontSize(14.px)
                    fontWeight(600)
                    marginBottom(10.px)
                    color(Color("var(--text)"))
                    opacity(0.9)
                }
            }) {
                Text("🪞 Reflection")
            }
            Div(attrs = {
                style {
                    fontSize(15.px)
                    color(Color("var(--text)"))
                    opacity(0.95)
                    lineHeight("1.7")
                    property("border-top", "1px solid var(--summary-border)")
                    paddingTop(12.px)
                }
            }) {
                Text(journal.reflectionSummary)
            }
        }
    }
}

@Composable
fun CompressionInsightPanel(viewModel: ChatViewModel) {
    Div(attrs = {
        style {
            padding(12.px, 16.px)
            backgroundColor(Color("var(--surface)"))
            property("border-top", "1px solid var(--border)")
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            gap(12.px)
        }
    }) {
        // Compression toggle
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                alignItems(AlignItems.Center)
                gap(12.px)
                flexWrap(FlexWrap.Wrap)
            }
        }) {
            Label(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    alignItems(AlignItems.Center)
                    gap(8.px)
                    cursor("pointer")
                }
                onClick { viewModel.useCompression = !viewModel.useCompression }
            }) {
                Input(type = InputType.Checkbox, attrs = {
                    checked(viewModel.useCompression)
                    onChange { viewModel.useCompression = it.value }
                })
                Text("Compression")
            }
            Button(attrs = {
                style {
                    padding(6.px, 12.px)
                    fontSize(12.px)
                }
                onClick { viewModel.forceSummarize() }
                if (viewModel.isLoading) disabled()
            }) {
                Text("Force Summarize Now")
            }
            Button(attrs = {
                style {
                    padding(6.px, 12.px)
                    fontSize(12.px)
                }
                onClick { viewModel.showRequestPrompt = !viewModel.showRequestPrompt }
            }) {
                Text(if (viewModel.showRequestPrompt) "Hide Request" else "Show Request")
            }
        }
        
        // Request prompt display
        if (viewModel.showRequestPrompt && viewModel.currentRequestPrompt != null) {
            Div(attrs = {
                style {
                    padding(12.px)
                    backgroundColor(Color("var(--background)"))
                    borderRadius(4.px)
                    border(1.px, LineStyle.Solid, Color("var(--border)"))
                    maxHeight(300.px)
                    overflowY("auto")
                    fontSize(11.px)
                    fontFamily("monospace")
                    whiteSpace("pre-wrap")
                    color(Color("var(--text)"))
                }
            }) {
                Text(viewModel.currentRequestPrompt!!)
            }
        }
        
        // Compression stats
        if (viewModel.compressionStats != null) {
            val stats = viewModel.compressionStats!!
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Row)
                    gap(16.px)
                    fontSize(12.px)
                    color(Color("var(--text)"))
                    opacity(0.8)
                }
            }) {
                Span { Text("Raw: ${stats.tokensRawApprox} tokens") }
                Span { Text("Compressed: ${stats.tokensCompressedApprox} tokens") }
                Span(attrs = {
                    style {
                        fontWeight(600)
                        color(Color("#4caf50"))
                    }
                }) {
                    Text("Savings: ${stats.savingsPercent}%")
                }
            }
        }
        
        // Latest summary preview
        if (viewModel.latestSummaryPreview != null) {
            Div(attrs = {
                style {
                    fontSize(12.px)
                    color(Color("var(--text)"))
                    opacity(0.7)
                    padding(8.px)
                    backgroundColor(Color("var(--background)"))
                    borderRadius(4.px)
                    cursor("pointer")
                }
                onClick { viewModel.showSummaryExpanded = !viewModel.showSummaryExpanded }
            }) {
                Text(if (viewModel.showSummaryExpanded) viewModel.latestSummaryPreview!! else "${viewModel.latestSummaryPreview!!.take(100)}...")
            }
        }
        
        // A/B comparison buttons
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                gap(8.px)
                fontSize(11.px)
                color(Color("var(--text)"))
                opacity(0.7)
            }
        }) {
            Text("Quick test:")
            Button(attrs = {
                style {
                    padding(4.px, 8.px)
                    fontSize(11.px)
                }
                if (viewModel.isLoading) disabled()
            }) {
                Text("Ask (Compressed)")
            }
            Button(attrs = {
                style {
                    padding(4.px, 8.px)
                    fontSize(11.px)
                }
                if (viewModel.isLoading) disabled()
            }) {
                Text("Ask (Raw)")
            }
            Span(attrs = {
                style {
                    fontSize(10.px)
                    opacity(0.6)
                }
            }) {
                Text("(Use main input + Send for normal flow)")
            }
        }
    }
}

@Composable
fun ChatInput(onSend: (String) -> Unit, isLoading: Boolean = false, placeholder: String = "Type a message...", viewModel: ChatViewModel? = null) {
    var inputValue by remember { mutableStateOf("") }
    
    Div(attrs = {
        classes(AppStylesheet.chatInput)
        style {
            property("class", "chat-input")
        }
    }) {
        Input(type = InputType.Text, attrs = {
            classes(AppStylesheet.input)
            placeholder(placeholder)
            value(inputValue)
            if (isLoading) disabled()
            onInput {
                inputValue = it.value
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
            classes(AppStylesheet.button, AppStylesheet.sendButton)
            if (isLoading) disabled()
            onClick { 
                val text = inputValue.trim()
                if (text.isNotEmpty() && !isLoading) {
                    onSend(text)
                    inputValue = ""
                }
            }
        }) {
            Text(if (isLoading) "..." else "Send")
        }
    }
}

class ChatViewModel(private val scope: CoroutineScope) {
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var toolCalls by mutableStateOf<Map<String, List<ToolCall>>>(emptyMap())
    var readingSummaries by mutableStateOf<Map<String, ReadingSummary>>(emptyMap())
    var isLoading by mutableStateOf(false)
    var conversations by mutableStateOf<List<Conversation>>(emptyList())
    var currentConversationId: String? by mutableStateOf(null)
    var isLoadingConversations by mutableStateOf(false)
    
    // Journal mode state
    var journalMessages by mutableStateOf<List<ChatMessage>>(emptyList())
    var journalResponses by mutableStateOf<Map<String, JournalResponse>>(emptyMap())
    var journalConversationHistory by mutableStateOf<List<String>>(emptyList())
    
    // Compression state
    var useCompression by mutableStateOf(true)
    var compressionStats by mutableStateOf<CompressionStats?>(null)
    var latestSummaryPreview by mutableStateOf<String?>(null)
    var showSummaryExpanded by mutableStateOf(false)
    var abComparisonResults by mutableStateOf<Pair<AgentResponse?, AgentResponse?>>(null to null)
    var showRequestPrompt by mutableStateOf(false)
    var currentRequestPrompt by mutableStateOf<String?>(null)

    // External memory state
    var useMemory by mutableStateOf(true)
    var memories by mutableStateOf<List<MemoryEntry>>(emptyList())
    
    private val transport = HttpTransport("http://localhost:8081")
    
    fun loadConversations() {
        if (isLoadingConversations) return
        isLoadingConversations = true
        scope.launch {
            try {
                conversations = transport.listConversations()
                if (conversations.isEmpty()) {
                    createNewChat()
                } else if (currentConversationId == null) {
                    loadConversation(conversations.first().id)
                }
            } catch (e: Exception) {
                println("Error loading conversations: ${e.message}")
            } finally {
                isLoadingConversations = false
            }
        }
    }
    
    fun loadConversation(id: String) {
        // Prevent reloading if already loading the same conversation
        if (isLoading && currentConversationId == id) return
        
        isLoading = true
        scope.launch {
            try {
                val conversation = transport.getConversation(id)
                messages = conversation.messages
                toolCalls = conversation.toolCalls.groupBy { it.timestamp.toString() }
                readingSummaries = emptyMap() // Clear summaries when loading conversation
                currentConversationId = id

                // Load external memories for this conversation
                if (useMemory) {
                    runCatching { transport.listMemories(id) }
                        .onSuccess { memories = it }
                } else {
                    memories = emptyList()
                }
            } catch (e: Exception) {
                messages = listOf(ChatMessage("assistant", "Error loading conversation: ${e.message}"))
            } finally {
                isLoading = false
            }
        }
    }
    
    fun createNewChat() {
        scope.launch {
            try {
                val newConversation = transport.createConversation()
                currentConversationId = newConversation.id
                messages = emptyList()
                toolCalls = emptyMap()
                readingSummaries = emptyMap()
                memories = emptyList()
                loadConversations() // Refresh list
            } catch (e: Exception) {
                println("Error creating conversation: ${e.message}")
            }
        }
    }
    
    fun deleteConversation(id: String) {
        scope.launch {
            try {
                transport.deleteConversation(id)
                if (currentConversationId == id) {
                    currentConversationId = null
                    messages = emptyList()
                    toolCalls = emptyMap()
                    readingSummaries = emptyMap()
                }
                loadConversations() // Refresh list
            } catch (e: Exception) {
                println("Error deleting conversation: ${e.message}")
            }
        }
    }
    
    fun sendMessage(text: String) {
        // Check if this is a /summarize command
        if (text.startsWith("/summarize") || text.startsWith("/summarize ")) {
            val textToSummarize = text.removePrefix("/summarize").trim()
            if (textToSummarize.isNotEmpty()) {
                summarizeText(textToSummarize)
            } else {
                // If no text provided, summarize the last assistant message
                val lastAssistantMessage = messages.lastOrNull { it.role == "assistant" }
                if (lastAssistantMessage != null) {
                    summarizeText(lastAssistantMessage.content)
                } else {
                    messages = messages + ChatMessage("assistant", "No text to summarize. Please provide text after /summarize or have an assistant message.")
                }
            }
            return
        }
        
        if (currentConversationId == null) {
            createNewChat()
            // Wait a bit for the conversation to be created
            scope.launch {
                kotlinx.coroutines.delay(100)
                sendMessageInternal(text)
            }
        } else {
            sendMessageInternal(text)
        }
    }
    
    private fun summarizeText(text: String) {
        if (isLoading) return
        isLoading = true
        
        // Only show "/summarize" in the UI, not the full text
        val userMessage = ChatMessage("user", "/summarize")
        messages = messages + userMessage
        
        scope.launch {
            try {
                val summary = transport.summarize(text)
                val summaryMessage = ChatMessage("assistant", "Summary generated for: ${summary.title}")
                messages = messages + summaryMessage
                
                // Store summary with the summary message timestamp
                readingSummaries = readingSummaries + (summaryMessage.timestamp.toString() to summary)
            } catch (e: Exception) {
                messages = messages + ChatMessage("assistant", "Error generating summary: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
    
    private var isSendingMessage = false
    
    private fun sendMessageInternal(text: String, useCompressionFlag: Boolean = useCompression) {
        // Prevent duplicate sends
        if (isSendingMessage || isLoading) return
        isSendingMessage = true
        
        val userMessage = ChatMessage("user", text)
        val currentMessages = messages
        messages = currentMessages + userMessage
        isLoading = true
        
        scope.launch {
            try {
                val request = AgentRequest(
                    messages = messages,
                    conversationId = currentConversationId,
                    useCompression = useCompressionFlag,
                    useMemory = useMemory
                )
                val response = transport.send(request)
                
                // Only add response if we're still on the same conversation
                if (currentConversationId != null) {
                    messages = messages + response.message
                    if (response.toolCalls.isNotEmpty()) {
                        toolCalls = toolCalls + (response.message.timestamp.toString() to response.toolCalls)
                    }
                    // Update compression & memory stats
                    compressionStats = response.compression
                    latestSummaryPreview = response.latestSummaryPreview
                    currentRequestPrompt = response.requestPrompt
                    response.memories?.let { memories = it }
                }
                
                // Refresh conversation list to update titles (non-blocking, don't reload current conversation)
                scope.launch {
                    try {
                        val updatedList = transport.listConversations()
                        conversations = updatedList
                    } catch (e: Exception) {
                        // Ignore errors when refreshing list
                    }
                }
            } catch (e: Exception) {
                // Only add error if we're still on the same conversation
                if (currentConversationId != null) {
                    messages = messages + ChatMessage("assistant", "Error: ${e.message}")
                }
            } finally {
                isLoading = false
                isSendingMessage = false
            }
        }
    }
    
    fun sendMessageCompressed(text: String) {
        sendMessageInternal(text, useCompressionFlag = true)
    }
    
    fun sendMessageRaw(text: String) {
        sendMessageInternal(text, useCompressionFlag = false)
    }
    
    fun sendMessageABComparison(text: String) {
        if (isSendingMessage || isLoading) return
        abComparisonResults = null to null
        
        scope.launch {
            try {
                val userMessage = ChatMessage("user", text)
                val currentMessages = messages
                messages = currentMessages + userMessage
                isLoading = true
                
                // Send both requests in parallel
                val compressedRequest = AgentRequest(
                    messages = messages,
                    conversationId = currentConversationId,
                    useCompression = true,
                    useMemory = useMemory
                )
                val rawRequest = AgentRequest(
                    messages = messages,
                    conversationId = currentConversationId,
                    useCompression = false,
                    useMemory = useMemory
                )
                
                val compressedResponse = transport.send(compressedRequest)
                val rawResponse = transport.send(rawRequest)
                
                abComparisonResults = compressedResponse to rawResponse
                
                // Add both responses to messages for display
                if (currentConversationId != null) {
                    messages = messages + ChatMessage("assistant", "[Compressed] ${compressedResponse.message.content}", timestamp = compressedResponse.message.timestamp)
                    messages = messages + ChatMessage("assistant", "[Raw] ${rawResponse.message.content}", timestamp = rawResponse.message.timestamp)
                }
            } catch (e: Exception) {
                if (currentConversationId != null) {
                    messages = messages + ChatMessage("assistant", "Error in A/B comparison: ${e.message}")
                }
            } finally {
                isLoading = false
            }
        }
    }
    
    fun forceSummarize() {
        if (isLoading || currentConversationId == null) return
        isLoading = true
        
        scope.launch {
            try {
                val request = AgentRequest(
                    messages = messages,
                    conversationId = currentConversationId,
                    useCompression = true,
                    useMemory = useMemory
                )
                val state = transport.forceSummarize(request)
                // Update compression stats if available
                latestSummaryPreview = state.segments.mapNotNull { it.summary }.lastOrNull()?.take(280)
            } catch (e: Exception) {
                messages = messages + ChatMessage("assistant", "Error forcing summarize: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
    
    fun exportMessages() {
        val json = Json { prettyPrint = true }.encodeToString(ListSerializer(ChatMessage.serializer()), messages)
        val timestamp = js("Date.now()").toString()
        val blob = js("new Blob([json], { type: 'application/json' })")
        val url = js("URL.createObjectURL(blob)")
        val a = js("document.createElement('a')")
        a.href = url
        a.download = "chat-export-$timestamp.json"
        js("document.body.appendChild(a)")
        a.click()
        js("document.body.removeChild(a)")
        js("URL.revokeObjectURL(url)")
    }
    
    fun sendJournalMessage(text: String) {
        if (isLoading) return
        isLoading = true
        
        val userMessage = ChatMessage("user", text)
        journalMessages = journalMessages + userMessage
        journalConversationHistory = journalConversationHistory + text
        
        scope.launch {
            try {
                val response = transport.journal(text, journalConversationHistory)
                
                // Store the journal response
                journalResponses = journalResponses + (userMessage.timestamp.toString() to response)
                
                // If status is collecting, show assistant message with questions
                if (response.status == "collecting") {
                    val questionText = if (response.missing.isNotEmpty()) {
                        // The missing array should now contain actual questions, not field names
                        response.missing.joinToString("\n\n")
                    } else {
                        "Tell me more about your day..."
                    }
                    val assistantMessage = ChatMessage("assistant", questionText)
                    journalMessages = journalMessages + assistantMessage
                } else if (response.status == "ready" && response.journal != null) {
                    // Journal is ready, show a completion message
                    val assistantMessage = ChatMessage(
                        "assistant",
                        "✨ Your journal entry is ready! Here's your reflection:"
                    )
                    journalMessages = journalMessages + assistantMessage
                }
            } catch (e: Exception) {
                journalMessages = journalMessages + ChatMessage("assistant", "Error: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
    
    fun resetJournal() {
        journalMessages = emptyList()
        journalResponses = emptyMap()
        journalConversationHistory = emptyList()
    }
}

fun formatTimestamp(timestamp: Long): String {
    val date = js("new Date(timestamp)")
    return date.toLocaleTimeString()
}

/**
 * Converts markdown text to HTML and renders it
 */
@Composable
fun MarkdownText(text: String) {
    val htmlContent = remember(text) {
        markdownToHtml(text)
    }
    
    Div(attrs = {
        ref { element ->
            val div = element?.unsafeCast<HTMLDivElement>()
            if (div != null) {
                div.innerHTML = htmlContent
            }
            onDispose { }
        }
    })
}

/**
 * Simple markdown-to-HTML converter
 * Handles: headers, bold, italic, lists, code blocks, links
 */
fun markdownToHtml(markdown: String): String {
    var html = markdown
    
    // Protect code blocks first (they shouldn't be processed)
    val codeBlockPlaceholder = "___CODE_BLOCK___"
    val codeBlocks = mutableListOf<String>()
    var codeBlockIndex = 0
    html = html.replace(Regex("```([\\s\\S]*?)```")) { matchResult ->
        val placeholder = "$codeBlockPlaceholder$codeBlockIndex"
        codeBlocks.add("<pre><code>${escapeHtml(matchResult.groupValues[1])}</code></pre>")
        codeBlockIndex++
        placeholder
    }
    
    // Protect inline code
    val inlineCodePlaceholder = "___INLINE_CODE___"
    val inlineCodes = mutableListOf<String>()
    var inlineCodeIndex = 0
    html = html.replace(Regex("`([^`]+)`")) { matchResult ->
        val placeholder = "$inlineCodePlaceholder$inlineCodeIndex"
        inlineCodes.add("<code>${escapeHtml(matchResult.groupValues[1])}</code>")
        inlineCodeIndex++
        placeholder
    }
    
    // Split into lines for processing
    val lines = html.split("\n")
    val processedLines = mutableListOf<String>()
    var inList = false
    var listItems = mutableListOf<String>()
    
    for (line in lines) {
        val trimmed = line.trim()
        
        // Headers
        when {
            trimmed.startsWith("### ") -> {
                if (inList) {
                    processedLines.add("</ul>")
                    inList = false
                }
                processedLines.add("<h3>${escapeHtml(trimmed.substring(4))}</h3>")
            }
            trimmed.startsWith("## ") -> {
                if (inList) {
                    processedLines.add("</ul>")
                    inList = false
                }
                processedLines.add("<h2>${escapeHtml(trimmed.substring(3))}</h2>")
            }
            trimmed.startsWith("# ") -> {
                if (inList) {
                    processedLines.add("</ul>")
                    inList = false
                }
                processedLines.add("<h1>${escapeHtml(trimmed.substring(2))}</h1>")
            }
            // Unordered list items
            trimmed.matches(Regex("^[-*] (.+)$")) -> {
                if (!inList) {
                    inList = true
                }
                val content = trimmed.substring(2)
                listItems.add("<li>${processInlineMarkdown(content)}</li>")
            }
            // Ordered list items
            trimmed.matches(Regex("^\\d+\\. (.+)$")) -> {
                if (!inList) {
                    inList = true
                }
                val content = trimmed.replaceFirst(Regex("^\\d+\\. "), "")
                listItems.add("<li>${processInlineMarkdown(content)}</li>")
            }
            // Empty line
            trimmed.isEmpty() -> {
                if (inList && listItems.isNotEmpty()) {
                    processedLines.add("<ul>${listItems.joinToString("")}</ul>")
                    listItems.clear()
                    inList = false
                }
                processedLines.add("<p></p>")
            }
            // Regular line
            else -> {
                if (inList && listItems.isNotEmpty()) {
                    processedLines.add("<ul>${listItems.joinToString("")}</ul>")
                    listItems.clear()
                    inList = false
                }
                processedLines.add("<p>${processInlineMarkdown(trimmed)}</p>")
            }
        }
    }
    
    // Close any remaining list
    if (inList && listItems.isNotEmpty()) {
        processedLines.add("<ul>${listItems.joinToString("")}</ul>")
    }
    
    html = processedLines.joinToString("\n")
    
    // Restore code blocks
    codeBlocks.forEachIndexed { index, code ->
        html = html.replace("$codeBlockPlaceholder$index", code)
    }
    
    // Restore inline code
    inlineCodes.forEachIndexed { index, code ->
        html = html.replace("$inlineCodePlaceholder$index", code)
    }
    
    return html
}

/**
 * Process inline markdown (bold, italic, links) - but not headers or code
 */
fun processInlineMarkdown(text: String): String {
    var html = text
    
    // Links [text](url) - process before bold/italic
    html = html.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")) { 
        "<a href=\"${escapeHtml(it.groupValues[2])}\" target=\"_blank\" rel=\"noopener noreferrer\">${escapeHtml(it.groupValues[1])}</a>"
    }
    
    // Bold (**text** or __text__) - must come before italic
    html = html.replace(Regex("\\*\\*([^*]+)\\*\\*")) { "<strong>${escapeHtml(it.groupValues[1])}</strong>" }
    html = html.replace(Regex("__([^_]+)__")) { "<strong>${escapeHtml(it.groupValues[1])}</strong>" }
    
    // Italic (*text* or _text_) - after bold to avoid conflicts
    html = html.replace(Regex("\\*([^*]+)\\*")) { "<em>${escapeHtml(it.groupValues[1])}</em>" }
    html = html.replace(Regex("_([^_]+)_")) { "<em>${escapeHtml(it.groupValues[1])}</em>" }
    
    // Escape remaining HTML
    html = escapeHtml(html)
    
    // Restore our HTML tags
    html = html.replace("&lt;strong&gt;", "<strong>")
    html = html.replace("&lt;/strong&gt;", "</strong>")
    html = html.replace("&lt;em&gt;", "<em>")
    html = html.replace("&lt;/em&gt;", "</em>")
    html = html.replace("&lt;a ", "<a ")
    html = html.replace("&lt;/a&gt;", "</a>")
    
    return html
}

/**
 * Escape HTML entities
 */
fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

object AppStylesheet : StyleSheet() {
    val topBar by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        padding(16.px)
        backgroundColor(Color("var(--surface)"))
        property("border-bottom", "1px solid var(--border)")
    }
    
    val title by style {
        fontSize(20.px)
        fontWeight(600)
        color(Color("var(--text)"))
    }
    
    val topBarActions by style {
        display(DisplayStyle.Flex)
        gap(8.px)
    }
    
    val chatThread by style {
        flex(1)
        property("overflow-y", "auto")
        padding(16.px)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(12.px)
        backgroundColor(Color("var(--background)"))
    }
    
    val messageBubble by style {
        maxWidth(70.percent)
        padding(12.px, 16.px)
        borderRadius(12.px)
        marginBottom(8.px)
        property("word-wrap", "break-word")
        property("overflow-wrap", "break-word")
    }
    
    val messageUser by style {
        alignSelf(AlignSelf.FlexEnd)
        backgroundColor(Color("var(--primary)"))
        color(Color.white)
    }
    
    val messageAssistant by style {
        alignSelf(AlignSelf.FlexStart)
        backgroundColor(Color("var(--surface)"))
        color(Color("var(--text)"))
        border(1.px, LineStyle.Solid, Color("var(--border)"))
    }
    
    val messageContent by style {
        marginBottom(4.px)
    }
    
    
    val messageTime by style {
        fontSize(11.px)
        opacity(0.6)
        marginTop(4.px)
    }
    
    val toolCallCard by style {
        marginTop(8.px)
        padding(8.px)
        borderRadius(8.px)
        backgroundColor(Color("rgba(0,0,0,0.05)"))
        fontSize(12.px)
    }
    
    val toolCallHeader by style {
        fontWeight(600)
        marginBottom(4.px)
    }
    
    val toolCallInput by style {
        marginBottom(2.px)
        opacity(0.8)
    }
    
    val toolCallResult by style {
        fontWeight(500)
    }
    
    val typingIndicator by style {
        display(DisplayStyle.Flex)
        gap(4.px)
        padding(12.px, 16.px)
        fontSize(20.px)
        color(Color("var(--text)"))
        opacity(0.6)
    }
    
    val chatInput by style {
        display(DisplayStyle.Flex)
        gap(8.px)
        padding(16.px)
        backgroundColor(Color("var(--surface)"))
        property("border-top", "1px solid var(--border)")
    }
    
    val input by style {
        flex(1)
        padding(12.px, 16.px)
        borderRadius(8.px)
        border(1.px, LineStyle.Solid, Color("var(--border)"))
        backgroundColor(Color("var(--surface)"))
        color(Color("var(--text)"))
        fontSize(14.px)
        property("outline", "0px")
        
        property(":disabled", "opacity: 0.6; cursor: not-allowed;")
    }
    
    val button by style {
        padding(12.px, 24.px)
        borderRadius(8.px)
        border(0.px)
        backgroundColor(Color("var(--primary)"))
        color(Color.white)
        fontSize(14.px)
        fontWeight(500)
        cursor("pointer")
        property("transition", "background-color 0.2s")
        
        property(":disabled", "opacity: 0.6; cursor: not-allowed;")
    }
    
    val modeButton by style {
        padding(8.px, 16.px)
        backgroundColor(Color("var(--primary)"))
        color(Color.white)
        border(0.px)
        borderRadius(6.px)
        cursor("pointer")
        fontSize(14.px)
        fontWeight(500)
        property("transition", "background-color 0.2s")
    }
    
    val modeButtonHover by style {
        backgroundColor(Color("var(--primary-dark)"))
    }
    
    val iconButton by style {
        padding(8.px, 12.px)
        minWidth(40.px)
    }
    
    val reasoningButton by style {
        padding(10.px, 16.px)
        backgroundColor(Color("var(--primary)"))
        color(Color.white)
        border(0.px)
        borderRadius(6.px)
        cursor("pointer")
        fontSize(14.px)
        fontWeight(500)
        property("transition", "background-color 0.2s")
        
        property(":hover:not(:disabled)", "background-color: var(--primary-dark);")
        property(":disabled", "opacity: 0.6; cursor: not-allowed;")
    }
    
    val sendButton by style {
        minWidth(80.px)
    }
    
    val sidebar by style {
        width(280.px)
        height(100.vh)
        backgroundColor(Color("var(--surface)"))
        property("border-right", "1px solid var(--border)")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("overflow-y", "auto")
    }
    
    val sidebarHeader by style {
        padding(16.px)
        property("border-bottom", "1px solid var(--border)")
    }
    
    val newChatButton by style {
        width(100.percent)
        justifyContent(JustifyContent.Center)
    }
    
    val conversationList by style {
        flex(1)
        property("overflow-y", "auto")
        padding(8.px)
    }
    
    val conversationItem by style {
        padding(12.px, 16.px)
        borderRadius(8.px)
        marginBottom(4.px)
        cursor("pointer")
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        property("transition", "background-color 0.2s")
        property(":hover", "background-color: rgba(0, 0, 0, 0.05)")
    }
    
    val conversationItemSelected by style {
        backgroundColor(Color("var(--primary)"))
        color(Color.white)
    }
    
    val conversationTitle by style {
        flex(1)
        property("overflow", "hidden")
        property("text-overflow", "ellipsis")
        property("white-space", "nowrap")
        fontSize(14.px)
    }
    
    val deleteButton by style {
        padding(4.px, 8.px)
        minWidth(24.px)
        fontSize(18.px)
        backgroundColor(Color.transparent)
        border(0.px)
        color(Color("inherit"))
        cursor("pointer")
        opacity(0.6)
        property(":hover", "opacity: 1")
    }
    
    val emptyState by style {
        flex(1)
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        color(Color("var(--text)"))
        fontSize(16.px)
        opacity(0.6)
        padding(32.px)
        textAlign("center")
    }
    
}

