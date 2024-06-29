package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

public class MethodStructure extends AnalysisVisitor {
    @Override
    public void buildVisitor() {
        addVisit(METHOD_DECL, this::visitMethod);
    }

    private Void visitMethod(JmmNode methodDeclNode, SymbolTable table) {

        checkReturnLast(methodDeclNode, table);
        checkVarArgsLast(methodDeclNode, table);

        if (methodDeclNode.get("name").equals("main")) {
            checkMainHeader(methodDeclNode, table);
            checkMainParams(methodDeclNode, table);
        }

        return null;
    }

    private void checkReturnLast(JmmNode methodDeclNode, SymbolTable table) {
        List<JmmNode> methodBody = methodDeclNode.getChildren();
        Type returnType = table.getReturnType(methodDeclNode.get("name"));

        if(!returnType.equals(new Type("void", false))){
            List<JmmNode> stmtList = methodDeclNode.getChildren();
            JmmNode lastStmt = stmtList.get(stmtList.size() - 1);
            if(!lastStmt.getKind().equals("ReturnStmt"))
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(lastStmt),
                        NodeUtils.getColumn(lastStmt),
                        "Return statement is not last statement.",
                        null));
        }
    }

    private void checkVarArgsLast(JmmNode methodDeclNode, SymbolTable table) {
        List<JmmNode> methodParams = methodDeclNode.getChildren(PARAM);
        for (int i = 0; i < methodParams.size(); i++) {
            if (Boolean.parseBoolean(methodParams.get(i).getChild(0).get("isVarArgs"))) {
                if (i != methodParams.size() - 1)
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(methodParams.get(i)),
                            NodeUtils.getColumn(methodParams.get(i)),
                            "Varargs must be the last of the method parameters.",
                            null));
            }
        }
    }

    private void checkMainHeader(JmmNode methodDeclNode, SymbolTable table) {
        boolean isStatic = Boolean.parseBoolean(methodDeclNode.get("isStatic"));
        boolean isPublic = Boolean.parseBoolean(methodDeclNode.get("isPublic"));
        Type returnType = table.getReturnType(methodDeclNode.get("name"));
        if (!(isStatic && isPublic && returnType.equals(new Type("void", false))))
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDeclNode),
                    NodeUtils.getColumn(methodDeclNode),
                    "Main method must be public and static.",
                    null));
    }

    private void checkMainParams(JmmNode methodDeclNode, SymbolTable table) {
        List<JmmNode> methodParams = methodDeclNode.getChildren(PARAM);
        if (methodParams.size() != 1)
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDeclNode),
                    NodeUtils.getColumn(methodDeclNode),
                    "Main method must have a single parameter.",
                    null));
        else {
            Type paramType = getVarType(methodParams.get(0), table);
            if (!paramType.equals(new Type("String", true)))
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodParams.get(0)),
                        NodeUtils.getColumn(methodParams.get(0)),
                        "Main method parameter must be a String[].",
                        null));
        }
    }
}
