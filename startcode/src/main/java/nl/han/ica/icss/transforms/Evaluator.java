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

    // Stack (stapel) van scopes.
    // Elke scope is een HashMap waarin variabelenamen aan Literal-waarden worden gekoppeld.
    // Gebruik: een linked list zodat scopes gemakkelijk aan het begin kunnen worden toegevoegd/verwijderd.
    private IHANLinkedList<HashMap<String, Literal>> variableValues;

    // Constructor: initialiseert een lege lijst van scopes.
    public Evaluator() {
        variableValues = new HANLinkedList<>();
    }

    @Override
    public void apply(AST ast) {
        // Start met een schone stack.
        variableValues = new HANLinkedList<>();
        // Open een globale scope (geldt voor de hele stylesheet).
        openNewScope();
        // Begin met traverseren van de AST vanaf de root.
        evaluateNode(ast.root, null);
        // Sluit de globale scope wanneer klaar.
        closeCurrentScope();
    }

    // -------------------------
    // Scope helpers
    // -------------------------

    // Nieuwe scope openen (wordt bovenaan de stack gelegd).
    private void openNewScope() {
        variableValues.addFirst(new HashMap<>());
    }

    // Huidige scope sluiten (verwijderen van de top van de stack).
    private void closeCurrentScope() {
        if (variableValues.getSize() > 0) variableValues.removeFirst();
    }

    // Variabele definiëren of overschrijven in de huidige scope.
    private void defineVariable(String name, Literal value) {
        variableValues.getFirst().put(name, value);
    }

    // Variabele opzoeken in de stack van scopes (van binnen naar buiten).
    private Literal findVariable(String name) {
        try {
            HashMap<String, Literal> top = variableValues.getFirst();
            if (top != null && top.containsKey(name)) return top.get(name);
        } catch (Exception ignored) { }

        // Zoek verder in oudere scopes (van binnen naar buiten).
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

    // Doorloopt recursief de AST en voert transformaties uit.
    private void evaluateNode(ASTNode node, ASTNode parent) {
        // Debug-output: toont welk type knoop wordt geëvalueerd.
        String indent = parent == null ? "" : "  ";
        System.out.println(indent + "Evaluating node: " + node.getClass().getSimpleName()
                + (node instanceof VariableAssignment ? " (" + ((VariableAssignment) node).name.name + ")" : "")
                + (node instanceof Declaration ? " (" + ((Declaration) node).property.name + ")" : "")
                + (node instanceof IfClause ? " (IfClause)" : ""));

        // Check of deze knoop een “modificeerbare body” heeft (direct aanpasbare lijst).
        List<ASTNode> editableList = getModifiableBody(node);
        if (editableList != null) {
            // Index-gestuurde loop zodat we veilig knopen kunnen verwijderen of invoegen.
            for (int index = 0; index < editableList.size(); index++) {
                ASTNode currentNode = editableList.get(index);

                // Nieuwe scope openen bij een stylerule.
                if (currentNode instanceof Stylerule) {
                    openNewScope();
                    evaluateNode(currentNode, node);
                    closeCurrentScope();
                    continue;
                }

                // IfClause evalueren: conditie checken en body vervangen.
                if (currentNode instanceof IfClause) {
                    IfClause ifNode = (IfClause) currentNode;
                    Literal condition = evaluateExpression(ifNode.conditionalExpression);
                    boolean isTrue = (condition instanceof BoolLiteral) && ((BoolLiteral) condition).value;

                    // Kies de juiste body (if of else).
                    List<ASTNode> chosenBody = isTrue
                            ? new ArrayList<>(ifNode.body)
                            : (ifNode.elseClause != null ? new ArrayList<>(ifNode.elseClause.body) : new ArrayList<>());

                    // Vervang de IfClause zelf door de gekozen body.
                    editableList.remove(index);

                    if (!chosenBody.isEmpty()) {
                        editableList.addAll(index, chosenBody);
                        // Index aanpassen zodat de net ingevoegde knopen ook bezocht worden.
                        index = index - 1;
                    } else {
                        // Geen body ingevoegd, dus alleen index bijstellen.
                        index = index - 1;
                    }
                    continue;
                }

                // ElseClause: verwerk gewoon de kinderen verder.
                if (currentNode instanceof ElseClause) {
                    evaluateNode(currentNode, node);
                    continue;
                }

                // VariableAssignment: rechterkant evalueren en variabele opslaan in scope.
                if (currentNode instanceof VariableAssignment) {
                    VariableAssignment va = (VariableAssignment) currentNode;
                    Literal value = evaluateExpression(va.expression);
                    va.expression = value;
                    defineVariable(va.name.name, value);
                    // Kinderen van deze knoop ook evalueren (voor de zekerheid).
                    evaluateNode(currentNode, node);
                    continue;
                }

                // Declaration: expression evalueren naar Literal.
                if (currentNode instanceof Declaration) {
                    Declaration decl = (Declaration) currentNode;
                    if (decl.expression != null) decl.expression = evaluateExpression(decl.expression);
                    evaluateNode(currentNode, node);
                    continue;
                }

                // Voor alle overige knopen: gewoon recursief verder.
                evaluateNode(currentNode, node);
            }
            return;
        }

        // Als dit geen modificeerbare body heeft, loop over de gewone children.
        for (ASTNode child : node.getChildren()) {
            evaluateNode(child, node);
        }
    }

    // Haal de echte aanpasbare lijst op voor knooptypes die dat hebben.
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

    // Reken een expression uit en geef een Literal terug.
    private Literal evaluateExpression(Expression expression) {
        if (expression == null) return new ScalarLiteral(0);

        // Als het al een Literal is, return meteen.
        if (expression instanceof PixelLiteral) return (PixelLiteral) expression;
        if (expression instanceof PercentageLiteral) return (PercentageLiteral) expression;
        if (expression instanceof ScalarLiteral) return (ScalarLiteral) expression;
        if (expression instanceof ColorLiteral) return (ColorLiteral) expression;
        if (expression instanceof BoolLiteral) return (BoolLiteral) expression;

        // Variabele-referentie: zoek waarde in scopes.
        if (expression instanceof VariableReference) {
            String name = ((VariableReference) expression).name;
            Literal found = findVariable(name);
            return (found != null) ? found : new ScalarLiteral(0);
        }

        // Optellen of aftrekken: beide kanten evalueren en combineren.
        if (expression instanceof AddOperation || expression instanceof SubtractOperation) {
            Operation operation = (Operation) expression;
            Literal left = evaluateExpression(operation.lhs);
            Literal right = evaluateExpression(operation.rhs);
            boolean isPlus = expression instanceof AddOperation;
            return computeOperation(left, right, isPlus ? "+" : "-");
        }

        // Vermenigvuldigen: beide kanten evalueren en resultaat berekenen.
        if (expression instanceof MultiplyOperation) {
            Operation operation = (Operation) expression;
            Literal left = evaluateExpression(operation.lhs);
            Literal right = evaluateExpression(operation.rhs);
            return computeOperation(left, right, "*");
        }

        // Onbekende expressie → terugvallen op 0 (scalar).
        return new ScalarLiteral(0);
    }

    // Uitvoeren van rekenoperaties op Literals.
    private Literal computeOperation(Literal lhs, Literal rhs, String op) {
        // px ± px
        if (lhs instanceof PixelLiteral && rhs instanceof PixelLiteral) {
            int a = ((PixelLiteral) lhs).value;
            int b = ((PixelLiteral) rhs).value;
            return new PixelLiteral(op.equals("+") ? a + b : a - b);
        }
        // % ± %
        if (lhs instanceof PercentageLiteral && rhs instanceof PercentageLiteral) {
            int a = ((PercentageLiteral) lhs).value;
            int b = ((PercentageLiteral) rhs).value;
            return new PercentageLiteral(op.equals("+") ? a + b : a - b);
        }
        // scalar ±/* scalar
        if (lhs instanceof ScalarLiteral && rhs instanceof ScalarLiteral) {
            int a = ((ScalarLiteral) lhs).value;
            int b = ((ScalarLiteral) rhs).value;
            switch (op) {
                case "+": return new ScalarLiteral(a + b);
                case "-": return new ScalarLiteral(a - b);
                case "*": return new ScalarLiteral(a * b);
            }
        }
        // px * scalar
        if (lhs instanceof PixelLiteral && rhs instanceof ScalarLiteral && op.equals("*"))
            return new PixelLiteral(((PixelLiteral) lhs).value * ((ScalarLiteral) rhs).value);
        // scalar * px
        if (lhs instanceof ScalarLiteral && rhs instanceof PixelLiteral && op.equals("*"))
            return new PixelLiteral(((ScalarLiteral) lhs).value * ((PixelLiteral) rhs).value);
        // % * scalar
        if (lhs instanceof PercentageLiteral && rhs instanceof ScalarLiteral && op.equals("*"))
            return new PercentageLiteral(((PercentageLiteral) lhs).value * ((ScalarLiteral) rhs).value);
        // scalar * %
        if (lhs instanceof ScalarLiteral && rhs instanceof PercentageLiteral && op.equals("*"))
            return new PercentageLiteral(((ScalarLiteral) lhs).value * ((PercentageLiteral) rhs).value);

        // Onbekende of ongeldige combinatie → fallback naar scalar(0).
        return new ScalarLiteral(0);
    }
}
