package nl.han.ica.icss.parser;

import nl.han.ica.datastructures.HANStack;
import nl.han.ica.datastructures.IHANStack;
import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.literals.*;
import nl.han.ica.icss.ast.operations.AddOperation;
import nl.han.ica.icss.ast.operations.MultiplyOperation;
import nl.han.ica.icss.ast.operations.SubtractOperation;
import nl.han.ica.icss.ast.selectors.ClassSelector;
import nl.han.ica.icss.ast.selectors.IdSelector;
import nl.han.ica.icss.ast.selectors.TagSelector;

/**
 * Deze class maakt een AST van ICSS aan op basis van de Antlr Parse Tree.
 */
public class ASTListener extends ICSSBaseListener {

	//========================
	// ATTRIBUTES
	//========================
	private AST ast; // Root AST
	private IHANStack<ASTNode> currentContainer; // Houdt parent nodes bij

	public ASTListener() {
		ast = new AST();
		currentContainer = new HANStack<>();
		currentContainer.push(ast.root); // root is Stylesheet
	}

	public AST getAST() {
		return ast;
	}

	//========================
	// VARIABLE ASSIGNMENT
	//========================
	@Override
	public void enterVariableAssignment(ICSSParser.VariableAssignmentContext ctx) {
		// Nieuwe VariableAssignment node
		VariableAssignment varAssign = new VariableAssignment();

		// Voeg VariableReference toe (naam variabele)
		VariableReference name = new VariableReference(ctx.VARIABLE_IDENT().getText());
		varAssign.addChild(name);

		// Voeg Expression toe
		Expression expr = null;
		String exprText = ctx.expression().getText();

		if (exprText.matches("#[0-9a-fA-F]{6}")) {
			expr = new ColorLiteral(exprText);
		} else if (exprText.endsWith("px")) {
			expr = new PixelLiteral(exprText);
		} else if (exprText.endsWith("%")) {
			expr = new PercentageLiteral(exprText);
		} else if (exprText.matches("[0-9]+")) {
			expr = new ScalarLiteral(exprText);
		} else if (exprText.equals("TRUE") || exprText.equals("FALSE")) {
			expr = new BoolLiteral(exprText);
		} else {
			expr = new VariableReference(exprText);
		}

		varAssign.addChild(expr);
		currentContainer.peek().addChild(varAssign);
	}

	//========================
	// RULESET
	//========================
	@Override
	public void enterRuleset(ICSSParser.RulesetContext ctx) {
		// Nieuwe Stylerule
		Stylerule stylerule = new Stylerule();

		// Voeg selector toe
		if (ctx.selector() != null) {
			String selText = ctx.selector().getText();
			Selector selectorNode;

			if (selText.startsWith("#")) {
				selectorNode = new IdSelector(selText);
			} else if (selText.startsWith(".")) {
				selectorNode = new ClassSelector(selText);
			} else {
				selectorNode = new TagSelector(selText);
			}

			stylerule.addChild(selectorNode);
		}

		// Voeg Stylerule toe aan parent
		currentContainer.peek().addChild(stylerule);

		// Stylerule wordt nieuwe container
		currentContainer.push(stylerule);
	}

	@Override
	public void exitRuleset(ICSSParser.RulesetContext ctx) {
		// Klaar met Stylerule, ga terug naar parent
		currentContainer.pop();
	}

	//========================
	// DECLARATION
	//========================
	@Override
	public void enterDeclaration(ICSSParser.DeclarationContext ctx) {
		Declaration decl = new Declaration(ctx.propertyName().getText());

		Expression exprNode = null;
		if (ctx.expression() != null && ctx.expression().additionExpr() != null) {
			exprNode = parseAdditionExpr(ctx.expression().additionExpr());
		}
		if (exprNode != null) decl.addChild(exprNode);

		// Voeg toe aan de juiste parent
		ASTNode parent = currentContainer.peek();
		if (parent instanceof ElseClause || parent instanceof IfClause || parent instanceof Stylerule) {
			parent.addChild(decl);
		}
	}


	//========================
	// EXPRESSIONS
	//========================
	@Override
	public void enterAdditionExpr(ICSSParser.AdditionExprContext ctx) {
		Expression expr = parseAdditionExpr(ctx);
		// expr wordt toegevoegd via Declaration, dus hier niets
	}

