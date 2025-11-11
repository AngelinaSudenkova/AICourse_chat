import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import AppStylesheet
import structured.TempRun

@Composable
fun TemperatureLabView(viewModel: TemperatureLabViewModel) {
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
        TemperatureTaskBox(
            title = "🔥 Coding Task",
            prompt = viewModel.codePrompt,
            onPromptChange = { viewModel.codePrompt = it },
            results = viewModel.codeResults,
            error = viewModel.codeError,
            compare = viewModel.codeCompare,
            compareError = viewModel.codeCompareError,
            loading = viewModel.codeLoading,
            loadingTemp = viewModel.codeLoadingTemp,
            compareLoading = viewModel.codeCompareLoading,
            onRunTemp = { viewModel.runCode(it) },
            onRunAll = { viewModel.runCodeAll() },
            onCompare = { viewModel.compareCode() },
            onReset = { viewModel.resetCode() }
        )

        TemperatureTaskBox(
            title = "🎨 Creative Task",
            prompt = viewModel.storyPrompt,
            onPromptChange = { viewModel.storyPrompt = it },
            results = viewModel.storyResults,
            error = viewModel.storyError,
            compare = viewModel.storyCompare,
            compareError = viewModel.storyCompareError,
            loading = viewModel.storyLoading,
            loadingTemp = viewModel.storyLoadingTemp,
            compareLoading = viewModel.storyCompareLoading,
            onRunTemp = { viewModel.runStory(it) },
            onRunAll = { viewModel.runStoryAll() },
            onCompare = { viewModel.compareStory() },
            onReset = { viewModel.resetStory() }
        )
    }
}

@Composable
private fun TemperatureTaskBox(
    title: String,
    prompt: String,
    onPromptChange: (String) -> Unit,
    results: List<TempRun>,
    error: String?,
    compare: CompareCard?,
    compareError: String?,
    loading: Boolean,
    loadingTemp: Double?,
    compareLoading: Boolean,
    onRunTemp: (Double) -> Unit,
    onRunAll: () -> Unit,
    onCompare: () -> Unit,
    onReset: () -> Unit
) {
    Div(attrs = {
        style {
            borderRadius(16.px)
            border(1.px, LineStyle.Solid, Color("var(--border)"))
            backgroundColor(Color("var(--surface)"))
            padding(20.px)
            property("box-shadow", "0 4px 18px rgba(0,0,0,0.06)")
        }
    }) {
        H3(attrs = {
            style {
                fontSize(20.px)
                fontWeight(600)
                color(Color("var(--text)"))
                marginTop(0.px)
                marginBottom(16.px)
            }
        }) { Text(title) }

        Label(attrs = {
            style {
                display(DisplayStyle.Block)
                marginBottom(8.px)
                fontSize(14.px)
                fontWeight(600)
                color(Color("var(--text)"))
            }
        }) { Text("Prompt") }

        TextArea(attrs = {
            style {
                width(100.percent)
                minHeight(90.px)
                padding(12.px)
                borderRadius(10.px)
                border(1.px, LineStyle.Solid, Color("var(--border)"))
                backgroundColor(Color("var(--background)"))
                color(Color("var(--text)"))
                fontFamily("monospace")
                fontSize(14.px)
                property("resize", "vertical")
            }
            value(prompt)
            onInput { onPromptChange(it.value) }
        })

        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexWrap(FlexWrap.Wrap)
                gap(12.px)
                marginTop(16.px)
                marginBottom(16.px)
            }
        }) {
            TemperatureButton("T = 0.0", loading, loadingTemp == 0.0) { onRunTemp(0.0) }
            TemperatureButton("T = 0.7", loading, loadingTemp == 0.7) { onRunTemp(0.7) }
            TemperatureButton("T = 1.2", loading, loadingTemp == 1.2) { onRunTemp(1.2) }

            Button(attrs = {
                classes(AppStylesheet.button)
                if (loading) disabled()
                onClick { onRunAll() }
            }) {
                Text(if (loading && loadingTemp == null) "Running..." else "Run All")
            }

            Button(attrs = {
                classes(AppStylesheet.button)
                if (loading || compareLoading) disabled()
                onClick { onCompare() }
            }) {
                Text(
                    when {
                        compareLoading -> "Comparing..."
                        else -> "Compare"
                    }
                )
            }

            Button(attrs = {
                classes(AppStylesheet.button)
                if (loading || compareLoading) disabled()
                onClick { onReset() }
            }) {
                Text("Reset")
            }
        }

        if (error != null) {
            Div(attrs = {
                style {
                    borderRadius(8.px)
                    padding(10.px, 12.px)
                    backgroundColor(Color("#fdecea"))
                    border(1.px, LineStyle.Solid, Color("#f5c2c0"))
                    color(Color("#8a1c1c"))
                    marginBottom(12.px)
                }
            }) {
                Text(error)
            }
        }

        results.sortedBy { it.temperature }.forEach { run ->
            ResultCard(run)
        }

        ComparisonSummaryCard(compare, compareError)

        if (compareLoading) {
            Div(attrs = {
                style {
                    marginTop(12.px)
                    fontSize(13.px)
                    color(Color("var(--text)"))
                    opacity(0.7)
                }
            }) {
                Text("Generating comparison summary...")
            }
        }
    }
}

