package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";

    private static final String BOOL_TYPE_NAME = "boolean";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    public static JmmNode findAncestorOfKind(JmmNode node, String kind) {
        JmmNode current = node.getParent();
        while (current != null) {
            if (current.getKind().equals(kind)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    public static Type getExprType(JmmNode expr, SymbolTable table) {
        // Handle different kinds of expressions
        var kind = Kind.fromString(expr.getKind());

        return switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case BOOLEAN_LITERAL -> new Type(BOOL_TYPE_NAME, false);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case INTEGER_LITERAL, ACCESS_EXPR -> new Type(INT_TYPE_NAME, false);
            case FUNCTION_CALL_EXPR -> {
                yield table.getReturnType(expr.get("func"));
                //JmmNode assignStmt = findAncestorOfKind(expr, "AssignStmt");
                //yield (assignStmt != null) ? getVarExprType(assignStmt.getChildren().get(0), table) : new Type("void", false);
            }
            case NEW_OBJECT_EXPR -> new Type(expr.get("name"), false);
            case NEW_VECTOR_EXPR, ELEMENT_EXPR -> new Type("int", true); // Assuming the array type is int for simplicity
            case THIS_EXPR -> new Type(table.getClassName(), false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");
        return switch (operator) {
            case "+", "*", "-", "/" -> new Type(INT_TYPE_NAME, false); // checkar tipo do &&
            case "&&", "<" -> new Type(BOOL_TYPE_NAME, false);
            default -> throw new RuntimeException("Unknown operator '" + operator + "' in binary expression.");
        };
    }

    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        // This needs to be expanded to handle real type retrieval based on variable name
        Type type = new Type(INT_TYPE_NAME, false);
        List<Symbol> params = table.getParameters(Objects.requireNonNull(findAncestorOfKind(varRefExpr, "MethodDecl")).get("name"));
        if (varRefExpr.getKind().equals("FunctionCallExpr")) return table.getReturnType(varRefExpr.get("func"));
        if (varRefExpr.getKind().equals("BinaryExpr")) return type;

        if (varRefExpr.getKind().equals("VarRefExpr")) {
            for (String i : table.getImports()) {
                if (i.equals(varRefExpr.get("name"))) return new Type(i, false);
            }
        }


        for (Symbol param : params) {
            if (param.getName().equals(varRefExpr.get("name"))) return param.getType();
        }

        List<Symbol> fields = table.getFields();
        for (Symbol field : fields) {
            if (field.getName().equals(varRefExpr.get("name"))) return field.getType();
        }

        List<Symbol> locals = table.getLocalVariables(Objects.requireNonNull(findAncestorOfKind(varRefExpr, "MethodDecl")).get("name"));
        for (Symbol local : locals) {
            if (local.getName().equals(varRefExpr.get("name"))) return local.getType();
        }

        return type;
    }

    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        return sourceType.getName().equals(destinationType.getName());
    }
}
