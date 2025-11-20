import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import RemindersViewModel
import models.Reminder

@Composable
fun RemindersView(viewModel: RemindersViewModel) {
    // Load reminders on first render
    LaunchedEffect(Unit) {
        viewModel.loadReminders()
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
                Text("🔔 Reminders")
            }
            P(attrs = {
                style {
                    fontSize(14.px)
                    color(Color("var(--text)"))
                    opacity(0.7)
                    margin(0.px)
                }
            }) {
                Text("Manage your reminders and get AI-powered summaries.")
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
                    fontSize(14.px)
                }
            }) {
                Text(viewModel.error!!)
            }
        }

        // Add Reminder Form
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
                    margin(0.px, 0.px, 16.px, 0.px)
                }
            }) {
                Text("Add New Reminder")
            }
            
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    gap(12.px)
                }
            }) {
                Input(type = InputType.Text, attrs = {
                    value(viewModel.newReminderText)
                    onInput { ev ->
                        viewModel.updateNewReminderText((ev.target as? org.w3c.dom.HTMLInputElement)?.value ?: "")
                    }
                    placeholder("Reminder text...")
                    style {
                        padding(12.px, 16.px)
                        fontSize(14.px)
                        borderRadius(8.px)
                        border(1.px, LineStyle.Solid, Color("var(--border)"))
                        backgroundColor(Color("var(--background)"))
                        color(Color("var(--text)"))
                        width(100.percent)
                    }
                })
                
                Input(type = InputType.Text, attrs = {
                    value(viewModel.newReminderDueDate)
                    onInput { ev ->
                        viewModel.updateNewReminderDueDate((ev.target as? org.w3c.dom.HTMLInputElement)?.value ?: "")
                    }
                    placeholder("Due date (YYYY-MM-DD HH:MM, optional)")
                    style {
                        padding(12.px, 16.px)
                        fontSize(14.px)
                        borderRadius(8.px)
                        border(1.px, LineStyle.Solid, Color("var(--border)"))
                        backgroundColor(Color("var(--background)"))
                        color(Color("var(--text)"))
                        width(100.percent)
                    }
                })
                
                Button(attrs = {
                    classes(AppStylesheet.button)
                    style {
                        alignSelf(AlignSelf.FlexStart)
                        padding(12.px, 24.px)
                        fontSize(14.px)
                    }
                    onClick { viewModel.addReminder() }
                    if (viewModel.isLoading || viewModel.newReminderText.isBlank()) disabled()
                }) {
                    Text(if (viewModel.isLoading) "Adding..." else "Add Reminder")
                }
            }
        }

        // Summary Section
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                gap(12.px)
            }
        }) {
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    justifyContent(JustifyContent.SpaceBetween)
                    alignItems(AlignItems.Center)
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
                    Text("Current Summary")
                }
                Button(attrs = {
                    classes(AppStylesheet.button)
                    style {
                        padding(8.px, 16.px)
                        fontSize(14.px)
                    }
                    onClick { viewModel.loadSummary() }
                    if (viewModel.isLoadingSummary) disabled()
                }) {
                    Text(if (viewModel.isLoadingSummary) "Loading..." else "Get Current Summary")
                }
            }
            
            if (viewModel.summary != null) {
                val s = viewModel.summary!!
                Div(attrs = {
                    style {
                        padding(20.px)
                        backgroundColor(Color("var(--surface)"))
                        border(1.px, LineStyle.Solid, Color("var(--border)"))
                        borderRadius(8.px)
                    }
                }) {
                    Div(attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            gap(12.px)
                        }
                    }) {
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                gap(16.px)
                                fontSize(14.px)
                                color(Color("var(--text)"))
                            }
                        }) {
                            Span {
                                Text("Pending: ${s.pendingCount}")
                            }
                            Span {
                                Text("Overdue: ${s.overdueCount}")
                            }
                        }
                        
                        if (s.aiSummary != null) {
                            Div(attrs = {
                                style {
                                    padding(12.px)
                                    backgroundColor(Color("var(--background)"))
                                    borderRadius(4.px)
                                    fontSize(14.px)
                                    color(Color("var(--text)"))
                                    property("line-height", "1.6")
                                    whiteSpace("pre-wrap")
                                }
                            }) {
                                Text(s.aiSummary!!)
                            }
                        }
                    }
                }
            }
        }

        // Reminders List
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
                Text("Pending Reminders (${viewModel.reminders.size})")
            }
            
            if (viewModel.reminders.isEmpty() && !viewModel.isLoading) {
                Div(attrs = {
                    style {
                        padding(24.px)
                        textAlign("center")
                        color(Color("var(--text)"))
                        opacity(0.6)
                    }
                }) {
                    Text("No pending reminders. Add one above!")
                }
            } else {
                viewModel.reminders.forEach { reminder ->
                    ReminderCard(reminder)
                }
            }
        }
    }
}

@Composable
fun ReminderCard(reminder: Reminder) {
    val dueDate = reminder.dueDate
    val isOverdue = dueDate != null && dueDate < kotlin.js.Date().getTime().toLong()
    
    Div(attrs = {
        style {
            padding(16.px)
            backgroundColor(Color("var(--surface)"))
            border(1.px, LineStyle.Solid, if (isOverdue) Color("#f44336") else Color("var(--border)"))
            borderRadius(8.px)
        }
    }) {
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                gap(8.px)
            }
        }) {
            // Text
            P(attrs = {
                style {
                    fontSize(14.px)
                    color(Color("var(--text)"))
                    margin(0.px)
                    fontWeight(if (isOverdue) 600 else 400)
                }
            }) {
                Text(reminder.text)
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
                }
            }) {
                Span {
                    if (dueDate != null) {
                        Text("Due: ${formatDate(dueDate)}${if (isOverdue) " (OVERDUE)" else ""}")
                    } else {
                        Text("No due date")
                    }
                }
                Span {
                    Text("Created: ${formatDate(reminder.createdAt)}")
                }
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    return try {
        val date = kotlin.js.Date(timestamp.toDouble())
        date.toLocaleString()
    } catch (e: Exception) {
        timestamp.toString()
    }
}

