package com.example.jueldemo;

import static com.example.jueldemo.Scanner.Symbol.*;
import static de.odysseus.el.tree.impl.Builder.Feature.*;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

import de.odysseus.el.misc.LocalMessages;
import de.odysseus.el.tree.*;
import de.odysseus.el.tree.impl.Builder;
import de.odysseus.el.tree.impl.Builder.Feature;
import de.odysseus.el.tree.impl.ast.*;

import com.example.jueldemo.Scanner.*;
import lombok.Data;

public class JuelParser {
    /**
     * Parse exception type
     */
    @SuppressWarnings("serial")
    public static class ParseException extends Exception {
        final int position;
        final String encountered;
        final String expected;
        public ParseException(int position, String encountered, String expected) {
            super(LocalMessages.get("error.parse", position, encountered, expected));
            this.position = position;
            this.encountered = encountered;
            this.expected = expected;
        }
    }

    /**
     * Token type (used to store lookahead)
     */
    private static final class LookaheadToken {
        final Scanner.Token token;
        final int position;

        LookaheadToken(Scanner.Token token, int position) {
            this.token = token;
            this.position = position;
        }
    }

    public enum ExtensionPoint {
        OR,
        AND,
        EQ,
        CMP,
        ADD,
        MUL,
        UNARY,
        LITERAL
    }

    /**
     * Provide limited support for syntax extensions.
     */
    public static abstract class ExtensionHandler {
        private final JuelParser.ExtensionPoint point;

        public ExtensionHandler(JuelParser.ExtensionPoint point) {
            this.point = point;
        }

        /**
         * @return the extension point specifying where this syntax extension is active
         */
        public JuelParser.ExtensionPoint getExtensionPoint() {
            return point;
        }

        /**
         * Called by the parser if it handles a extended token associated with this handler
         * at the appropriate extension point.
         * @param children
         * @return abstract syntax tree node
         */
        public abstract AstNode createAstNode(AstNode... children);
    }

    private static final String EXPR_FIRST =
            IDENTIFIER + "|" +
                    STRING + "|" + FLOAT + "|" + INTEGER + "|" + TRUE + "|" + FALSE + "|" + NULL + "|" +
                    MINUS + "|" + NOT + "|" + EMPTY + "|" +
                    LPAREN;

    protected final Builder context;
    protected final Scanner scanner;

    private List<IdentifierNode> identifiers = Collections.emptyList();
    private List<FunctionNode> functions = Collections.emptyList();
    private List<JuelParser.LookaheadToken> lookahead = Collections.emptyList();

    private Scanner.Token token; // current token
    private int position;// current token's position

    protected Map<Scanner.ExtensionToken, JuelParser.ExtensionHandler> extensions = Collections.emptyMap();

    public JuelParser(Builder context, String input) {
        this.context = context;
        this.scanner = createScanner(input);
    }

    protected Scanner createScanner(String expression) {
        return new Scanner(expression);
    }

    public void putExtensionHandler(Scanner.ExtensionToken token, JuelParser.ExtensionHandler extension) {
        if (extensions.isEmpty()) {
            extensions = new HashMap<Scanner.ExtensionToken, JuelParser.ExtensionHandler>(16);
        }
        extensions.put(token, extension);
    }

    protected JuelParser.ExtensionHandler getExtensionHandler(Token token) {
        return extensions.get(token);
    }

    /**
     * Parse an integer literal.
     * @param string string to parse
     * @return <code>Long.valueOf(string)</code>
     */
    protected Number parseInteger(String string) throws JuelParser.ParseException {
        try {
            return Long.valueOf(string);
        } catch (NumberFormatException e) {
            fail(INTEGER);
            return null;
        }
    }

    /**
     * Parse a floating point literal.
     * @param string string to parse
     * @return <code>Double.valueOf(string)</code>
     */
    protected Number parseFloat(String string) throws JuelParser.ParseException {
        try {
            return Double.valueOf(string);
        } catch (NumberFormatException e) {
            fail(FLOAT);
            return null;
        }
    }

