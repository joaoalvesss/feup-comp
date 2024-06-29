package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

public class ThisUsageCheck extends AnalysisVisitor {

    private String currentMethod = null;
    private boolean isStaticMethod = false;

    @Override
    public void buildVisitor() {
        addVisit("MethodDecl", this::visitMethodDecl);
        addVisit("ThisExpr", this::visitThisExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        isStaticMethod = method.getOptional("isStatic").map(Boolean::parseBoolean).orElse(false);
        return null;
    }

    private Void visitThisExpr(JmmNode node, SymbolTable table) {
        if (isStaticMethod && "main".equals(currentMethod)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(node),
                    NodeUtils.getColumn(node),
                    "Usage of 'this' in static context of 'main' method is not allowed.",
                    null
            ));
        }

        if(node.getParent().getKind().equals("FieldAccess")){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(node),
                    NodeUtils.getColumn(node),
                    "Usage of 'this' in field access is not allowed.",
                    null
            ));
        }

        return null;
    }
}
