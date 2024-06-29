package pt.up.fe.comp2024.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImportedClass{

    public String className;
    public List<String> methods;
    public List<Symbol> fields;
    public Map<String, Type> returnTypes;
    public Map<String, List<Symbol>> params;

    public ImportedClass(String className){
        this.className = className;
        this.methods = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.returnTypes = new HashMap<>();
        this.params = new HashMap<>();
    }

    public String getClassName() {
        return className;
    }

    public List<String> getMethods() {
        return methods;
    }

    public List<Symbol> getFields() {
        return fields;
    }

    public Map<String, Type> getReturnTypes() {
        return returnTypes;
    }

    public Map<String, List<Symbol>> getParams() {
        return params;
    }


}