    protected AstBinary createAstBinary(AstNode left, AstNode right, AstBinary.Operator operator) {
        return new AstBinary(left, right, operator);
    }

    protected AstBracket createAstBracket(AstNode base, AstNode property, boolean lvalue, boolean strict) {
        return new AstBracket(base, property, lvalue, strict, context.isEnabled(Feature.IGNORE_RETURN_TYPE));
    }

    protected AstChoice createAstChoice(AstNode question, AstNode yes, AstNode no) {
        return new AstChoice(question, yes, no);
    }

    protected AstComposite createAstComposite(List<AstNode> nodes) {
        return new AstComposite(nodes);
    }

    protected AstDot createAstDot(AstNode base, String property, boolean lvalue) {
        return new AstDot(base, property, lvalue, context.isEnabled(Feature.IGNORE_RETURN_TYPE));
    }

    protected AstFunction createAstFunction(String name, int index, AstParameters params) {
        return new AstFunction(name, index, params, context.isEnabled(Feature.VARARGS));
    }

    protected AstIdentifier createAstIdentifier(String name, int index) {
        return new AstIdentifier(name, index, context.isEnabled(Feature.IGNORE_RETURN_TYPE));
    }

    protected AstMethod createAstMethod(AstProperty property, AstParameters params) {
        return new AstMethod(property, params);
    }

    protected AstUnary createAstUnary(AstNode child, AstUnary.Operator operator) {
        return new AstUnary(child, operator);
    }

    protected final List<FunctionNode> getFunctions() {
        return functions;
    }

    protected final List<IdentifierNode> getIdentifiers() {
        return identifiers;
    }

    protected final Token getToken() {
        return token;
    }

    /**
     * throw exception
     */
    protected void fail(String expected) throws JuelParser.ParseException {
        throw new JuelParser.ParseException(position, "'" + token.getImage() + "'", expected);
    }

    /**
     * throw exception
     */
    protected void fail(Symbol expected) throws JuelParser.ParseException {
        fail(expected.toString());
    }

    /**
     * get lookahead symbol.
     */
    protected final Token lookahead(int index) throws ScanException, JuelParser.ParseException {
        if (lookahead.isEmpty()) {
            lookahead = new LinkedList<JuelParser.LookaheadToken>();
        }
        while (index >= lookahead.size()) {
            lookahead.add(new JuelParser.LookaheadToken(scanner.next(), scanner.getPosition()));
        }
        return lookahead.get(index).token;
    }

    /**
     * consume current token (get next token).
     * @return the consumed token (which was the current token when calling this method)
     */
    protected final Token consumeToken() throws ScanException, JuelParser.ParseException {
        Token result = token;
        if (lookahead.isEmpty()) {
            token = scanner.next();
            position = scanner.getPosition();
        } else {
            JuelParser.LookaheadToken next = lookahead.remove(0);
            token = next.token;
            position = next.position;
        }
        return result;
    }

    /**
     * consume current token (get next token); throw exception if the current token doesn't
     * match the expected symbol.
     */
    protected final Token consumeToken(Symbol expected) throws ScanException, JuelParser.ParseException {
        if (token.getSymbol() != expected) {
            fail(expected);
        }
        return consumeToken();
    }

    /**
     * tree := text? ((dynamic text?)+ | (deferred text?)+)?
     */
    public Tree tree() throws ScanException, JuelParser.ParseException {
        consumeToken();
        AstNode t = text();
        if (token.getSymbol() == EOF) {
            if (t == null) {
                t = new AstText("");
            }
            return new Tree(t, functions, identifiers, false);
        }
        AstEval e = eval();
        if (token.getSymbol() == EOF && t == null) {
            return new Tree(e, functions, identifiers, e.isDeferred());
        }
        ArrayList<AstNode> list = new ArrayList<AstNode>();
        if (t != null) {
            list.add(t);
        }
        list.add(e);
        t = text();
        if (t != null) {
            list.add(t);
        }
        while (token.getSymbol() != EOF) {
            if (e.isDeferred()) {
                list.add(eval(true, true));
            } else {
                list.add(eval(true, false));
            }
            t = text();
            if (t != null) {
                list.add(t);
            }
        }
        return new Tree(createAstComposite(list), functions, identifiers, e.isDeferred());
    }

