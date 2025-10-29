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

    private void assignVariable(String name, Literal value) {
        // Zoek de scope waarin deze variabele al bestaat
        for (int i = 0; i < variableValues.getSize(); i++) {
            HashMap<String, Literal> scope = variableValues.get(i);
            if (scope.containsKey(name)) {
                scope.put(name, value); // update hier
                return;
            }
        }
        // Anders voeg toe aan de huidige bovenste scope
        variableValues.getFirst().put(name, value);
    }

    private void evaluateNode(ASTNode node) {
        // Debugregel:
        System.out.println("Evaluating: " + node.getClass().getSimpleName());

        if (node instanceof VariableAssignment) {
            VariableAssignment varAssign = (VariableAssignment) node;
            Literal evaluated = evaluateExpression(varAssign.expression);
            varAssign.expression = evaluated;
            assignVariable(varAssign.name.name, evaluated);

        } else if (node instanceof Declaration) {
            Declaration decl = (Declaration) node;
            decl.expression = evaluateExpression(decl.expression);

        } else if (node instanceof Stylerule) {
            variableValues.addFirst(new HashMap<>());

            // Gebruik de echte lijst, niet een kopie!
            // Zo wordt een vervanging (zoals IfClause) meteen zichtbaar
            for (int i = 0; i < node.getChildren().size(); i++) {
                ASTNode child = node.getChildren().get(i);
                evaluateNode(child);
            }

            variableValues.removeFirst();
        } else if (node instanceof IfClause) {
            System.out.println("Evaluating IfClause");
            IfClause ifNode = (IfClause) node;
            ASTNode parent = node.getParent();
            Literal condLiteral = evaluateExpression(ifNode.conditionalExpression);
            if (!(condLiteral instanceof BoolLiteral)) {
                throw new RuntimeException("Conditie van if-statement moet een boolean zijn, maar kreeg: "
                        + condLiteral.getClass().getSimpleName());
            }
            boolean condition = ((BoolLiteral) condLiteral).value;

            // Vlak vóór het vervangen
            if (parent != null) {
                System.out.println("Parent before replace: " + parent.getChildren().size());
                System.out.println("Replacing IfClause in " + parent.getClass().getSimpleName());
            }

            // Nieuwe lijst die IfClause gaat vervangen
            List<ASTNode> replacement = new ArrayList<>();

            if (condition) {
                for (ASTNode stmt : ifNode.body) {
                    // Evaluatie van een VariableAssignment moet de expression aanpassen
                    if (stmt instanceof VariableAssignment) {
                        VariableAssignment va = (VariableAssignment) stmt;
                        va.expression = evaluateExpression(va.expression); // hier vervangen
                        assignVariable(va.name.name, va.expression);
                    }
                    replacement.add(stmt);
                }
            } else if (ifNode.elseClause != null) {
                for (ASTNode stmt : ifNode.elseClause.body) {
                    if (stmt instanceof VariableAssignment) {
                        VariableAssignment va = (VariableAssignment) stmt;
                        va.expression = evaluateExpression(va.expression);
                        assignVariable(va.name.name, va.expression);
                    }
                    replacement.add(stmt);
                }
            }


            // === Belangrijk: vervang node in de parent correct ===
            if (parent != null) {
                List<ASTNode> siblings = parent.getChildren();
                int idx = siblings.indexOf(node);
                if (idx != -1) {
                    siblings.remove(idx);
                    siblings.addAll(idx, replacement);
                    for (ASTNode n : replacement) {
                        n.setParent(parent);
                    }

                    // Debug
                    System.out.println("Parent after replace: " + parent.getChildren().size());
                    for (ASTNode c : parent.getChildren()) {
                        System.out.println("  -> " + c.getNodeLabel());
                    }
                }
            }

            // Stop verdere verwerking van deze IfClause
            return;
        } else if (node instanceof ElseClause) {
            // ElseClause wordt al in IfClause verwerkt
            return;
        }
    }



    private Literal evaluateExpression(Expression expr) {
        if (expr instanceof Literal) {
            return (Literal) expr; // literal blijft hetzelfde

        } else if (expr instanceof VariableReference) {
            String varName = ((VariableReference) expr).name;

            // Debug: print stack inhoud en grootte (NIET muteren)
            System.out.println("---- lookup " + varName + " ----");
            System.out.println("stack size: " + variableValues.getSize());
            for (int j = 0; j < variableValues.getSize(); j++) {
                HashMap<String, Literal> sc = variableValues.get(j);
                System.out.println(" scope[" + j + "] keys: " + sc.keySet());
            }

            // Zoek van bovenste scope naar onderste (last index -> 0) of omgekeerd
            for (int i = variableValues.getSize() - 1; i >= 0; i--) {
                HashMap<String, Literal> scope = variableValues.get(i);
                if (scope.containsKey(varName)) {
                    System.out.println("FOUND " + varName + " in scope[" + i + "] -> " + scope.get(varName).getNodeLabel());
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
        // PixelLiteral
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

        // PercentageLiteral
        if (lhs instanceof PercentageLiteral && rhs instanceof ScalarLiteral) {
            int result = 0;
            if (op.equals("*")) {
                // cast naar int (afronden naar beneden)
                result = (int)(((PercentageLiteral) lhs).value * ((ScalarLiteral) rhs).value);
            } else {
                throw new RuntimeException("Ongeldige operatie voor PercentageLiteral: " + op);
            }
            return new PercentageLiteral(result);
        }


        // ScalarLiteral
        if (lhs instanceof ScalarLiteral && rhs instanceof ScalarLiteral) {
            int result = 0;
            if (op.equals("+")) {
                result = ((ScalarLiteral) lhs).value + ((ScalarLiteral) rhs).value;
            } else if (op.equals("-")) {
                result = ((ScalarLiteral) lhs).value - ((ScalarLiteral) rhs).value;
            } else if (op.equals("*")) {
                result = ((ScalarLiteral) lhs).value * ((ScalarLiteral) rhs).value;
            } else {
                throw new RuntimeException("Ongeldige operatie voor ScalarLiteral: " + op);
            }
            return new ScalarLiteral(result);
        }


        // fallback voor alle andere combinaties (bijv. Pixel * Scalar)
        if (lhs instanceof PixelLiteral && rhs instanceof ScalarLiteral && op.equals("*")) {
            int result = (int)(((PixelLiteral) lhs).value * ((ScalarLiteral) rhs).value);
            return new PixelLiteral(result);
        }

        if (lhs instanceof ScalarLiteral && rhs instanceof PixelLiteral && op.equals("*")) {
            int result = (int)(((ScalarLiteral) lhs).value * ((PixelLiteral) rhs).value);
            return new PixelLiteral(result);
        }

        throw new RuntimeException("Onbekende of ongeldig combinatie van types bij operatie: "
                + lhs.getClass() + " " + op + " " + rhs.getClass());
    }
}
