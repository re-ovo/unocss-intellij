package me.rerere.unocssintellij.documentation.selection.ui

import com.intellij.icons.AllIcons
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.editor.impl.EditorCssFontResolver
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.util.coroutines.flow.collectLatestUndispatched
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBViewport
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.SmartList
import com.intellij.util.ui.ExtendableHTMLViewFactory.Extensions
import com.intellij.util.ui.ExtendableHTMLViewFactory.Extensions.icons
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import com.intellij.util.ui.html.cssMargin
import com.intellij.util.ui.html.cssPadding
import com.intellij.util.ui.html.width
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.rerere.unocssintellij.documentation.selection.ui.UnocssDocumentationDef.docPopupMaxHeight
import me.rerere.unocssintellij.documentation.selection.ui.UnocssDocumentationDef.docPopupMaxWidth
import me.rerere.unocssintellij.documentation.selection.ui.UnocssDocumentationDef.docPopupMinWidth
import me.rerere.unocssintellij.documentation.selection.ui.UnocssDocumentationDef.docPopupPreferredMaxWidth
import me.rerere.unocssintellij.documentation.selection.ui.UnocssDocumentationDef.docPopupPreferredMinWidth
import me.rerere.unocssintellij.util.IconResources
import me.rerere.unocssintellij.util.RoundedBorder
import me.rerere.unocssintellij.util.appendHighlightedCss
import one.util.streamex.StreamEx
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.Border
import javax.swing.text.Document
import javax.swing.text.View
import javax.swing.text.html.HTML
import kotlin.math.max
import kotlin.math.min


internal val FORCED_WIDTH: Key<Int> = Key.create("WidthBasedLayout.width")

class UnocssSelectionStylePresentationPane(
    border: Border = JBUI.Borders.empty(12),
    var iconVisible: Boolean = true
) : JLabel(
    "UnoCSS utilities in the selection will be equivalent to:",
    IconResources.PluginIcon,
    LEADING
) {
    init {
        this.border = border
        this.iconTextGap = 6
    }

    fun toggleIconVisible(visible: Boolean) {
        iconVisible = visible
        icon = if (visible) IconResources.PluginIcon else null
    }
}

private val editorPaneBackgroundColor = JBColor.lazy {
    val colorKey = EditorColors.DOCUMENTATION_COLOR
    val scheme = EditorColorsUtil.getColorSchemeForBackground(null)
    scheme.getColor(colorKey) ?: colorKey.defaultColor ?: scheme.defaultBackground
}

private val styledCodeStyleDefinition: StyledCodeStyleDefinition
    get() = IntellijDocumentationUISupport.getStyledCodeProperties(
        EditorColorsManager.getInstance().globalScheme,
        editorPaneBackgroundColor,
    )

class UnocssDocumentationEditorPane : JEditorPane(), Disposable {

    private var myText = ""
    private var myCachedPreferredSize: Dimension? = null

    private val _editorBackgroundFlow: MutableStateFlow<StyledCodeStyleDefinition>
    val editorBackgroundFlow: StateFlow<StyledCodeStyleDefinition> get() = _editorBackgroundFlow

    init {
        enableEvents(AWTEvent.KEY_EVENT_MASK)
        this.isEditable = false
        if (ScreenReader.isActive()) {
            caret.isVisible = true
        } else {
            putClientProperty("caretWidth", 0)
            UIUtil.doNotScrollToCaret(this)
        }
        val curStyleDefinition = styledCodeStyleDefinition
        this.background = curStyleDefinition.backgroundColor
        this._editorBackgroundFlow = MutableStateFlow(curStyleDefinition)

        val editorKit = HTMLEditorKitBuilder()
            .replaceViewFactoryExtensions(
                icons(HtmlChunk.icon("UnoCSS.PluginIcon", IconResources.PluginIcon)),
                Extensions.HIDPI_IMAGES,
                Extensions.INLINE_VIEW_EX,
                Extensions.PARAGRAPH_VIEW_EX,
                Extensions.LINE_VIEW_EX,
                Extensions.BLOCK_VIEW_EX,
                Extensions.FIT_TO_WIDTH_IMAGES,
                Extensions.WBR_SUPPORT
            )
            .withFontResolver(EditorCssFontResolver.getGlobalInstance())
            .build()

        editorKit.styleSheet.apply {
            addRule("body {padding: 0;margin: 0;overflow-wrap: anywhere;}")
            addRule("pre {white-space: pre-wrap;}")
        }

        addPropertyChangeListener { evt ->
            val propertyName = evt.propertyName
            if ("background" == propertyName || "UI" == propertyName) {
                val curStyleDefinition = styledCodeStyleDefinition
                this.background = curStyleDefinition.backgroundColor
                this._editorBackgroundFlow.value = curStyleDefinition
            }
        }

        this.editorKit = editorKit
        this.border = JBUI.Borders.empty(0, 12, 8, 8)
    }