    /**
     * text := &lt;TEXT&gt;
     */
    protected AstNode text() throws ScanException, JuelParser.ParseException {
        AstNode v = null;
        if (token.getSymbol() == TEXT) {
            v = new AstText(token.getImage());
            consumeToken();
        }
        return v;
    }

    /**
     * eval := dynamic | deferred
     */
    protected AstEval eval() throws ScanException, JuelParser.ParseException {
        AstEval e = eval(false, false);
        if (e == null) {
            e = eval(false, true);
            if (e == null) {
                fail(START_EVAL_DEFERRED + "|" + START_EVAL_DYNAMIC);
            }
        }
        return e;
    }

    /**
     * dynmamic := &lt;START_EVAL_DYNAMIC&gt; expr &lt;END_EVAL&gt;
     * deferred := &lt;START_EVAL_DEFERRED&gt; expr &lt;END_EVAL&gt;
     */
    protected AstEval eval(boolean required, boolean deferred) throws ScanException, JuelParser.ParseException {
        AstEval v = null;
        Symbol start_eval = deferred ? START_EVAL_DEFERRED : START_EVAL_DYNAMIC;
        if (token.getSymbol() == start_eval) {
            consumeToken();
            v = new AstEval(expr(true), deferred);
            consumeToken(END_EVAL);
        } else if (required) {
            fail(start_eval);
        }
        return v;
    }

    /**
     * expr := or (&lt;QUESTION&gt; expr &lt;COLON&gt; expr)?
     */
    protected AstNode expr(boolean required) throws ScanException, JuelParser.ParseException {
        AstNode v = or(required);
        if (v == null) {
            return null;
        }
        if (token.getSymbol() == QUESTION) {
            consumeToken();
            AstNode a = expr(true);
            consumeToken(COLON);
            AstNode b = expr(true);
            v = createAstChoice(v, a, b);
        }
        return v;
    }

    /**
     * or := and (&lt;OR&gt; and)*
     */
    protected AstNode or(boolean required) throws ScanException, JuelParser.ParseException {
        AstNode v = and(required);
        if (v == null) {
            return null;
        }
        while (true) {
            switch (token.getSymbol()) {
                case OR:
                    consumeToken();
                    v = createAstBinary(v, and(true), AstBinary.OR);
                    break;
                case EXTENSION:
                    if (getExtensionHandler(token).getExtensionPoint() == JuelParser.ExtensionPoint.OR) {
                        v = getExtensionHandler(consumeToken()).createAstNode(v, and(true));
                        break;
                    }
                default:
                    return v;
            }
        }
    }

    /**
     * and := eq (&lt;AND&gt; eq)*
     */
    protected AstNode and(boolean required) throws ScanException, JuelParser.ParseException {
        AstNode v = eq(required);
        if (v == null) {
            return null;
        }
        while (true) {
            switch (token.getSymbol()) {
                case AND:
                    consumeToken();
                    v = createAstBinary(v, eq(true), AstBinary.AND);
                    break;
                case EXTENSION:
                    if (getExtensionHandler(token).getExtensionPoint() == JuelParser.ExtensionPoint.AND) {
                        v = getExtensionHandler(consumeToken()).createAstNode(v, eq(true));
                        break;
                    }
                default:
                    return v;
            }
        }
    }

    /**
     * eq := cmp (&lt;EQ&gt; cmp | &lt;NE&gt; cmp)*
     */
    protected AstNode eq(boolean required) throws ScanException, JuelParser.ParseException {
        AstNode v = cmp(required);
        if (v == null) {
            return null;
        }
        while (true) {
            switch (token.getSymbol()) {
                case EQ:
                    consumeToken();
                    v = createAstBinary(v, cmp(true), AstBinary.EQ);
                    break;
                case NE:
                    consumeToken();
                    v = createAstBinary(v, cmp(true), AstBinary.NE);
                    break;
                case EXTENSION:
                    if (getExtensionHandler(token).getExtensionPoint() == JuelParser.ExtensionPoint.EQ) {
                        v = getExtensionHandler(consumeToken()).createAstNode(v, cmp(true));
                        break;
                    }
                default:
                    return v;
            }
        }
    }