@Composable
private fun TemperatureButton(
    label: String,
    loading: Boolean,
    active: Boolean,
    onClickAction: () -> Unit
) {
    Button(attrs = {
        classes(AppStylesheet.button)
        if (loading) disabled()
        onClick { onClickAction() }
        style {
            if (active) {
                backgroundColor(Color("var(--primary-dark)"))
            }
        }
    }) {
        Text(
            when {
                loading && active -> "Running..."
                else -> label
            }
        )
    }
}

@Composable
private fun ResultCard(run: TempRun) {
    Div(attrs = {
        style {
            borderRadius(12.px)
            border(1.px, LineStyle.Solid, Color("var(--border)"))
            backgroundColor(Color("var(--background)"))
            padding(16.px)
            marginBottom(12.px)
        }
    }) {
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.SpaceBetween)
                alignItems(AlignItems.Center)
                marginBottom(10.px)
            }
        }) {
            Span(attrs = {
                style {
                    fontWeight(600)
                    fontSize(14.px)
                    color(Color("var(--text)"))
                }
            }) {
                Text("Temperature ${run.temperature}")
            }
            Span(attrs = {
                style {
                    fontSize(12.px)
                    color(Color("var(--text)"))
                    opacity(0.65)
                }
            }) {
                Text("Length: ${run.text.length} chars")
            }
        }

        Div(attrs = {
            style {
                whiteSpace("pre-wrap")
                fontSize(14.px)
                lineHeight("1.6")
                fontFamily("var(--font-body)")
                color(Color("var(--text)"))
            }
        }) {
            Text(run.text)
        }
    }
}

@Composable
fun ComparisonSummaryCard(summary: CompareCard?, error: String?) {
    if (summary == null && error == null) return

    Div(attrs = {
        style {
            marginTop(16.px)
            padding(16.px)
            borderRadius(14.px)
            border(1.px, LineStyle.Solid, Color("#cfe1ff"))
            backgroundColor(Color("#eef5ff"))
        }
    }) {
        when {
            error != null -> {
                Div(attrs = {
                    style {
                        color(Color("#8a1c1c"))
                        fontSize(14.px)
                    }
                }) {
                    Text(error)
                }
            }
            summary != null -> {
                SummaryContent(summary)
            }
        }
    }
}

@Composable
private fun SummaryContent(summary: CompareCard) {
    H4(attrs = {
        style {
            marginTop(0.px)
            fontSize(18.px)
            fontWeight(600)
            color(Color("var(--text)"))
            marginBottom(12.px)
        }
    }) { Text("Comparison Summary") }

    summary.perTemp.sortedBy { it.temperature }.forEach { item ->
        P(attrs = {
            style {
                marginBottom(8.px)
                fontSize(14.px)
                color(Color("var(--text)"))
            }
        }) {
            Span(attrs = {
                style {
                    fontWeight(600)
                    color(Color("var(--text)"))
                }
            }) { Text("Temp ${item.temperature}: ") }
            Text("Accuracy: ${item.accuracy}; Creativity: ${item.creativity}; Diversity: ${item.diversity}; Risks: ${item.risks}")
        }
    }

    if (summary.keyDifferences.isNotEmpty()) {
        Div(attrs = {
            style {
                marginTop(10.px)
            }
        }) {
            H5(attrs = {
                style {
                    marginBottom(6.px)
                    fontWeight(600)
                    color(Color("var(--text)"))
                }
            }) { Text("Key Differences") }

            Ul(attrs = {
                style {
                    margin(0.px)
                    paddingLeft(20.px)
                }
            }) {
                summary.keyDifferences.forEach { diff ->
                    Li(attrs = {
                        style {
                            marginBottom(4.px)
                            fontSize(14.px)
                            color(Color("var(--text)"))
                        }
                    }) {
                        Text(diff)
                    }
                }
            }
        }
    }

    if (summary.bestUseCases.isNotEmpty()) {
        H5(attrs = {
            style {
                marginTop(14.px)
                marginBottom(6.px)
                fontWeight(600)
                color(Color("var(--text)"))
            }
        }) { Text("Best Use Cases") }

        summary.bestUseCases.entries.sortedBy { it.key }.forEach { (temp, useCases) ->
            P(attrs = {
                style {
                    marginBottom(4.px)
                    fontSize(14.px)
                    color(Color("var(--text)"))
                }
            }) {
                Span(attrs = {
                    style {
                        fontWeight(600)
                        color(Color("var(--text)"))
                    }
                }) { Text("T=$temp: ") }
                Text(useCases.joinToString("; "))
            }
        }
    }

    Div(attrs = {
        style {
            marginTop(16.px)
            padding(12.px)
            borderRadius(10.px)
            backgroundColor(Color("#dce9ff"))
            border(1.px, LineStyle.Solid, Color("#b6d1ff"))
            color(Color("var(--text)"))
            fontSize(14.px)
        }
    }) {
        Span(attrs = {
            style {
                fontWeight(600)
                color(Color("var(--text)"))
            }
        }) { Text("Verdict: ") }
        Text(summary.verdict)
    }
}