    override fun dispose() {
        caret.isVisible = false
    }

    override fun getText(): String = myText

    override fun setText(t: String) {
        myText = t
        myCachedPreferredSize = null
        super.setText(t)
    }

    override fun paintComponent(g: Graphics) {
        GraphicsUtil.setupAntialiasing(g)
        super.paintComponent(g)
    }

    override fun setDocument(doc: Document) {
        super.setDocument(doc)
        myCachedPreferredSize = null
        doc.putProperty("IgnoreCharsetDirective", true)
    }

    fun getPackedSize(minWidth: Int, maxWidth: Int): Dimension {
        val width = min(max(max(definitionPreferredWidth(), minimumSize.width), minWidth), maxWidth)
        val height: Int = getPreferredHeightByWidth(width)
        return Dimension(width, height)
    }

    private fun definitionPreferredWidth(): Int {
        val preferredDefinitionWidth = max(
            getPreferredSectionsWidth("definition"),
            getPreferredSectionsWidth("definition-separated")
        )
        if (preferredDefinitionWidth < 0) {
            return -1
        }
        val preferredLocationWidth: Int = getPreferredSectionsWidth("bottom")
        val preferredCodeBlockWidth: Int = getPreferredSectionsWidth("styled-code")
        val preferredContentWidth = getPreferredContentWidth(document.length)
        return max(
            max(preferredCodeBlockWidth.toDouble(), preferredContentWidth.toDouble()),
            max(preferredDefinitionWidth.toDouble(), preferredLocationWidth.toDouble())
        ).toInt()
    }

    private fun getPreferredSectionsWidth(sectionClassName: String): Int {
        val definitions = findSections(getUI().getRootView(this), sectionClassName)
        return StreamEx.of(definitions).mapToInt(::getPreferredWidth).max().orElse(-1)
    }

    private fun getPreferredHeightByWidth(width: Int): Int {
        var cachedPreferredSize = this.myCachedPreferredSize
        if (cachedPreferredSize != null && cachedPreferredSize.width == width) {
            return cachedPreferredSize.height
        }
        setSize(width, Short.MAX_VALUE.toInt())
        val result = preferredSize
        cachedPreferredSize = Dimension(width, result.height)
            .also { this.myCachedPreferredSize = it }
        return cachedPreferredSize.height
    }

    private fun getPreferredWidth(view: View): Int {
        var result = view.getPreferredSpan(View.X_AXIS).toInt()
        if (result > 0) {
            result += view.cssMargin.width
            var parent = view.parent
            while (parent != null) {
                result += parent.cssMargin.width + parent.cssPadding.width
                parent = parent.parent
            }
        }
        return result
    }

    private fun findSections(view: View, sectionClassName: String): List<View> {
        val queue = arrayListOf<View>()
        queue.add(view)

        val result = SmartList<View>()
        while (queue.isNotEmpty()) {
            val cur = queue.removeAt(queue.size - 1)
            if (sectionClassName == cur.element.attributes.getAttribute(HTML.Attribute.CLASS)) {
                result.add(cur)
            }
            for (i in 0 until cur.viewCount) {
                queue.add(cur.getView(i))
            }
        }
        return result
    }

    private fun getPreferredContentWidth(textLength: Int): Int {
        val contentLengthPreferredSize = if (textLength < 200) {
            docPopupPreferredMinWidth
        } else if (textLength in 201..999) {
            docPopupPreferredMinWidth +
                    (textLength - 200) *
                    (docPopupPreferredMaxWidth - docPopupPreferredMinWidth) / (1000 - 200)
        } else {
            docPopupPreferredMaxWidth
        }
        return scale(contentLengthPreferredSize)
    }
}

class UnocssDocumentationScrollPane : JBScrollPane(VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED) {
    init {
        val definition = styledCodeStyleDefinition
        background = definition.backgroundColor
        border = RoundedBorder(definition.borderColor, 10)
        viewportBorder = null
    }

    override fun getPreferredSize(): Dimension {
        val forcedWidth = ClientProperty.get(this, FORCED_WIDTH)
        val minWidth = forcedWidth ?: scale(docPopupMinWidth)
        val maxWidth = forcedWidth ?: scale(docPopupMaxWidth)
        return getPreferredSize(minWidth, maxWidth, scale(docPopupMaxHeight))
    }

    fun setViewportView(editorPane: UnocssDocumentationEditorPane) {
        val panel = object : JPanel(BorderLayout()) {
            override fun getPreferredSize(): Dimension {
                val parent = parent as JBViewport
                val minimumSize = editorPane.minimumSize
                val insets = getInsets()
                val insetWidth = insets.left + insets.right
                val width = max(max(minimumSize.width, parent.width - insetWidth), scale(200))
                val editorPaneSize = editorPane.getPackedSize(width, width)
                return Dimension(
                    width + insetWidth,
                    editorPaneSize.height + insets.top + insets.bottom
                )
            }
        }
        panel.add(editorPane, BorderLayout.CENTER)
        panel.isOpaque = true
        setViewportView(panel)
    }