    /**
     * cmp := add (&lt;LT&gt; add | &lt;LE&gt; add | &lt;GE&gt; add | &lt;GT&gt; add)*
     */
    protected AstNode cmp(boolean required) throws ScanException, JuelParser.ParseException {
        AstNode v = add(required);
        if (v == null) {
            return null;
        }
        while (true) {
            switch (token.getSymbol()) {
                case LT:
                    consumeToken();
                    v = createAstBinary(v, add(true), AstBinary.LT);
                    break;
                case LE:
                    consumeToken();
                    v = createAstBinary(v, add(true), AstBinary.LE);
                    break;
                case GE:
                    consumeToken();
                    v = createAstBinary(v, add(true), AstBinary.GE);
                    break;
                case GT:
                    consumeToken();
                    v = createAstBinary(v, add(true), AstBinary.GT);
                    break;
                case EXTENSION:
                    if (getExtensionHandler(token).getExtensionPoint() == JuelParser.ExtensionPoint.CMP) {
                        v = getExtensionHandler(consumeToken()).createAstNode(v, add(true));
                        break;
                    }
                default:
                    return v;
            }
        }
    }

    /**
     * add := add (&lt;PLUS&gt; mul | &lt;MINUS&gt; mul)*
     */
    protected AstNode add(boolean required) throws ScanException, JuelParser.ParseException {
        AstNode v = mul(required);
        if (v == null) {
            return null;
        }
        while (true) {
            switch (token.getSymbol()) {
                case PLUS:
                    consumeToken();
                    v = createAstBinary(v, mul(true), AstBinary.ADD);
                    break;
                case MINUS:
                    consumeToken();
                    v = createAstBinary(v, mul(true), AstBinary.SUB);
                    break;
                case EXTENSION:
                    if (getExtensionHandler(token).getExtensionPoint() == JuelParser.ExtensionPoint.ADD) {
                        v = getExtensionHandler(consumeToken()).createAstNode(v, mul(true));
                        break;
                    }
                default:
                    return v;
            }
        }
    }

    /**
     * mul := unary (&lt;MUL&gt; unary | &lt;DIV&gt; unary | &lt;MOD&gt; unary)*
     */
    protected AstNode mul(boolean required) throws ScanException, JuelParser.ParseException {
        AstNode v = unary(required);
        if (v == null) {
            return null;
        }
        while (true) {
            switch (token.getSymbol()) {
                case MUL:
                    consumeToken();
                    v = createAstBinary(v, unary(true), AstBinary.MUL);
                    break;
                case DIV:
                    consumeToken();
                    v = createAstBinary(v, unary(true), AstBinary.DIV);
                    break;
                case MOD:
                    consumeToken();
                    v = createAstBinary(v, unary(true), AstBinary.MOD);
                    break;
                case EXTENSION:
                    if (getExtensionHandler(token).getExtensionPoint() == JuelParser.ExtensionPoint.MUL) {
                        v = getExtensionHandler(consumeToken()).createAstNode(v, unary(true));
                        break;
                    }
                default:
                    return v;
            }
        }
    }

    /**
     * unary := &lt;NOT&gt; unary | &lt;MINUS&gt; unary | &lt;EMPTY&gt; unary | value
     */
    protected AstNode unary(boolean required) throws ScanException, JuelParser.ParseException {
        AstNode v = null;
        switch (token.getSymbol()) {
            case NOT:
                consumeToken();
                v = createAstUnary(unary(true), AstUnary.NOT);
                break;
            case MINUS:
                consumeToken();
                v = createAstUnary(unary(true), AstUnary.NEG);
                break;
            case EMPTY:
                consumeToken();
                v = createAstUnary(unary(true), AstUnary.EMPTY);
                break;
            case EXTENSION:
                if (getExtensionHandler(token).getExtensionPoint() == JuelParser.ExtensionPoint.UNARY) {
                    v = getExtensionHandler(consumeToken()).createAstNode(unary(true));
                    break;
                }
            default:
                v = value();
        }
        if (v == null && required) {
            fail(EXPR_FIRST);
        }
        return v;
    }

