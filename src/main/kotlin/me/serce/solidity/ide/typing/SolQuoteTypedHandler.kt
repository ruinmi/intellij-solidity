package me.serce.solidity.ide.typing

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.openapi.fileTypes.FileType
import me.serce.solidity.lang.SolidityLanguage

class SolQuoteTypedHandler : TypedHandlerDelegate() {
  override fun beforeCharTyped(
    c: Char,
    project: Project,
    editor: Editor,
    file: PsiFile,
    fileType: FileType
  ): Result {
    if (file.language != SolidityLanguage) return Result.CONTINUE
    if (c == '"' || c == '\'') {
      val offset = editor.caretModel.offset
      val text = editor.document.charsSequence
      if (offset < text.length && text[offset] == c) {
        editor.caretModel.moveToOffset(offset + 1)
      } else {
        EditorModificationUtil.insertStringAtCaret(editor, "$c$c")
        editor.caretModel.moveToOffset(offset + 1)
      }
      return Result.STOP
    }
    return Result.CONTINUE
  }
}
