package com.example.jueldemo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.odysseus.el.ExpressionFactoryImpl;
import de.odysseus.el.tree.ExpressionNode;
import de.odysseus.el.tree.impl.Builder;
import de.odysseus.el.tree.impl.ast.AstNode;
import de.odysseus.el.util.SimpleContext;
import de.odysseus.el.util.SimpleResolver;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import javax.el.ExpressionFactory;
import javax.el.ValueExpression;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 简单条件表达式解析器
 * 提供以下功能：解析1.0条件表单生成简单条件表达式、通过简单条件表达式生成1.0条件表单、计算1.0简单条件表达式的值
 * @author hu
 */
public class SimpleConditionExpressionParser {

    // 测试条件表单json，用来生成条件表达式
    public static List<List<ConditionNode>> raw_nodeList = new ArrayList<>();
    // 测试条件表达式变量值集合，用来计算
    public static List<ExpressionVariable> raw_variableList = new ArrayList<>();
    // 变量传参
    public static Map<String, Object> raw_objectParamMap = new HashMap<>();
    // 日期校验格式
    private static final String DATE_PATTERN = "^\\d{4}-\\d{1,2}-\\d{1,2}";

    static {
        List<ConditionNode> orExp_string = new ArrayList<>();
        List<ConditionNode> orExp_number = new ArrayList<>();
        List<ConditionNode> orExp_date = new ArrayList<>();
        List<ConditionNode> orExp_bool = new ArrayList<>();

        // 字符串：等于、空、非空
        // 值类型：固定值
        orExp_string.add(new ConditionNode("string1", MyOperator.EQ, "helloworld", ConditionClass.STRING, ConditionValueType.FIXED));
        orExp_string.add(new ConditionNode("string1", MyOperator.EQ, null, ConditionClass.STRING, ConditionValueType.FIXED));
        orExp_string.add(new ConditionNode("string1", MyOperator.NE, null, ConditionClass.STRING, ConditionValueType.FIXED));
        // 值类型：参数
        orExp_string.add(new ConditionNode("string1", MyOperator.EQ, "varhelloworld", ConditionClass.STRING, ConditionValueType.PARAM));
        // 值类型：对象参数
        orExp_string.add(new ConditionNode("string1", MyOperator.GT, "objectparam.string_var1", ConditionClass.STRING, ConditionValueType.OBJECTPARAM));

        // 数值：大于、等于、小于、大于等于、小于等于、不等于、空、非空
        // 值类型：固定值
        orExp_number.add(new ConditionNode("number2", MyOperator.GT, "1", ConditionClass.NUMBER, ConditionValueType.FIXED));
        orExp_number.add(new ConditionNode("number2", MyOperator.EQ, "6.0", ConditionClass.NUMBER, ConditionValueType.FIXED));
        orExp_number.add(new ConditionNode("number2", MyOperator.LT, "7.001", ConditionClass.NUMBER, ConditionValueType.FIXED));
        orExp_number.add(new ConditionNode("number2", MyOperator.GE, "6", ConditionClass.NUMBER, ConditionValueType.FIXED));
        orExp_number.add(new ConditionNode("number2", MyOperator.LE, "6.000", ConditionClass.NUMBER, ConditionValueType.FIXED));
        orExp_number.add(new ConditionNode("number2", MyOperator.NE, "-10.00010000", ConditionClass.NUMBER, ConditionValueType.FIXED));
        orExp_number.add(new ConditionNode("number2", MyOperator.EQ, null, ConditionClass.NUMBER, ConditionValueType.FIXED));
        orExp_number.add(new ConditionNode("number2", MyOperator.NE, null, ConditionClass.NUMBER, ConditionValueType.FIXED));
        // 值类型：参数
        orExp_number.add(new ConditionNode("number2", MyOperator.EQ, "varnumber", ConditionClass.NUMBER, ConditionValueType.PARAM));
        // 值类型：对象参数
        orExp_number.add(new ConditionNode("number2", MyOperator.EQ, "objectparam.num_var2", ConditionClass.NUMBER, ConditionValueType.OBJECTPARAM));

        // 日期：指定某天、某天之前、某天之后、空、非空
        // 值类型：固定值
        orExp_date.add(new ConditionNode("date3", MyOperator.NE, null, ConditionClass.DATE, ConditionValueType.FIXED));
        orExp_date.add(new ConditionNode("date3", MyOperator.EQ, null, ConditionClass.DATE, ConditionValueType.FIXED));
        orExp_date.add(new ConditionNode("date3", MyOperator.EQ, "2021-08-11", ConditionClass.DATE, ConditionValueType.FIXED));
        orExp_date.add(new ConditionNode("date3", MyOperator.LT, "2022-01-01", ConditionClass.DATE, ConditionValueType.FIXED));
        orExp_date.add(new ConditionNode("date3", MyOperator.GT, "2021-01-01", ConditionClass.DATE, ConditionValueType.FIXED));
        // 值类型：参数
        orExp_date.add(new ConditionNode("date3", MyOperator.EQ, "vardate3", ConditionClass.DATE, ConditionValueType.PARAM));
        // 值类型：对象参数
        orExp_date.add(new ConditionNode("date3", MyOperator.EQ, "objectparam.date_var3", ConditionClass.DATE, ConditionValueType.OBJECTPARAM));

        // 布尔：为真、为假
        // 值类型：固定值
        orExp_bool.add(new ConditionNode("bool4", MyOperator.EQ, "true", ConditionClass.BOOL, ConditionValueType.FIXED));
        orExp_bool.add(new ConditionNode("bool4", MyOperator.EQ, "false", ConditionClass.BOOL, ConditionValueType.FIXED));
        // 值类型：参数
        orExp_bool.add(new ConditionNode("bool4", MyOperator.EQ, "varbool4", ConditionClass.BOOL, ConditionValueType.PARAM));
        // 值类型：对象参数
        orExp_bool.add(new ConditionNode("bool4", MyOperator.EQ, "objectparam.bool_var4", ConditionClass.BOOL, ConditionValueType.OBJECTPARAM));

        raw_nodeList.add(orExp_string);
        raw_nodeList.add(orExp_number);
        raw_nodeList.add(orExp_date);
        raw_nodeList.add(orExp_bool);

        // 变量传参
        raw_objectParamMap.put("string_var1", "Narcos Mexico");
        raw_objectParamMap.put("num_var2", 60);
        raw_objectParamMap.put("date_var3", "2027-12-31");
        raw_objectParamMap.put("bool_var4", false);

        raw_variableList.add(new ExpressionVariable("#{string1}", String.class, "helloworld"));
        raw_variableList.add(new ExpressionVariable("#{number2}", Number.class, 6));
        raw_variableList.add(new ExpressionVariable("#{date3}", LocalDate.class, LocalDate.now().plusYears(3)));
        raw_variableList.add(new ExpressionVariable("#{bool4}", Boolean.class, true));
        raw_variableList.add(new ExpressionVariable("#{varhelloworld}", String.class, "helloworld"));
        raw_variableList.add(new ExpressionVariable("#{varnumber}", Number.class, -60.0010));
        raw_variableList.add(new ExpressionVariable("#{vardate3}", String.class, "2048-01-01"));
        raw_variableList.add(new ExpressionVariable("#{varbool4}", String.class, true));
        raw_variableList.add(new ExpressionVariable("#{objectparam}", Map.class, raw_objectParamMap));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("\n*****条件表单数据:\n" + new ObjectMapper().writeValueAsString(raw_nodeList));
        // 解析1.0条件表单生成简单条件表达式
        String expression = SimpleConditionExpressionParser.generateSimpleExpression(raw_nodeList);
        System.out.println("\n*****条件表单生成条件表达式:\n" + expression);
        // 计算1.0简单条件表达式的值
        String expressionValue = SimpleConditionExpressionParser.getExpressionValue(raw_variableList, expression);
        System.out.println("\n*****条件表达式求值:\n" + expressionValue);
        // 通过语法树解析生成1.0条件表单，todo
//        List<List<ConditionNode>> conditionNodeList = parseSimpleExpressionByTree(expression);
//        System.out.println("conditionNodeList:" + new ObjectMapper().writeValueAsString(conditionNodeList));
        // 通过简单条件表达式生成1.0条件表单
        List<List<ConditionNode>> conditionNodeList = SimpleConditionExpressionParser.parseSimpleExpression(expression);
        System.out.println("\n*****条件表达式解析成条件表单:\n" + new ObjectMapper().writeValueAsString(conditionNodeList));
        // 验证生成的1.0条件表单是否符合条件表达式规则
        String parsedExpression = SimpleConditionExpressionParser.generateSimpleExpression(conditionNodeList);
        System.out.println("\n*****解析后的不完整条件表单再次生成条件表达式:\n" + parsedExpression);
        System.out.println("\n*****二次生成的条件表达式对比:\n" + ((expression.equals(parsedExpression)) ? "一致" : "不一致"));

    }

