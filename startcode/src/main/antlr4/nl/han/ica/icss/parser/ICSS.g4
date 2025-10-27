grammar ICSS;

//========================
//        LEXER
//========================

//--- Keywords voor logica ---
IF: 'if';
ELSE: 'else';

//--- Speciale tekens voor blokken ---
BOX_BRACKET_OPEN: '[';
BOX_BRACKET_CLOSE: ']';
OPEN_BRACE: '{';
CLOSE_BRACE: '}';
SEMICOLON: ';';
COLON: ':';

//--- Operatoren ---
PLUS: '+';
MIN: '-';
MUL: '*';
ASSIGNMENT_OPERATOR: ':=';

//--- Literals ---
// Booleans
TRUE: 'TRUE';
FALSE: 'FALSE';

// Maten en waarden
PIXELSIZE: [0-9]+ 'px';
PERCENTAGE: [0-9]+ '%';
SCALAR: [0-9]+;

// Kleur
// Kleuren krijgen prioriteit boven ID-idents
COLOR: '#' [0-9a-f] [0-9a-f] [0-9a-f] [0-9a-f] [0-9a-f] [0-9a-f];

//--- Identifiers ---
// Specifiek voor ID's en CSS-classes
ID_IDENT: '#' [a-z0-9\-]+;
CLASS_IDENT: '.' [a-z0-9\-]+;

// Algemene variabelen en identifiers
VARIABLE_IDENT: [A-Z][a-zA-Z0-9]*;
LOWER_IDENT: [a-z] [a-z0-9\-]*;
CAPITAL_IDENT: [A-Z] [A-Za-z0-9_]*;

//--- Whitespace ---
// Alle whitespace wordt overgeslagen
WS: [ \t\r\n]+ -> skip;


//========================
//        PARSER
//========================

//--- Stylesheet structuur ---
stylesheet
    : (variableAssignment | ruleset)* EOF
    ;

// Een statement kan een variable assignment of ruleset zijn
statement
    : variableAssignment
    | ruleset
    ;

//--- Variable Assignment ---
variableAssignment
    : VARIABLE_IDENT ASSIGNMENT_OPERATOR expression SEMICOLON
    ;

//--- Rulesets en selectors ---
ruleset
    : selector OPEN_BRACE declaration* CLOSE_BRACE
    ;

selector
    : ID_IDENT
    | CLASS_IDENT
    | LOWER_IDENT
    ;

//--- Declaraties ---
declaration
    : propertyName COLON expression SEMICOLON
    ;

// Eigenschappen die ondersteund worden
propertyName
    : 'color'
    | 'background-color'
    | 'width'
    | 'height'
    ;

//--- Expressions ---
// Volgorde van bewerkingen: multiplicatie > optellen/aftrekken
expression
    : additionExpr
    ;

additionExpr
    : multiplicationExpr ( (PLUS | MIN) multiplicationExpr )*
    ;

multiplicationExpr
    : primaryExpr ( MUL primaryExpr )*
    ;

primaryExpr
    : PIXELSIZE
    | PERCENTAGE
    | SCALAR
    | COLOR
    | VARIABLE_IDENT
    | LOWER_IDENT
    | TRUE
    | FALSE
    | '(' additionExpr ')'   // Haakjes om expressies te groeperen
    ;
