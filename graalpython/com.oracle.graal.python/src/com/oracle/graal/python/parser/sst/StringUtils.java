/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.graal.python.parser.sst;

import java.util.Locale;

import com.ibm.icu.lang.UCharacter;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.control.BaseBlockNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.literal.StringLiteralNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.regex.chardata.UnicodeCharacterAliases;

public class StringUtils {

    public static StringLiteralNode extractDoc(StatementNode node) {
        if (node instanceof ExpressionNode.ExpressionStatementNode) {
            return extractDoc(((ExpressionNode.ExpressionStatementNode) node).getExpression());
        } else if (node instanceof BaseBlockNode) {
            StatementNode[] statements = ((BaseBlockNode) node).getStatements();
            if (statements != null && statements.length > 0) {
                return extractDoc(statements[0]);
            }
            return null;
        }
        return null;
    }

    public static StringLiteralNode extractDoc(ExpressionNode node) {
        if (node instanceof StringLiteralNode) {
            return (StringLiteralNode) node;
        } else if (node instanceof ExpressionNode.ExpressionWithSideEffect) {
            return extractDoc(((ExpressionNode.ExpressionWithSideEffect) node).getSideEffect());
        } else if (node instanceof ExpressionNode.ExpressionWithSideEffects) {
            StatementNode[] sideEffects = ((ExpressionNode.ExpressionWithSideEffects) node).getSideEffects();
            if (sideEffects != null && sideEffects.length > 0) {
                return extractDoc(sideEffects[0]);
            }
        }
        return null;
    }

