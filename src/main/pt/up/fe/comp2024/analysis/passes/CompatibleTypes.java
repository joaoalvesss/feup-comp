package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import static pt.up.fe.comp2024.ast.Kind.ASSIGN_STMT;
import static pt.up.fe.comp2024.ast.Kind.BINARY_EXPR;

public class CompatibleTypes extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(BINARY_EXPR, this::visitBinaryOp);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
    }

    private Void visitBinaryOp(JmmNode binaryOp, SymbolTable table) {
        String operation = binaryOp.get("op");
        JmmNode leftOp = binaryOp.getChildren().get(0);
        JmmNode rightOp = binaryOp.getChildren().get(1);

        Type leftType = getType(leftOp, table);
        Type rightType = getType(rightOp, table);

        if (leftType == null || rightType == null) {
            return null;
        }

        // Check for logical operation
        if (operation.equals("&&")) {
            if (!leftType.getName().equals("boolean") || !rightType.getName().equals("boolean")) {
                var message = String.format("Operands '%s' and '%s' must both be boolean for logical '&&' operation", leftType, rightType);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(binaryOp),
                        NodeUtils.getColumn(binaryOp),
                        message,
                        null)
                );
            }
        } else {
            // Arithmetic operations (+, -, *, /)
            if (!leftType.getName().equals("int") || !rightType.getName().equals("int")) {
                var message = String.format("Operands '%s' and '%s' must both be integers for arithmetic operation", leftType, rightType);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(binaryOp),
                        NodeUtils.getColumn(binaryOp),
                        message,
                        null)
                );
            }

            // Check for array-scalar mix in arithmetic operations
            if (((leftType.isArray() && !rightType.isArray()) || (!leftType.isArray() && rightType.isArray()))) {
                String message = "Array types cannot be mixed with scalar types in arithmetic expressions.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(binaryOp),
                        NodeUtils.getColumn(binaryOp),
                        message,
                        null));
            }
        }

        return null;
    }

    //assignIntToBool, objectAssignmentFail
    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable symbolTable) {

        Type leftType = getVarType(assignStmt.getChild(0), symbolTable);
        Type rightType = getType(assignStmt.getChild(1), symbolTable);

        if (leftType == null || rightType == null) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(assignStmt),
                    NodeUtils.getColumn(assignStmt),
                    "At least one of the Operands wasnt found or is not a valid type",
                    null));
            return null;
        }

        String leftName = leftType.getName();
        String rightName = rightType.getName();

        if (rightName.equals("yodaPotatoDestroyer2024GigaSpecific") ||
                leftName.equals("yodaPotatoDestroyer2024GigaSpecific")) return null; // assume that the function call is correct from import

        if (leftType.equals(rightType)) { // equal types
            if (isPrimordial(leftName)) return null; // if both are primordial types
            else if (symbolTable.getImports().contains(leftName)) return null; // if both are imported types
            else if (leftName.equals(symbolTable.getClassName())) return null; // if both are the class
            else {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(assignStmt),
                        NodeUtils.getColumn(assignStmt),
                        "Var of type " + leftName + "was not imported",
                        null));
            }
        } else if (rightName.equals(symbolTable.getClassName())) {           // check if the right side is super class of the left side class
            if (leftName.equals(symbolTable.getSuper())) return null;
            else {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(assignStmt),
                        NodeUtils.getColumn(assignStmt),
                        "Incompatible types 1: " + leftType + " cannot be converted to " + rightType,
                        null));
            }
        } else if (symbolTable.getImports().contains(rightName) && symbolTable.getImports().contains(leftName)) { // check if both are imported classes
            return null;
        } else {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(assignStmt),
                    NodeUtils.getColumn(assignStmt),
                    "Incompatible types 2: " + leftType + " cannot be converted to " + rightType,
                    null));
        }
        return null;
    }

    private boolean isPrimordial(String typeName) {
        return typeName.equals("int") || typeName.equals("boolean") || typeName.equals("String");
    }

}
