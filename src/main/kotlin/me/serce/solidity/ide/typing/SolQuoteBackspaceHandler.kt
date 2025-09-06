package me.serce.solidity.ide.typing

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import me.serce.solidity.lang.SolidityLanguage

class SolQuoteBackspaceHandler : BackspaceHandlerDelegate() {
  override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
    if (file.language != SolidityLanguage) return
    if (c == '\"' || c == '\'') {
      val offset = editor.caretModel.offset
      val text = editor.document.charsSequence
      if (offset < text.length && offset > 0 && text[offset] == c) {
        editor.document.deleteString(offset, offset + 1)
      }
    }
  }

  override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean = true
}
