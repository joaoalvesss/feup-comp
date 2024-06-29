package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.*;
import java.util.stream.Collectors;

import static org.specs.comp.ollir.InstructionType.CALL;
import static org.specs.comp.ollir.OperationType.GTE;
import static org.specs.comp.ollir.OperationType.LTH;
import static pt.up.fe.comp2024.backend.MyJasminUtils.*;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    private final FunctionClassMap<TreeNode, String> generators;

    MyJasminUtils jUtils;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit); // done
        generators.put(Method.class, this::generateMethod); // done
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCond);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(ArrayOperand.class, this::generateArrayOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(OpCondInstruction.class, this::generateOpCond);
        generators.put(UnaryOpInstruction.class, this::dealWithUnaryOp);

        jUtils = new MyJasminUtils(ollirResult, reports, generators);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {
        var code = new StringBuilder();
        if (jUtils.isNullOrEmpty(classUnit.getSuperClass()) || classUnit.getSuperClass().equals("Object"))
            classUnit.setSuperClass("java/lang/Object");

        code.append(".class public ").append(classUnit.getClassName()).append(NL);
        code.append(".super ").append(classUnit.getSuperClass());
        code.append(NL).append(NL);

        for (var field : classUnit.getFields()) {
            code.append(".field ")
                    .append(jUtils.solveAccessModifier(field.getFieldAccessModifier()))
                    .append(field.getFieldName()).append(" ")
                    .append(jUtils.solveType(field.getFieldType())).append(NL);
        }

        code.append(NL);

        code.append(".method public <init>()V").append(NL)
                .append(TAB).append("aload_0").append(NL)
                .append(TAB).append("invokespecial ").append(classUnit.getSuperClass()).append("/<init>()V").append(NL)
                .append(TAB).append("return").append(NL)
                .append(".end method").append(NL);

        classUnit.getMethods().removeIf(Method::isConstructMethod);

        for (var method : classUnit.getMethods()) {
            jUtils.resetStackLimits();
            currMethod = method;
            code.append(generators.apply(method));
        }

        return code.toString();
    }


    private String generateMethod(Method method) {
        return generateMethodHead(method) + generateMethodBody(method);
    }

    private String generateMethodHead(Method method) {
        var code = new StringBuilder(NL + ".method ");

        code.append(jUtils.solveAccessModifier(method.getMethodAccessModifier()));

        if (method.isStaticMethod()) code.append("static ");
        if (method.isFinalMethod()) code.append("final ");

        code.append(method.getMethodName());

        code.append("(");
        for (var param : method.getParams())
            code.append(jUtils.solveType(param.getType()));
        code.append(")");

        code.append(jUtils.solveType(method.getReturnType())).append(NL);

        return code.toString();
    }

    private String generateMethodBody(Method method) {
        StringBuilder code = new StringBuilder();
        StringBuilder limits = new StringBuilder();

        for (var inst : method.getInstructions()) {

            method.getLabels().entrySet().stream()
                    .filter(label -> label.getValue().equals(inst))
                    .findFirst()
                    .ifPresent(label -> code.append(label.getKey()).append(":").append(NL));

            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);

            if(inst.getInstType() == CALL){
                if(((CallInstruction) inst).getReturnType().getTypeOfElement() != ElementType.VOID){
                    jUtils.updateStackLimits(-1);
                    code.append("\tpop").append(NL);
                }
            }
        }

        code.append(".end method").append(NL);

        // Add limits
        limits.append(TAB).append(".limit stack ").append(jUtils.stackLimit).append(NL);
        limits.append(TAB).append(".limit locals ").append(getLocalsNum()).append(NL);

        return limits.append(code).toString();
    }

    private String generateArrayOperand(ArrayOperand arrayOperand) {
        StringBuilder code = new StringBuilder();

        code.append("aload").append(jUtils.generateVarNumber(arrayOperand.getName())).append(NL);
        jUtils.updateStackLimits(1);

        code.append(jUtils.generateLoad(arrayOperand.getIndexOperands().get(0)));
        jUtils.updateStackLimits(-1);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return jUtils.generateLoad(singleOp.getSingleOperand());
    }

    private String generateGoto(GotoInstruction gotoInst) {
        return "goto " + gotoInst.getLabel() + NL;
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // Get the destination and source of the assignment
        var lhs = assign.getDest();
        var rhs = assign.getRhs();

        if (lhs instanceof ArrayOperand array) {
            jUtils.updateStackLimits(1);
            code.append("aload")
                    .append(jUtils.generateVarNumber(array.getName())).append(NL)
                    .append(jUtils.generateLoad(array.getIndexOperands().get(0)));
        }

        code.append(generators.apply(rhs));
        code.append(jUtils.generateStore((Operand) lhs));

        return code.toString();
    }

    private String dealWithUnaryOp(UnaryOpInstruction inst) {
        StringBuilder code = new StringBuilder();

        code.append(jUtils.generateLoad(inst.getOperand()));
        code.append(jUtils.solveBinOp(inst.getOperation())).append(" ");

        if (inst.getOperation().getOpType().equals(OperationType.NOTB))
            code.append(jUtils.pushComparisonResultToStack());

        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putField) {
        Operand fstOperand = (Operand) putField.getChildren().get(0);
        Operand sndOperand = (Operand) putField.getChildren().get(1);
        Element trdOperand = (Element) putField.getChildren().get(2);
        String opType = jUtils.solveType(sndOperand.getType());

        String code = jUtils.generateLoad(fstOperand) +
                jUtils.generateLoad(trdOperand) +
                "putfield " + jUtils.convertClassName(fstOperand.getName()) + "/" +
                sndOperand.getName() + " " + opType + NL;

        jUtils.updateStackLimits(-2);

        return code;
    }

    private String generateGetField(GetFieldInstruction getField) {
        Operand fstOperand = (Operand) getField.getChildren().get(0);
        Operand sndOperand = (Operand) getField.getChildren().get(1);
        String opType = jUtils.solveType(sndOperand.getType());

        return jUtils.generateLoad(fstOperand) + "getfield " +
                jUtils.convertClassName(fstOperand.getName()) + "/" +
                sndOperand.getName() + " " + opType + NL;
    }

    private String generateSingleOpCond(SingleOpCondInstruction singleOpCond) {
        StringBuilder code = new StringBuilder();

        SingleOpInstruction inst = singleOpCond.getCondition();

        if (Objects.requireNonNull(inst.getInstType()) == InstructionType.NOPER) {
            code.append(generators.apply(inst));
            code.append("ifne ");
        } else {
            reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, "Error: Instruction Type not Recognized" + NL));
        }

        code.append(singleOpCond.getLabel()).append(NL);

        return code.toString();
    }

    private String generateLiteral(LiteralElement literal) {
        StringBuilder code = new StringBuilder();
        int parsedInt = Integer.parseInt(literal.getLiteral());

        code.append("ldc ");
        code.append(parsedInt == -1 ? "m1" : parsedInt);
        code.append(NL);

        jUtils.updateStackLimits(1);

        return code.toString();
    }

    private String generateOperand(Operand operand) {
        StringBuilder code = new StringBuilder();
        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                code.append("iload").append(jUtils.generateVarNumber(operand.getName()));
                jUtils.updateStackLimits(1);
            }
            case OBJECTREF, STRING ->{
                code.append("aload").append(jUtils.generateVarNumber(operand.getName()));
                jUtils.updateStackLimits(1);
            }
            case ARRAYREF -> {
                if (operand.getName().equals("array"))
                    code.append("newarray int");
                else code.append("aload")
                        .append(jUtils.generateVarNumber(operand.getName()));

                jUtils.updateStackLimits(1);
            }
            case THIS -> {
                code.append("aload_0");
                jUtils.updateStackLimits(1);
            }
            default ->
                    reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, "Type not recognized " + operand.getType().getTypeOfElement() + NL));
        }
        code.append(NL);
        return code.toString();
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        StringBuilder code = new StringBuilder();

        code.append(jUtils.generateLoad(binaryOp.getLeftOperand()));                // load left operand
        code.append(jUtils.generateLoad(binaryOp.getRightOperand())).append(" ");   // load right operand
        code.append(jUtils.solveBinOp(binaryOp.getOperation())).append(NL);         // make operation

        OperationType opType = binaryOp.getOperation().getOpType();

        if (opType == LTH || opType == GTE)
            code.append(jUtils.pushComparisonResultToStack());

        jUtils.updateStackLimits(-1);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        StringBuilder code = new StringBuilder();

        if (returnInst.hasReturnValue()) {
            code.append(generators.apply(returnInst.getOperand()));
            ElementType et = returnInst.getOperand().getType().getTypeOfElement();
            code.append((et == ElementType.BOOLEAN || et == ElementType.INT32) ? "i" : "a");
        }

        return code + "return" + NL;
    }

    private String generateCall(CallInstruction callInst) {
        StringBuilder code = new StringBuilder();

        int updateValue = 0;

        switch (callInst.getInvocationType()) {
            case invokespecial -> updateValue = jUtils.generateInvSpecial(callInst, code);
            case invokestatic -> updateValue = jUtils.generateInvStatic(callInst, code);
            case invokevirtual -> updateValue = jUtils.generateInvVirtual(callInst, code);
            case arraylength -> code.append(jUtils.generateArrayLength(callInst));
            case NEW -> updateValue = jUtils.generateNew(callInst, code);
            case ldc -> code.append(jUtils.generateLdc(callInst));
            default ->
                    reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, "Error: Invocation Type not Recognized" + NL));
        }

        jUtils.updateStackLimits(-updateValue);

        return code.toString();
    }

    private String generateOpCond(OpCondInstruction opCond) {
        StringBuilder code = new StringBuilder();

        Instruction inst = opCond.getCondition();
        String command = "not implemented";
        int stackUpdateValue = 0;

        switch (inst.getInstType()) {
            case BINARYOPER -> {
                BinaryOpInstruction binInst = (BinaryOpInstruction) inst;
                Element left = ((BinaryOpInstruction) inst).getLeftOperand();
                Element right = ((BinaryOpInstruction) inst).getRightOperand();

                switch (binInst.getOperation().getOpType()) {
                    case LTH, GTE -> {
                        OperationType opType = binInst.getOperation().getOpType();
                        String ifNoZero = opType.equals(LTH) ? "if_icmplt " : "if_icmpge ";
                        String ifRightLiteral = opType.equals(LTH) ? "iflt " : "ifge ";
                        String ifLeftLiteral = opType.equals(LTH) ? "ifgt " : "ifle ";

                        Integer lit_value = null;
                        if (right.isLiteral()) lit_value = Integer.parseInt(((LiteralElement) right).getLiteral());
                        else if (left.isLiteral()) lit_value = Integer.parseInt(((LiteralElement) left).getLiteral());

                        boolean isZero = lit_value != null && lit_value == 0;
                        command = !isZero ? ifNoZero : right.isLiteral() ? ifRightLiteral : ifLeftLiteral;
                        stackUpdateValue = !isZero ? 2 : 1;

                        if (isZero) {
                            code.append(jUtils.generateLoad(right.isLiteral() ? left : right));
                        } else {
                            code.append(generators.apply(left));
                            code.append(generators.apply(right));
                        }
                    }
                }
            }
            default ->
                    reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, "Error: Instruction Type not Recognized" + NL));
        }

        code.append(command).append(opCond.getLabel()).append(NL);
        jUtils.updateStackLimits(stackUpdateValue);

        return code.toString();
    }

}
