package me.serce.solidity.ide.typing

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class SolSemicolonTypedHandler : TypedHandlerDelegate() {
  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (c != ';' || !isSolidity(file)) return Result.CONTINUE

    val doc = editor.document
    val text = doc.charsSequence
    val caret = editor.caretModel.offset
    val delPos = caret - 1 // 刚键入的分号位置

    // 若在字符串里键入了分号，先把扫描起点移到收尾引号之后（同一行）
    val caretLine = doc.getLineNumber(caret.coerceAtMost(text.length))
    val caretLineStart = doc.getLineStartOffset(caretLine)
    val scanStart = advanceOutOfStringIfInside(text, caret, caretLineStart)

    val closeParen = findNextClosingParen(text, scanStart, hardLimit = 4000)

    if (closeParen == null) {
      // ── Fallback：无右括号 → 在当前行行尾（// 前）插入
      val line = doc.getLineNumber(scanStart.coerceAtMost(text.length))
      val lineStart = doc.getLineStartOffset(line)
      val lineEnd = doc.getLineEndOffset(line)
      val lineSeg = text.subSequence(lineStart, lineEnd).toString()
      val commentIdxInLine = lineSeg.indexOf("//")
      val hardEnd = if (commentIdxInLine >= 0) lineStart + commentIdxInLine else lineEnd

      // 目标插入点（去掉尾随空白、在 // 之前）
      var insertAt = hardEnd - 1
      while (insertAt >= lineStart && text[insertAt].isWhitespace() && text[insertAt] != '\n') insertAt--
      insertAt++ // 最终应插入的位置

      // NEW: 刚键入的分号已经在目标位置 → 什么都不做
      if (delPos == insertAt - 1 && delPos >= lineStart && delPos < hardEnd && text[delPos] == ';') {
        return Result.STOP
      }

      // 若该行（目标范围内）本就有旧分号（非本次键入），不需要再插
      if (hasSemicolonBeforeEolSkippingBlockCommentsAndStrings(text, scanStart, hardEnd)) {
        return Result.CONTINUE
      }

      val nextLine = line + 1
      if (nextLine <= doc.lineCount - 1) {
        val ns = nextNonWs(text, doc.getLineStartOffset(nextLine), doc.getLineEndOffset(nextLine))
        if (ns >= 0 && text[ns] == ';') return Result.CONTINUE
      }

      val prev = prevNonWs(text, insertAt)
      // NEW: 只在“已有旧的分号且它不是刚键入的那个”时，才删除刚键入分号
      if (prev >= 0 && text[prev] == ';' && prev != delPos) {
        return deleteTypedSemicolonOnly(project, editor, doc, caret)
      }

      val willDelete = delPos >= 0 && delPos < text.length && text[delPos] == ';'
      var target = insertAt
      if (willDelete && delPos < insertAt) {
        target -= 1 // 删除发生在插入点之前 → 目标左移
      }

      WriteCommandAction.runWriteCommandAction(project) {
        if (willDelete && delPos != insertAt - 1) { // 若不在位且需要移动，先删
          doc.deleteString(delPos, delPos + 1)
        } else if (!willDelete) {
          // 理论上不会发生（charTyped 时文档已有分号），留作安全兜底
        }
        if (delPos != insertAt - 1) {
          doc.insertString(target, ";")
          editor.caretModel.moveToOffset(target + 1)
        } else {
          // 已在位：不插不删；确保光标在分号后
          editor.caretModel.moveToOffset(delPos + 1)
        }
      }
      return Result.STOP
    }

    // ── 原有基于 ) 的路径
    val line = doc.getLineNumber(closeParen)
    val lineStart = doc.getLineStartOffset(line)
    val lineEnd = doc.getLineEndOffset(line)
    val lineSeg = text.subSequence(lineStart, lineEnd).toString()
    val commentIdxInLine = lineSeg.indexOf("//")
    val hardEnd = if (commentIdxInLine >= 0) lineStart + commentIdxInLine else lineEnd

    // 目标插入点（在 // 前、去尾空白）
    var insertAt = hardEnd - 1
    while (insertAt >= lineStart && text[insertAt].isWhitespace() && text[insertAt] != '\n') insertAt--
    insertAt++

    // NEW: 刚键入的分号已经在目标位置 → 什么都不做
    if (delPos == insertAt - 1 && delPos >= lineStart && delPos < hardEnd && text[delPos] == ';') {
      return Result.STOP
    }

    if (hasSemicolonBeforeEolSkippingBlockCommentsAndStrings(text, closeParen + 1, hardEnd)) {
      return Result.CONTINUE
    }

    val nextLine = line + 1
    if (nextLine <= doc.lineCount - 1) {
      val ns = nextNonWs(text, doc.getLineStartOffset(nextLine), doc.getLineEndOffset(nextLine))
      if (ns >= 0 && text[ns] == ';') return Result.CONTINUE
    }

    val prev = prevNonWs(text, insertAt)
    if (prev >= 0 && text[prev] == ';' && prev != delPos) {
      return deleteTypedSemicolonOnly(project, editor, doc, caret)
    }

    val willDelete = delPos >= 0 && delPos < text.length && text[delPos] == ';'
    var target = insertAt
    if (willDelete && delPos < insertAt) {
      target -= 1
    }

    WriteCommandAction.runWriteCommandAction(project) {
      if (willDelete && delPos != insertAt - 1) {
        doc.deleteString(delPos, delPos + 1)
      }
      if (delPos != insertAt - 1) {
        doc.insertString(target, ";")
        editor.caretModel.moveToOffset(target + 1)
      } else {
        editor.caretModel.moveToOffset(delPos + 1)
      }
    }
    return Result.STOP
  }

  private fun isSolidity(file: PsiFile): Boolean =
    file.fileType.defaultExtension.equals("sol", true)

  private fun deleteTypedSemicolonOnly(
    project: Project,
    editor: Editor,
    doc: com.intellij.openapi.editor.Document,
    caret: Int
  ): Result {
    WriteCommandAction.runWriteCommandAction(project) {
      val t = doc.charsSequence
      val delPos = caret - 1
      if (delPos >= 0 && delPos < t.length && t[delPos] == ';') {
        doc.deleteString(delPos, delPos + 1)
      }
    }
    return Result.STOP
  }

  private fun advanceOutOfStringIfInside(text: CharSequence, caret: Int, lineStart: Int): Int {
    if (caret <= lineStart) return caret
    var i = lineStart
    var inLineComment = false
    var inBlockComment = false
    var inString = false
    var strQuote = '\u0000'

    fun isEscaped(pos: Int): Boolean {
      var backslashes = 0
      var j = pos - 1
      while (j >= lineStart && text[j] == '\\') { backslashes++; j-- }
      return (backslashes % 2) == 1
    }

    while (i < caret) {
      val c = text[i]
      if (inLineComment) {
        if (c == '\n') inLineComment = false
        i++; continue
      }
      if (inBlockComment) {
        if (c == '*' && i + 1 < caret && text[i + 1] == '/') { inBlockComment = false; i += 2; continue }
        i++; continue
      }
      if (inString) {
        if (c == strQuote && !isEscaped(i)) inString = false
        i++; continue
      }
      when (c) {
        '"', '\'' -> { if (!isEscaped(i)) { inString = true; strQuote = c }; i++ }
        '/' -> {
          if (i + 1 < caret) {
            val n = text[i + 1]
            if (n == '/') { inLineComment = true; i += 2; continue }
            if (n == '*') { inBlockComment = true; i += 2; continue }
          }
          i++
        }
        else -> i++
      }
    }

    if (!inString) return caret

    var j = caret
    while (j < text.length) {
      val c = text[j]
      if (c == '\n') break
      if (c == strQuote) {
        var escCnt = 0
        var k = j - 1
        while (k >= lineStart && text[k] == '\\') { escCnt++; k-- }
        if (escCnt % 2 == 0) return j + 1
      }
      j++
    }
    return caret
  }

  private fun findNextClosingParen(text: CharSequence, from: Int, hardLimit: Int): Int? {
    val end = (from + hardLimit).coerceAtMost(text.length)
    var i = from
    while (i < end) {
      when (val c = text[i]) {
        ')' -> return i
        ' ', '\t', '\r', '\n' -> {}
        '/' -> {
          if (i + 1 < end) {
            when (text[i + 1]) {
              '/' -> { i = skipToLineEnd(text, i + 2, end) - 1 }
              '*' -> { i = skipBlockComment(text, i + 2, end) - 1 }
              else -> return null
            }
          }
        }
        '"', '\'' -> { i = skipString(text, i, end) - 1 }
        else -> return null
      }
      i++
    }
    return null
  }

  private fun hasSemicolonBeforeEolSkippingBlockCommentsAndStrings(text: CharSequence, from: Int, to: Int): Boolean {
    var i = from.coerceAtLeast(0)
    val end = to.coerceAtMost(text.length)
    while (i < end) {
      when (text[i]) {
        ';' -> return true
        '/' -> if (i + 1 < end && text[i + 1] == '*') { i = skipBlockComment(text, i + 2, end); continue }
        '"', '\'' -> { i = skipString(text, i, end); continue }
      }
      i++
    }
    return false
  }

  private fun skipToLineEnd(text: CharSequence, from: Int, end: Int): Int {
    var i = from
    while (i < end && text[i] != '\n') i++
    return i
  }

  private fun skipBlockComment(text: CharSequence, from: Int, end: Int): Int {
    var i = from
    while (i + 1 < end) {
      if (text[i] == '*' && text[i + 1] == '/') return i + 2
      i++
    }
    return end
  }

  private fun skipString(text: CharSequence, from: Int, end: Int): Int {
    if (from >= end) return from
    val quote = text[from]
    var i = from + 1
    while (i < end) {
      val c = text[i]
      if (c == '\\') { i += 2; continue }
      if (c == quote) return i + 1
      i++
    }
    return end
  }

  private fun nextNonWs(text: CharSequence, from: Int, to: Int): Int {
    var i = from
    val end = to.coerceAtMost(text.length)
    while (i < end) {
      if (!text[i].isWhitespace()) return i
      i++
    }
    return -1
  }

  private fun prevNonWs(text: CharSequence, from: Int): Int {
    var i = (from - 1).coerceAtMost(text.length - 1)
    while (i >= 0) {
      if (!text[i].isWhitespace()) return i
      i--
    }
    return -1
  }
}
