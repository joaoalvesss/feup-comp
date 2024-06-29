grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LSQUARE : '[' ;
RSQUARE : ']' ;
MUL : '*' ;
DIV : '/' ;
ADD : '+' ;
SUB : '-' ;
LESS : '<' ;
AND : '&&' ;
NEG : '!' ;
TRUE : 'true' ;
FALSE : 'false' ;

NEW : 'new' ;
VOID : 'void' ;
CLASS : 'class' ;
INT : 'int' ;
BOOLEAN : 'boolean' ;
STRING: 'String';
ELLIPSIS : '...' ;
PUBLIC : 'public' ;
IMPORT : 'import' ;
RETURN : 'return' ;

IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;

INTEGER : [0] | ([1-9][0-9]*) ;
ID : [a-zA-Z_$]([a-zA-Z_$0-9])* ;

WS : [ \t\n\r\f]+ -> skip ;
COMMENT : '//' ~[\r\n]* -> skip;
ML_COMMENT : '/*' .*? '*/' -> skip;

program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT packageName+=ID ('.' packageName+=ID)* SEMI
    ;

classDecl
    : CLASS name=ID
        ( 'extends' superName=ID )?
        LCURLY
        varDecl* methodDecl*
        RCURLY
    ;

varDecl
    : type name=ID SEMI
    ;

methodDecl locals[boolean isPublic=false, boolean isStatic=false;]
    : (PUBLIC {$isPublic=true;})?
        ('static' {$isStatic=true;})?
        type name=ID
        LPAREN (param (',' param)*)? RPAREN
        LCURLY varDecl* stmt* RCURLY
    ;

type locals[boolean isArray=false, boolean isVarArgs=false]
    : name= INT
    | name= INT LSQUARE RSQUARE {$isArray=true;}
    | name= INT ELLIPSIS {$isVarArgs=true;}
    | name= BOOLEAN
    | name= STRING
    | name= STRING LSQUARE RSQUARE {$isArray=true;}
    | name= VOID
    | name= ID
    ;

param
    : type name=ID
    ;

stmt
    : LCURLY stmt* RCURLY #StmtSeq
    | IF LPAREN expr RPAREN stmt (ELSE stmt) #IfStmt
    | WHILE LPAREN expr RPAREN stmt #WhileStmt
    | expr SEMI #ExprStmt
    | expr (LSQUARE expr RSQUARE)? EQUALS expr SEMI #AssignStmt
    | RETURN expr SEMI #ReturnStmt
    ;

expr
    : LPAREN expr RPAREN #ParenthesesExpr
    | LSQUARE (expr (',' expr)*)? RSQUARE #ElementExpr
    | expr '.' func=ID LPAREN (expr (',' expr)*)? RPAREN #FunctionCallExpr
    | expr '.' field=ID #FieldAccess
    | NEG expr #Not
    | expr LSQUARE expr RSQUARE #AccessExpr
    | expr op= (MUL | DIV) expr #BinaryExpr
    | expr op= (ADD | SUB) expr #BinaryExpr
    | expr op= LESS expr #BinaryExpr
    | expr op= AND expr #BinaryExpr
    | NEW type LSQUARE expr RSQUARE #NewVectorExpr
    | NEW name=ID LPAREN RPAREN #NewObjectExpr
    | value=INTEGER #IntegerLiteral
    | value=TRUE #BooleanLiteral
    | value=FALSE #BooleanLiteral
    | name=ID #VarRefExpr
    | name='this' #ThisExpr
    ;
