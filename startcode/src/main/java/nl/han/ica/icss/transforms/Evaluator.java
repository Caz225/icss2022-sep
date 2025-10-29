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
        variableValues.addFirst(new HashMap<>()); // globale scope
        evaluateNode(ast.root, null);
        variableValues.removeFirst();
    }

    private void evaluateNode(ASTNode node, ASTNode parent) {
        String indent = parent == null ? "" : "  ";
        System.out.println(indent + "Evaluating node: " + node.getClass().getSimpleName()
                + (node instanceof VariableAssignment ? " (" + ((VariableAssignment) node).name.name + ")" : "")
                + (node instanceof Declaration ? " (" + ((Declaration) node).property.name + ")" : "")
                + (node instanceof IfClause ? " (IfClause cond=" + ((IfClause) node).conditionalExpression + ")" : ""));

        // -------------------------
        // 1. IfClause: check en vervang direct in parent
        // -------------------------
        if (node instanceof IfClause) {
            IfClause ifNode = (IfClause) node;
            Literal condLit = evaluateExpression(ifNode.conditionalExpression);

            if (!(condLit instanceof BoolLiteral)) {
                throw new RuntimeException("If-conditie moet boolean zijn!");
            }
            boolean condition = ((BoolLiteral) condLit).value;
            System.out.println(indent + "  IfClause condition evaluated to: " + condLit);

            // Bepaal replacement body als **kopieën**
            List<ASTNode> replacementBody = new ArrayList<>();
            if (condition) {
                for (ASTNode child : ifNode.body) {
                    replacementBody.add(deepCopy(child));
                }
            } else if (ifNode.elseClause != null) {
                for (ASTNode child : ifNode.elseClause.body) {
                    replacementBody.add(deepCopy(child));
                }
            }
            // fallback: lege lijst als geen body en geen else


            // Verwijder de IfClause en voeg replacement body direct toe in parent
            int index = parent.getChildren().indexOf(node);
            parent.removeChild(node);
            for (int i = 0; i < replacementBody.size(); i++) {
                insertChildAt(parent, index + i, replacementBody.get(i));
            }

            // Recursief evalueren van de nieuwe body
            for (ASTNode child : replacementBody) {
                evaluateNode(child, parent); // parent blijft hetzelfde
            }
            return; // oude IfClause is verwijderd/verwerkt
        }

        // -------------------------
        // 2. VariableAssignment
        // -------------------------
        if (node instanceof VariableAssignment) {
            VariableAssignment va = (VariableAssignment) node;
            Literal value = evaluateExpression(va.expression);
            va.expression = value;
            assignVariable(va.name.name, value);
            System.out.println(indent + "  VariableAssignment: " + va.name.name + " = " + value);
        }

        // -------------------------
        // 3. Declaration
        // -------------------------
        if (node instanceof Declaration) {
            Declaration decl = (Declaration) node;
            decl.expression = evaluateExpression(decl.expression);
            System.out.println(indent + "  Declaration: " + decl.property.name + " = " + decl.expression);
        }

        // -------------------------
        // 4. Nodes met children (Stylesheet, Stylerule, ElseClause)
        // -------------------------
        if (node instanceof Stylesheet || node instanceof Stylerule || node instanceof ElseClause) {
            if (node instanceof Stylerule) variableValues.addFirst(new HashMap<>()); // lokale scope

            // **Direct itereren over originele children-lijst**
            int index = 0;
            while (index < node.getChildren().size()) {
                ASTNode child = node.getChildren().get(index);
                int originalSize = node.getChildren().size();
                evaluateNode(child, node);

                // Als children zijn toegevoegd door IfClause replacement, index verhogen om niet over te slaan
                int newSize = node.getChildren().size();
                if (newSize > originalSize) index += (newSize - originalSize);
                index++;
            }

            if (node instanceof Stylerule) variableValues.removeFirst(); // scope verlaten
        }
    }

    private Literal evaluateExpression(Expression expr) {
        if (expr instanceof Literal) {
            // Literal blijft onveranderd
            return (Literal) expr;
        }

        if (expr instanceof VariableReference) {
            String varName = ((VariableReference) expr).name;
            // Zoek de variabele in de scope-stack
            for (int i = variableValues.getSize() - 1; i >= 0; i--) {
                HashMap<String, Literal> scope = variableValues.get(i);
                if (scope.containsKey(varName)) {
                    Literal value = scope.get(varName);
                    return value; // vervang VariableReference door de Literal
                }
            }
            // fallback: als variabele niet gevonden wordt
            return new ScalarLiteral(0);
        }

        if (expr instanceof AddOperation) {
            AddOperation add = (AddOperation) expr;
            Literal lhs = evaluateExpression(add.lhs);
            Literal rhs = evaluateExpression(add.rhs);
            Literal result = computeOperation(lhs, rhs, "+");
            return result; // vervang AddOperation door Literal
        }

        if (expr instanceof SubtractOperation) {
            SubtractOperation sub = (SubtractOperation) expr;
            Literal lhs = evaluateExpression(sub.lhs);
            Literal rhs = evaluateExpression(sub.rhs);
            Literal result = computeOperation(lhs, rhs, "-");
            return result; // vervang SubtractOperation door Literal
        }

        if (expr instanceof MultiplyOperation) {
            MultiplyOperation mul = (MultiplyOperation) expr;
            Literal lhs = evaluateExpression(mul.lhs);
            Literal rhs = evaluateExpression(mul.rhs);
            Literal result = computeOperation(lhs, rhs, "*");
            return result; // vervang MultiplyOperation door Literal
        }

        throw new RuntimeException("Onbekende expressie: " + expr.getClass().getSimpleName());
    }


    private Literal computeOperation(Literal lhs, Literal rhs, String op) {
        if (lhs instanceof PixelLiteral && rhs instanceof PixelLiteral) {
            int result = op.equals("+") ? ((PixelLiteral) lhs).value + ((PixelLiteral) rhs).value
                    : ((PixelLiteral) lhs).value - ((PixelLiteral) rhs).value;
            return new PixelLiteral(result);
        }
        if (lhs instanceof ScalarLiteral && rhs instanceof ScalarLiteral) {
            int result = 0;
            switch (op) {
                case "+": result = ((ScalarLiteral) lhs).value + ((ScalarLiteral) rhs).value; break;
                case "-": result = ((ScalarLiteral) lhs).value - ((ScalarLiteral) rhs).value; break;
                case "*": result = ((ScalarLiteral) lhs).value * ((ScalarLiteral) rhs).value; break;
            }
            return new ScalarLiteral(result);
        }
        if (lhs instanceof PixelLiteral && rhs instanceof ScalarLiteral && op.equals("*")) return new PixelLiteral(((PixelLiteral) lhs).value * ((ScalarLiteral) rhs).value);
        if (lhs instanceof ScalarLiteral && rhs instanceof PixelLiteral && op.equals("*")) return new PixelLiteral(((ScalarLiteral) lhs).value * ((PixelLiteral) rhs).value);
        throw new RuntimeException("Ongeldige operatie: " + lhs.getClass().getSimpleName() + " " + op + " " + rhs.getClass().getSimpleName());
    }

    private void assignVariable(String name, Literal value) {
        for (int i = 0; i < variableValues.getSize(); i++) {
            HashMap<String, Literal> scope = variableValues.get(i);
            if (scope.containsKey(name)) {
                scope.put(name, value);
                return;
            }
        }
        variableValues.getFirst().put(name, value);
    }

    private void insertChildAt(ASTNode parent, int index, ASTNode child) {
        child.setParent(parent);
        parent.getChildren().add(index, child);
    }

    private ASTNode deepCopy(ASTNode node) {
        ASTNode copy;
        try {
            copy = node.getClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Kan ASTNode niet kopiëren", e);
        }

        // kopieer properties specifiek per type
        if (node instanceof VariableAssignment) {
            VariableAssignment va = (VariableAssignment) node;
            VariableAssignment vaCopy = (VariableAssignment) copy;
            vaCopy.name = va.name; // Name is immutable
            vaCopy.expression = va.expression; // Literal kan onveranderd
            copy = vaCopy;
        } else if (node instanceof Declaration) {
            Declaration decl = (Declaration) node;
            Declaration declCopy = (Declaration) copy;
            declCopy.property = decl.property;
            declCopy.expression = decl.expression;
            copy = declCopy;
        } else if (node instanceof IfClause) {
            IfClause ifNode = (IfClause) node;
            IfClause ifCopy = (IfClause) copy;
            ifCopy.conditionalExpression = ifNode.conditionalExpression;
            for (ASTNode child : ifNode.body) ifCopy.addChild(deepCopy(child));
            if (ifNode.elseClause != null) ifCopy.elseClause = (ElseClause) deepCopy(ifNode.elseClause);
            copy = ifCopy;
        }
        // Voeg children kopieën toe voor andere types
        for (ASTNode child : node.getChildren()) {
            copy.addChild(deepCopy(child));
        }
        return copy;
    }


}
