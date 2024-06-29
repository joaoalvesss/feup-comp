package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.OllirUtils;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(IMPORT_DECL, this::visitImportDecl);
        addVisit(FUNCTION_CALL_EXPR, this::visitFunctionInvk);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(IF_STMT, this::visitIfStmt);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitIfStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Generate label names
        String ifLabel = "if" + OptUtils.getLabel();
        String elseLabel = "else" + OptUtils.getLabel();
        String endIfLabel = "endif" + OptUtils.getLabel();

        // Visit the condition
        var conditionResult = exprVisitor.visit(node.getJmmChild(0));
        code.append(conditionResult.getComputation());

        // Generate if condition code
        String conditionVar = conditionResult.getCode();
        code.append("if (").append(conditionVar).append(") goto ").append(ifLabel).append(";\n");

        // Generate else body code
        code.append("goto ").append(elseLabel).append(";\n");
        code.append(ifLabel).append(":\n");
        code.append(visit(node.getJmmChild(1))); // If body
        code.append("goto ").append(endIfLabel).append(";\n");

        // Generate if body code
        code.append(elseLabel).append(":\n");
        if (node.getNumChildren() == 3) {
            code.append(visit(node.getJmmChild(2))); // Else body
        }
        code.append(endIfLabel).append(":\n");

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        OllirExprResult condition = exprVisitor.visit(node.getChild(0));
        StringBuilder ifgoto = new StringBuilder();
        ifgoto.append(condition.getComputation());
        String whileBody = "whilebody_" + OptUtils.getLabel();
        String endWhile = "endwhile_" + OptUtils.getLabel();
        ifgoto.append("if (" + condition.getCode() + ") goto ").append(whileBody);
        ifgoto.append(END_STMT);

        code.append(ifgoto).append("goto ").append(endWhile).append(END_STMT);;
        code.append(whileBody).append(":").append("\n");

        code.append(this.visit(node.getChild(1)));

        code.append(ifgoto);
        code.append(endWhile).append(":").append("\n");

        return code.toString();
    }

    private String visitFunctionInvk(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        String invoke = "invokevirtual(";
        //List<String> imports = table.getImports();
        TypeUtils.getExprType(node.getChild(0), table);
        Type a = TypeUtils.getExprType(node.getChild(0), table);
        String s = TypeUtils.getExprType(node.getChild(0), table).getName();
        if (node.getChild(0).get("name").equals(TypeUtils.getExprType(node.getChild(0), table).getName())) {
            invoke = "invokestatic(";
        }

        Type resType = TypeUtils.getExprType(node, table);
        boolean isExtended = false;
        boolean isImported = false;
        if (resType != null) {
            isExtended = table.getSuper().equals(resType.getClass().getName());
            isImported = table.getImports().contains(resType.getClass().getName());
        }
        if(isImported) {
            for(String i : table.getImports()) {
                if(i.equals(resType.getClass().getName())){
                    code.append(invoke).append(node.getChildren().get(0).get("name")).append(".").append(i).append(", \"").append(node.get("func")).append("\"");
                }
            }
        }
        else if(isExtended) {
            code.append(invoke).append(node.getChildren().get(0).get("name")).append(".").append(table.getSuper()).append(", \"").append(node.get("func")).append("\"");
        }
        else {
            String varType = resType == null ? "" : OptUtils.toOllirType(TypeUtils.getExprType(node.getChild(0), table));
            code.append(invoke).append(node.getChildren().get(0).get("name")).append(varType).append(", \"").append(node.get("func")).append("\"");
        }

        if(OptUtils.methodHasVarArgs(node, table)){
            int param_size = table.getParameters(node.get("func")).size();
            code = code.append(", ").append(OptUtils.getTemp("__varargs_array_", false)).append(".array.i32");
            for (int i = 1; i < param_size; i++) {
                if (node.getChildren().get(i).hasAttribute("name")) { // CHECKAR SE ISTO ESTA CERTO
                    var name = node.getChildren().get(i).get("name");
                    JmmNode param = node.getChildren().get(i);
                    if (param.getKind().equals("VarRefExpr"))
                        name = exprVisitor.isFunctionParam(node.getChildren().get(i)) + param.get("name");
                    else name = param.get("value"); // Node ElementExpr does not contain attribute 'value'
                    var type = OptUtils.toOllirType(TypeUtils.getExprType(node.getJmmChild(i), table));
                    //var type = OptUtils.toOllirType(table.getReturnType(node.getChildren().get(i).get("name")));
                    code.append(", ").append(name).append(type);
                } else if (node.getChildren().get(i).hasAttribute("value")) {
                    var value = node.getChildren().get(i).get("value");
                    var type = OptUtils.toOllirType(TypeUtils.getExprType(node.getJmmChild(i), table));
                    //var type = OptUtils.toOllirType(table.getReturnType(node.getChildren().get(i).get("name")));
                    code.append(", ").append(value).append(type);
                } else if (node.getChildren().get(i).hasAttribute("field")) {
                    StringBuilder computation = new StringBuilder(); //temp1.i32 :=.i32 arraylength(a.array.i32).i32.i32;
                    String temp = OptUtils.getTemp(false) + ".i32";
                    computation.append(temp).append(" :=.i32 ");
                    if (node.getChildren().get(i).get("field").equals("length")) {
                        computation.append("arraylength(").append(node.getChildren().get(i).getChild(0).get("name")).append(".array.i32).i32.i32").append(END_STMT);
                    }
                    code.append(", ").append(temp);
                    code = computation.append(code);
                } else if (node.getChildren().get(i).getKind().equals("AccessExpr")) {
                    OllirExprResult position = exprVisitor.visit(node.getChild(i).getChild(1));

                    StringBuilder computation = new StringBuilder();
                    computation.append(position.getComputation());
                    String temp = OptUtils.getTemp(false) + ".i32";
                    computation.append(temp).append(" :=.i32 ").append(node.getChildren().get(i).getChild(0).get("name")).append("[").append(position.getCode()).append("].i32").append(END_STMT);

                    code.append(", ").append(temp);
                    code = computation.append(code);

                } else if (node.getChild(i).getKind().equals("FunctionCallExpr")) {
                    OllirExprResult funcCode = exprVisitor.visit(node.getChild(i));

                    StringBuilder computation = new StringBuilder();
                    computation.append(funcCode.getComputation());
                /*String type = OptUtils.toOllirType(TypeUtils.getExprType(node.getChild(i), table));
                String temp = OptUtils.getTemp(false) + type;
                computation.append(temp).append(" :=").append(type).append(" ").append(node.getChildren().get(i).getChild(0).get("name")).append("[").append(position.getCode()).append("].i32").append(END_STMT);
*/
                    code.append(", ").append(funcCode.getCode());
                    code = computation.append(code);
                }

                else if (node.getChild(i).getKind().equals("ElementExpr")) {
                    OllirExprResult funcCode = exprVisitor.visit(node.getChild(i));

                    code.append(", ").append(funcCode.getCode());
                    code = new StringBuilder(funcCode.getComputation() + code);

                }
            }
        }
        else {
            for (int i = 1; i < node.getChildren().size(); i++) {
                if (node.getChildren().get(i).hasAttribute("name")) { // CHECKAR SE ISTO ESTA CERTO
                    var name = node.getChildren().get(i).get("name");
                    JmmNode param = node.getChildren().get(i);
                    if (param.getKind().equals("VarRefExpr"))
                        name = exprVisitor.isFunctionParam(node.getChildren().get(i)) + param.get("name");
                    else name = param.get("value"); // Node ElementExpr does not contain attribute 'value'
                    var type = OptUtils.toOllirType(TypeUtils.getExprType(node.getJmmChild(i), table));
                    //var type = OptUtils.toOllirType(table.getReturnType(node.getChildren().get(i).get("name")));
                    code.append(", ").append(name).append(type);
                } else if (node.getChildren().get(i).hasAttribute("value")) {
                    var value = node.getChildren().get(i).get("value");
                    var type = OptUtils.toOllirType(TypeUtils.getExprType(node.getJmmChild(i), table));
                    //var type = OptUtils.toOllirType(table.getReturnType(node.getChildren().get(i).get("name")));
                    code.append(", ").append(value).append(type);
                } else if (node.getChildren().get(i).hasAttribute("field")) {
                    StringBuilder computation = new StringBuilder(); //temp1.i32 :=.i32 arraylength(a.array.i32).i32.i32;
                    String temp = OptUtils.getTemp(false) + ".i32";
                    computation.append(temp).append(" :=.i32 ");
                    if (node.getChildren().get(i).get("field").equals("length")) {
                        computation.append("arraylength(").append(node.getChildren().get(i).getChild(0).get("name")).append(".array.i32).i32.i32").append(END_STMT);
                    }
                    code.append(", ").append(temp);
                    code = computation.append(code);
                } else if (node.getChildren().get(i).getKind().equals("AccessExpr")) {
                    OllirExprResult position = exprVisitor.visit(node.getChild(i).getChild(1));

                    StringBuilder computation = new StringBuilder();
                    computation.append(position.getComputation());
                    String temp = OptUtils.getTemp(false) + ".i32";
                    computation.append(temp).append(" :=.i32 ").append(node.getChildren().get(i).getChild(0).get("name")).append("[").append(position.getCode()).append("].i32").append(END_STMT);

                    code.append(", ").append(temp);
                    code = computation.append(code);

                } else if (node.getChild(i).getKind().equals("FunctionCallExpr")) {
                    OllirExprResult funcCode = exprVisitor.visit(node.getChild(i));

                    StringBuilder computation = new StringBuilder();
                    computation.append(funcCode.getComputation());
                /*String type = OptUtils.toOllirType(TypeUtils.getExprType(node.getChild(i), table));
                String temp = OptUtils.getTemp(false) + type;
                computation.append(temp).append(" :=").append(type).append(" ").append(node.getChildren().get(i).getChild(0).get("name")).append("[").append(position.getCode()).append("].i32").append(END_STMT);
*/
                    code.append(", ").append(funcCode.getCode());
                    code = computation.append(code);
                }

                else if (node.getChild(i).getKind().equals("ElementExpr")) {
                    OllirExprResult funcCode = exprVisitor.visit(node.getChild(i));

                    code.append(", ").append(funcCode.getCode());
                    code = new StringBuilder(funcCode.getComputation() + code);

                }
            }
        }

        Type ftype = TypeUtils.getExprType(node, table);

        code.append(")").append(OptUtils.toOllirType(ftype));
        code.append(END_STMT);

        return code.toString();
        //invokestatic(io, "println", a.i32).V;
    }
    private String visitImportDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        code.append("import ");
        code.append(node.get("ID"));
        code.append(END_STMT);

        return code.toString();
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        boolean isField = false;
        String fieldName = node.getChild(0).getKind().equals("FieldAccess") ?
                node.getChild(0).get("field") : "";//node.getChild(0).get("name"); // H ERRO DO TIPO not contain 'name' atribute NESTA LINHA
        for (Symbol field : table.getFields()) {
            String fieldNameTable = field.getName();
            if (fieldName.equals(fieldNameTable)) isField = true;
        }

        if (isField) {
            var lhs = exprVisitor.visit(node.getJmmChild(1));
            code.append(lhs.getComputation());

            code.append("putfield(this, ");
            code.append(fieldName).append(OptUtils.toOllirType(TypeUtils.getExprType(node.getChild(1), table)));
            code.append(", ");
            code.append(lhs.getCode());
            code.append(").V;\n");

        } //putfield(this, intField.i32, 1.i32).V;
        else {

            if (node.getChild(0).hasAttribute("name")) fieldName = node.getChild(0).get("name");
            else if (node.getChild(0).hasAttribute("value")) fieldName = node.getChild(0).get("value");

            if (node.getChild(0).getKind().equals("AccessExpr")) {
                OllirExprResult position = exprVisitor.visit(node.getChild(0).getChild(1));
                code.append(position.getComputation());
                fieldName = node.getChild(0).getChild(0).get("name") + "[" + position.getCode() + "]";
            }

            var lhs = exprVisitor.visit(node.getJmmChild(1));
            //var rhs = exprVisitor.visit(node.getJmmChild(1));


            // code to compute the children
            code.append(lhs.getComputation());
            //code.append(rhs.getComputation());

            // code to compute self
            // statement has type of lhs
            Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
            String typeString = OptUtils.toOllirType(thisType);

            code.append(fieldName);

            var typeCode = typeString;
            //var typeCode = OptUtils.toOllirType(node);
            code.append(typeCode);

            code.append(SPACE);

            code.append(ASSIGN);
            code.append(typeString);
            code.append(SPACE);

            code.append(lhs.getCode());
            //code.append(rhs.getCode());

            code.append(END_STMT);
        }

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        return id + typeCode;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }
        if (node.get("isStatic").equals("true")) {
            code.append("static ");
        }

        // name
        var name = node.get("name");
        code.append(name);

        // param
        var paramCode = "";
        List<String> params = new ArrayList<>();
        for (var param : node.getChildren(PARAM)) {
            params.add(visit(param));
        }
        paramCode = String.join(", ", params);
        code.append("(").append(paramCode).append(")");

        // type
        var retType = OptUtils.toOllirType(node.getJmmChild(0));
        code.append(retType);
        code.append(L_BRACKET);


        // rest of its children stmts
        var afterParam = node.getChildren(PARAM).size() + 1;
        for (int i = afterParam; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            if (!child.isInstance(VAR_DECL)) {
                var childCode = visit(child);
                code.append(childCode);
                //code.append(END_STMT);
            }
        }

        if (retType.equals(".V")) {
            code.append("ret.V ;\n");
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }

    private String visitVarDecl(JmmNode node, Void unused) {

        return node.get("name") + OptUtils.toOllirType(node.getJmmChild(0));
    }
    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());

        if (!table.getSuper().equals("")){
            code.append(" extends ").append(table.getSuper());
        } /*else {
            code.append(" extends Object");
        }*/

        code.append(L_BRACKET);

        code.append(NL);
        var needNl = true;


        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }
            else if (VAR_DECL.check(child)) {
                code.append("\n.field public ");
                result += ';';
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        for (var child : node.getChildren()) {
            code.append(visit(child));
        }

        return code.toString();
    }
}
