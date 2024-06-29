package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.*;

public class Duplicates extends AnalysisVisitor {
    @Override
    public void buildVisitor() {
        addVisit(PROGRAM, this::visitImports);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethod);
    }

    private Void visitImports(JmmNode jmmNode, SymbolTable symbolTable) {
        Set<String> impSet = new HashSet<>();
        for (var imp : symbolTable.getImports()) {
            if (!impSet.add(imp)) {
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(jmmNode), NodeUtils.getColumn(jmmNode), "Duplicate import: " + imp, null));
            }
        }
        return null;
    }

    private Void visitClass(JmmNode jmmNode, SymbolTable symbolTable) {
        Set<String> fieldSet = new HashSet<>();

        for (var field : symbolTable.getFields()) {
            if (!fieldSet.add(field.getName())) {
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(jmmNode), NodeUtils.getColumn(jmmNode), "Duplicate field: " + field.getName(), null));
            }
        }


        for(String methodA : symbolTable.getMethods()){
            int count = 0;
            for(String methodB : symbolTable.getMethods()){
                if(Objects.equals(methodA, methodB)){
                    List<Symbol> paramsA = symbolTable.getParameters(methodA);
                    List<Symbol> paramsB = symbolTable.getParameters(methodB);
                    if(paramsA.equals(paramsB)) count++;
                }
            }
            if(count > 1){
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(jmmNode), NodeUtils.getColumn(jmmNode), "Duplicate method: " + methodA, null));
            }
        }
        return null;
    }

    private Void visitMethod(JmmNode jmmNode, SymbolTable symbolTable) {
        Set<String> methodVarsSet = new HashSet<>();
        for (var param : symbolTable.getParameters(jmmNode.get("name"))) {
            if (!methodVarsSet.add(param.getName())) {
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(jmmNode), NodeUtils.getColumn(jmmNode), "Duplicate parameter: " + param.getName(), null));
            }
        }

        for (var variable : symbolTable.getLocalVariables(jmmNode.get("name"))) {
            if (!methodVarsSet.add(variable.getName())) {
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(jmmNode), NodeUtils.getColumn(jmmNode), "Duplicate variable: " + variable.getName(), null));
            }
        }

        if(jmmNode.getChildren(RETURN_STMT).size() > 1){
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(jmmNode), NodeUtils.getColumn(jmmNode), "Multiple return statements in method.", null));
        }
        return null;
    }
}
