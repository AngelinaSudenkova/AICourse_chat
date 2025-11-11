package ui

import AppStylesheet
import ModelComparisonViewModel
import UiModelSpec
import UiRun
import ComparisonTask
import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun ModelComparisonTab(viewModel: ModelComparisonViewModel) {
    Div(attrs = {
        style {
            padding(24.px)
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            gap(20.px)
            property("overflow-y", "auto")
            height(100.percent)
        }
    }) {
        H2(attrs = {
            style {
                marginTop(0.px)
                fontSize(24.px)
                fontWeight(700)
                color(Color("var(--text)"))
            }
        }) {
            Text("🧪 Model Comparison")
        }

        if (viewModel.error != null) {
            ErrorBanner(error = viewModel.error ?: "", onDismiss = { viewModel.clearError() })
        }

        TaskTabs(
            tasks = viewModel.tasks,
            current = viewModel.currentTask,
            onSelect = { viewModel.setTask(it) }
        )

        PromptEditor(
            prompt = viewModel.prompt,
            onChange = viewModel::updatePrompt
        )

        ModelsEditor(
            models = viewModel.models,
            onIdChange = viewModel::updateModelId,
            onPriceInChange = viewModel::updateModelPriceIn,
            onPriceOutChange = viewModel::updateModelPriceOut,
            onAddModel = viewModel::addModel,
            onRemoveModel = viewModel::removeModel
        )

        ActionButtons(
            isLoading = viewModel.isLoading,
            onRun = viewModel::run,
            onReset = viewModel::reset
        )

        ResultsSection(
            runs = viewModel.results,
            onToggle = viewModel::toggleExpand
        )
    }
}

@Composable
private fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    Div(attrs = {
        style {
            backgroundColor(Color("#fdecea"))
            borderRadius(10.px)
            border(1.px, LineStyle.Solid, Color("#f5c2c0"))
            padding(12.px, 16.px)
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.SpaceBetween)
            alignItems(AlignItems.Center)
            color(Color("#8a1c1c"))
        }
    }) {
        Span {
            Text(error)
        }
        Button(attrs = {
            classes(AppStylesheet.button)
            onClick { onDismiss() }
        }) {
            Text("Dismiss")
        }
    }
}

@Composable
private fun PromptEditor(prompt: String, onChange: (String) -> Unit) {
    Div {
        Label(attrs = {
            style {
                display(DisplayStyle.Block)
                fontWeight(600)
                marginBottom(8.px)
                color(Color("var(--text)"))
            }
        }) { Text("Prompt") }

        TextArea(attrs = {
            style {
                width(100.percent)
                minHeight(160.px)
                padding(16.px)
                borderRadius(10.px)
                border(1.px, LineStyle.Solid, Color("var(--border)"))
                backgroundColor(Color("var(--surface)"))
                color(Color("var(--text)"))
                fontSize(15.px)
                property("resize", "vertical")
            }
            value(prompt)
            onInput { onChange(it.value) }
        })
    }
}

@Composable
private fun ModelsEditor(
    models: List<UiModelSpec>,
    onIdChange: (Int, String) -> Unit,
    onPriceInChange: (Int, String) -> Unit,
    onPriceOutChange: (Int, String) -> Unit,
    onAddModel: () -> Unit,
    onRemoveModel: (Int) -> Unit
) {
    Div {
        Label(attrs = {
            style {
                display(DisplayStyle.Block)
                fontWeight(600)
                marginBottom(12.px)
                color(Color("var(--text)"))
            }
        }) { Text("Models") }

        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                gap(8.px)
            }
        }) {
            models.forEachIndexed { index, model ->
                ModelRow(
                    index = index,
                    model = model,
                    onIdChange = onIdChange,
                    onPriceInChange = onPriceInChange,
                    onPriceOutChange = onPriceOutChange,
                    onRemoveModel = onRemoveModel,
                    isOnlyRow = models.size == 1
                )
            }
            Button(attrs = {
                classes(AppStylesheet.button)
                onClick { onAddModel() }
            }) {
                Text("+ Add Model")
            }
        }
    }
}

@Composable
private fun ModelRow(
    index: Int,
    model: UiModelSpec,
    onIdChange: (Int, String) -> Unit,
    onPriceInChange: (Int, String) -> Unit,
    onPriceOutChange: (Int, String) -> Unit,
    onRemoveModel: (Int) -> Unit,
    isOnlyRow: Boolean
) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            flexWrap(FlexWrap.Wrap)
            gap(8.px)
            padding(12.px)
            borderRadius(10.px)
            border(1.px, LineStyle.Solid, Color("var(--border)"))
            backgroundColor(Color("var(--surface)"))
        }
    }) {
        Input(type = InputType.Text, attrs = {
            style {
                flexGrow(1)
                minWidth(220.px)
                padding(10.px)
                borderRadius(8.px)
                border(1.px, LineStyle.Solid, Color("var(--border)"))
                backgroundColor(Color("var(--background)"))
                color(Color("var(--text)"))
            }
            placeholder("model id")
            value(model.id)
            onInput { onIdChange(index, it.value) }
        })

        Input(type = InputType.Text, attrs = {
            style {
                width(140.px)
                padding(10.px)
                borderRadius(8.px)
                border(1.px, LineStyle.Solid, Color("var(--border)"))
                backgroundColor(Color("var(--background)"))
                color(Color("var(--text)"))
            }
            placeholder("price in (USD)")
            value(model.priceIn)
            onInput { onPriceInChange(index, it.value) }
        })

        Input(type = InputType.Text, attrs = {
            style {
                width(140.px)
                padding(10.px)
                borderRadius(8.px)
                border(1.px, LineStyle.Solid, Color("var(--border)"))
                backgroundColor(Color("var(--background)"))
                color(Color("var(--text)"))
            }
            placeholder("price out (USD)")
            value(model.priceOut)
            onInput { onPriceOutChange(index, it.value) }
        })

        Button(attrs = {
            classes(AppStylesheet.button)
            if (isOnlyRow) attr("disabled", "true")
            onClick {
                if (!isOnlyRow) onRemoveModel(index)
            }
        }) {
            Text("Remove")
        }
    }
}

