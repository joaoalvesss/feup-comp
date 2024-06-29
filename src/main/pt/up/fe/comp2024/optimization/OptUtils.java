package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.Instruction;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2024.ast.Kind.TYPE;

public class OptUtils {
    private static int tempNumber = -1;

    private static int labelCounter = 0;

    public static String getLabel() {
        return String.valueOf(labelCounter++);
    }

    public static String getTemp(boolean special) {

        return getTemp("tmp", special);
    }

    public static String getTemp(String prefix, boolean special) {

        return prefix + getNextTempNum(special);
    }

    public static int getNextTempNum(boolean special) {
        if (!special) {
            tempNumber += 1;
        }
        return tempNumber;
    }

    public static String toOllirType(JmmNode typeNode) {

        TYPE.checkOrThrow(typeNode);

        String typeName = typeNode.get("name");
        if (typeNode.get("isArray").equals("true") || typeNode.get("isVarArgs").equals("true")) {
            typeName += "[]";
        }

        return toOllirType(typeName);
    }

    public static String toOllirType(Type type) {
        if (type == null) return ".V";
        if (type.isArray()) return ".array" + toOllirType(type.getName());
        return toOllirType(type.getName());
    }

    private static String toOllirType(String typeName) {

        return "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "int[]" -> "array.i32";
            case "void" -> "V";
            case "String" -> "String";
            case "String[]" -> "array.String";
            default -> typeName;
        };
    }

    public static boolean methodHasVarArgs(JmmNode node, SymbolTable table){
        String methodName = node.get("func");
        int numberOfParams = node.getChildren().size() - 1;
        for(String m : table.getMethods()){
            if(m.equals(methodName)){
                if(table.getParameters(m).size() <= numberOfParams){
                    List<Symbol> methodParams = table.getParameters(m);
                    if (methodParams.size() == 0) return false;
                    return methodParams.get(methodParams.size()-1).getType().isArray();
                }
            }
        }
        return false;
    }

}
