package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;
    private boolean isStaticMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        isStaticMethod = method.getOptional("isStatic").map(Boolean::parseBoolean).orElse(false);
        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        // Check if exists a parameter or variable declaration with the same name as the variable reference
        var varRefName = varRefExpr.get("name");

        // Check if the variable is declared as a local variable
        if (table.getLocalVariables(currentMethod).stream().anyMatch(varDecl -> varDecl.getName().equals(varRefName))) {
            return null;
        }

        // Check if the variable is declared as a method parameter
        if (table.getParameters(currentMethod).stream().anyMatch(param -> param.getName().equals(varRefName))) {
            return null;
        }

        // Check if the variable is a field and ensure it is not accessed in a static method
        if (table.getFields().stream().anyMatch(param -> param.getName().equals(varRefName))) {
            if (isStaticMethod) {
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(varRefExpr), NodeUtils.getColumn(varRefExpr),
                        "Cannot access instance variable '" + varRefName + "' in a static method.", null));
            }
            return null;
        }

        // Check if the variable is an imported class
        if (table.getImports().stream().anyMatch(name -> name.equals(varRefName))) {
            return null;
        }

        // Create error report for undeclared variable
        var message = String.format("Variable '%s' does not exist.", varRefName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(varRefExpr),
                NodeUtils.getColumn(varRefExpr),
                message,
                null)
        );

        return null;
    }
}
