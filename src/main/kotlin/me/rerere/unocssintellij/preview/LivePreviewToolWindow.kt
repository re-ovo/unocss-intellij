package me.rerere.unocssintellij.preview

import com.intellij.lang.javascript.JSElementType
import com.intellij.lang.javascript.JSElementTypes
import com.intellij.lang.javascript.JSStringLiteralLexer
import com.intellij.lang.javascript.psi.JSElement
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlElementType
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import kotlinx.coroutines.runBlocking
import me.rerere.unocssintellij.model.UnocssResolveMeta
import me.rerere.unocssintellij.rpc.ResolveCSSResult

private val EmptyHtml = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body { margin: 0; padding: 0; font-family: sans-serif; background: white; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
        </style>
    </head>
    <body>
        Click on a UnoCSS token to preview
    </body>
    </html>
""".trimIndent()

class LivePreviewToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (!JBCefApp.isSupported()) {
            // Fallback to an alternative browser-less solution
            return;
        }

        val webview = LivePreviewWindow(toolWindow)
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(
                webview.component,
                "",
                false
            )
        )
    }
}

class LivePreviewWindow(toolWindow: ToolWindow) {
    private val webview by lazy {
        val browser = JBCefBrowser()
        browser.loadHTML(EmptyHtml)

        Disposer.register(toolWindow.disposable, browser)
        EditorFactory.getInstance().eventMulticaster
            .addCaretListener(LivePreviewListener(this), toolWindow.disposable)

        browser
    }

    val component = webview.component

    fun loadHtml(html: String) {
        webview.loadHTML(html)
    }
}

private val sensitiveElementTypes = setOf(
    XmlElementType.XML_NAME,
    XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN,
    CssElementTypes.CSS_IDENT,
    CssElementTypes.CSS_STRING_TOKEN,
)

private fun isJsString(element: PsiElement): Boolean {
    return element is LeafPsiElement && element.parent is JSLiteralExpression
}

class LivePreviewListener(private val window: LivePreviewWindow) : CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
        resolve(event)
    }

    override fun caretAdded(event: CaretEvent) {
        resolve(event)
    }

    override fun caretRemoved(event: CaretEvent) {
        resolve(event)
    }

    private fun resolve(event: CaretEvent) {
        runReadAction {
            val editor = event.editor
            val project = event.editor.project ?: return@runReadAction
            val psiFile =
                PsiDocumentManager.getInstance(project).getPsiFile(event.editor.document) ?: return@runReadAction
            val element = psiFile.viewProvider.findElementAt(editor.caretModel.offset) ?: return@runReadAction

            if (element.elementType in sensitiveElementTypes || isJsString(element)) {
                val matchedElement = arrayListOf<ResolveCSSResult>()
                runBlocking {
                    UnocssResolveMeta(
                        bindElement = element,
                        attrName = element.text,
                    ).resolveCss()?.let {
                        matchedElement.add(it)
                    }

                    element.parent?.let { parent ->
                        UnocssResolveMeta(
                            bindElement = parent,
                            attrName = parent.text,
                        ).resolveCss()?.let {
                            matchedElement.add(it)
                        }
                    }

                    generateHtml(matchedElement)
                }
            } else {
                window.loadHtml(EmptyHtml)
            }
        }
    }

    private fun generateHtml(matchedResult: List<ResolveCSSResult>) {
        val html = StringBuilder()
        html.append("<!DOCTYPE html>")
        html.append("<html><head><style>")
        html.append("body { margin: 0; padding: 1rem; font-family: sans-serif; background: white; }")
        html.append(matchedResult.joinToString("\n") { it.css })
        html.append(
            """
            .token-title {
                font-size: 0.8rem;
                font-weight: bold;
                margin-bottom: 0.5rem;
            }
            
            .token-title::before {
                content: "♦";
                margin-right: 0.5rem;
            }
            
            .token-title:hover {
                color: #ff0000;
            }
            
            .token-title:hover::before {
                color: #ff0000;
            }
        """.trimIndent()
        )
        html.append("</style>")
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        html.append("</head><body>")
        html.append("<div style=\"font-size: 1.5rem; font-weight: bold; margin-bottom: 1rem;\">UnoCSS Live Preview</div>")
        matchedResult.forEach { item ->
            html.append("<div class=\"token-title\">${item.matchedTokens.joinToString(" ")}</div>")
            html.append(
                """
                <div class="${item.matchedTokens.joinToString(" ")}">
                    UnoCSS Preview
                </div>
            """.trimIndent()
            )
            html.append("<br/>")
        }
        html.append("</body></html>")
        window.loadHtml(html.toString())
    }
}