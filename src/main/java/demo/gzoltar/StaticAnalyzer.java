package demo.gzoltar;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.tools.*;
import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Static analyzer to estimate test traces without execution
 * This analyzes the code to predict which components a test might call
 */
public class StaticAnalyzer {
    
    private Map<String, Set<String>> methodCallGraph;
    private Map<String, Set<String>> testTraceEstimates;
    
    public StaticAnalyzer() {
        this.methodCallGraph = new HashMap<>();
        this.testTraceEstimates = new HashMap<>();
    }
    
    /**
     * Analyze source files to build call graph and estimate test traces
     * @param sourceDir Directory containing source files
     * @param testDir Directory containing test files  
     */
    public void analyzeProject(Path sourceDir, Path testDir) throws IOException {
        System.out.println("🔍 Starting static analysis...");
        
        // Step 1: Build call graph from source files
        buildCallGraph(sourceDir);
        
        // Step 2: Analyze test methods to estimate traces
        analyzeTestMethods(testDir);
        
        System.out.printf("✅ Analyzed %d methods, estimated traces for %d tests%n", 
            methodCallGraph.size(), testTraceEstimates.size());
    }
    
    /**
     * Build a call graph by analyzing source files
     */
    private void buildCallGraph(Path sourceDir) throws IOException {
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(this::analyzeSourceFile);
    }
    