    private fun getPreferredSize(minWidth: Int, maxWidth: Int, maxHeight: Int): Dimension {
        val view = viewport.view
        val paneSize: Dimension

        val hBar = horizontalScrollBar
        val vBar = verticalScrollBar
        var insetWidth = 0
        when (view) {
            is UnocssDocumentationEditorPane -> {
                paneSize = view.getPackedSize(minWidth, maxWidth)
            }

            is JPanel -> {
                val components = view.components
                val viewInsets = view.insets
                insetWidth = viewInsets.left + viewInsets.right
                val editorPaneSize = (components[0] as UnocssDocumentationEditorPane)
                    .getPackedSize(minWidth, maxWidth)
                paneSize = Dimension(
                    editorPaneSize.width + vBar.preferredSize.width + insetWidth,
                    editorPaneSize.height + viewInsets.top + viewInsets.bottom
                )
            }

            else -> throw IllegalStateException(view.javaClass.name)
        }
        val hasHBar = paneSize.width - vBar.preferredSize.width - insetWidth > maxWidth && hBar.isOpaque
        val hBarHeight = if (hasHBar) hBar.preferredSize.height else 0

        val hasVBar = paneSize.height + hBarHeight > maxHeight && vBar.isOpaque
        val vBarWidth = if (hasVBar) vBar.preferredSize.width else 0

        val insets = insets
        val preferredWidth = paneSize.width + vBarWidth + insets.left + insets.right
        val preferredHeight = paneSize.height + hBarHeight + insets.top + insets.bottom
        return Dimension(min(preferredWidth, maxWidth), min(preferredHeight, maxHeight))
    }
}

class UnocssDocumentationUI(val project: Project, initialStyle: String?) : Disposable {

    private val scrollPane = UnocssDocumentationScrollPane()

    val presentationPane = UnocssSelectionStylePresentationPane()

    val containerPane = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(0, 12, 12, 12)
        add(scrollPane, BorderLayout.CENTER)
    }

    val editorPane = UnocssDocumentationEditorPane()

    private val cs = CoroutineScope(Dispatchers.EDT)

    private val myContentFlow = MutableStateFlow(initialStyle)

    val hasContent get() = myContentFlow.value != null

    private val myContentUpdates = MutableSharedFlow<ContentUpdateEvent>(
        replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val contentUpdates get() = myContentUpdates.asSharedFlow()

    init {
        scrollPane.setViewportView(editorPane)
        trackEditorBackgroundChange(this) {
            scrollPane.background = it.backgroundColor
            scrollPane.border = RoundedBorder(it.borderColor, 10)
            // reload content highlighting style to follow current theme
            handleContent(myContentFlow.value)
        }

        cs.launch(CoroutineName("DocumentationUI content update"), start = CoroutineStart.UNDISPATCHED) {
            myContentFlow.collectLatestUndispatched {
                handleContent(it)
            }
        }
    }

    private suspend fun handleContent(selectionStyle: String?) {
        try {
            if (selectionStyle.isNullOrBlank()) {
                presentationPane.icon = AllIcons.General.Information
                presentationPane.text = "Please select some text to preview UnoCSS styles."
                scrollPane.isVisible = false
            } else {
                presentationPane.text = "UnoCSS utilities in the selection will be equivalent to:"
                presentationPane.icon = if (presentationPane.iconVisible) IconResources.PluginIcon else null
                scrollPane.isVisible = true
                val decorated = decorateHtmlContent(selectionStyle)
                editorPane.text = decorated
            }
        } catch (e: Exception) {
            println("generateMergedCss Failed! ${e.message}")
            presentationPane.icon = AllIcons.General.Error
            presentationPane.text = "Cannot parse current selection!"
            scrollPane.isVisible = true
            editorPane.text = e.message ?: "Unknown error"
        }

        editorPane.scrollRectToVisible(Rectangle(0, 0))
        if (ScreenReader.isActive()) {
            editorPane.caretPosition = 0
        }

        myContentUpdates.emit(ContentUpdateEvent)
    }

    private suspend fun decorateHtmlContent(rawContent: String) = buildString {
        append(DocumentationMarkup.DEFINITION_START)
        appendHighlightedCss(project, rawContent)
        append(DocumentationMarkup.DEFINITION_END)
    }

    private inline fun trackEditorBackgroundChange(
        disposable: Disposable,
        crossinline onChange: suspend (StyledCodeStyleDefinition) -> Unit
    ) {
        val job = cs.launch {
            editorPane.editorBackgroundFlow.collectLatest { onChange(it) }
        }
        Disposer.register(disposable) {
            job.cancel()
        }
    }

    fun updateContent(it: String?) {
        myContentFlow.value = it
    }

    override fun dispose() {
        cs.cancel("UnocssDocumentationUI disposal")
    }
}

data object ContentUpdateEvent