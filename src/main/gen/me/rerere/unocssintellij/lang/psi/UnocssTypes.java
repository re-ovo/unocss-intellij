// This is a generated file. Not intended for manual editing.
package me.rerere.unocssintellij.lang.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import me.rerere.unocssintellij.lang.UnocssElementType;
import me.rerere.unocssintellij.lang.UnocssTokenType;
import me.rerere.unocssintellij.lang.psi.impl.*;

public interface UnocssTypes {

  IElementType CLASS_VALUE = new UnocssElementType("CLASS_VALUE");

  IElementType CLASSNAME = new UnocssTokenType("CLASSNAME");
  IElementType WHITESPACE = new UnocssTokenType("WHITESPACE");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == CLASS_VALUE) {
        return new UnocssClassValueImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