    /**
     * Analyze a single source file to extract method calls
     */
    private void analyzeSourceFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            
            // Visit all method declarations
            cu.accept(new VoidVisitorAdapter<Void>() {
                private String currentClass = "";
                private String currentMethod = "";
                
                @Override
                public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                    currentClass = n.getFullyQualifiedName().orElse(n.getNameAsString());
                    super.visit(n, arg);
                }
                
                @Override
                public void visit(MethodDeclaration n, Void arg) {
                    currentMethod = formatMethodSignature(currentClass, n);
                    methodCallGraph.putIfAbsent(currentMethod, new HashSet<>());
                    super.visit(n, arg);
                }
                
                @Override
                public void visit(MethodCallExpr n, Void arg) {
                    if (!currentMethod.isEmpty()) {
                        // Try to resolve the called method
                        String calledMethod = resolveMethodCall(n, currentClass);
                        if (calledMethod != null) {
                            methodCallGraph.get(currentMethod).add(calledMethod);
                        }
                    }
                    super.visit(n, arg);
                }
            }, null);
            
        } catch (Exception e) {
            System.err.println("Failed to parse " + file + ": " + e.getMessage());
        }
    }
    
    /**
     * Format method signature for consistency with GZoltar format
     */
    private String formatMethodSignature(String className, MethodDeclaration method) {
        StringBuilder sig = new StringBuilder(className);
        sig.append("#").append(method.getNameAsString()).append("(");
        
        // Add parameter types
        List<String> paramTypes = method.getParameters().stream()
            .map(p -> p.getType().asString())
            .collect(Collectors.toList());
        sig.append(String.join(",", paramTypes));
        sig.append(")");
        
        return sig.toString();
    }
    
    /**
     * Try to resolve a method call to its fully qualified signature
     */
    private String resolveMethodCall(MethodCallExpr call, String currentClass) {
        // Simplified resolution - in practice would need symbol table
        String methodName = call.getNameAsString();
        
        // If it's a local call (no scope), assume same class
        if (!call.getScope().isPresent()) {
            return currentClass + "#" + methodName + "(...)"; // Simplified
        }
        
        // Try to extract class from scope
        Expression scope = call.getScope().get();
        if (scope.isNameExpr()) {
            // Could be a variable - need type inference
            // For demo, assume it's Calculator if variable name suggests it
            String varName = scope.asNameExpr().getNameAsString();
            if (varName.toLowerCase().contains("calc")) {
                return "demo.gzoltar.Calculator#" + methodName + "(...)";
            }
        }
        
        return null; // Could not resolve
    }
    
    /**
     * Analyze test methods to estimate their traces
     */
    private void analyzeTestMethods(Path testDir) throws IOException {
        Files.walk(testDir)
            .filter(p -> p.toString().endsWith("Test.java"))
            .forEach(this::analyzeTestFile);
    }
    
    /**
     * Analyze a test file to estimate traces for each test method
     */
    private void analyzeTestFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodDeclaration n, Void arg) {
                    // Check if it's a test method (has @Test annotation)
                    if (n.getAnnotationByName("Test").isPresent()) {
                        String testName = n.getNameAsString();
                        Set<String> estimatedTrace = estimateTestTrace(n);
                        testTraceEstimates.put(testName, estimatedTrace);
                    }
                    super.visit(n, arg);
                }
            }, null);
            
        } catch (Exception e) {
            System.err.println("Failed to parse test file " + file + ": " + e.getMessage());
        }
    }
    
    /**
     * Estimate which components a test method will call
     */
    private Set<String> estimateTestTrace(MethodDeclaration testMethod) {
        Set<String> trace = new HashSet<>();
        
        // Find all method calls in the test
        testMethod.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                String methodName = n.getNameAsString();
                
                // Look for Calculator method calls
                if (isCalculatorMethod(methodName)) {
                    String component = "demo.gzoltar.Calculator#" + methodName + "(int,int)";
                    trace.add(component);
                    
                    // Add transitive calls if known
                    if (methodCallGraph.containsKey(component)) {
                        trace.addAll(methodCallGraph.get(component));
                    }
                }
                super.visit(n, arg);
            }
        }, null);
        
        return trace;
    }
    
    /**
     * Check if a method name belongs to Calculator
     */
    private boolean isCalculatorMethod(String methodName) {
        return Arrays.asList("add", "subtract", "multiply", "divide", 
                           "power", "factorial", "isPrime")
                .contains(methodName);
    }
    
    /**
     * Get estimated trace for a test
     */
    public Set<String> getEstimatedTrace(String testName) {
        return testTraceEstimates.getOrDefault(testName, new HashSet<>());
    }
    
    /**
     * Get all estimated test traces
     */
    public Map<String, Set<String>> getAllEstimatedTraces() {
        return new HashMap<>(testTraceEstimates);
    }
    
    /**
     * Create available tests based on static analysis
     */
    public List<TDPDataStructures.AvailableTest> createAvailableTests() {
        List<TDPDataStructures.AvailableTest> availableTests = new ArrayList<>();
        
        // For each test method we found, create an AvailableTest
        for (Map.Entry<String, Set<String>> entry : testTraceEstimates.entrySet()) {
            String testName = entry.getKey();
            Set<String> trace = entry.getValue();
            
            if (!trace.isEmpty()) {
                availableTests.add(new TDPDataStructures.AvailableTest(testName, trace));
            }
        }
        
        // Also add some synthetic tests for methods not covered
        addSyntheticTests(availableTests);
        
        return availableTests;
    }
    
    /**
     * Add synthetic tests for additional coverage
     */
    private void addSyntheticTests(List<TDPDataStructures.AvailableTest> tests) {
        // Edge case tests not in the original test suite
        tests.add(new TDPDataStructures.AvailableTest("testAddLargeNumbers",
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#add(int,int)"))));
            
        tests.add(new TDPDataStructures.AvailableTest("testMultiplyByOne", 
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#multiply(int,int)"))));
            
        tests.add(new TDPDataStructures.AvailableTest("testDivideNegativeByPositive",
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#divide(int,int)"))));
            
        tests.add(new TDPDataStructures.AvailableTest("testPowerOfZero",
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#power(int,int)"))));
            
        tests.add(new TDPDataStructures.AvailableTest("testIsPrimeLargePrime",
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#isPrime(int)"))));
    }
    
    /**
     * Simplified static analysis using reflection (alternative approach)
     */
    public static class ReflectionAnalyzer {
        
        /**
         * Use reflection to analyze test class and estimate traces
         */
        public static Map<String, Set<String>> analyzeTestClass(Class<?> testClass) {
            Map<String, Set<String>> traces = new HashMap<>();
            
            for (Method method : testClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(org.junit.Test.class)) {
                    String testName = method.getName();
                    Set<String> estimatedTrace = estimateTraceFromTestName(testName);
                    traces.put(testName, estimatedTrace);
                }
            }
            
            return traces;
        }
        
        /**
         * Heuristic: estimate trace from test name
         */
        private static Set<String> estimateTraceFromTestName(String testName) {
            Set<String> trace = new HashSet<>();
            String basePath = "demo.gzoltar.Calculator#";
            
            // Simple heuristic based on test name
            if (testName.toLowerCase().contains("add")) {
                trace.add(basePath + "add(int,int)");
            }
            if (testName.toLowerCase().contains("subtract")) {
                trace.add(basePath + "subtract(int,int)");
            }
            if (testName.toLowerCase().contains("multiply")) {
                trace.add(basePath + "multiply(int,int)");
            }
            if (testName.toLowerCase().contains("divide")) {
                trace.add(basePath + "divide(int,int)");
            }
            if (testName.toLowerCase().contains("power")) {
                trace.add(basePath + "power(int,int)");
            }
            if (testName.toLowerCase().contains("factorial")) {
                trace.add(basePath + "factorial(int)");
            }
            if (testName.toLowerCase().contains("prime")) {
                trace.add(basePath + "isPrime(int)");
            }
            
            return trace;
        }
    }
    
    /**
     * Main method for testing
     */
    public static void main(String[] args) throws Exception {
        StaticAnalyzer analyzer = new StaticAnalyzer();
        
        // Analyze the project
        Path sourceDir = Paths.get("src/main/java");
        Path testDir = Paths.get("src/test/java");
        
        if (Files.exists(sourceDir) && Files.exists(testDir)) {
            analyzer.analyzeProject(sourceDir, testDir);
            
            // Show results
            System.out.println("\n📊 Static Analysis Results:");
            for (Map.Entry<String, Set<String>> entry : analyzer.getAllEstimatedTraces().entrySet()) {
                System.out.printf("Test: %s -> Estimated trace: %s%n", 
                    entry.getKey(), entry.getValue());
            }
        } else {
            System.out.println("⚠️ Source or test directory not found");
            System.out.println("Using reflection-based analysis instead...");
            
            // Fallback to reflection
            try {
                Class<?> testClass = Class.forName("demo.gzoltar.CalculatorTest");
                Map<String, Set<String>> traces = ReflectionAnalyzer.analyzeTestClass(testClass);
                System.out.println("Found " + traces.size() + " test methods");
            } catch (ClassNotFoundException e) {
                System.out.println("Test class not found");
            }
        }
    }
}