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

	// Indentatie voor nette CSS-output (2 spaties).
	private static final String INDENT = "  ";

	// -------------------------
	// Publieke API
	// -------------------------
	public String generate(AST ast) {
		// Lege of ongeldige AST? Geef lege string terug.
		if (ast == null || ast.root == null) {
			return "";
		}

		// Scope-stack voor variabelen (LIFO). Bovenste (peek) is de huidige scope.
		Deque<Map<String, Literal>> scopes = new ArrayDeque<>();
		scopes.push(new HashMap<>()); // globale scope

		// Verzamelt tekstuele CSS-blokken (één per stylerule).
		List<String> blocks = new ArrayList<>();

		// Doorloop top-level knopen: variabele-toekenningen en stylerules.
		for (ASTNode node : ast.root.getChildren()) {
			if (node instanceof VariableAssignment) {
				// Top-level variabele toekennen: eerst rechterkant evalueren, dan opslaan in huidige scope.
				VariableAssignment assignment = (VariableAssignment) node;
				Literal value = evaluateExpression(assignment.expression, scopes);
				assignment.expression = value; // Let op: dit muteert de AST (bewust ontwerp).
				assignVariable(scopes, assignment.name.name, value);
			} else if (node instanceof Stylerule) {
				// Genereer CSS voor een volledige stylerule en voeg toe aan output.
				blocks.add(renderStylerule((Stylerule) node, scopes));
			}
		}

		// Globale scope sluiten.
		scopes.pop();

		// CSS-blokken scheiden met een lege regel.
		return String.join("\n\n", blocks);
	}

	// -------------------------
	// Stylerule-rendering
	// -------------------------
	private String renderStylerule(Stylerule stylerule, Deque<Map<String, Literal>> scopes) {
		StringBuilder builder = new StringBuilder();

		// Selector-lijst naar tekst, met komma’s gescheiden (bijv. "p, .class, #id").
		String selectorText = stylerule.selectors.stream()
				.map(this::selectorToString)
				.collect(Collectors.joining(", "));
		builder.append(selectorText).append(" {\n");

		// Nieuwe lokale scope voor deze stylerule.
		scopes.push(new HashMap<>());
		appendStatements(stylerule.body, builder, scopes, 1);
		scopes.pop();

		builder.append("}");
		return builder.toString();
	}

	// -------------------------
	// Body/Statements verwerken
	// -------------------------
	private void appendStatements(List<ASTNode> statements, StringBuilder builder,
								  Deque<Map<String, Literal>> scopes, int indentLevel) {
		// Verwerk alleen declaraties, variabele-toekenningen en if-clauses.
		for (ASTNode statement : statements) {
			if (statement instanceof Declaration) {
				// Eigenschap: waarde genereren met correcte evaluatie.
				appendDeclaration((Declaration) statement, builder, scopes, indentLevel);

			} else if (statement instanceof VariableAssignment) {
				// Variabele binnen de stylerule toekennen: evalueren en in huidige scope plaatsen.
				VariableAssignment assignment = (VariableAssignment) statement;
				Literal value = evaluateExpression(assignment.expression, scopes);
				assignment.expression = value; // Let op: muteert de AST.
				assignVariable(scopes, assignment.name.name, value);

			} else if (statement instanceof IfClause) {
				// Voorwaardelijke sectie renderen.
				appendIfClause((IfClause) statement, builder, scopes, indentLevel);
			}
		}
	}

	// -------------------------
	// Declaration genereren
	// -------------------------
	private void appendDeclaration(Declaration declaration, StringBuilder builder,
								   Deque<Map<String, Literal>> scopes, int indentLevel) {
		// Expression evalueren naar Literal en als CSS-tekst uitschrijven.
		Literal literal = evaluateExpression(declaration.expression, scopes);
		declaration.expression = literal; // Let op: muteert de AST (expression wordt Literal).

		builder.append(INDENT.repeat(indentLevel))
				.append(declaration.property.name)
				.append(": ")
				.append(literalToCss(literal))
				.append(";\n");
	}

	// -------------------------
	// IfClause verwerken
	// -------------------------
	private void appendIfClause(IfClause ifClause, StringBuilder builder,
								Deque<Map<String, Literal>> scopes, int indentLevel) {
		// Voorwaarde evalueren naar boolean.
		Literal conditionLiteral = evaluateExpression(ifClause.conditionalExpression, scopes);
		boolean condition = conditionLiteral instanceof BoolLiteral && ((BoolLiteral) conditionLiteral).value;

		// Hier wordt een extra scope geopend voor de if/else-body.
		// Dat betekent dat variabele-toekenningen binnen de if/else NIET
		// zichtbaar zijn buiten de if/else voor latere declaraties in dezelfde stylerule.
		// Moet volgens mij van Assignment.md Scope-regels
		scopes.push(new HashMap<>());

		// Kies de juiste body op basis van de voorwaarde.
		List<ASTNode> chosenBody = condition
				? ifClause.body
				: ifClause.elseClause != null ? ifClause.elseClause.body : Collections.emptyList();

		// Genereer statements van de gekozen tak, met dezelfde indentatie.
		appendStatements(chosenBody, builder, scopes, indentLevel);

		// If/else-scope sluiten.
		scopes.pop();
	}

	// -------------------------
	// Helpers: selectors en literals
	// -------------------------
	private String selectorToString(Selector selector) {
		// Bekende selector-types: gebruik hun toString().
		if (selector instanceof ClassSelector || selector instanceof IdSelector || selector instanceof TagSelector) {
			return selector.toString();
		}
		// Fallback voor andere selector-types.
		return selector.getNodeLabel();
	}

	private String literalToCss(Literal literal) {
		// Zet een Literal om naar CSS-tekst.
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
			// In CSS komt een boolean normaliter niet voor.
			return ((BoolLiteral) literal).value ? "TRUE" : "FALSE";
		}
		// Reserve fallback.
		return literal.toString();
	}

	// -------------------------
	// Expressie-evaluatie
	// -------------------------
	private Literal evaluateExpression(Expression expression, Deque<Map<String, Literal>> scopes) {
		// Null-veiligheid: behandel als 0 (scalar).
		if (expression == null) {
			return new ScalarLiteral(0);
		}

		// Literals zijn al eindresultaten.
		if (expression instanceof Literal) {
			return (Literal) expression;
		}

		// Variabele-referentie: zoek van boven (huidige scope) naar beneden (globale scope).
		if (expression instanceof VariableReference) {
			String name = ((VariableReference) expression).name;
			for (Map<String, Literal> scope : scopes) {
				if (scope.containsKey(name)) {
					return scope.get(name);
				}
			}
			// Niet gevonden → veilig fallback 0.
			return new ScalarLiteral(0);
		}

		// Optellen
		if (expression instanceof AddOperation) {
			AddOperation add = (AddOperation) expression;
			Literal left = evaluateExpression(add.lhs, scopes);
			Literal right = evaluateExpression(add.rhs, scopes);
			return computeOperation(left, right, "+");
		}

		// Aftrekken
		if (expression instanceof SubtractOperation) {
			SubtractOperation sub = (SubtractOperation) expression;
			Literal left = evaluateExpression(sub.lhs, scopes);
			Literal right = evaluateExpression(sub.rhs, scopes);
			return computeOperation(left, right, "-");
		}

		// Vermenigvuldigen
		if (expression instanceof MultiplyOperation) {
			MultiplyOperation mul = (MultiplyOperation) expression;
			Literal left = evaluateExpression(mul.lhs, scopes);
			Literal right = evaluateExpression(mul.rhs, scopes);
			return computeOperation(left, right, "*");
		}

		// Onbekend type → 0.
		return new ScalarLiteral(0);
	}

	// -------------------------
	// Rekenregels voor literals
	// -------------------------
	private Literal computeOperation(Literal left, Literal right, String operator) {
		// px ± px
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

		// % * scalar  (of  scalar * %)
		if (left instanceof PercentageLiteral && right instanceof ScalarLiteral && operator.equals("*")) {
			int result = ((PercentageLiteral) left).value * ((ScalarLiteral) right).value;
			return new PercentageLiteral(result);
		}
		if (left instanceof ScalarLiteral && right instanceof PercentageLiteral && operator.equals("*")) {
			int result = ((ScalarLiteral) left).value * ((PercentageLiteral) right).value;
			return new PercentageLiteral(result);
		}

		// px * scalar  (of  scalar * px)
		if (left instanceof PixelLiteral && right instanceof ScalarLiteral && operator.equals("*")) {
			int result = ((PixelLiteral) left).value * ((ScalarLiteral) right).value;
			return new PixelLiteral(result);
		}
		if (left instanceof ScalarLiteral && right instanceof PixelLiteral && operator.equals("*")) {
			int result = ((ScalarLiteral) left).value * ((PixelLiteral) right).value;
			return new PixelLiteral(result);
		}

		// scalar +/-/* scalar
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

		// % +/- %
		if (left instanceof PercentageLiteral && right instanceof PercentageLiteral && operator.equals("+")) {
			int result = ((PercentageLiteral) left).value + ((PercentageLiteral) right).value;
			return new PercentageLiteral(result);
		}
		if (left instanceof PercentageLiteral && right instanceof PercentageLiteral && operator.equals("-")) {
			int result = ((PercentageLiteral) left).value - ((PercentageLiteral) right).value;
			return new PercentageLiteral(result);
		}

		// Fallback: ongeldig type → 0.
		return new ScalarLiteral(0);
	}

	// -------------------------
	// Variabelen toekennen aan scopes
	// -------------------------
	private void assignVariable(Deque<Map<String, Literal>> scopes, String name, Literal value) {
		// Werk de dichtstbijzijnde scope bij waar de variabele reeds bestaat...
		for (Map<String, Literal> scope : scopes) {
			if (scope.containsKey(name)) {
				scope.put(name, value);
				return;
			}
		}
		// ...of anders: definieer in de huidige (peek) scope.
		if (!scopes.isEmpty()) {
			scopes.peek().put(name, value);
		}
	}
}
