package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        addVisit(FUNCTION_CALL_EXPR, this::visitInvk);
        addVisit(NEW_OBJECT_EXPR, this::visitNewObject);
        addVisit(NOT, this::visitNot);
        addVisit(NEW_VECTOR_EXPR, this::visitNewArray);
        addVisit(ELEMENT_EXPR, this::visitElementExpr);
        addVisit(ARRAY_LENGTH, this::visitArrayLength);
        addVisit(ACCESS_EXPR, this::visitAccessExpr);
        addVisit(FIELD_ACCESS, this::visitFieldAccess);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitFieldAccess(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        String code = OptUtils.getTemp(false) + ".i32";

        if (node.get("field").equals("length")) {
            computation.append(code).append(" :=.i32 ");
            computation.append("arraylength(").append(node.getChild(0).get("name")).append(".array.i32).i32.i32").append(END_STMT);
        }

        return new OllirExprResult(code, computation.toString());
    }

    private OllirExprResult visitAccessExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        String code = OptUtils.getTemp(false) + ".i32";

        OllirExprResult position = this.visit(node.getChild(1));
        computation.append(position.getComputation());

        String varType = OptUtils.toOllirType(TypeUtils.getExprType(node.getChild(0), table));
        computation.append(code).append(" :=.i32 ").append(node.getChild(0).get("name")).append(varType).append("[").append(position.getCode()).append("].i32").append(END_STMT);

        return new OllirExprResult(code, computation.toString());
    }

    private OllirExprResult visitArrayLength(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        OllirExprResult arrayResult = visit(node.getJmmChild(0));

        computation.append(arrayResult.getComputation());

        Type resType = new Type("int", false);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp(false) + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append("arraylength(").append(arrayResult.getCode()).append(")").append(resOllirType)
                .append(END_STMT);

        return new OllirExprResult(code, computation.toString());
    }

    private OllirExprResult visitElementExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        String code = "__varargs_array_" + OptUtils.getLabel() + ".array.i32";
        String temp = OptUtils.getTemp(false) + ".array.i32";
        computation.append(temp).append(" :=.array.i32 new(array, ").append(node.getChildren().size()).append(".i32).array.i32").append(END_STMT);
        computation.append(code).append(" :=.array.i32 ").append(temp).append(END_STMT);
        for (int i = 0; i < node.getChildren().size(); i++) {
            OllirExprResult code2 = this.visit(node.getChild(i));
            computation.append(code2.getComputation());
            computation.append(code).append("["+i+".i32].i32 :=.i32 ").append(code2.getCode()).append(END_STMT);
        }

        return new OllirExprResult(code, computation.toString());
    }
  /*tmp2.array.i32 :=.array.i32 new(array, 4.i32).array.i32;
    __varargs_array_0.array.i32 :=.array.i32 tmp2.array.i32;
    __varargs_array_0.array.i32[0.i32].i32 :=.i32 1.i32;
    __varargs_array_0.array.i32[1.i32].i32 :=.i32 2.i32;
    __varargs_array_0.array.i32[2.i32].i32 :=.i32 3.i32;
    __varargs_array_0.array.i32[3.i32].i32 :=.i32 4.i32;
    a.array.i32 :=.array.i32 __varargs_array_0.array.i32;*/

    private OllirExprResult visitNewArray(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        code.append("new(array");

        for (int i = 1; i < node.getChildren().size(); i++) {
            String temp = OptUtils.getTemp(false) + ".i32";
            computation.append(temp).append(SPACE).append(ASSIGN).append(".i32").append(SPACE).append(this.visit(node.getChild(i)).getCode()).append(END_STMT);
            code.append(", ").append(temp);
        }
        code.append(").array.i32");

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitNot(JmmNode node, Void unused) {
        String code = OptUtils.getTemp(false) + ".bool";
        StringBuilder computation = new StringBuilder();
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(".bool").append(SPACE).append("!.bool ");
        if (node.getChild(0).getKind().equals("BooleanLiteral"))
            computation.append(this.visitBoolean(node.getChild(0), null).getCode());
        else if (node.getChild(0).getKind().equals("BinaryExpr")) {
            OllirExprResult result = this.visitBinExpr(node.getChild(0), null);
            computation = new StringBuilder(result.getComputation() + END_STMT + computation);
            computation.append(result.getCode());
        } else if (node.getChild(0).getKind().equals("VarRefExpr")) {
            OllirExprResult result = this.visitVarRef(node.getChild(0), null);
            computation = new StringBuilder(result.getComputation() + END_STMT + computation);
            computation.append(result.getCode());
        }
        computation.append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewObject(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp(false) + resOllirType;

        // Falta checkar caso do import ou extend, dar add espaço antes do new
        boolean isExtended = table.getSuper().equals(resType.getName());
        boolean isImported = table.getImports().contains(resType.getName());
        List<String> lis = table.getImports();
        if (isImported) {
            for (String i : table.getImports()) {
                if (i.equals(resType.getName())) {
                    computation.append(code).append(" ").append(ASSIGN).append(resOllirType).append(" new(").append(resType.getName()).append(")").append(resOllirType).append(END_STMT)
                            .append("invokespecial(").append(code).append(", \"<init>\").V").append(END_STMT);
                }
            }
        } else if (isExtended) {
            computation.append(code).append(" ").append(ASSIGN).append(resOllirType).append(" new(").append(table.getSuper()).append(")").append(resOllirType).append(END_STMT)
                    .append("invokespecial(").append(code).append(", \"<init>\").V").append(END_STMT);
        } else {
            computation.append(code).append(" ").append(ASSIGN).append(resOllirType).append(" new(").append(table.getClassName()).append(")").append(resOllirType).append(END_STMT)
                    .append("invokespecial(").append(code).append(", \"<init>\").V").append(END_STMT);
        }

        return new OllirExprResult(code, computation.toString());
    }

    // tmp2.Simple :=.Simple new(Simple).Simple;
    // invokespecial(tmp2.Simple, "").V;

    // public boolean IsObjectType(List<String> imports)

    private OllirExprResult visitInvk(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();


        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String varArgCode = "";
        if(OptUtils.methodHasVarArgs(node, table)){
            varArgCode = generateVarArgs(node, computation);
        }
        String code = OptUtils.getTemp(false) + resOllirType;

        String invoke = "invokevirtual(";
        List<String> imports = table.getImports();
        if (imports.contains(TypeUtils.getExprType(node.getChild(0), table).getName())) {
            invoke = "invokestatic(";
        }

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(invoke);

        // Falta checkar caso do import ou extend
        boolean isExtended = false;
        boolean isImported = false;
        if (resType != null) {
            isExtended = table.getSuper().equals(resType.getClass().getName());
            isImported = table.getImports().contains(resType.getClass().getName());
        }
        if (isImported) {
            for (String i : table.getImports()) {
                if (i.equals(resType.getClass().getName())) {
                    computation.append(node.getChild(0).get("name")).append(".").append(i).append(", \"").append(node.get("func")).append("\"");
                }
            }
        } else if (isExtended) {
            computation.append(node.getChild(0).get("name")).append(".").append(table.getSuper()).append(", \"").append(node.get("func")).append("\"");
        } else {
            var varType = OptUtils.toOllirType(TypeUtils.getExprType(node.getChild(0), table));
            computation.append(node.getChild(0).get("name")).append(varType).append(", \"").append(node.get("func")).append("\"");
        }


        if(OptUtils.methodHasVarArgs(node, table)){
            int param_size = table.getParameters(node.get("func")).size();
            List<JmmNode> params = node.getChildren();
            for (int i = 1; i < param_size; i++) {
                Type paramType = TypeUtils.getExprType(params.get(i), table);
                String paramOllirType = OptUtils.toOllirType(paramType);

                JmmNode param = params.get(i);
                String paramName = "";
                if (param.getKind().equals("VarRefExpr")) paramName = isFunctionParam(params.get(i)) + param.get("name");
                else if (param.hasAttribute("value"))
                    paramName = param.get("value"); // Node BinaryExpr does not contain attribute 'value'
                if (param.getKind().equals("AccessExpr")) {
                    OllirExprResult access = this.visitAccessExpr(param, null);
                    paramName = access.getCode();
                    computation = new StringBuilder(access.getComputation() + computation);
                    computation.append(", ").append(paramName);
                } else if (param.getKind().equals("FunctionCallExpr")) {
                    OllirExprResult funcCode = this.visit(node.getChild(i));

                    computation = new StringBuilder(funcCode.getComputation() + computation);
                /*String type = OptUtils.toOllirType(TypeUtils.getExprType(node.getChild(i), table));
                String temp = OptUtils.getTemp(false) + type;
                computation.append(temp).append(" :=").append(type).append(" ").append(node.getChildren().get(i).getChild(0).get("name")).append("[").append(position.getCode()).append("].i32").append(END_STMT);
*/
                    computation.append(", ").append(funcCode.getCode());
                }
                else if (param.getKind().equals("ElementExpr")) {
                    OllirExprResult funcCode = this.visit(node.getChild(i));

                    computation = new StringBuilder(funcCode.getComputation() + computation);
                    computation.append(", ").append(funcCode.getCode());

                }
                else computation.append(", ").append(paramName).append(paramOllirType);
            }
            computation.append(", ").append(varArgCode);
        } else{
            List<JmmNode> params = node.getChildren();
            for (int i = 1; i < params.size(); i++) {
                Type paramType = TypeUtils.getExprType(params.get(i), table);
                String paramOllirType = OptUtils.toOllirType(paramType);

                JmmNode param = params.get(i);
                String paramName = "";
                if (param.getKind().equals("VarRefExpr")) paramName = isFunctionParam(params.get(i)) + param.get("name");
                else if (param.hasAttribute("value"))
                    paramName = param.get("value"); // Node BinaryExpr does not contain attribute 'value'
                if (param.getKind().equals("AccessExpr")) {
                    OllirExprResult access = this.visitAccessExpr(param, null);
                    paramName = access.getCode();
                    computation = new StringBuilder(access.getComputation() + computation);
                    computation.append(", ").append(paramName);
                } else if (param.getKind().equals("FunctionCallExpr")) {
                    OllirExprResult funcCode = this.visit(node.getChild(i));

                    computation = new StringBuilder(funcCode.getComputation() + computation);
                /*String type = OptUtils.toOllirType(TypeUtils.getExprType(node.getChild(i), table));
                String temp = OptUtils.getTemp(false) + type;
                computation.append(temp).append(" :=").append(type).append(" ").append(node.getChildren().get(i).getChild(0).get("name")).append("[").append(position.getCode()).append("].i32").append(END_STMT);
*/
                    computation.append(", ").append(funcCode.getCode());
                }
                else computation.append(", ").append(paramName).append(paramOllirType);
            }
        }

        computation.append(")").append(resOllirType);

        computation.append(END_STMT);
        return new OllirExprResult(code, computation.toString()); //invokevirtual(this.CompileMethodInvocation, "bar").i32;
    }

    private String generateVarArgs(JmmNode node, StringBuilder computation){
        String code = OptUtils.getTemp(false) + ".array.i32";
        int paramNr = node.getChildren().size();
        int param_size = table.getParameters(node.get("func")).size();
        computation.append(code).append(" :=.array.i32 new(array, ").append(paramNr-param_size).append(".i32).array.i32").append(END_STMT);
        String varArgsArray = OptUtils.getTemp("__varargs_array_", false) + ".array.i32";
        computation.append(varArgsArray).append(ASSIGN).append(".array.i32 ").append(code).append(END_STMT);
        for(int i = param_size; i< paramNr; i++){
            computation.append(varArgsArray).append("[").append(i-param_size).append(".i32].i32 :=.i32 ");
            computation.append(this.visit(node.getChild(i)).getCode()).append(END_STMT);
        }
        return varArgsArray;
    }

    public String isFunctionParam(JmmNode node) {
        JmmNode func = TypeUtils.findAncestorOfKind(node, "MethodDecl");
        List<JmmNode> params = func.getChildren("Param");
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i).get("name").equals(node.get("name"))) return "$" + (i + 1) + ".";
        }
        return "";
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;

        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        var boolType = new Type("boolean", false);
        String ollirIntType = OptUtils.toOllirType(boolType);
        String value = "0";
        if (node.get("value").equals("true")) value = "1";
        String code = value + ollirIntType;
        //ver se é parametro da funcao e $
        return new OllirExprResult(code);
    }

    public OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();


        // code to compute the children
        computation.append(lhs.getComputation());
        if (node.get("op").equals("&&")) {
            String trueN = "true_" + OptUtils.getLabel();
            String endN = "end_" + OptUtils.getLabel();
            String code = OptUtils.getTemp(false) + ".bool";
            computation.append("if(").append(lhs.getCode()).append(") goto ").append(trueN).append(END_STMT);
            computation.append(code).append(" :=.bool 0.bool;\ngoto ").append(endN).append(END_STMT);
            computation.append(trueN).append(":\n");
            computation.append(rhs.getComputation());
            computation.append(code).append(" :=.bool ").append(rhs.getCode()).append(END_STMT);
            computation.append(endN).append(":\n");
            return new OllirExprResult(code, computation);
        }

        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp(false) + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = TypeUtils.getExprType(node, table);
        computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        boolean isField = false;
        String fieldName = node.get("name");
        for (Symbol field : table.getFields()) {
            String fieldNameTable = field.getName();
            if (fieldName.equals(fieldNameTable)) isField = true;
        }

        if (isField) {
            StringBuilder computation = new StringBuilder();

            Type resType = TypeUtils.getExprType(node, table);
            String resOllirType = OptUtils.toOllirType(resType);
            code.append(OptUtils.getTemp(false)).append(resOllirType);

            computation.append(code);
            computation.append(" :=").append(resOllirType).append(" getfield(this, ");
            computation.append(fieldName).append(resOllirType).append(")").append(resOllirType).append(END_STMT);
            return new OllirExprResult(code.toString(), computation.toString());
        } else {
            var id = node.get("name");
            Type type = TypeUtils.getExprType(node, table);
            String ollirType = OptUtils.toOllirType(type);

            code.append(isFunctionParam(node) + id + ollirType);
        }

        return new OllirExprResult(code.toString());
    }
// tmp0.i32 :=.i32 getfield(this, intField.i32).i32;
// ret.i32 tmp0.i32;

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}