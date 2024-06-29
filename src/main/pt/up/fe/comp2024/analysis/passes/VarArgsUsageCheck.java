package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

public class VarArgsUsageCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(CLASS_DECL, this::visitClassDecl);
        addVisit(METHOD_DECL, this::visitVarDecl);
    }

    private Void visitClassDecl(JmmNode classDeclNode, SymbolTable table) {
        for (JmmNode varDecl : classDeclNode.getChildren(VAR_DECL)) {
            if (Boolean.parseBoolean(varDecl.getChild(0).get("isVarArgs"))) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varDecl),
                        NodeUtils.getColumn(varDecl),
                        "Varargs are not allowed in Field variable declarations.",
                        null));
            }
        }
        return null;
    }

    private Void visitVarDecl(JmmNode methodDeclNode, SymbolTable table) {
        for (JmmNode varDecl : methodDeclNode.getChildren(VAR_DECL)) {
            if (Boolean.parseBoolean(varDecl.getChild(0).get("isVarArgs"))) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varDecl),
                        NodeUtils.getColumn(varDecl),
                        "Varargs are not allowed in Local variable declarations.",
                        null));
            }
        }
        return null;
    }


}