	private Expression parseAdditionExpr(ICSSParser.AdditionExprContext ctx) {
		Expression left = parseMultiplicationExpr(ctx.multiplicationExpr(0));

		for (int i = 1; i < ctx.multiplicationExpr().size(); i++) {
			ICSSParser.MultiplicationExprContext rightCtx = ctx.multiplicationExpr(i);
			if (ctx.PLUS(i-1) != null) {
				AddOperation add = new AddOperation();
				add.addChild(left);
				add.addChild(parseMultiplicationExpr(rightCtx));
				left = add;
			} else if (ctx.MIN(i-1) != null) {
				SubtractOperation sub = new SubtractOperation();
				sub.addChild(left);
				sub.addChild(parseMultiplicationExpr(rightCtx));
				left = sub;
			}
		}

		return left;
	}

	private Expression parseMultiplicationExpr(ICSSParser.MultiplicationExprContext ctx) {
		Expression left = parsePrimaryExpr(ctx.primaryExpr(0));

		for (int i = 1; i < ctx.primaryExpr().size(); i++) {
			MultiplyOperation mul = new MultiplyOperation();
			mul.addChild(left);
			mul.addChild(parsePrimaryExpr(ctx.primaryExpr(i)));
			left = mul;
		}

		return left;
	}

	private Expression parsePrimaryExpr(ICSSParser.PrimaryExprContext ctx) {
		if (ctx.PIXELSIZE() != null) return new PixelLiteral(ctx.PIXELSIZE().getText());
		if (ctx.PERCENTAGE() != null) return new PercentageLiteral(ctx.PERCENTAGE().getText());
		if (ctx.SCALAR() != null) return new ScalarLiteral(ctx.SCALAR().getText());
		if (ctx.COLOR() != null) return new ColorLiteral(ctx.COLOR().getText());
		if (ctx.VARIABLE_IDENT() != null) return new VariableReference(ctx.VARIABLE_IDENT().getText());
		if (ctx.TRUE() != null || ctx.FALSE() != null) return new BoolLiteral(ctx.getText());
		if (ctx.additionExpr() != null) return parseAdditionExpr(ctx.additionExpr());
		return null;
	}


	@Override
	public void enterIfStatement(ICSSParser.IfStatementContext ctx) {
		Expression condition = parseAdditionExpr(ctx.expression().additionExpr());
		IfClause ifNode = new IfClause();
		ifNode.conditionalExpression = condition;
		currentContainer.peek().addChild(ifNode);
		currentContainer.push(ifNode);
	}

	@Override
	public void exitIfStatement(ICSSParser.IfStatementContext ctx) {
		// Pop tot en met de IfClause
		while (!(currentContainer.peek() instanceof IfClause)) {
			currentContainer.pop();
		}
		currentContainer.pop(); // Pop de IfClause zelf
	}





	// ELSE-block wordt pas gepushed als we statements binnen else tegenkomen
	@Override
	public void enterStatement(ICSSParser.StatementContext ctx) {
		// Controleer of dit statement deel uitmaakt van een IF-statement
		if (ctx.getParent() instanceof ICSSParser.IfStatementContext) {
			ICSSParser.IfStatementContext ifCtx = (ICSSParser.IfStatementContext) ctx.getParent();

			// Als er een ELSE aanwezig is, bepaal dan of deze statement ná de ELSE staat
			if (ifCtx.ELSE() != null) {
				int stmtTokenIndex = ctx.getStart().getTokenIndex();
				int elseTokenIndex = ifCtx.ELSE().getSymbol().getTokenIndex();

				// Alleen pushen als we daadwerkelijk in het ELSE-gedeelte binnenkomen
				if (stmtTokenIndex > elseTokenIndex && currentContainer.peek() instanceof IfClause) {
					ElseClause elseNode = new ElseClause();
					IfClause ifNode = (IfClause) currentContainer.peek();
					ifNode.elseClause = elseNode;
					ifNode.addChild(elseNode);
					currentContainer.push(elseNode);
				}
			}
		}
		// anders: geen aanpassing (andere statements worden normaal toegevoegd door uw listener)
	}



	@Override
	public void exitStatement(ICSSParser.StatementContext ctx) {
		// Pop ElseClause aan het einde van het else-block
		if (currentContainer.peek() instanceof ElseClause) {
			currentContainer.pop();
		}
	}
}
