package nl.han.ica.icss.parser;

import java.util.Stack;


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
 * This class extracts the ICSS Abstract Syntax Tree from the Antlr Parse tree.
 */
public class ASTListener extends ICSSBaseListener {
	
	//Accumulator attributes:
	private AST ast;

	//Use this to keep track of the parent nodes when recursively traversing the ast
	private IHANStack<ASTNode> currentContainer;

	public ASTListener() {
		ast = new AST();                 // AST met root Stylesheet
		currentContainer = new HANStack<>();
		currentContainer.push(ast.root); // root is een Stylesheet, een subclass van ASTNode
	}

	public AST getAST() {
        return ast;
    }

	@Override
	public void enterVariableAssignment(ICSSParser.VariableAssignmentContext ctx) {
		// Maak een nieuwe VariableAssignment node
		VariableAssignment varAssign = new VariableAssignment();

		// VariableReference toevoegen (de naam van de variabele)
		VariableReference name = new VariableReference(ctx.VARIABLE_IDENT().getText());
		varAssign.addChild(name);

		// Expression toevoegen: bepaal het type literal
		Expression expr = null;
		String exprText = ctx.expression().getText();

		// Hier kijkt u naar wat voor type waarde het is
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
			// Als het een variabele is (bijv. $mainColor)
			expr = new VariableReference(exprText);
		}

		// Voeg de Expression toe aan de VariableAssignment
		varAssign.addChild(expr);

		// Voeg de VariableAssignment toe aan de huidige container (bijvoorbeeld Stylesheet)
		currentContainer.peek().addChild(varAssign);
	}

	@Override
	public void enterRuleset(ICSSParser.RulesetContext ctx) {
		// Maak nieuwe Stylerule
		Stylerule stylerule = new Stylerule();

		// Voeg selectors toe
		if (ctx.selector() != null) {
			String selText = ctx.selector().getText();
			Selector selectorNode;

			if (selText.startsWith("#")) {
				// IdSelector met # behouden
				selectorNode = new IdSelector(selText);
			} else if (selText.startsWith(".")) {
				// ClassSelector met . behouden
				selectorNode = new ClassSelector(selText);
			} else {
				// TagSelector
				selectorNode = new TagSelector(selText);
			}

			// Voeg selector toe aan de Stylerule
			stylerule.addChild(selectorNode);
		}

		// Voeg deze Stylerule toe aan de huidige container (meestal Stylesheet)
		currentContainer.peek().addChild(stylerule);

		// Stylerule wordt nieuwe container voor alle declarations binnen
		currentContainer.push(stylerule);
	}



	@Override
	public void exitRuleset(ICSSParser.RulesetContext ctx) {
		// Klaar met deze Stylerule, ga terug naar parent
		currentContainer.pop();
	}

	@Override
	public void enterDeclaration(ICSSParser.DeclarationContext ctx) {
		// Maak een nieuwe Declaration node met de property-naam
		Declaration decl = new Declaration(ctx.propertyName().getText());

		// Parse de volledige expressie (inclusief operatoren)
		Expression exprNode = parseExpression(ctx.expression());

		// Voeg de expressie toe als child van de Declaration
		if (exprNode != null) {
			decl.addChild(exprNode);
		}

		// Voeg de Declaration toe aan de huidige container (bijv. Stylerule)
		currentContainer.peek().addChild(decl);
	}



	private Expression parseExpression(ICSSParser.ExpressionContext ctx) {
		if (ctx == null) return null;

		// Operatoren (recursieve structuur)
		if (ctx.PLUS() != null) {
			AddOperation add = new AddOperation();
			add.addChild(parseExpression(ctx.expression(0)));
			add.addChild(parseExpression(ctx.expression(1)));
			return add;
		}
		if (ctx.MIN() != null) {
			SubtractOperation sub = new SubtractOperation();
			sub.addChild(parseExpression(ctx.expression(0)));
			sub.addChild(parseExpression(ctx.expression(1)));
			return sub;
		}
		if (ctx.MUL() != null) {
			MultiplyOperation mul = new MultiplyOperation();
			mul.addChild(parseExpression(ctx.expression(0)));
			mul.addChild(parseExpression(ctx.expression(1)));
			return mul;
		}

		// Basis-literals en variabelen
		if (ctx.COLOR() != null) return new ColorLiteral(ctx.COLOR().getText());
		if (ctx.PIXELSIZE() != null) return new PixelLiteral(ctx.PIXELSIZE().getText());
		if (ctx.PERCENTAGE() != null) return new PercentageLiteral(ctx.PERCENTAGE().getText());
		if (ctx.SCALAR() != null) return new ScalarLiteral(ctx.SCALAR().getText());
		if (ctx.TRUE() != null || ctx.FALSE() != null) return new BoolLiteral(ctx.getText());
		if (ctx.VARIABLE_IDENT() != null) return new VariableReference(ctx.VARIABLE_IDENT().getText());

		return null;
	}




	@Override
	public void enterExpression(ICSSParser.ExpressionContext ctx) {
		Expression exprNode = null;

		if (ctx.PLUS() != null) {
			AddOperation add = new AddOperation();
			add.addChild(parseExpression(ctx.expression(0)));
			add.addChild(parseExpression(ctx.expression(1)));
			exprNode = add;
		} else if (ctx.MIN() != null) {
			SubtractOperation sub = new SubtractOperation();
			sub.addChild(parseExpression(ctx.expression(0)));
			sub.addChild(parseExpression(ctx.expression(1)));
			exprNode = sub;
		} else if (ctx.MUL() != null) {
			MultiplyOperation mul = new MultiplyOperation();
			mul.addChild(parseExpression(ctx.expression(0)));
			mul.addChild(parseExpression(ctx.expression(1)));
			exprNode = mul;
		} else {
			// leaf: literal of variable
			exprNode = parseExpression(ctx);
		}

		// Voeg exprNode **niet** toe aan currentContainer hier
		// De declaratie zelf voegt exprNode toe via decl.addChild(exprNode)
	}













}