    /**
     * value := (nonliteral | literal) (&lt;DOT&gt; &lt;IDENTIFIER&gt; | &lt;LBRACK&gt; expr &lt;RBRACK&gt;)*
     */
    protected AstNode value() throws ScanException, JuelParser.ParseException {
        boolean lvalue = true;
        AstNode v = nonliteral();
        if (v == null) {
            v = literal();
            if (v == null) {
                return null;
            }
            lvalue = false;
        }
        while (true) {
            switch (token.getSymbol()) {
                case DOT:
                    consumeToken();
                    String name = consumeToken(IDENTIFIER).getImage();
                    AstDot dot = createAstDot(v, name, lvalue);
                    if (token.getSymbol() == LPAREN && context.isEnabled(METHOD_INVOCATIONS)) {
                        v = createAstMethod(dot, params());
                    } else {
                        v = dot;
                    }
                    break;
                case LBRACK:
                    consumeToken();
                    AstNode property = expr(true);
                    boolean strict = !context.isEnabled(NULL_PROPERTIES);
                    consumeToken(RBRACK);
                    AstBracket bracket = createAstBracket(v, property, lvalue, strict);
                    if (token.getSymbol() == LPAREN && context.isEnabled(METHOD_INVOCATIONS)) {
                        v = createAstMethod(bracket, params());
                    } else {
                        v = bracket;
                    }
                    break;
                default:
                    return v;
            }
        }
    }

    /**
     * nonliteral := &lt;IDENTIFIER&gt; | function | &lt;LPAREN&gt; expr &lt;RPAREN&gt;
     * function   := (&lt;IDENTIFIER&gt; &lt;COLON&gt;)? &lt;IDENTIFIER&gt; &lt;LPAREN&gt; list? &lt;RPAREN&gt;
     */
    protected AstNode nonliteral() throws ScanException, JuelParser.ParseException {
        AstNode v = null;
        switch (token.getSymbol()) {
            case IDENTIFIER:
                String name = consumeToken().getImage();
                if (token.getSymbol() == COLON && lookahead(0).getSymbol() == IDENTIFIER && lookahead(1).getSymbol() == LPAREN) { // ns:f(...)
                    consumeToken();
                    name += ":" + token.getImage();
                    consumeToken();
                }
                if (token.getSymbol() == LPAREN) { // function
                    v = function(name, params());
                } else { // identifier
                    v = identifier(name);
                }
                break;
            case LPAREN:
                consumeToken();
                v = expr(true);
                consumeToken(RPAREN);
                v = new AstNested(v);
                break;
        }
        return v;
    }

    /**
     * params := &lt;LPAREN&gt; (expr (&lt;COMMA&gt; expr)*)? &lt;RPAREN&gt;
     */
    protected AstParameters params() throws ScanException, JuelParser.ParseException {
        consumeToken(LPAREN);
        List<AstNode> l = Collections.emptyList();
        AstNode v = expr(false);
        if (v != null) {
            l = new ArrayList<AstNode>();
            l.add(v);
            while (token.getSymbol() == COMMA) {
                consumeToken();
                l.add(expr(true));
            }
        }
        consumeToken(RPAREN);
        return new AstParameters(l);
    }

