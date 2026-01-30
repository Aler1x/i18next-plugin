# InlayHintsSink — add element methods

Reference for `com.intellij.codeInsight.hints.InlayHintsSink` methods used to add inlay hints. Source: IntelliJ Platform `InlayHintsSink.kt`.

---

## addInlineElement

```text
addInlineElement(
  offset: Int,
  relatesToPrecedingText: Boolean,
  presentation: InlayPresentation,
  placeAtTheEndOfLine: Boolean
)
```

**Inline inlays** are drawn in the flow of the line, between code tokens (e.g. after a closing paren).

| Parameter | Type | Description |
|-----------|------|-------------|
| **offset** | `Int` | Document offset where the inlay is inserted. The hint appears at this position in the text. |
| **relatesToPrecedingText** | `Boolean` | Whether the hint is associated with the preceding code. Affects selection and folding behavior. |
| **presentation** | `InlayPresentation` | What is drawn (e.g. `factory.roundWithBackground(factory.smallText("…"))`). |
| **placeAtTheEndOfLine** | `Boolean` | If `true`, the inlay is treated as end-of-line: the caret cannot be placed after it. Use for hints that should not be “inside” editable text. |

---

## addBlockElement

```text
addBlockElement(
  offset: Int,
  relatesToPrecedingText: Boolean,
  showAbove: Boolean,
  priority: Int,
  presentation: InlayPresentation
)
```

**Block inlays** are drawn on their own row above or below a line, at the start of the line (not at `offset`). The `offset` only identifies which line the block is attached to.

| Parameter | Type | Description |
|-----------|------|-------------|
| **offset** | `Int` | Document offset that identifies the line. The block is always drawn at the **start of that line**; horizontal position is not controlled by this value. |
| **relatesToPrecedingText** | `Boolean` | Whether the hint is associated with the code at this offset. Affects selection and folding. |
| **showAbove** | `Boolean` | `true` → block above the line; `false` → block below the line. |
| **priority** | `Int` | When several block inlays are on the same line, **lower value** = drawn **higher** (closer to the code). Use `0` as default. |
| **presentation** | `InlayPresentation` | What is drawn. For block inlays the presentation may need to shift itself (e.g. via `EditorUtil.getPlainSpaceWidth`) to align with indentation. |

---

## Summary

| Method | Placement | Use when |
|--------|-----------|----------|
| **addInlineElement** | In the line at `offset` | Short hint right after an expression (e.g. type, translation on same line). |
| **addBlockElement** | Above/below the line, at line start | Longer or multiline content (e.g. translation above the key). |