    public static String unescapeJavaString(ParserErrorCallback errorCallback, String st) {
        if (st.indexOf("\\") == -1) {
            return st;
        }
        StringBuilder sb = new StringBuilder(st.length());
        boolean wasDeprecationWarning = false;
        for (int i = 0; i < st.length(); i++) {
            char ch = st.charAt(i);
            if (ch == '\\') {
                char nextChar = (i == st.length() - 1) ? '\\' : st.charAt(i + 1);
                // Octal escape?
                if (nextChar >= '0' && nextChar <= '7') {
                    String code = "" + nextChar;
                    i++;
                    if ((i < st.length() - 1) && st.charAt(i + 1) >= '0' && st.charAt(i + 1) <= '7') {
                        code += st.charAt(i + 1);
                        i++;
                        if ((i < st.length() - 1) && st.charAt(i + 1) >= '0' && st.charAt(i + 1) <= '7') {
                            code += st.charAt(i + 1);
                            i++;
                        }
                    }
                    sb.append((char) Integer.parseInt(code, 8));
                    continue;
                }
                switch (nextChar) {
                    case '\\':
                        ch = '\\';
                        break;
                    case 'a':
                        ch = '\u0007';
                        break;
                    case 'b':
                        ch = '\b';
                        break;
                    case 'f':
                        ch = '\f';
                        break;
                    case 'n':
                        ch = '\n';
                        break;
                    case 'r':
                        ch = '\r';
                        break;
                    case 't':
                        ch = '\t';
                        break;
                    case 'v':
                        ch = '\u000b';
                        break;
                    case '\"':
                        ch = '\"';
                        break;
                    case '\'':
                        ch = '\'';
                        break;
                    case '\r':
                        nextChar = (i == st.length() - 2) ? '\\' : st.charAt(i + 2);
                        if (nextChar == '\n') {
                            i++;
                        }
                        i++;
                        continue;
                    case '\n':
                        i++;
                        continue;
                    // Hex Unicode: u????
                    case 'u':
                        int code = getHexValue(st, i + 2, 4);
                        sb.append(Character.toChars(code));
                        i += 5;
                        continue;
                    // Hex Unicode: U????????
                    case 'U':
                        code = getHexValue(st, i + 2, 8);
                        if (Character.isValidCodePoint(code)) {
                            sb.append(Character.toChars(code));
                        } else {
                            throw PConstructAndRaiseNode.raiseUncachedUnicodeDecodeError("unicodeescape", st, i, i + 9, ILLEGAl_CHARACTER);
                        }
                        i += 9;
                        continue;
                    // Hex Unicode: x??
                    case 'x':
                        code = getHexValue(st, i + 2, 2);
                        sb.append(Character.toChars(code));
                        i += 3;
                        continue;
                    case 'N':
                        // a character from Unicode Data Database
                        i = doCharacterName(st, sb, i + 2);
                        continue;
                    default:
                        if (!wasDeprecationWarning) {
                            wasDeprecationWarning = true;
                            warnInvalidEscapeSequence(errorCallback, nextChar);
                        }
                        sb.append(ch);
                        sb.append(nextChar);
                        i++;
                        continue;
                }
                i++;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    public static String unescapeString(int startOffset, int endOffset, Source source, ParserErrorCallback errors, String text) {
        try {
            return unescapeJavaString(errors, text);
        } catch (PException e) {
            e.expect(PythonBuiltinClassType.UnicodeDecodeError, IsBuiltinClassProfile.getUncached());
            String message = PyObjectStrAsJavaStringNode.getUncached().execute(e.getUnreifiedException());
            throw errors.raiseInvalidSyntax(source, source.createSection(startOffset, endOffset - startOffset), PythonUtils.format("(unicode error) %s", message));
        }
    }

    public static void warnInvalidEscapeSequence(ParserErrorCallback errorCallback, char nextChar) {
        errorCallback.warn(PythonBuiltinClassType.DeprecationWarning, "invalid escape sequence '\\%c'", nextChar);
    }

    private static final String UNICODE_ERROR = "'unicodeescape' codec can't decode bytes in position %d-%d:";
    private static final String MALFORMED_ERROR = "malformed \\N character escape";
    private static final String TRUNCATED_XXX_ERROR = "truncated \\xXX escape";
    private static final String TRUNCATED_UXXXX_ERROR = "truncated \\uXXXX escape";
    private static final String TRUNCATED_UXXXXXXXX_ERROR = "truncated \\UXXXXXXXX escape";
    private static final String UNKNOWN_UNICODE_ERROR = "unknown Unicode character name";
    private static final String ILLEGAl_CHARACTER = "illegal Unicode character";

    private static int getHexValue(String text, int start, int len) {
        int digit;
        int result = 0;
        for (int index = start; index < (start + len); index++) {
            if (index < text.length()) {
                digit = Character.digit(text.charAt(index), 16);
                if (digit == -1) {
                    // Like cpython, raise error with the wrong character first,
                    // even if there are not enough characters
                    throw createTruncatedError(text, start - 2, index - 1, len);
                }
                result = result * 16 + digit;
            } else {
                throw createTruncatedError(text, start - 2, index - 1, len);
            }
        }
        return result;
    }

    private static PException createTruncatedError(String text, int startIndex, int endIndex, int len) {
        String truncatedMessage = null;
        switch (len) {
            case 2:
                truncatedMessage = TRUNCATED_XXX_ERROR;
                break;
            case 4:
                truncatedMessage = TRUNCATED_UXXXX_ERROR;
                break;
            case 8:
                truncatedMessage = TRUNCATED_UXXXXXXXX_ERROR;
                break;
        }
        return PConstructAndRaiseNode.raiseUncachedUnicodeDecodeError("unicodeescape", text, startIndex, endIndex, truncatedMessage);
    }

    /**
     * Replace '/N{Unicode Character Name}' with the code point of the character.
     *
     * @param text a text that contains /N{...} escape sequence
     * @param sb string builder where the result code point will be written
     * @param offset this is offset of the open brace
     * @return offset of the close brace
     */
    @TruffleBoundary
    private static int doCharacterName(String text, StringBuilder sb, int offset) {
        if (offset >= text.length()) {
            throw PConstructAndRaiseNode.raiseUncachedUnicodeDecodeError("unicodeescape", text, offset - 2, offset, MALFORMED_ERROR);
        }
        char ch = text.charAt(offset);
        if (ch != '{') {
            throw PConstructAndRaiseNode.raiseUncachedUnicodeDecodeError("unicodeescape", text, offset - 2, offset, MALFORMED_ERROR);
        }
        int closeIndex = text.indexOf("}", offset + 1);
        if (closeIndex == -1) {
            throw PConstructAndRaiseNode.raiseUncachedUnicodeDecodeError("unicodeescape", text, offset - 2, text.length(), MALFORMED_ERROR);
        }
        String charName = text.substring(offset + 1, closeIndex).toUpperCase();
        // When JDK 1.8 will not be supported, we can replace with Character.codePointOf(String
        // name) in the
        int cp = getCodePoint(charName);
        if (cp >= 0) {
            sb.append(Character.toChars(cp));
        } else {
            throw PConstructAndRaiseNode.raiseUncachedUnicodeDecodeError("unicodeescape", text, offset - 2, text.length(), UNKNOWN_UNICODE_ERROR);
        }
        return closeIndex;
    }

    @TruffleBoundary
    public static int getCodePoint(String characterName) {
        // CPython's logic for resolving these character names goes like this:
        // 1) handle Hangul Syllables in region AC00-D7A3
        // 2) handle CJK Ideographs
        // 3) handle character names as given in UnicodeData.txt
        // 4) handle all aliases as given in NameAliases.txt
        // With ICU's UCharacter, we get cases 1), 2) and 3). As for 4), the aliases, ICU only
        // handles aliases of type 'correction'. Therefore, we extract the contents of
        // NameAliases.txt and handle aliases by ourselves.
        String normalizedName = characterName.trim().toUpperCase(Locale.ROOT);
        if (UnicodeCharacterAliases.CHARACTER_ALIASES.containsKey(normalizedName)) {
            return UnicodeCharacterAliases.CHARACTER_ALIASES.get(normalizedName);
        } else {
            return UCharacter.getCharFromName(characterName);
        }
    }
}