    /**
     * 计算1.0简单条件表达式的值
     * @param params
     * @param expression
     * @return
     * @throws NoSuchMethodException
     */
    public static String getExpressionValue(List<ExpressionVariable> params, String expression) throws Exception {
        ExpressionFactory factory = new ExpressionFactoryImpl();
        SimpleContext context = new SimpleContext(new SimpleResolver());
        for (ExpressionVariable exp:params) {
            factory.createValueExpression(context, exp.getVariable(), exp.getValueClass()).setValue(context, exp.getValue());
        }
        ValueExpression testExp = factory.createValueExpression(context, expression, boolean.class);
        return testExp.getValue(context).toString();
    }

    /**
     * 解析1.0条件表单生成简单条件表达式
     * @param nodeList
     * @return
     * @throws Exception
     */
    public static String generateSimpleExpression(List<List<ConditionNode>> nodeList) throws Exception {
        StringJoiner stringJoiner = new StringJoiner("");
        // ${
        stringJoiner.add(MySymbol.START_EVAL_DYNAMIC.toString());

        // ()
        for (int i = 0; i < nodeList.size(); i++) {
            List<ConditionNode> or_i = nodeList.get(i);
            if (i !=0 ) {
                stringJoiner.add(MySymbol.SPACE.toString());
                stringJoiner.add(MySymbol.OR.toString());
                stringJoiner.add(MySymbol.SPACE.toString());
            }
            stringJoiner.add(MySymbol.LPAREN.toString());
            for (int j = 0; j < or_i.size(); j++) {
                ConditionNode node = or_i.get(j);
                if (j != 0) {
                    stringJoiner.add(MySymbol.SPACE.toString());
                    stringJoiner.add(MySymbol.AND.toString());
                    stringJoiner.add(MySymbol.SPACE.toString());
                }
                stringJoiner.add(node.getVariable());
                stringJoiner.add(node.getOperator().toString());

                if (node.getValue() == null) {
                    stringJoiner.add("null");
                } else {
                    switch (node.getValueType()) {
                        case FIXED:
                            switch (node.getConditionClass()) {
                                case NUMBER:
                                    String numText = node.getValue();
                                    if (StringUtils.isNumeric(numText)) {
                                        BigDecimal bigDecimal = new BigDecimal(numText);
                                        if (bigDecimal.scale() > 0) {
                                            numText = bigDecimal.toString();
                                        } else {
                                            numText = bigDecimal.toBigInteger().toString();
                                        }
                                    }
                                    stringJoiner.add(numText);
                                    break;
                                case STRING:
                                    stringJoiner.add(MySymbol.SQUOT.toString());
                                    stringJoiner.add(node.getValue());
                                    stringJoiner.add(MySymbol.SQUOT.toString());
                                    break;
                                case BOOL:
                                    boolean aBoolean = Boolean.FALSE;
                                    try {
                                        aBoolean = Boolean.parseBoolean(node.getValue());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    String boolText = aBoolean ? "true" : "false";
                                    stringJoiner.add(boolText);
                                    break;
                                case DATE:
                                    LocalDate aDate = LocalDate.parse(node.getValue());
                                    String dateText = aDate.format(DateTimeFormatter.ISO_DATE);
                                    stringJoiner.add(MySymbol.SQUOT.toString());
                                    stringJoiner.add(dateText);
                                    stringJoiner.add(MySymbol.SQUOT.toString());
                                    break;
                                default:
                                    break;
                            }
                            break;
                        default:
                            stringJoiner.add(node.getValue());
                            break;
                    }
                }

            }
            stringJoiner.add(MySymbol.RPAREN.toString());
        }

        // }
        stringJoiner.add(MySymbol.END_EVAL.toString());
        return stringJoiner.toString();
    }

    /**
     * 通过简单条件表达式生成1.0条件表单
     * @param expression
     * @return
     */
    public static List<List<ConditionNode>> parseSimpleExpression(String expression) throws Exception {
        String nodesText = StringUtils.substringBetween(expression, MySymbol.START_EVAL_DYNAMIC.toString(), MySymbol.END_EVAL.toString());
        List<String> orNodeList = Arrays.asList(StringUtils.split(nodesText, MySymbol.OR.toString()));
        List<List<ConditionNode>> orNodeArr = new ArrayList<>();
        for (String orNode:orNodeList) {
            String andNodeText = StringUtils.substringBetween(orNode, MySymbol.LPAREN.toString(), MySymbol.RPAREN.toString());
            List<String> andNodeTextList = Arrays.asList(StringUtils.split(andNodeText, MySymbol.AND.toString()));
            List<ConditionNode> andNodeArray = new ArrayList<>();
            for (String andNode:andNodeTextList) {
                andNode = andNode.trim();
                Iterator<MyOperator> iterator = MyOperator.enumSet().iterator();
                String variable = null;
                String value = null;
                while (iterator.hasNext()) {
                    MyOperator operator = iterator.next();
                    if (andNode.contains(operator.toString())) {
                        variable = StringUtils.substringBefore(andNode, operator.toString());
                        value = StringUtils.substringAfter(andNode, operator.toString());

                        // 空值
                        if (StringUtils.equals(value, "null")) {
                            andNodeArray.add(new ConditionNode(variable, operator, null, ConditionClass.UNKNOWN, ConditionValueType.FIXED));
                            break;
                        }
                        // 布尔值
                        if (StringUtils.equals(value, "true") || StringUtils.equals(value, "false")) {
                            andNodeArray.add(new ConditionNode(variable, operator, Boolean.valueOf(value).toString(), ConditionClass.BOOL, ConditionValueType.FIXED));
                            break;
                        }
                        // 字符串
                        if (value.startsWith(MySymbol.SQUOT.toString()) && value.endsWith(MySymbol.SQUOT.toString())) {
                            value = StringUtils.substringBetween(value, MySymbol.SQUOT.toString(), MySymbol.SQUOT.toString());
                            // 日期
                            if (Pattern.matches(DATE_PATTERN, value)) {
                                andNodeArray.add(new ConditionNode(variable, operator, value, ConditionClass.DATE, ConditionValueType.FIXED));
                                break;
                            }
                            // 字符串
                            andNodeArray.add(new ConditionNode(variable, operator, value, ConditionClass.STRING, ConditionValueType.FIXED));
                            break;
                        }
                        // 数值
                        if (NumberUtils.isParsable(value)) {
                            BigDecimal decimal = NumberUtils.createBigDecimal(value);
                            if (decimal.scale() > 0) {
                                value = decimal.toString();
                            } else {
                                value = decimal.toBigInteger().toString();
                            }
                            andNodeArray.add(new ConditionNode(variable, operator, value, ConditionClass.NUMBER, ConditionValueType.FIXED));
                            break;
                        }
                        // 对象参数
                        if (value.contains(".")) {
                            andNodeArray.add(new ConditionNode(variable, operator, value, ConditionClass.UNKNOWN, ConditionValueType.OBJECTPARAM));
                            break;
                        }
                        // 变量参数
                        andNodeArray.add(new ConditionNode(variable, operator, value, ConditionClass.UNKNOWN, ConditionValueType.PARAM));
                    }
                }
            }
            orNodeArr.add(andNodeArray);
        }
        return orNodeArr;
    }

    /**
     * @Deprecated
     * 通过语法树解析简单条件表达式生成1.0条件表单
     * @param expression
     * @return
     */
    @Deprecated
    public static List<List<ConditionNode>> parseSimpleExpressionByTree(String expression) throws Exception {
        String nodesText = StringUtils.substringBetween(expression, MySymbol.START_EVAL_DYNAMIC.toString(), MySymbol.END_EVAL.toString());
        List<String> ornodeList = Arrays.asList(StringUtils.split(nodesText, MySymbol.OR.toString()));
        List<List<ConditionNode>> orNodeArr = new ArrayList<>();
        for (String ornode:ornodeList) {
            String andnodeText = StringUtils.substringBetween(ornode, MySymbol.LPAREN.toString(), MySymbol.RPAREN.toString());
            List<ConditionNode> andNodeArr = new ArrayList<>();

            JuelParser parser = new JuelParser(new Builder(), MySymbol.START_EVAL_DYNAMIC.toString() + andnodeText + MySymbol.END_EVAL.toString());
            ExpressionNode expressionNode = null;
            try {
                expressionNode = parser.parseExp();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                AstNode root = (AstNode) expressionNode.getChild(0);
                JuelParser.ExpLeafNode rootNode = JuelParser.parseExpressTree(root);
                extractNodeListFromTree(andNodeArr, rootNode);
            } catch (Exception e) {
                e.printStackTrace();
            }
            orNodeArr.add(andNodeArr);
        }
        return orNodeArr;
    }

    @Deprecated
    private static void extractNodeListFromTree(List<ConditionNode> andNodeList, JuelParser.ExpLeafNode leafNode) throws Exception {
        ConditionNode conditionNode = new ConditionNode();
        if (leafNode.getLeftNode() != null) {
            extractNodeListFromTree(andNodeList, leafNode.getLeftNode());
        } else {
            conditionNode.setVariable(leafNode.getLeftLeaf());
        }
        conditionNode.setOperator(MyOperator.find(leafNode.getOperator()));
        if (leafNode.getRightNode() != null) {
            extractNodeListFromTree(andNodeList, leafNode.getRightNode());
        } else {
            conditionNode.setValue(leafNode.getRightLeaf());
        }
//        conditionNode.setValueType();
//        conditionNode.setConditionClass();
        andNodeList.add(conditionNode);
    }

    /**
     * 1.0条件表单节点
     */
    public static class ConditionNode implements Serializable {
        private String variable;
        private MyOperator operator;
        private String value;
        private ConditionClass conditionClass;
        private ConditionValueType valueType;

        public ConditionNode() {
        }

        public ConditionNode(String variable, MyOperator operator, String value, ConditionClass conditionClass, ConditionValueType valueType) {
            this.variable = variable;
            this.operator = operator;
            this.value = value;
            this.conditionClass = conditionClass;
            this.valueType = valueType;
        }

        public String getVariable() {
            return variable;
        }

        public void setVariable(String variable) {
            this.variable = variable;
        }

        public MyOperator getOperator() {
            return operator;
        }

        public void setOperator(MyOperator operator) {
            this.operator = operator;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public ConditionClass getConditionClass() {
            return conditionClass;
        }

        public void setConditionClass(ConditionClass conditionClass) {
            this.conditionClass = conditionClass;
        }

        public ConditionValueType getValueType() {
            return valueType;
        }

        public void setValueType(ConditionValueType valueType) {
            this.valueType = valueType;
        }

        @Override
        public String toString() {
            try {
                return new ObjectMapper().writeValueAsString(this);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    /**
     * 1.0条件表单变量类型
     */
    public enum ConditionClass {
        STRING,
        NUMBER,
        DATE,
        BOOL,
        UNKNOWN,
        ;
    }

    /**
     * 1.0条件表单值类型
     */
    public enum ConditionValueType {
        FIXED,
        PARAM,
        OBJECTPARAM
        ;
    }

    /**
     * 1.0条件表单分隔符枚举
     */
    public enum MySymbol {
        LPAREN("("),
        RPAREN(")"),
        AND("&&"),
        OR("||"),
        START_EVAL_DYNAMIC("${"),
        END_EVAL("}"),
        DOT("."),
        SQUOT("'"),
        SPACE(" "),
        ;

        private final String string;

        private MySymbol() {
            this((String)null);
        }

        private MySymbol(String string) {
            this.string = string;
        }

        public String toString() {
            return this.string == null ? "<" + this.name() + ">" : this.string;
        }
    }

    /**
     * 1.0条件表单操作符枚举
     */
    public enum MyOperator {
        LE("<="),
        LT("<"),
        GE(">="),
        GT(">"),
        EQ("=="),
        NE("!="),
        UNKNOWN,
        ;

        private final String string;

        private MyOperator() {
            this((String)null);
        }

        private MyOperator(String string) {
            this.string = string;
        }

        public String toString() {
            return this.string == null ? "<" + this.name() + ">" : this.string;
        }

        public static EnumSet<MyOperator> enumSet() {
            EnumSet<MyOperator> myOperators = EnumSet.allOf(MyOperator.class);
            return myOperators;
        }

        private static final Map<String, MyOperator> lookup = new HashMap<>();


        static {
            for(MyOperator e:MyOperator.enumSet()){
                lookup.put(e.string, e);
            }
        }

        public static MyOperator find(String operator){
            MyOperator value = lookup.get(operator);
            if(value == null){
                return MyOperator.UNKNOWN;
            }
            return value;
        }

    }

    /**
     * 条件表达式变量参数实体
     * @param <T>
     */
    public static class ExpressionVariable<T> {
        private String variable;
        private Class<T> valueClass;
        private T value;

        public ExpressionVariable(String variable, Class<T> valueClass, T value) {
            this.variable = variable;
            this.valueClass = valueClass;
            this.value = value;
        }

        public String getVariable() {
            return variable;
        }

        public void setVariable(String variable) {
            this.variable = variable;
        }

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }

        public Class<T> getValueClass() {
            return valueClass;
        }

        public void setValueClass(Class<T> valueClass) {
            this.valueClass = valueClass;
        }
    }

}
