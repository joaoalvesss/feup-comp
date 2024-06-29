package pt.up.fe.comp2024.analysis.passes;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

public class BooleanStatements extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(WHILE_STMT, this::visitBooleanStmt);
        addVisit(IF_STMT, this::visitBooleanStmt);
    }

    private Void visitBooleanStmt(JmmNode boolStmtNode, SymbolTable table) {
        JmmNode condition = boolStmtNode.getChild(0);
        Type condType = getType(condition, table);
        if (!condType.equals(new Type("boolean", false))) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(boolStmtNode), NodeUtils.getColumn(boolStmtNode), "If condition must be a boolean expression.", null));
        }
        return null;
    }

}
