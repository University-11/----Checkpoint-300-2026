package ru.axenix.blueprints.ast;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import ru.axenix.blueprints.model.BlueprintModels.*;

import javax.tools.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Модуль 1: Промышленный парсер AST Java (OpenJDK 17 Compiler Tree API)
 * с автоматическим распознаванием конструкций обязательного (Разделы 1-4)
 * и бонусного (Слайд 8: record, enum, generics, lambdas, Stream API & Collectors) уровней.
 */
public class JavaCompilerAstParser extends TreePathScanner<Void, Void> {

    private final CompilationUnitTree unit;
    private final LineMap lineMap;
    private final SourcePositions positions;
    private final String sourceText;

    private String currentClassName = "UnknownClass";
    private String currentMethodName = null;
    private int loopDepth = 0;
    private int nestingLevel = 0;
    private int maxLoopDepthInMethod = 0;
    private int cyclomaticInMethod = 1;
    private int cognitiveInMethod = 0;
    private int nodeCounter = 0;

    public final List<MethodBlueprint> methods = new ArrayList<>();
    public final List<DefectFinding> defects = new ArrayList<>();
    public final List<BonusConstructItem> bonusConstructs = new ArrayList<>();
    public final List<CallGraphEdge> allCalls = new ArrayList<>();

    public JavaCompilerAstParser(CompilationUnitTree unit, SourcePositions positions, String sourceText) {
        this.unit = unit;
        this.lineMap = unit.getLineMap();
        this.positions = positions;
        this.sourceText = sourceText;
    }

    public static JavaCompilerAstParser parse(String fileName, String sourceCode) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = compiler.getStandardFileManager(null, Locale.US, StandardCharsets.UTF_8);
        JavaFileObject fileObj = new SimpleJavaFileObject(URI.create("string:///" + fileName), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return sourceCode;
            }
        };
        JavacTask task = (JavacTask) compiler.getTask(null, fm, null, List.of("-proc:none"), null, List.of(fileObj));
        Iterable<? extends CompilationUnitTree> units = task.parse();
        Trees trees = Trees.instance(task);
        CompilationUnitTree unit = units.iterator().next();
        JavaCompilerAstParser scanner = new JavaCompilerAstParser(unit, trees.getSourcePositions(), sourceCode);
        scanner.scan(unit, null);
        return scanner;
    }

    private int startLine(Tree t) {
        long p = positions.getStartPosition(unit, t);
        return p >= 0 ? (int) lineMap.getLineNumber(p) : -1;
    }

    private int endLine(Tree t) {
        long p = positions.getEndPosition(unit, t);
        return p >= 0 ? (int) lineMap.getLineNumber(p) : -1;
    }

    private String snippet(Tree t, int maxLen) {
        long s = positions.getStartPosition(unit, t);
        long e = positions.getEndPosition(unit, t);
        if (s < 0 || e < s || e > sourceText.length()) {
            return t.toString().replaceAll("\\s+", " ").trim();
        }
        String raw = sourceText.substring((int) s, (int) e).replaceAll("\\s+", " ").trim();
        return raw.length() > maxLen ? raw.substring(0, maxLen - 3) + "..." : raw;
    }

    @Override
    public Void visitClass(ClassTree node, Void p) {
        String prev = currentClassName;
        if (!node.getSimpleName().isEmpty()) {
            currentClassName = node.getSimpleName().toString();
        }
        Tree.Kind k = node.getKind();
        if (k == Tree.Kind.RECORD) {
            bonusConstructs.add(new BonusConstructItem("RECORD", "record " + currentClassName, startLine(node), snippet(node, 80)));
        } else if (k == Tree.Kind.ENUM) {
            bonusConstructs.add(new BonusConstructItem("ENUM", "enum " + currentClassName, startLine(node), snippet(node, 80)));
        }
        if (!node.getTypeParameters().isEmpty()) {
            bonusConstructs.add(new BonusConstructItem("GENERICS", currentClassName + "<" + node.getTypeParameters() + ">", startLine(node), snippet(node, 80)));
        }
        super.visitClass(node, p);
        currentClassName = prev;
        return null;
    }

    @Override
    public Void visitLambdaExpression(LambdaExpressionTree node, Void p) {
        bonusConstructs.add(new BonusConstructItem("LAMBDA", "Lambda (" + node.getParameters() + " -> ...)", startLine(node), snippet(node, 72)));
        return super.visitLambdaExpression(node, p);
    }

    @Override
    public Void visitMemberReference(MemberReferenceTree node, Void p) {
        bonusConstructs.add(new BonusConstructItem("METHOD_REF", node.toString(), startLine(node), snippet(node, 64)));
        return super.visitMemberReference(node, p);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
        String sel = node.getMethodSelect().toString();
        if (sel.endsWith(".stream") || sel.contains("Collectors.") || sel.endsWith(".flatMap") || sel.endsWith(".filter")) {
            bonusConstructs.add(new BonusConstructItem("STREAM_API", sel + "(...)", startLine(node), snippet(node, 72)));
        }
        if (currentMethodName != null) {
            String callee = sel.contains(".") ? sel.substring(sel.lastIndexOf('.') + 1) : sel;
            allCalls.add(new CallGraphEdge(currentMethodName, callee, startLine(node)));
        }
        return super.visitMethodInvocation(node, p);
    }

    @Override
    public Void visitMethod(MethodTree node, Void p) {
        String mName = node.getName().toString();
        if ("<init>".equals(mName)) {
            return super.visitMethod(node, p);
        }
        if (!node.getTypeParameters().isEmpty()) {
            bonusConstructs.add(new BonusConstructItem("GENERIC_METHOD", "<" + node.getTypeParameters() + "> " + mName, startLine(node), snippet(node, 72)));
        }
        currentMethodName = mName;
        super.visitMethod(node, p);
        currentMethodName = null;
        return null;
    }
}
