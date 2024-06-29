package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

public class ArrayUsageCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit("AccessExpr", this::visitArrayAccess);
    }

    // arrayIndexNotInt
    private Void visitArrayAccess(JmmNode node, SymbolTable table) {
        JmmNode array = node.getJmmChild(0);
        JmmNode index = node.getJmmChild(1);

        Type leftType = getType(array, table);
        Type rightType = getType(index, table);

        if (!leftType.isArray()) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "First:" + leftType + "element inst an Array.", null));
        }

        if (!rightType.equals(new Type("int", false))) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Array access being done without an int type.", null));
        }

        return null;
    }
}
