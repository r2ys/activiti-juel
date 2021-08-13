# activiti-juel

### 自定义简单格式的条件编辑器
因为activiti采用了juel语言，框架本身的表达式解析器和语法树要求严格，实际业务场景的要求是使用简单的表达式即可：
${(a==1 && b==2) || (c==3 && d==4) || (e==5 && f==6)}
支持多个或公式，每个公式包含多个与公式

### 用法
```java
// 解析1.0条件表单生成简单条件表达式
List<List<ConditionNode>> raw_nodeList = Arrays.asList(...);
String expression = SimpleConditionExpressionParser.generateSimpleExpression(raw_nodeList);
System.out.println("\n*****条件表单生成条件表达式:\n" + expression);

// 通过简单条件表达式生成1.0条件表单
List<List<ConditionNode>> conditionNodeList = SimpleConditionExpressionParser.parseSimpleExpression(expression);
System.out.println("\n*****条件表达式解析成条件表单:\n" + new ObjectMapper().writeValueAsString(conditionNodeList));
```
