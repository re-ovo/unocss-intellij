package me.rerere.unocssintellij.lang;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import me.rerere.unocssintellij.lang.psi.UnocssTypes;

%%

%class UnocssLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

CLASSNAME=[a-zA-Z0-9\-\_\#\:]+
WHITESPACE=[\ \t\n\r]+

%state WAITING_CLASSNAME

%%

<YYINITIAL> {CLASSNAME} {
    yybegin(YYINITIAL);
    return UnocssTypes.CLASSNAME;
}

<YYINITIAL> {WHITESPACE} {
    yybegin(WAITING_CLASSNAME);
    return UnocssTypes.WHITESPACE;
}

<WAITING_CLASSNAME> {CLASSNAME} {
    yybegin(YYINITIAL);
    return UnocssTypes.CLASSNAME;
}

<WAITING_CLASSNAME> {WHITESPACE} {
    yybegin(WAITING_CLASSNAME);
    return UnocssTypes.WHITESPACE;
}

[^] { return TokenType.WHITE_SPACE; }