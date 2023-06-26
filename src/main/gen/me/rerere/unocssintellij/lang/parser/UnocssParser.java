// This is a generated file. Not intended for manual editing.
package me.rerere.unocssintellij.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static me.rerere.unocssintellij.lang.psi.UnocssTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class UnocssParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return unocssFile(b, l + 1);
  }

  /* ********************************************************** */
  // CLASSNAME (WHITESPACE CLASSNAME)*
  public static boolean classValue(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classValue")) return false;
    if (!nextTokenIs(b, CLASSNAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, CLASSNAME);
    r = r && classValue_1(b, l + 1);
    exit_section_(b, m, CLASS_VALUE, r);
    return r;
  }

  // (WHITESPACE CLASSNAME)*
  private static boolean classValue_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classValue_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!classValue_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "classValue_1", c)) break;
    }
    return true;
  }

  // WHITESPACE CLASSNAME
  private static boolean classValue_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classValue_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, WHITESPACE, CLASSNAME);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // classValue*
  static boolean unocssFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unocssFile")) return false;
    while (true) {
      int c = current_position_(b);
      if (!classValue(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "unocssFile", c)) break;
    }
    return true;
  }

}
