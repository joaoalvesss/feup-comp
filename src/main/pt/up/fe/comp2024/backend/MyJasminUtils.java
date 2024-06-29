package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.specs.util.classmap.FunctionClassMap;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class MyJasminUtils {

    protected static final String NL = "\n";
    protected static final String TAB = "\t";

    OllirResult ollirResult;
    List<Report> reports;
    protected static Method currMethod;
    FunctionClassMap<TreeNode, String> generators;

    protected int customLabelCounter = 0;
    public int stackLimit = 0;
    public int stackPointer = 0;

    public MyJasminUtils(OllirResult ollirResult, List<Report> reports, FunctionClassMap<TreeNode, String> generators) {
        this.ollirResult = ollirResult;
        this.reports = reports;
        this.generators = generators;
    }

    public String solveAccessModifier(AccessModifier accessModifier) {
        if (accessModifier == AccessModifier.DEFAULT) return "protected ";
        return accessModifier.name().toLowerCase() + " ";
    }

    public String solveObjectClass(CallInstruction callInst) {
        var className = callInst.getReturnType().toString();
        if (className.equals("VOID")) return "this";
        return className.substring(className.indexOf('(') + 1, className.indexOf(')'));
    }

    public String solveNameColonDot(String method) {
        int colonIndex = method.indexOf(":");
        int dotIndex = method.indexOf(".");

        if (colonIndex != -1 && dotIndex != -1) { // Check if ":" and "." are found in the input string
            // Extract the substring between ":" and "."
            String output = method.substring(colonIndex + 1, dotIndex);
            return output.trim();
        } else {
            return "':' and/or '.' not found in the input string.";
        }
    }

    public String solveObjectClass(String method) {
        return method.substring(method.indexOf('(') + 1, method.indexOf(')'));
    }

    public String solveType(Type type) {
        return switch (type.getTypeOfElement()) {
            case ARRAYREF -> "[" + solveType(((ArrayType) type).getElementType());
            case INT32 -> "I";
            case BOOLEAN -> "Z";
            case OBJECTREF -> "L" + convertClassName(((ClassType) type).getName()) + ";";
            case STRING -> "Ljava/lang/String;";
            case VOID -> "V";
            default -> {
                reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, "Error: Var Type not Recognized" + NL));
                yield "ERROR";
            }
        };
    }

    public String convertClassName(String className) {
        return className.equals("this") ? this.ollirResult.getOllirClass().getClassName() : className;
    }

    public String generateVarNumber(String varName) {
        if (varName.equals("this")) return "_0";

        int varNumber = currMethod.getVarTable().get(varName).getVirtualReg();

        return ((varNumber <= 3) ? "_" : " ") + varNumber;
    }

    public String generateLoad(Element element) {
        if (element instanceof ArrayOperand operand){
            return generators.apply(operand) + "iaload" + NL;
        }
        else if (element instanceof Operand operand)
            return generators.apply(operand);
        else if (element instanceof LiteralElement literal)
            return generators.apply(literal);

        return "LOAD ERROR";
    }

    public String generateStore(Operand operand) {
        ElementType elType = operand.getType().getTypeOfElement();
        ElementType varType = currMethod.getVarTable().get(operand.getName()).getVarType().getTypeOfElement();

        switch (elType) {
            case INT32, BOOLEAN ->{
                updateStackLimits(varType == ElementType.ARRAYREF ? -3 : -1);
                return (varType == ElementType.ARRAYREF) ? "iastore" : "istore" + generateVarNumber(operand.getName());
            }

            case OBJECTREF, STRING, ARRAYREF, THIS -> {
                updateStackLimits(-1);
                return "astore" + generateVarNumber(operand.getName());
            }

            default -> {
                reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, "Error: Var Type not Recognized" + NL));
                return "STORE ERROR";
            }
        }
    }

    public int generateNew(CallInstruction callInst, StringBuilder code){
        int updateValue = 0;

        switch (callInst.getReturnType().getTypeOfElement()) {
            case OBJECTREF -> {
                for (Element e : callInst.getArguments()) {
                    code.append(generateLoad(e));
                }
                updateValue = callInst.getArguments().size() - 2;
                code.append("new ").append(convertClassName(solveObjectClass(callInst)));
            }
            case ARRAYREF -> {
                for (Element e : callInst.getArguments()) {
                    code.append(generateLoad(e));
                }
                updateValue = callInst.getArguments().size() - 2;
                code.append("newarray int");
            }
            default -> {
                reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, "Error: Var Type not Recognized" + NL));
            }
        }

        code.append(NL);
        return updateValue;
    }

    public int generateInvSpecial(CallInstruction callInst, StringBuilder code) {

        code.append(generateLoad(callInst.getOperands().get(0))).append("invokespecial ");

        var className = convertClassName(solveObjectClass(callInst));
        if (className.equals("this")) code.append(ollirResult.getSymbolTable().getSuper());
        else code.append(className);

        code.append("/<init>(");

        for (Element e : callInst.getArguments())
            code.append(solveType(e.getType()));

        code.append(")").append(solveType(callInst.getReturnType())).append(NL);
        return 0;
    }

    public int generateInvStatic(CallInstruction callInst, StringBuilder code) {
        for(Element arg : callInst.getArguments())
            code.append(generateLoad(arg));

        var className = solveNameColonDot(callInst.getCaller().toString());
        if (className.equals("this")) className = ollirResult.getSymbolTable().getClassName();
        code.append("invokestatic ").append(className);

        var methodName = solveNameColonDot(callInst.getMethodName().toString().replace("\"", ""));
        code.append(getMethodParams(callInst, methodName));

        return callInst.getArguments().size() - 1; // the amount of arguments that will be popped
    }

    public int generateInvVirtual(CallInstruction callInst, StringBuilder code) {
        code.append(generateLoad(callInst.getOperands().get(0)));

        for (Element arg : callInst.getArguments())
            code.append(generateLoad(arg));

        var className = convertClassName(solveObjectClass(callInst.getCaller().toString()));
        if (className.equals("this")) className = ollirResult.getSymbolTable().getClassName();
        code.append("invokevirtual ").append(className);

        var methodName = solveNameColonDot(callInst.getMethodName().toString().replace("\"", ""));
        code.append(getMethodParams(callInst, methodName));

        return callInst.getArguments().size();
    }

    private String getMethodParams(CallInstruction callInst, String methodName) {
        StringBuilder code = new StringBuilder();

        code.append("/").append(methodName).append("(");

        for (Element e : callInst.getArguments()) {
            code.append(solveType(e.getType()));
        }

        code.append(")").append(solveType(callInst.getReturnType())).append(NL);

        return code.toString();
    }

    public String generateArrayLength(CallInstruction callInst) {
        return generateLoad(callInst.getOperands().get(0)) + "arraylength" + NL;
    }

    public String generateLdc(CallInstruction callInst){
        return generateLoad(callInst.getOperands().get(0));
    }

    public String solveBinOp(Operation binaryOp) {
        return switch (binaryOp.getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case DIV -> "idiv";
            case SUB -> "isub";
            case ANDB -> "iand";
            case NOTB -> "ifeq";
            case LTH -> "if_icmplt";
            case GTH -> "if_icmpgt";
            default -> {
                reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, "Error: Binary Operation not Recognized" + NL));
                yield "ERROR";
            }

        };
    }

    public String pushComparisonResultToStack() {
        StringBuilder code = new StringBuilder();

        code.append("MyLabel").append(customLabelCounter).append(NL);
        code.append("ldc 0").append(NL);
        code.append("goto MySkip").append(customLabelCounter).append(NL);
        code.append("MyLabel").append(customLabelCounter).append(":").append(NL);
        code.append("ldc 1").append(NL);
        code.append("MySkip").append(customLabelCounter++).append(":").append(NL);

        return code.toString();
    }

    public boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static int getLocalsNum() {
        Set<Integer> regs = new TreeSet<>();
        regs.add(0); // adding the default needed register

        for (var v : currMethod.getVarTable().values())
            regs.add(v.getVirtualReg()); // adding the needed registers

        return regs.size();
    }

    public void updateStackLimits(int increment) {
        stackPointer += increment;
        stackLimit = Math.max(stackLimit, stackPointer);
    }

    public void resetStackLimits() {
        stackPointer = 0;
        stackLimit = 0;
    }
}
