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
        // === 1. Nieuwe scope starten (stylerule of if/else) ===
        if (node instanceof Stylerule || node instanceof IfClause || node instanceof ElseClause) {
            variableTypes.addFirst(new HashMap<>());
        }

        // === 2. Variabele declaratie ===
        if (node instanceof VariableAssignment) {
            VariableAssignment varAssign = (VariableAssignment) node;
            ExpressionType type = determineType(varAssign.expression);

            // -------------------------
            // Nieuwe eis (taaluitbreiding): typeconsistentie van variabelen
            // -------------------------
            // Als de variabele al eerder gedeclareerd is in de huidige of een buitenliggende scope,
            // controleer dan of het nieuwe type gelijk is aan het eerdere type.
            for (HashMap<String, ExpressionType> scope : variableTypes) {
                if (scope.containsKey(varAssign.name.name)) {
                    ExpressionType oldType = scope.get(varAssign.name.name);
                    if (oldType != type) {
                        // Als het type verschilt, foutmelding geven.
                        node.setError("Variabele " + varAssign.name.name
                                + " had eerder type " + oldType
                                + " maar krijgt nu type " + type + ".");
                    }
                    // Stop, we hebben de variabele al gevonden.
                    return;
                }
            }

            // Als de variabele nog niet eerder gedeclareerd was, voeg hem toe in de huidige scope.
            variableTypes.getFirst().put(varAssign.name.name, type);
        }


        // === 3. Declaraties controleren ===
        else if (node instanceof Declaration) {
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
        }

        // === 4. Expressions in if/operation controleren ===
        if (node instanceof IfClause) {
            IfClause ifNode = (IfClause) node;
            determineType(ifNode.conditionalExpression);
        }

        // === Controle op operaties ===
        if (node instanceof Operation) {
            Operation opNode = (Operation) node;

            // Bepaal de types van de linker- en rechterkant
            ExpressionType leftType = determineType(opNode.lhs);
            ExpressionType rightType = determineType(opNode.rhs);

            // Controleer of een van beide kleuren bevat
            if (leftType == ExpressionType.COLOR || rightType == ExpressionType.COLOR) {
                node.setError("Kleuren mogen niet gebruikt worden in operaties (+, -, *).");
            }

            // Controleer of beide zijden numeriek zijn
            if (!(leftType == ExpressionType.PIXEL || leftType == ExpressionType.PERCENTAGE || leftType == ExpressionType.SCALAR) ||
                    !(rightType == ExpressionType.PIXEL || rightType == ExpressionType.PERCENTAGE || rightType == ExpressionType.SCALAR)) {
                node.setError("Operaties mogen alleen uitgevoerd worden op numerieke waarden (px, %, scalar).");
            }
        }


        // === 5. Kinderen recursief controleren ===
        for (ASTNode child : node.getChildren()) {
            checkNode(child);
        }

        // === 6. Controleer of de conditie bij "if" een boolean is ===
        if (node instanceof IfClause) {
            IfClause ifNode = (IfClause) node;

            ExpressionType conditionType = determineType(ifNode.conditionalExpression);

            if (conditionType != ExpressionType.BOOL) {
                ifNode.setError("De conditie van een if-statement moet een boolean zijn.");
            }
        }

        // === 7. Scope verlaten ===
        if (node instanceof Stylerule || node instanceof IfClause || node instanceof ElseClause) {
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

        if (expression instanceof Operation) {
            Operation op = (Operation) expression;
            ExpressionType leftType = determineType(op.lhs);
            ExpressionType rightType = determineType(op.rhs);

            // Kleuren mogen niet in operaties
            if (leftType == ExpressionType.COLOR || rightType == ExpressionType.COLOR) {
                expression.setError("Kleuren mogen niet gebruikt worden in operaties (+, -, *).");
                return ExpressionType.UNDEFINED;
            }

            // Als beide numeriek zijn, is de operatie numeriek
            if ((leftType == ExpressionType.PIXEL || leftType == ExpressionType.PERCENTAGE || leftType == ExpressionType.SCALAR) &&
                    (rightType == ExpressionType.PIXEL || rightType == ExpressionType.PERCENTAGE || rightType == ExpressionType.SCALAR)) {

                // Pixel + Pixel = Pixel, Percentage + Percentage = Percentage, Pixel + Percentage? Kies PIXEL als default
                if (leftType == ExpressionType.PIXEL && rightType == ExpressionType.PIXEL) return ExpressionType.PIXEL;
                if (leftType == ExpressionType.PERCENTAGE && rightType == ExpressionType.PERCENTAGE) return ExpressionType.PERCENTAGE;

                return ExpressionType.SCALAR; // mengvorm voor gemengde types
            }

            // anders ongeldig
            expression.setError("Operaties mogen alleen op numerieke waarden (px, %, scalar).");
            return ExpressionType.UNDEFINED;
        }


        return ExpressionType.UNDEFINED;
    }
}