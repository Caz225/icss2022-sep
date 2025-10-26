grammar ICSS;

//--- LEXER: ---

// IF support:
IF: 'if';
ELSE: 'else';
BOX_BRACKET_OPEN: '[';
BOX_BRACKET_CLOSE: ']';


//Literals
TRUE: 'TRUE';
FALSE: 'FALSE';
PIXELSIZE: [0-9]+ 'px';
PERCENTAGE: [0-9]+ '%';
SCALAR: [0-9]+;


//Color value takes precedence over id idents
COLOR: '#' [0-9a-f] [0-9a-f] [0-9a-f] [0-9a-f] [0-9a-f] [0-9a-f];

//Specific identifiers for id's and css classes
ID_IDENT: '#' [a-z0-9\-]+;
CLASS_IDENT: '.' [a-z0-9\-]+;

//General identifiers
VARIABLE_IDENT: [A-Z][a-zA-Z0-9]*;

LOWER_IDENT: [a-z] [a-z0-9\-]*;
CAPITAL_IDENT: [A-Z] [A-Za-z0-9_]*;

//All whitespace is skipped
WS: [ \t\r\n]+ -> skip;

//
OPEN_BRACE: '{';
CLOSE_BRACE: '}';
SEMICOLON: ';';
COLON: ':';
PLUS: '+';
MIN: '-';
MUL: '*';
ASSIGNMENT_OPERATOR: ':=';







//--- PARSER: ---
stylesheet
    : (variableAssignment | ruleset)* EOF
    ;

statement
    : variableAssignment
    | ruleset
    ;

variableAssignment
    : VARIABLE_IDENT ASSIGNMENT_OPERATOR expression SEMICOLON
    ;

ruleset
    : selector OPEN_BRACE declaration* CLOSE_BRACE
    ;

selector
    : ID_IDENT
    | CLASS_IDENT
    | LOWER_IDENT
    ;

declaration
    : propertyName COLON expression SEMICOLON
    ;

propertyName
    : 'color'
    | 'background-color'
    | 'width'
    | 'height'
    ;

expression
    : expression PLUS expression
    | expression MIN expression
    | expression MUL expression
    | PIXELSIZE
    | PERCENTAGE
    | SCALAR
    | TRUE
    | FALSE
    | COLOR
    | VARIABLE_IDENT
    | LOWER_IDENT
    | ID_IDENT
    | CLASS_IDENT
    | '(' expression ')'
    ;