    /**
     * literal := &lt;TRUE&gt; | &lt;FALSE&gt; | &lt;STRING&gt; | &lt;INTEGER&gt; | &lt;FLOAT&gt; | &lt;NULL&gt;
     */
    protected AstNode literal() throws ScanException, JuelParser.ParseException {
        AstNode v = null;
        switch (token.getSymbol()) {
            case TRUE:
                v = new AstBoolean(true);
                consumeToken();
                break;
            case FALSE:
                v = new AstBoolean(false);
                consumeToken();
                break;
            case STRING:
                v = new AstString(token.getImage());
                consumeToken();
                break;
            case INTEGER:
                v = new AstNumber(parseInteger(token.getImage()));
                consumeToken();
                break;
            case FLOAT:
                v = new AstNumber(parseFloat(token.getImage()));
                consumeToken();
                break;
            case NULL:
                v = new AstNull();
                consumeToken();
                break;
            case EXTENSION:
                if (getExtensionHandler(token).getExtensionPoint() == JuelParser.ExtensionPoint.LITERAL) {
                    v = getExtensionHandler(consumeToken()).createAstNode();
                    break;
                }
        }
        return v;
    }

    protected final AstFunction function(String name, AstParameters params) {
        if (functions.isEmpty()) {
            functions = new ArrayList<FunctionNode>(4);
        }
        AstFunction function = createAstFunction(name, functions.size(), params);
        functions.add(function);
        return function;
    }

    protected final AstIdentifier identifier(String name) {
        if (identifiers.isEmpty()) {
            identifiers = new ArrayList<IdentifierNode>(4);
        }
        AstIdentifier identifier = createAstIdentifier(name, identifiers.size());
        identifiers.add(identifier);
        return identifier;
    }

