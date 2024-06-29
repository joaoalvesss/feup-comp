package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2024.ast.Kind.*;

public class MethodCallCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(METHOD_DECL, this::visitMethodDeclaration);
        addVisit(FUNCTION_CALL_EXPR, this::visitMethodCall);
        addVisit(RETURN_STMT, this::visitReturnStmt);
    }

    private Void visitMethodDeclaration(JmmNode methodCallNode, SymbolTable symbolTable) {
        if (!isMethodDeclared(methodCallNode, methodCallNode.getParent().get("name"), symbolTable)) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(methodCallNode), NodeUtils.getColumn(methodCallNode), "Call to an undeclared method '" + methodCallNode.get("name") + "' on class '" + methodCallNode.getParent().get("name") + "'.", null));
        }
        return null;
    }

    private Void visitMethodCall(JmmNode methodCallNode, SymbolTable symbolTable) {
        Type objInstance = getType(methodCallNode, symbolTable);
        if (objInstance == null) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(methodCallNode), NodeUtils.getColumn(methodCallNode), "Object instance not found.", null));
        } else {
            JmmNode method = getMethodHeader(methodCallNode, symbolTable);
            if(method != null) {
                checkMethodCallParams(methodCallNode, method, symbolTable);
            }
        }
        return null;
    }

    private JmmNode getMethodHeader(JmmNode methodCallNode, SymbolTable symbolTable) {
        Optional<JmmNode> classNode = methodCallNode.getAncestor(CLASS_DECL);
        return classNode.flatMap(jmmNode -> jmmNode.getChildren(METHOD_DECL).stream().filter(method -> method.get("name").equals(methodCallNode.get("func"))).findFirst()).orElse(null);
    }

    protected Void visitReturnStmt(JmmNode returnStmt, SymbolTable symbolTable) {
        JmmNode method = returnStmt.getParent();
        Type methodType = getType(method.getChild(0), symbolTable);

        if (methodType.getName().equals("void")) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(returnStmt), NodeUtils.getColumn(returnStmt), "Return statement in void method.", null));
            return null;
        }

        Type returnType = getType(returnStmt.getChild(0), symbolTable);
        if (returnType == null) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(returnStmt), NodeUtils.getColumn(returnStmt), "Return type not found.", null));
        } else if (!returnType.equals(methodType)) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(returnStmt), NodeUtils.getColumn(returnStmt), "Return type does not match method return type.", null));
        }
        return null;
    }

    private boolean isMethodDeclared(JmmNode method, String className, SymbolTable symbolTable) {
        String methodName = method.get("name");

        if (className.equals(symbolTable.getClassName()) || className.equals(symbolTable.getSuper()))
            return symbolTable.getMethods().contains(methodName);
        else
            return symbolTable.getImports().contains(className);
    }

    private void checkMethodCallParams(JmmNode methodCallNode, JmmNode method, SymbolTable symbolTable) {
        List<JmmNode> methodParams = method.getChildren(PARAM);
        for (int i = 0; i < methodParams.size(); i++) {
            if (methodCallNode.getChildren().size() >= i + 1) {
                JmmNode paramTypeA = methodParams.get(i).getChild(0);
                Type paramTypeB = getType(paramTypeA, symbolTable);
                Type corrVarType = getType(methodCallNode.getChild(i + 1), symbolTable);
                if (Boolean.parseBoolean(paramTypeA.get("isVarArgs"))) {
                    if (!allSameType(paramTypeB, methodCallNode.getChildren().subList(i + 1, methodCallNode.getChildren().size()), symbolTable)) {
                        addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(methodCallNode), NodeUtils.getColumn(methodCallNode), "Method call parameter type does not match method parameter type.", null));
                    }
                } else if (!paramTypeB.equals(corrVarType)) {
                    addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(methodCallNode), NodeUtils.getColumn(methodCallNode), "Method call parameter type does not match method parameter type.", null));
                }
            } else {
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(methodCallNode), NodeUtils.getColumn(methodCallNode), "Method call has fewer arguments than expected.", null));
                break;
            }
        }
    }
}