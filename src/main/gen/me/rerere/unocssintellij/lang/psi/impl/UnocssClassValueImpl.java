// This is a generated file. Not intended for manual editing.
package me.rerere.unocssintellij.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static me.rerere.unocssintellij.lang.psi.UnocssTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import me.rerere.unocssintellij.lang.psi.*;

public class UnocssClassValueImpl extends ASTWrapperPsiElement implements UnocssClassValue {

  public UnocssClassValueImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull UnocssVisitor visitor) {
    visitor.visitClassValue(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof UnocssVisitor) accept((UnocssVisitor)visitor);
    else super.accept(visitor);
  }

}
