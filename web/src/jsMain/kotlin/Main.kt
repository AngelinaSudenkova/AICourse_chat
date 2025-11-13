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
                val allowed = setOf("chat", "journal", "reasoning", "temperature", "modelComparison")
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
                onExport = { viewModel.exportMessages() }
            )
            
            if (mode == "journal") {
                JournalView(viewModel)
            } else if (mode == "reasoning") {
                ReasoningLabView(reasoningViewModel)
            } else if (mode == "temperature") {
                TemperatureLabView(temperatureViewModel)
            } else if (mode == "modelComparison") {
                ModelComparisonTab(modelComparisonViewModel)
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
            
            if (mode == "chat" || mode == "journal") {
                if (mode == "chat") {
                    CompressionInsightPanel(viewModel)
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
    onExport: () -> Unit
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
                val request = AgentRequest(messages = messages, conversationId = currentConversationId, useCompression = useCompressionFlag)
                val response = transport.send(request)
                
                // Only add response if we're still on the same conversation
                if (currentConversationId != null) {
                    messages = messages + response.message
                    if (response.toolCalls.isNotEmpty()) {
                        toolCalls = toolCalls + (response.message.timestamp.toString() to response.toolCalls)
                    }
                    // Update compression stats
                    compressionStats = response.compression
                    latestSummaryPreview = response.latestSummaryPreview
                    currentRequestPrompt = response.requestPrompt
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
                val compressedRequest = AgentRequest(messages = messages, conversationId = currentConversationId, useCompression = true)
                val rawRequest = AgentRequest(messages = messages, conversationId = currentConversationId, useCompression = false)
                
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
                val request = AgentRequest(messages = messages, conversationId = currentConversationId, useCompression = true)
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

