package pt.up.fe.comp2024.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2024.ast.Kind.*;


public abstract class AnalysisVisitor extends PreorderJmmVisitor<SymbolTable, Void> implements AnalysisPass {

    private final List<Report> reports;

    public AnalysisVisitor() {
        reports = new ArrayList<>();
        setDefaultValue(() -> null);
    }

    protected void addReport(Report report) {
        reports.add(report);
    }

    protected List<Report> getReports() {
        return reports;
    }

    protected Type getType(JmmNode node, SymbolTable symbolTable) {
        JmmNode arrayNode;
        Type arrayType;
        switch (node.getKind()) {
            case "IntegerLiteral", "FieldAccess":
                return new Type("int", false);

            case "BooleanLiteral", "Not":
                return new Type("boolean", false);

            case "VarRefExpr":
                return getVarType(node, symbolTable);

            case "BinaryExpr":
                String operator = node.get("op");
                if (operator.equals("+") || operator.equals("-") || operator.equals("/") || operator.equals("*")) {
                    return new Type("int", false);
                } else {
                    return new Type("boolean", false);
                }
            case "ParenthesesExpr":
                return getType(node.getChildren().get(0), symbolTable);

            case "ArrayAccess":
                arrayNode = node.getChildren().get(0);
                arrayType = getType(arrayNode, symbolTable);
                if (arrayType != null && arrayType.isArray()) {
                    return new Type(arrayType.getName(), false);
                }
                return null;

            case "NewObjectExpr":
                return new Type(node.get("name"), false);

            case "NewVectorExpr":
                return new Type(node.getChild(0).get("name"), true);

            case "ThisExpr":
                return new Type(symbolTable.getClassName(), false);

            case "FunctionCallExpr":
                return manageFunctionCall(node, symbolTable);

            case "AccessExpr":
                String array = getType(node.getChildren().get(0), symbolTable).getName();
                return new Type(array, false);

            case "ElementExpr":
                if (checkArraySameType(node, symbolTable)) {
                    String val = getType(node.getChildren().get(0), symbolTable).getName();
                    return new Type(val, true);
                } else return null;
            case "Type":
                return new Type(node.get("name"), Boolean.parseBoolean(node.get("isArray")));
            case "Param":
                return new Type(node.getChild(0).get("name"), Boolean.parseBoolean(node.getChild(0).get("isArray")));
            default:
                return null;
        }
    }

    protected Type getFieldType(JmmNode node, SymbolTable symbolTable) {
        Type varType = getType(node.getChild(0), symbolTable);
        if(!varType.getName().equals(symbolTable.getClassName())) return new Type("yodaPotatoDestroyer2024GigaSpecific", false);

        String fieldName = node.get("field");
        Optional<Symbol> field = symbolTable.getFields().stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst();

        return field.map(Symbol::getType).orElse(null);
    }

    protected Type getVarType(JmmNode node, SymbolTable symbolTable) {
        // IntegerLiteral, AccessExpr, FunctionCallExpr, FieldAccess
        if(node.getKind().equals("FieldAccess")) {
            return getFieldType(node, symbolTable);
        }

        // TODO CHECKAR SE ISTO ESTA CERTO
        if (!node.hasAttribute("name")) {
            return null;
        }

        String varName = node.get("name");
        Optional<JmmNode> methodNode = node.getAncestor(METHOD_DECL);
        if (methodNode.isPresent()) {
            List<JmmNode> variables = new ArrayList<>();
            variables.addAll(methodNode.get().getChildren(PARAM));
            variables.addAll(methodNode.get().getChildren(VAR_DECL));

            for (var child : variables) {
                if (child.get("name").equals(varName)) {
                    var varType = child.getChild(0).get("name");
                    var isArray = Boolean.parseBoolean(child.getChild(0).get("isArray"));
                    var isVarArgs = Boolean.parseBoolean(child.getChild(0).get("isVarArgs"));
                    return new Type(varType, isArray || isVarArgs);
                }
            }
        }

        Optional<Symbol> field = symbolTable.getFields().stream()
                .filter(f -> f.getName().equals(varName))
                .findFirst();

        return field.map(Symbol::getType).orElse(null);
    }


    protected Type manageFunctionCall(JmmNode node, SymbolTable symbolTable) {
        var variable = node.getChild(0);

        if (isStatic(node, symbolTable)) {
            return checkStaticMethod(variable.get("name"), node, symbolTable);
        } else {
            var varType = getType(variable, symbolTable);
            return checkInstanceMethod(varType, node, symbolTable);
        }
    }

    private Type checkInstanceMethod(Type varType, JmmNode node, SymbolTable symbolTable) {
        if (varType.getName().equals(symbolTable.getClassName())) {         // no caso de ter a mesma classe que a atual
            if (symbolTable.getReturnType(node.get("func")) != null)        // se tiver o metodo tudo bem
                return symbolTable.getReturnType(node.get("func"));
            else if (!symbolTable.getSuper().isBlank())                                          // se houver super
                return new Type("yodaPotatoDestroyer2024GigaSpecific", false);      // assumir que esta na super
            else
                return null;                                                               // se nao erro
        } else if (varType.getName().equals(symbolTable.getSuper())) {      // no caso de ser a super classe
            if (symbolTable.getReturnType(node.get("func")) != null)     // se tiver o metodo tudo bem
                return symbolTable.getReturnType(node.get("func"));
            return new Type("yodaPotatoDestroyer2024GigaSpecific", false); // se nao -> assumir metodo na super classe
        } else if (symbolTable.getImports().contains(varType.getName())) {                  // no caso de ser uma classe importada
            return new Type("yodaPotatoDestroyer2024GigaSpecific", false);  // assumir metodo na classe importada
        } else
            return null;
    }

    private Type checkStaticMethod(String callerClass, JmmNode node, SymbolTable symbolTable) {
        if (symbolTable.getImports().contains(callerClass)) {                     // no caso de ser uma classe importada
            return new Type("yodaPotatoDestroyer2024GigaSpecific", false);  // assumir metodo na classe importada
        } else
            return null;
    }

    private boolean isStatic(JmmNode node, SymbolTable symbolTable) {
        JmmNode variable = node.getChild(0);
        Type varType = getType(variable, symbolTable);
        return varType == null;
    }


    protected boolean allSameType(Type assignType, List<JmmNode> elements, SymbolTable symbolTable) {
        return elements.stream()
                .map(element -> getType(element, symbolTable))
                .allMatch(assignType::equals);
    }

    protected boolean checkArraySameType(JmmNode node, SymbolTable symbolTable) {
        JmmNode arrayNode = node.getChildren().get(0);
        for (int i = 0; i < node.getNumChildren() - 1; i++) {
            if (!getType(node.getChild(i), symbolTable).equals(getType(node.getChild(i + 1), symbolTable))) {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(node),
                        NodeUtils.getColumn(node),
                        "Array elements must be of the same type",
                        null));
                return false;
            }
        }
        return true;
    }


    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        // Visit the node
        visit(root, table);

        // Return reports
        return getReports();
    }
}