@Composable
private fun ActionButtons(
    isLoading: Boolean,
    onRun: () -> Unit,
    onReset: () -> Unit
) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            gap(12.px)
        }
    }) {
        Button(attrs = {
            classes(AppStylesheet.button)
            if (isLoading) attr("disabled", "true")
            onClick { onRun() }
        }) {
            Text(if (isLoading) "Running..." else "Run Comparison")
        }

        Button(attrs = {
            classes(AppStylesheet.button)
            if (isLoading) attr("disabled", "true")
            onClick { onReset() }
        }) {
            Text("Reset")
        }
    }
}

@Composable
private fun ResultsSection(
    runs: List<UiRun>,
    onToggle: (Int) -> Unit
) {
    if (runs.isEmpty()) return

    Table(attrs = {
        style {
            width(100.percent)
            property("border-collapse", "separate")
            property("border-spacing", "0px")
        }
    }) {
        Thead {
            Tr {
                headerCell("Model")
                headerCell("Latency (ms)")
                headerCell("Input toks ~")
                headerCell("Output toks ~")
                headerCell("Cost (USD)")
                headerCell("Output")
            }
        }
        Tbody {
            runs.forEachIndexed { index, run ->
                ResultRow(index = index, run = run, onToggle = onToggle)
            }
        }
    }
}

@Composable
private fun headerCell(text: String) {
    Th(attrs = {
        style {
            padding(12.px)
            backgroundColor(Color("var(--surface)"))
            property("border-bottom", "2px solid var(--border)")
            textAlign("left")
            color(Color("var(--text)"))
            fontWeight(600)
        }
    }) {
        Text(text)
    }
}

@Composable
private fun ResultRow(index: Int, run: UiRun, onToggle: (Int) -> Unit) {
    Tr {
        TdCell {
            A(attrs = {
                href("https://huggingface.co/${run.model}")
                attr("target", "_blank")
                attr("rel", "noopener noreferrer")
                style {
                    color(Color("var(--primary)"))
                    fontWeight(600)
                    property("text-decoration", "none")
                }
            }) {
                Text(run.model)
            }
        }
        TdCell { Text(run.latencyMs.toString()) }
        TdCell { Text(run.inputTokensApprox.toString()) }
        TdCell { Text(run.outputTokensApprox.toString()) }
        TdCell {
            val costText = run.costUSD?.let { "\$${formatCost(it)}" } ?: "—"
            Text(costText)
        }
        TdCell {
            val snippet = if (run.output.length > 200) run.output.take(200) + "…" else run.output
            Div(attrs = {
                style {
                    whiteSpace("pre-wrap")
                    color(Color("var(--text)"))
                }
            }) {
                Text(snippet)
            }
            if (run.output.length > 200) {
                Button(attrs = {
                    classes(AppStylesheet.button)
                    style {
                        marginTop(8.px)
                        padding(6.px, 12.px)
                        fontSize(12.px)
                    }
                    onClick { onToggle(index) }
                }) {
                    Text(if (run.expanded) "Collapse" else "Expand")
                }
            }
            if (run.expanded) {
                Div(attrs = {
                    style {
                        marginTop(10.px)
                        padding(10.px)
                        borderRadius(8.px)
                        border(1.px, LineStyle.Solid, Color("var(--border)"))
                        backgroundColor(Color("var(--background)"))
                        whiteSpace("pre-wrap")
                        color(Color("var(--text)"))
                        maxHeight(220.px)
                        overflowY("auto")
                    }
                }) {
                    Text(run.output)
                }
            }
        }
    }
}

@Composable
private fun TdCell(content: @Composable () -> Unit) {
    Td(attrs = {
        style {
            padding(12.px)
            property("border-bottom", "1px solid var(--border)")
            property("vertical-align", "top")
        }
    }) {
        content()
    }
}

private fun formatCost(value: Double): String {
    val number = js("Number")(value)
    return number.asDynamic().toFixed(4) as String
}

@Composable
private fun TaskTabs(
    tasks: List<ComparisonTask>,
    current: ComparisonTask,
    onSelect: (ComparisonTask) -> Unit
) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            gap(12.px)
        }
    }) {
        tasks.forEach { task ->
            Button(attrs = {
                classes(AppStylesheet.button)
                if (task == current) {
                    style {
                        backgroundColor(Color("var(--primary)"))
                        color(Color.white)
                    }
                } else {
                    style {
                        backgroundColor(Color("var(--surface)"))
                        color(Color("var(--text)"))
                        border(1.px, LineStyle.Solid, Color("var(--border)"))
                    }
                }
                onClick { onSelect(task) }
            }) {
                Text(task.label)
            }
        }
    }
}


