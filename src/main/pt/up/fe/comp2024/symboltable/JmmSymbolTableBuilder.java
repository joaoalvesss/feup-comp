package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {

        var imports = getImportDecl(root);

        var classDecl = root.getChildren(CLASS_DECL).get(0);
        assert classDecl != null;
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");
        String superClass = classDecl.hasAttribute("superName") ? classDecl.get("superName") : "";

        var fields = getFields(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        return new JmmSymbolTable(imports, className, superClass, fields, methods, returnTypes, params, locals);
    }

    private static List<String> getImportDecl(JmmNode root){
        List<String> imports = new ArrayList<>();

        for(JmmNode child : root.getChildren(IMPORT_DECL)){
            String packageName = child.get("packageName");
            String trimmedInput = packageName.substring(1, packageName.length() - 1);
            String[] packages = trimmedInput.split(",\\s*");
            imports.add(packages[packages.length - 1]);
        }

        return imports;
    }

    private static List<Symbol> getFields(JmmNode root){
        List<Symbol> fields = new ArrayList<>();
        for(JmmNode child : root.getChildren(VAR_DECL)){
            fields.add(new Symbol(new Type(child.getJmmChild(0).get("name"), Boolean.parseBoolean(child.getJmmChild(0).get("isArray"))  || Boolean.parseBoolean(child.getJmmChild(0).get("isVarArgs"))), child.get("name")));
        }
        return fields;
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        for(JmmNode child : classDecl.getChildren(METHOD_DECL)){
            JmmNode retType = child.getChildren(TYPE).get(0);
            Type type = new Type(retType.get("name"), Boolean.parseBoolean(retType.get("isArray")) || Boolean.parseBoolean(retType.get("isVarArgs")));
            map.put(child.get("name"), type);
        }

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).forEach(method -> map.put(method.get("name"), getMethodParams(method)));

        return map;
    }

    private static List<Symbol> getMethodParams(JmmNode method){
        List<Symbol> params  = new ArrayList<>();

        for(JmmNode param : method.getChildren(PARAM)){
            JmmNode varType = param.getJmmChild(0);
            Type type = new Type(varType.get("name"), Boolean.parseBoolean(varType.get("isArray")) || Boolean.parseBoolean(varType.get("isVarArgs")));
            params.add( new Symbol(type, param.get("name")));
        }

        return params;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();
        classDecl.getChildren(METHOD_DECL).forEach(method -> map.put(method.get("name"), getLocalsList(method)));

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {
        return classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        //var intType = new Type(TypeUtils.getIntTypeName(), false);

        return methodDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> new Symbol(new Type(varDecl.getChild(0).get("name"), Boolean.parseBoolean(varDecl.getChild(0).get("isArray"))  || Boolean.parseBoolean(varDecl.getChild(0).get("isVarArgs"))), varDecl.get("name")))
                .toList();
    }

}
