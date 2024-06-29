package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.*;

public class JmmSymbolTable implements SymbolTable {

    private final List<String> imports;
    private final String className;
    private final String superClass;
    private final List<Symbol> fields;
    private final List<String> methods;
    private final Map<String, Type> returnTypes;
    private final Map<String, List<Symbol>> params;
    private final Map<String, List<Symbol>> locals;
    private final Map<String, Boolean> isArrayMap = new HashMap<>();
    private final Map<String, Boolean> isVarArgsMap = new HashMap<>();

    public JmmSymbolTable(List<String> imports,
                          String className,
                          String superClass,
                          List<Symbol> fields,
                          List<String> methods,
                          Map<String, Type> returnTypes,
                          Map<String, List<Symbol>> params,
                          Map<String, List<Symbol>> locals) {
        this.imports = imports;
        this.className = className;
        this.superClass = superClass;
        this.fields = fields;
        this.methods = methods;
        this.returnTypes = returnTypes;
        this.params = params;
        this.locals = locals;

        initializeMaps();
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getSuper() {
        return superClass;
    }

    @Override
    public List<Symbol> getFields() {
        return fields;
    }

    @Override
    public List<String> getMethods() {
        return methods;
    }

    @Override
    public Type getReturnType(String methodSignature) {
        return returnTypes.get(methodSignature);
    }

    @Override
    public List<Symbol> getParameters(String methodSignature) {
        return params.get(methodSignature);
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        return locals.get(methodSignature);
    }

    private void initializeMaps() {
        for (Symbol field : fields) {
            isArrayMap.put(field.getName(), field.getType().isArray());
            isVarArgsMap.put(field.getName(), false);
        }
    }

    public boolean isArray(String id) {
        return isArrayMap.getOrDefault(id, false);
    }

    public boolean isVarArgs(String id) {
        return isVarArgsMap.getOrDefault(id, false);
    }
}
