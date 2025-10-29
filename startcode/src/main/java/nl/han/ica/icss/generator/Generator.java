package nl.han.ica.icss.generator;

import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.literals.*;
import nl.han.ica.icss.ast.operations.AddOperation;
import nl.han.ica.icss.ast.operations.MultiplyOperation;
import nl.han.ica.icss.ast.operations.SubtractOperation;
import nl.han.ica.icss.ast.selectors.ClassSelector;
import nl.han.ica.icss.ast.selectors.IdSelector;
import nl.han.ica.icss.ast.selectors.TagSelector;

import java.util.*;
import java.util.stream.Collectors;

public class Generator {

    private static final String INDENT = "    ";

    public String generate(AST ast) {
        if (ast == null || ast.root == null) {
            return "";
        }

        Deque<Map<String, Literal>> scopes = new ArrayDeque<>();
        scopes.push(new HashMap<>());

        List<String> blocks = new ArrayList<>();

        for (ASTNode node : ast.root.getChildren()) {
            if (node instanceof VariableAssignment) {
                VariableAssignment assignment = (VariableAssignment) node;
                Literal value = evaluateExpression(assignment.expression, scopes);
                assignment.expression = value;
                assignVariable(scopes, assignment.name.name, value);
            } else if (node instanceof Stylerule) {
                blocks.add(renderStylerule((Stylerule) node, scopes));
            }
        }

        scopes.pop();

        return String.join("\n\n", blocks);
    }

    private String renderStylerule(Stylerule stylerule, Deque<Map<String, Literal>> scopes) {
        StringBuilder builder = new StringBuilder();

        String selectorText = stylerule.selectors.stream()
                .map(this::selectorToString)
                .collect(Collectors.joining(", "));
        builder.append(selectorText).append(" {\n");

        scopes.push(new HashMap<>());
        appendStatements(stylerule.body, builder, scopes, 1);
        scopes.pop();

        builder.append("}");
        return builder.toString();
    }

    private void appendStatements(List<ASTNode> statements, StringBuilder builder,
                                  Deque<Map<String, Literal>> scopes, int indentLevel) {
        for (ASTNode statement : statements) {
            if (statement instanceof Declaration) {
                appendDeclaration((Declaration) statement, builder, scopes, indentLevel);
            } else if (statement instanceof VariableAssignment) {
                VariableAssignment assignment = (VariableAssignment) statement;
                Literal value = evaluateExpression(assignment.expression, scopes);
                assignment.expression = value;
                assignVariable(scopes, assignment.name.name, value);
            } else if (statement instanceof IfClause) {
                appendIfClause((IfClause) statement, builder, scopes, indentLevel);
            }
        }
    }

    private void appendDeclaration(Declaration declaration, StringBuilder builder,
                                   Deque<Map<String, Literal>> scopes, int indentLevel) {
        Literal literal = evaluateExpression(declaration.expression, scopes);
        declaration.expression = literal;
        builder.append(INDENT.repeat(indentLevel))
                .append(declaration.property.name)
                .append(": ")
                .append(literalToCss(literal))
                .append(";\n");
    }

    private void appendIfClause(IfClause ifClause, StringBuilder builder,
                                Deque<Map<String, Literal>> scopes, int indentLevel) {
        Literal conditionLiteral = evaluateExpression(ifClause.conditionalExpression, scopes);
        boolean condition = conditionLiteral instanceof BoolLiteral && ((BoolLiteral) conditionLiteral).value;

        scopes.push(new HashMap<>());
        List<ASTNode> chosenBody = condition
                ? ifClause.body
                : ifClause.elseClause != null ? ifClause.elseClause.body : Collections.emptyList();
        appendStatements(chosenBody, builder, scopes, indentLevel);
        scopes.pop();
    }

    private String selectorToString(Selector selector) {
        if (selector instanceof ClassSelector || selector instanceof IdSelector || selector instanceof TagSelector) {
            return selector.toString();
        }
        return selector.getNodeLabel();
    }

    private String literalToCss(Literal literal) {
        if (literal instanceof PixelLiteral) {
            return ((PixelLiteral) literal).value + "px";
        }
        if (literal instanceof PercentageLiteral) {
            return ((PercentageLiteral) literal).value + "%";
        }
        if (literal instanceof ScalarLiteral) {
            return Integer.toString(((ScalarLiteral) literal).value);
        }
        if (literal instanceof ColorLiteral) {
            return ((ColorLiteral) literal).value;
        }
        if (literal instanceof BoolLiteral) {
            return ((BoolLiteral) literal).value ? "TRUE" : "FALSE";
        }
        return literal.toString();
    }

