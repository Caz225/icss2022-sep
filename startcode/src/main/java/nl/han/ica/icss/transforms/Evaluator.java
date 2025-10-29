package nl.han.ica.icss.transforms;

import nl.han.ica.datastructures.HANLinkedList;
import nl.han.ica.datastructures.IHANLinkedList;
import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.literals.*;
import nl.han.ica.icss.ast.operations.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Evaluator implements Transform {

    private IHANLinkedList<HashMap<String, Literal>> variableValues;

    public Evaluator() {
        variableValues = new HANLinkedList<>();
    }

    @Override
    public void apply(AST ast) {
        variableValues = new HANLinkedList<>();
        openNewScope();
        evaluateNode(ast.root, null);
        closeCurrentScope();
    }

    // -------------------------
    // Scope helpers
    // -------------------------
    private void openNewScope() {
        variableValues.addFirst(new HashMap<>());
    }

    private void closeCurrentScope() {
        if (variableValues.getSize() > 0) variableValues.removeFirst();
    }

    private void defineVariable(String name, Literal value) {
        variableValues.getFirst().put(name, value);
    }

    private Literal findVariable(String name) {
        try {
            HashMap<String, Literal> top = variableValues.getFirst();
            if (top != null && top.containsKey(name)) return top.get(name);
        } catch (Exception ignored) { }

        int n = variableValues.getSize();
        for (int i = n - 1; i >= 0; i--) {
            HashMap<String, Literal> scope = variableValues.get(i);
            if (scope != null && scope.containsKey(name)) return scope.get(name);
        }
        return null;
    }

    // -------------------------
    // Tree traversal
    // -------------------------
    private void evaluateNode(ASTNode node, ASTNode parent) {
        String indent = parent == null ? "" : "  ";
        System.out.println(indent + "Evaluating node: " + node.getClass().getSimpleName()
                + (node instanceof VariableAssignment ? " (" + ((VariableAssignment) node).name.name + ")" : "")
                + (node instanceof Declaration ? " (" + ((Declaration) node).property.name + ")" : "")
                + (node instanceof IfClause ? " (IfClause)" : ""));

        List<ASTNode> editableList = getModifiableBody(node);
        if (editableList != null) {
            for (int index = 0; index < editableList.size(); index++) {
                ASTNode currentNode = editableList.get(index);

                if (currentNode instanceof Stylerule) {
                    openNewScope();
                    evaluateNode(currentNode, node);
                    closeCurrentScope();
                    continue;
                }

                if (currentNode instanceof IfClause) {
                    IfClause ifNode = (IfClause) currentNode;
                    Literal condition = evaluateExpression(ifNode.conditionalExpression);
                    boolean isTrue = (condition instanceof BoolLiteral) && ((BoolLiteral) condition).value;

                    List<ASTNode> chosenBody = isTrue
                            ? new ArrayList<>(ifNode.body)
                            : (ifNode.elseClause != null ? new ArrayList<>(ifNode.elseClause.body) : new ArrayList<>());

                    editableList.remove(index);

                    if (!chosenBody.isEmpty()) {
                        editableList.addAll(index, chosenBody);
                        index = index - 1;
                    } else {
                        index = index - 1;
                    }
                    continue;
                }

                if (currentNode instanceof ElseClause) {
                    evaluateNode(currentNode, node);
                    continue;
                }

                if (currentNode instanceof VariableAssignment) {
                    VariableAssignment va = (VariableAssignment) currentNode;
                    Literal value = evaluateExpression(va.expression);
                    va.expression = value;
                    defineVariable(va.name.name, value);
                    evaluateNode(currentNode, node);
                    continue;
                }

                if (currentNode instanceof Declaration) {
                    Declaration decl = (Declaration) currentNode;
                    if (decl.expression != null) decl.expression = evaluateExpression(decl.expression);
                    evaluateNode(currentNode, node);
                    continue;
                }

                evaluateNode(currentNode, node);
            }
            return;
        }

        for (ASTNode child : node.getChildren()) {
            evaluateNode(child, node);
        }
    }

    private List<ASTNode> getModifiableBody(ASTNode node) {
        if (node instanceof Stylesheet) return ((Stylesheet) node).body;
        if (node instanceof Stylerule) return ((Stylerule) node).body;
        if (node instanceof IfClause) return ((IfClause) node).body;
        if (node instanceof ElseClause) return ((ElseClause) node).body;
        return null;
    }

    // -------------------------
    // Expression evaluation
    // -------------------------
    private Literal evaluateExpression(Expression expression) {
        if (expression == null) return new ScalarLiteral(0);

        if (expression instanceof PixelLiteral) return (PixelLiteral) expression;
        if (expression instanceof PercentageLiteral) return (PercentageLiteral) expression;
        if (expression instanceof ScalarLiteral) return (ScalarLiteral) expression;
        if (expression instanceof ColorLiteral) return (ColorLiteral) expression;
        if (expression instanceof BoolLiteral) return (BoolLiteral) expression;

        if (expression instanceof VariableReference) {
            String name = ((VariableReference) expression).name;
            Literal found = findVariable(name);
            return (found != null) ? found : new ScalarLiteral(0);
        }

        if (expression instanceof AddOperation || expression instanceof SubtractOperation) {
            Operation operation = (Operation) expression;
            Literal left = evaluateExpression(operation.lhs);
            Literal right = evaluateExpression(operation.rhs);
            boolean isPlus = expression instanceof AddOperation;
            return computeOperation(left, right, isPlus ? "+" : "-");
        }

        if (expression instanceof MultiplyOperation) {
            Operation operation = (Operation) expression;
            Literal left = evaluateExpression(operation.lhs);
            Literal right = evaluateExpression(operation.rhs);
            return computeOperation(left, right, "*");
        }

        return new ScalarLiteral(0);
    }

    private Literal computeOperation(Literal lhs, Literal rhs, String op) {
        if (lhs instanceof PixelLiteral && rhs instanceof PixelLiteral) {
            int a = ((PixelLiteral) lhs).value;
            int b = ((PixelLiteral) rhs).value;
            return new PixelLiteral(op.equals("+") ? a + b : a - b);
        }
        if (lhs instanceof PercentageLiteral && rhs instanceof PercentageLiteral) {
            int a = ((PercentageLiteral) lhs).value;
            int b = ((PercentageLiteral) rhs).value;
            return new PercentageLiteral(op.equals("+") ? a + b : a - b);
        }
        if (lhs instanceof ScalarLiteral && rhs instanceof ScalarLiteral) {
            int a = ((ScalarLiteral) lhs).value;
            int b = ((ScalarLiteral) rhs).value;
            switch (op) {
                case "+": return new ScalarLiteral(a + b);
                case "-": return new ScalarLiteral(a - b);
                case "*": return new ScalarLiteral(a * b);
            }
        }
        if (lhs instanceof PixelLiteral && rhs instanceof ScalarLiteral && op.equals("*"))
            return new PixelLiteral(((PixelLiteral) lhs).value * ((ScalarLiteral) rhs).value);
        if (lhs instanceof ScalarLiteral && rhs instanceof PixelLiteral && op.equals("*"))
            return new PixelLiteral(((ScalarLiteral) lhs).value * ((PixelLiteral) rhs).value);
        if (lhs instanceof PercentageLiteral && rhs instanceof ScalarLiteral && op.equals("*"))
            return new PercentageLiteral(((PercentageLiteral) lhs).value * ((ScalarLiteral) rhs).value);
        if (lhs instanceof ScalarLiteral && rhs instanceof PercentageLiteral && op.equals("*"))
            return new PercentageLiteral(((ScalarLiteral) lhs).value * ((PercentageLiteral) rhs).value);

        return new ScalarLiteral(0);
    }
}
