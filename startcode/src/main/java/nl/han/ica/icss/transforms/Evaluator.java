package nl.han.ica.icss.transforms;

import nl.han.ica.datastructures.HANLinkedList;
import nl.han.ica.datastructures.IHANLinkedList;
import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.literals.BoolLiteral;
import nl.han.ica.icss.ast.literals.PercentageLiteral;
import nl.han.ica.icss.ast.literals.PixelLiteral;
import nl.han.ica.icss.ast.literals.ScalarLiteral;
import nl.han.ica.icss.ast.operations.AddOperation;
import nl.han.ica.icss.ast.operations.MultiplyOperation;
import nl.han.ica.icss.ast.operations.SubtractOperation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class Evaluator implements Transform {

    private IHANLinkedList<HashMap<String, Literal>> variableValues;

    public Evaluator() {
        variableValues = new HANLinkedList<>();
    }

    @Override
    public void apply(AST ast) {
        variableValues.addFirst(new HashMap<>()); // globale scope
        evaluateNode(ast.root);
        variableValues.removeFirst();
    }

    private void evaluateNode(ASTNode node) {
        if (node instanceof VariableAssignment) {
            VariableAssignment va = (VariableAssignment) node;
            Literal value = evaluateExpression(va.expression);
            va.expression = value;
            assignVariable(va.name.name, value);

        } else if (node instanceof Declaration) {
            Declaration decl = (Declaration) node;
            decl.expression = evaluateExpression(decl.expression);

        } else if (node instanceof Stylerule) {
            variableValues.addFirst(new HashMap<>()); // lokale scope voor rule
            for (ASTNode child : node.getChildren()) {
                evaluateNode(child);
            }
            variableValues.removeFirst();

        } else if (node instanceof IfClause) {
            IfClause ifNode = (IfClause) node;
            Literal condLit = evaluateExpression(ifNode.conditionalExpression);
            if (!(condLit instanceof BoolLiteral)) {
                throw new RuntimeException("If-conditie moet boolean zijn!");
            }
            boolean condition = ((BoolLiteral) condLit).value;

            variableValues.addFirst(new HashMap<>()); // scope voor if-block
            List<ASTNode> bodyToEval = condition ? ifNode.body : (ifNode.elseClause != null ? ifNode.elseClause.body : new ArrayList<>());
            for (ASTNode stmt : bodyToEval) evaluateNode(stmt);
            variableValues.removeFirst();
        }

        // Recursief over children
        for (ASTNode child : node.getChildren()) evaluateNode(child);
    }

    private Literal evaluateExpression(Expression expr) {
        if (expr instanceof Literal) {
            return (Literal) expr; // literal blijft hetzelfde

        } else if (expr instanceof VariableReference) {
            String varName = ((VariableReference) expr).name;

            // Zoek van bovenste scope naar onderste (last index -> 0) of omgekeerd
            for (int i = variableValues.getSize() - 1; i >= 0; i--) {
                HashMap<String, Literal> scope = variableValues.get(i);
                if (scope.containsKey(varName)) {
                    return scope.get(varName);
                }
            }
            System.out.println("NOT FOUND " + varName + " -> fallback Scalar(0)");
            return new ScalarLiteral(0);
        } else if (expr instanceof AddOperation) {
            AddOperation add = (AddOperation) expr;
            Literal lhs = evaluateExpression(add.lhs);
            Literal rhs = evaluateExpression(add.rhs);
            return computeOperation(lhs, rhs, "+");

        } else if (expr instanceof SubtractOperation) {
            SubtractOperation sub = (SubtractOperation) expr;
            Literal lhs = evaluateExpression(sub.lhs);
            Literal rhs = evaluateExpression(sub.rhs);
            return computeOperation(lhs, rhs, "-");

        } else if (expr instanceof MultiplyOperation) {
            MultiplyOperation mul = (MultiplyOperation) expr;
            Literal lhs = evaluateExpression(mul.lhs);
            Literal rhs = evaluateExpression(mul.rhs);
            return computeOperation(lhs, rhs, "*");
        }

        return null; // onbekend type
    }

    private Literal computeOperation(Literal lhs, Literal rhs, String op) {
        // PixelLiteral + PixelLiteral of - PixelLiteral
        if (lhs instanceof PixelLiteral && rhs instanceof PixelLiteral) {
            int result = 0;
            if (op.equals("+")) {
                result = ((PixelLiteral) lhs).value + ((PixelLiteral) rhs).value;
            } else if (op.equals("-")) {
                result = ((PixelLiteral) lhs).value - ((PixelLiteral) rhs).value;
            } else {
                throw new RuntimeException("Ongeldige operatie voor PixelLiteral: " + op);
            }
            return new PixelLiteral(result);
        }

        // PercentageLiteral * ScalarLiteral
        if (lhs instanceof PercentageLiteral && rhs instanceof ScalarLiteral) {
            int result = 0;
            if (op.equals("*")) {
                result = (int)(((PercentageLiteral) lhs).value * ((ScalarLiteral) rhs).value);
            } else {
                throw new RuntimeException("Ongeldige operatie voor PercentageLiteral: " + op);
            }
            return new PercentageLiteral(result);
        }

        // ScalarLiteral met +, -, *
        if (lhs instanceof ScalarLiteral && rhs instanceof ScalarLiteral) {
            int result = 0;
            switch (op) {
                case "+": result = ((ScalarLiteral) lhs).value + ((ScalarLiteral) rhs).value; break;
                case "-": result = ((ScalarLiteral) lhs).value - ((ScalarLiteral) rhs).value; break;
                case "*": result = ((ScalarLiteral) lhs).value * ((ScalarLiteral) rhs).value; break;
                default: throw new RuntimeException("Ongeldige operatie voor ScalarLiteral: " + op);
            }
            return new ScalarLiteral(result);
        }

        // PixelLiteral * ScalarLiteral
        if (lhs instanceof PixelLiteral && rhs instanceof ScalarLiteral && op.equals("*")) {
            int result = (int)(((PixelLiteral) lhs).value * ((ScalarLiteral) rhs).value);
            return new PixelLiteral(result);
        }

        // ScalarLiteral * PixelLiteral
        if (lhs instanceof ScalarLiteral && rhs instanceof PixelLiteral && op.equals("*")) {
            int result = (int)(((ScalarLiteral) lhs).value * ((PixelLiteral) rhs).value);
            return new PixelLiteral(result);
        }

        throw new RuntimeException("Onbekende of ongeldig combinatie van types bij operatie: "
                + lhs.getClass().getSimpleName() + " " + op + " " + rhs.getClass().getSimpleName());
    }



    // Helpermethode
    private void assignVariable(String name, Literal value) {
        // Zoek de scope waar de variabele al bestaat
        for (int i = 0; i < variableValues.getSize(); i++) {
            HashMap<String, Literal> scope = variableValues.get(i);
            if (scope.containsKey(name)) {
                scope.put(name, value);
                return;
            }
        }
        // Anders in de bovenste scope zetten
        variableValues.getFirst().put(name, value);
    }

    // Helpermethode
    private Literal getVariable(String name) {
        for (int i = variableValues.getSize() - 1; i >= 0; i--) {
            HashMap<String, Literal> scope = variableValues.get(i);
            if (scope.containsKey(name)) return scope.get(name);
        }
        return new ScalarLiteral(0); // fallback
    }


}