    private Literal evaluateExpression(Expression expression, Deque<Map<String, Literal>> scopes) {
        if (expression == null) {
            return new ScalarLiteral(0);
        }

        if (expression instanceof Literal) {
            return (Literal) expression;
        }

        if (expression instanceof VariableReference) {
            String name = ((VariableReference) expression).name;
            for (Map<String, Literal> scope : scopes) {
                if (scope.containsKey(name)) {
                    return scope.get(name);
                }
            }
            return new ScalarLiteral(0);
        }

        if (expression instanceof AddOperation) {
            AddOperation add = (AddOperation) expression;
            Literal left = evaluateExpression(add.lhs, scopes);
            Literal right = evaluateExpression(add.rhs, scopes);
            return computeOperation(left, right, "+");
        }

        if (expression instanceof SubtractOperation) {
            SubtractOperation sub = (SubtractOperation) expression;
            Literal left = evaluateExpression(sub.lhs, scopes);
            Literal right = evaluateExpression(sub.rhs, scopes);
            return computeOperation(left, right, "-");
        }

        if (expression instanceof MultiplyOperation) {
            MultiplyOperation mul = (MultiplyOperation) expression;
            Literal left = evaluateExpression(mul.lhs, scopes);
            Literal right = evaluateExpression(mul.rhs, scopes);
            return computeOperation(left, right, "*");
        }

        return new ScalarLiteral(0);
    }

    private Literal computeOperation(Literal left, Literal right, String operator) {
        if (left instanceof PixelLiteral && right instanceof PixelLiteral) {
            int leftValue = ((PixelLiteral) left).value;
            int rightValue = ((PixelLiteral) right).value;
            if (operator.equals("+")) {
                return new PixelLiteral(leftValue + rightValue);
            }
            if (operator.equals("-")) {
                return new PixelLiteral(leftValue - rightValue);
            }
        }

        if (left instanceof PercentageLiteral && right instanceof ScalarLiteral && operator.equals("*")) {
            int result = ((PercentageLiteral) left).value * ((ScalarLiteral) right).value;
            return new PercentageLiteral(result);
        }

        if (left instanceof ScalarLiteral && right instanceof PercentageLiteral && operator.equals("*")) {
            int result = ((ScalarLiteral) left).value * ((PercentageLiteral) right).value;
            return new PercentageLiteral(result);
        }

        if (left instanceof PixelLiteral && right instanceof ScalarLiteral && operator.equals("*")) {
            int result = ((PixelLiteral) left).value * ((ScalarLiteral) right).value;
            return new PixelLiteral(result);
        }

        if (left instanceof ScalarLiteral && right instanceof PixelLiteral && operator.equals("*")) {
            int result = ((ScalarLiteral) left).value * ((PixelLiteral) right).value;
            return new PixelLiteral(result);
        }

        if (left instanceof ScalarLiteral && right instanceof ScalarLiteral) {
            int leftValue = ((ScalarLiteral) left).value;
            int rightValue = ((ScalarLiteral) right).value;
            switch (operator) {
                case "+":
                    return new ScalarLiteral(leftValue + rightValue);
                case "-":
                    return new ScalarLiteral(leftValue - rightValue);
                case "*":
                    return new ScalarLiteral(leftValue * rightValue);
            }
        }

        if (left instanceof PercentageLiteral && right instanceof PercentageLiteral && operator.equals("+")) {
            int result = ((PercentageLiteral) left).value + ((PercentageLiteral) right).value;
            return new PercentageLiteral(result);
        }

        if (left instanceof PercentageLiteral && right instanceof PercentageLiteral && operator.equals("-")) {
            int result = ((PercentageLiteral) left).value - ((PercentageLiteral) right).value;
            return new PercentageLiteral(result);
        }

        return new ScalarLiteral(0);
    }

    private void assignVariable(Deque<Map<String, Literal>> scopes, String name, Literal value) {
        for (Map<String, Literal> scope : scopes) {
            if (scope.containsKey(name)) {
                scope.put(name, value);
                return;
            }
        }
        if (!scopes.isEmpty()) {
            scopes.peek().put(name, value);
        }
    }
}
