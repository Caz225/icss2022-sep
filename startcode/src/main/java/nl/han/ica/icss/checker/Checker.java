package nl.han.ica.icss.checker;

import nl.han.ica.datastructures.HANLinkedList;
import nl.han.ica.datastructures.IHANLinkedList;
import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.literals.*;
import nl.han.ica.icss.ast.types.ExpressionType;

import java.util.HashMap;

//public class Checker {
//    private IHANLinkedList<HashMap<String, ExpressionType>> variableTypes;
//
//    public void check(AST ast) {
//        // variableTypes = new HANLinkedList<>();
//    }
//}

public class Checker {

    private HANLinkedList<HashMap<String, ExpressionType>> variableTypes;

    public void check(AST ast) {
        variableTypes = new HANLinkedList<>();
        variableTypes.addFirst(new HashMap<>()); // globale scope
        checkNode(ast.root);
    }

    private void checkNode(ASTNode node) {
        if (node instanceof VariableAssignment) {
            VariableAssignment varAssign = (VariableAssignment) node;
            ExpressionType type = determineType(varAssign.expression);
            variableTypes.getFirst().put(varAssign.name.name, type);

        } else if (node instanceof Declaration) {
            Declaration decl = (Declaration) node;
            ExpressionType valueType = determineType(decl.expression);
            String property = decl.property.name;

            if (property.equals("color") || property.equals("background-color")) {
                if (valueType != ExpressionType.COLOR) {
                    node.setError("Property " + property + " verwacht een kleurwaarde.");
                }
            }

            if (property.equals("width") || property.equals("height")) {
                if (valueType != ExpressionType.PIXEL && valueType != ExpressionType.PERCENTAGE) {
                    node.setError("Property " + property + " verwacht een numerieke waarde (px of %).");
                }
            }
        } else if (node instanceof Stylerule) {
            // Nieuwe scope binnen een selector
            variableTypes.addFirst(new HashMap<>());
        }

        // Recursief kinderen checken
        for (ASTNode child : node.getChildren()) {
            checkNode(child);
        }

        if (node instanceof Stylerule) {
            // Scope verlaten
            variableTypes.removeFirst();
        }
    }

    private ExpressionType determineType(Expression expression) {
        if (expression instanceof ColorLiteral) return ExpressionType.COLOR;
        if (expression instanceof PixelLiteral) return ExpressionType.PIXEL;
        if (expression instanceof PercentageLiteral) return ExpressionType.PERCENTAGE;
        if (expression instanceof ScalarLiteral) return ExpressionType.SCALAR;
        if (expression instanceof BoolLiteral) return ExpressionType.BOOL;

        if (expression instanceof VariableReference) {
            VariableReference ref = (VariableReference) expression;
            for (HashMap<String, ExpressionType> scope : variableTypes) {
                if (scope.containsKey(ref.name)) {
                    return scope.get(ref.name);
                }
            }
            expression.setError("Variabele " + ref.name + " is niet gedefinieerd.");
            return ExpressionType.UNDEFINED;
        }

        return ExpressionType.UNDEFINED;
    }
}