    public static void main(String[] args) {
        String expressionText = "${(char1==person.name && char1=='helloworld' && char1!=null && char1==varhelloworld) || (number1>1.00100 && number1==6.00 && number1<7.001 && number1<=6.0000 && number1>=6 && number1!=5 && number1!=null) || (bool1==true && bool2==false && time1!=null && timenull==null && time1=='2021-08-11' && time1<'2022-01-01' && time1>'2021-01-01')}";
        JuelParser parser = new JuelParser(new Builder(), expressionText);
        ExpressionNode expressionNode = null;
        try {
            expressionNode = parser.parseExp();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            AstNode root = (AstNode) expressionNode.getChild(0);
            ExpLeafNode rootNode = JuelParser.parseExpressTree(root);
            System.out.println(rootNode.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 扩展语法树解析方法
     * @return
     * @throws ScanException
     * @throws JuelParser.ParseException
     */
    public ExpressionNode parseExp() throws ScanException, JuelParser.ParseException {
        consumeToken();
        AstNode t = text();
        if (token.getSymbol() == EOF) {
            if (t == null) {
                t = new AstText("");
            }
            return t;
        }
        AstEval e = eval();
        if (token.getSymbol() == EOF && t == null) {
            return e;
        }
        ArrayList<AstNode> list = new ArrayList<AstNode>();
        if (t != null) {
            list.add(t);
        }
        list.add(e);
        t = text();
        if (t != null) {
            list.add(t);
        }
        while (token.getSymbol() != EOF) {
            if (e.isDeferred()) {
                list.add(eval(true, true));
            } else {
                list.add(eval(true, false));
            }
            t = text();
            if (t != null) {
                list.add(t);
            }
        }
        return createAstComposite(list);
    }

    /**
     * 根据语法树生成简单树
     * @param rootChild
     * @return
     * @throws Exception
     */
    public static ExpLeafNode parseExpressTree(AstNode rootChild) throws Exception {
        ExpLeafNode leafNode = new ExpLeafNode();
        if (rootChild instanceof AstBinary) {
            AstBinary.Operator operator = ((AstBinary) rootChild).getOperator();
            Class<? extends AstNode> rootChildClass = rootChild.getClass();
            Field leftField = rootChildClass.getDeclaredField("left");
            leftField.setAccessible(true);
            AstNode leftNode = (AstNode) leftField.get(rootChild);
            Field rightField = rootChildClass.getDeclaredField("right");
            rightField.setAccessible(true);
            AstNode rightNode = (AstNode) rightField.get(rootChild);
            leafNode.setOperator(operator.toString());
            ExpLeafNode leftSubLeafNode = null;
            ExpLeafNode rightSubLeafNode = null;

            if (leftNode instanceof AstBinary) {
                leftSubLeafNode = parseExpressTree(leftNode);
            } else if (leftNode instanceof AstNested) {
                AstNode leftSubNode = (AstNode) leftNode.getChild(0);
                leftSubLeafNode = parseExpressTree(leftSubNode);
            } else if (leftNode instanceof AstIdentifier) {
                String variableName = ((AstIdentifier) leftNode).getName();
                leafNode.setLeftLeaf(variableName);
            } else if (leftNode instanceof AstNull) {
                leafNode.setLeftLeaf(null);
            }
            leafNode.setLeftNode(leftSubLeafNode);

            if (rightNode instanceof AstBinary) {
                rightSubLeafNode = parseExpressTree(rightNode);
            } else if (rightNode instanceof AstNested) {
                AstNode rightSubNode = (AstNode) rightNode.getChild(0);
                rightSubLeafNode = parseExpressTree(rightSubNode);
            } else if (rightNode instanceof AstBoolean) {
                AstBoolean rightValue = (AstBoolean) rightNode;
                Field valueField = rightValue.getClass().getDeclaredField("value");
                valueField.setAccessible(true);
                String rightLeafValue = valueField.get(rightValue).toString();
                leafNode.setRightLeaf(rightLeafValue);
            } else if (rightNode instanceof AstString) {
                AstString rightValue = (AstString) rightNode;
                Field valueField = rightValue.getClass().getDeclaredField("value");
                valueField.setAccessible(true);
                String rightLeafValue = valueField.get(rightValue).toString();
                leafNode.setRightLeaf("'" + rightLeafValue + "'");
            } else if (rightNode instanceof AstNumber) {
                AstNumber rightValue = (AstNumber) rightNode;
                Field valueField = rightValue.getClass().getDeclaredField("value");
                valueField.setAccessible(true);
                Number number = (Number) valueField.get(rightValue);
                BigDecimal bigDecimal = new BigDecimal(number.toString());
                String rightLeafValue = null;
                if (bigDecimal.scale() > 0) {
                    rightLeafValue = bigDecimal.toString();
                } else {
                    rightLeafValue = bigDecimal.toBigInteger().toString();
                }
                leafNode.setRightLeaf(rightLeafValue);
            } else if (rightNode instanceof AstNull) {
                leafNode.setRightLeaf(null);
            } else if (rightNode instanceof AstIdentifier) {
                String variableName = ((AstIdentifier) rightNode).getName();
                leafNode.setRightLeaf(variableName);
            } else if (rightNode instanceof AstDot) {
                AstDot rightValue = (AstDot) rightNode;
                Field prefixField = null;
                try {
                    prefixField = rightValue.getClass().getDeclaredField("prefix");
                } catch (NoSuchFieldException e) {
                    prefixField = rightValue.getClass().getSuperclass().getDeclaredField("prefix");
                }
                prefixField.setAccessible(true);
                AstIdentifier objectIdentifier = (AstIdentifier) prefixField.get(rightValue);
                String clazzName = objectIdentifier.getName();
                Field propertyField = rightValue.getClass().getDeclaredField("property");
                propertyField.setAccessible(true);
                String propertyName = propertyField.get(rightValue).toString();
                leafNode.setRightLeaf("#" + clazzName + "." + propertyName);
            }
            leafNode.setRightNode(rightSubLeafNode);

        }
        return leafNode;
    }

    @Data
    static class ExpLeafNode {
        private ExpLeafNode leftNode;
        private String operator;
        private ExpLeafNode rightNode;

        private String leftLeaf;
        private String rightLeaf;

        @Override
        public String toString() {
            StringJoiner stringJoiner = new StringJoiner("");
            stringJoiner.add("(");
            if (leftNode != null) {
                stringJoiner.add(leftNode.toString());
            } else {
                stringJoiner.add(leftLeaf);
            }
            stringJoiner.add(" " + operator + " ");
            if (rightNode != null) {
                stringJoiner.add(rightNode.toString());
            } else {
                stringJoiner.add(rightLeaf);
            }
            stringJoiner.add(")");

            return stringJoiner.toString();
        }
    }
}
