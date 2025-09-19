package demo.gzoltar;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import demo.gzoltar.TDPDataStructures.*;

/**
 * Main TDP Algorithm Controller
 * Orchestrates the Test, Diagnose, and Plan loop using GZoltar data
 */
public class TDPAlgorithm {
    
    private final HittingSetComputer hittingSetComputer;
    private final EntropyTestPlanner testPlanner;
    
    // TDP state
    private List<String> elements;
    private List<EmbeddedSFL.TestCase> observedTests;
    private boolean[][] coverageMatrix;
    private List<EmbeddedSFL.ElemStats> elementStats;
    private List<AvailableTest> availableTests;
    
    public TDPAlgorithm() {
        this.hittingSetComputer = new HittingSetComputer();
        this.testPlanner = new EntropyTestPlanner();
    }
    
    /**
     * Main entry point: Run the complete TDP algorithm
     * @param gzoltarDataDir Directory containing GZoltar output files
     * @return Final diagnosis result
     */
    public Diagnosis runTDP(Path gzoltarDataDir) throws Exception {
        System.out.println("🚀 Starting TDP (Test, Diagnose, Plan) Algorithm");
        System.out.println("===================================================================");
        
        // Phase 1: Initialize from GZoltar data
        initializeFromGZoltarData(gzoltarDataDir);
        
        // Phase 2: Run TDP loop
        return executeTDPLoop();
    }
    
    /**
     * Initialize TDP state from GZoltar output files
     */
    private void initializeFromGZoltarData(Path gzoltarDataDir) throws Exception {
        System.out.println("📊 Phase 1: Loading GZoltar Data");
        System.out.println("------------------------------------------------");
        
        // Load GZoltar files (your existing EmbeddedSFL logic)
        Path spectra = gzoltarDataDir.resolve("spectra.csv");
        Path tests = gzoltarDataDir.resolve("tests.csv");
        Path matrix = gzoltarDataDir.resolve("matrix.txt");
        
        if (!Files.isRegularFile(spectra) || !Files.isRegularFile(tests) || !Files.isRegularFile(matrix)) {
            throw new FileNotFoundException("Expected files not found in " + gzoltarDataDir.toAbsolutePath());
        }
        
        // Load data using your existing methods
        elements = EmbeddedSFL.readNonEmptyLines(spectra);
        if (!elements.isEmpty() && elements.get(0).toLowerCase().contains("name")) {
            elements = elements.subList(1, elements.size());
        }
        
        observedTests = EmbeddedSFL.parseTests(tests);
        coverageMatrix = EmbeddedSFL.parseMatrix(matrix, observedTests.size(), elements.size());
        
        // Build element statistics
        elementStats = buildElementStatistics();
        
        // Create available tests for future execution
        availableTests = EntropyTestPlanner.createAvailableTestsForCalculator();
        
        // Print initial state
        System.out.printf("✅ Loaded %d elements, %d observed tests%n", elements.size(), observedTests.size());
        System.out.printf("✅ Found %d failed tests%n", observedTests.stream().mapToInt(t -> t.failed ? 1 : 0).sum());
        System.out.printf("✅ Created %d available tests for planning%n", availableTests.size());
        
        System.out.println("\nInitial Test Results:");
        for (EmbeddedSFL.TestCase test : observedTests) {
            System.out.printf("  %s: %s%n", test.name, test.failed ? "FAILED ❌" : "PASSED ✅");
        }
    }
    
    /**
     * Execute the main TDP loop: Diagnose → Plan → Test → Repeat
     */
    private Diagnosis executeTDPLoop() {
        System.out.println("\n🔄 Phase 2: TDP Iterative Loop");
        System.out.println("-----------------------------------------------");
        
        int iteration = 1;
        final int MAX_ITERATIONS = 10;
        
        while (iteration <= MAX_ITERATIONS) {
            System.out.printf("%n🔍 === TDP Iteration %d ===%n", iteration);
            
            // Step 1: DIAGNOSE - Generate current diagnosis set
            List<Diagnosis> diagnoses = generateCurrentDiagnoses();
            
            if (diagnoses.isEmpty()) {
                System.out.println("❌ No diagnoses generated - algorithm failed");
                return null;
            }
            
            // Display current diagnoses
            displayDiagnoses(diagnoses);
            
            // Step 2: Check stopping condition
            DiagnosisStatistics stats = testPlanner.getDiagnosisStatistics(diagnoses);
            System.out.println("\n📊 Diagnosis Statistics:");
            System.out.println("  " + stats);
            
            if (stats.isComplete()) {
                System.out.println("\n🎯 TDP COMPLETE! Final diagnosis found:");
                Diagnosis finalDiagnosis = stats.getMostLikelyDiagnosis();
                displayFinalResult(finalDiagnosis);
                return finalDiagnosis;
            }
            
            // Step 3: PLAN - Select next test
            AvailableTest nextTest = testPlanner.selectBestTest(availableTests, diagnoses);
            
            if (nextTest == null) {
                System.out.println("❌ No suitable test found for planning - ending TDP");
                break;
            }
            
            // Step 4: TEST - Execute the selected test
            TestResult testResult = executeTest(nextTest);
            
            // Step 5: Update state with new test result
            updateStateWithNewTest(testResult);
            
            iteration++;
        }
        
        // If we exit the loop without finding a single diagnosis
        List<Diagnosis> finalDiagnoses = generateCurrentDiagnoses();
        if (!finalDiagnoses.isEmpty()) {
            Diagnosis bestDiagnosis = finalDiagnoses.get(0); // Already sorted by probability
            System.out.println("\n⚠️  TDP ended without single diagnosis. Best diagnosis:");
            displayFinalResult(bestDiagnosis);
            return bestDiagnosis;
        }
        
        System.out.println("❌ TDP failed to find any diagnosis");
        return null;
    }
    
    /**
     * Generate current diagnoses from observed test failures
     */
    private List<Diagnosis> generateCurrentDiagnoses() {
        // Extract conflicts from failed tests
        List<Conflict> conflicts = HittingSetComputer.extractConflicts(
            observedTests, coverageMatrix, elements);
        
        if (conflicts.isEmpty()) {
            System.out.println("No conflicts found - no failed tests");
            return new ArrayList<>();
        }
        
        // Compute diagnoses using hitting sets
        return hittingSetComputer.computeDiagnoses(conflicts, elementStats, 20);
    }
    
    /**
     * Display current diagnoses in a readable format
     */
    private void displayDiagnoses(List<Diagnosis> diagnoses) {
        System.out.printf("%n📋 Current Diagnoses (%d total):%n", diagnoses.size());
        
        for (int i = 0; i < Math.min(diagnoses.size(), 10); i++) {
            Diagnosis d = diagnoses.get(i);
            System.out.printf("  %d. [%.3f] %s%n", 
                i + 1, d.getProbability(), formatComponents(d.getComponents()));
        }
        
        if (diagnoses.size() > 10) {
            System.out.printf("  ... and %d more diagnoses%n", diagnoses.size() - 10);
        }
    }
    
    /**
     * Execute a test (simulated - in practice would run actual test)
     */
    private TestResult executeTest(AvailableTest test) {
        System.out.printf("%n🧪 === EXECUTING TEST: %s ===%n", test.getTestName());
        System.out.println("📍 Expected to cover: " + formatComponents(test.getEstimatedTrace()));
        
        return EntropyTestPlanner.simulateTestExecution(test);
    }
    
    /**
     * Update TDP state with results from newly executed test
     */
    private void updateStateWithNewTest(TestResult testResult) {
        System.out.printf("%n🔄 Updating state with test result: %s = %s%n", 
            testResult.getTestName(), testResult.isPassed() ? "PASSED ✅" : "FAILED ❌");
        
        // Add new test to observed tests
        EmbeddedSFL.TestCase newTestCase = new EmbeddedSFL.TestCase();
        newTestCase.name = testResult.getTestName();
        newTestCase.failed = testResult.isFailed();
        observedTests.add(newTestCase);
        
        // Expand coverage matrix
        coverageMatrix = expandCoverageMatrix(testResult);
        
        // Rebuild element statistics
        elementStats = buildElementStatistics();
        
        // Remove executed test from available tests
        availableTests.removeIf(t -> t.getTestName().equals(testResult.getTestName()));
        
        System.out.printf("✅ Updated state: %d observed tests, %d available tests remaining%n",
            observedTests.size(), availableTests.size());
    }
    
    /**
     * Build element statistics for Barinel computation
     */
    private List<EmbeddedSFL.ElemStats> buildElementStatistics() {
        List<EmbeddedSFL.ElemStats> stats = new ArrayList<>();
        
        for (int j = 0; j < elements.size(); j++) {
            int ef = 0, ep = 0, nf = 0, np = 0;
            
            for (int i = 0; i < observedTests.size(); i++) {
                boolean covers = (j < coverageMatrix[i].length) && coverageMatrix[i][j];
                boolean failed = observedTests.get(i).failed;
                
                if (failed && covers) ef++;
                else if (!failed && covers) ep++;
                else if (failed && !covers) nf++;
                else np++;
            }
            
            EmbeddedSFL.ElemStats es = new EmbeddedSFL.ElemStats();
            es.id = elements.get(j);
            es.ef = ef;
            es.ep = ep;
            es.nf = nf;
            es.np = np;
            stats.add(es);
        }
        
        return stats;
    }
    
    /**
     * Expand coverage matrix to include new test result
     */
    private boolean[][] expandCoverageMatrix(TestResult newTest) {
        boolean[][] newMatrix = new boolean[observedTests.size()][elements.size()];
        
        // Copy existing coverage
        for (int i = 0; i < observedTests.size() - 1; i++) {
            System.arraycopy(coverageMatrix[i], 0, newMatrix[i], 0, 
                Math.min(coverageMatrix[i].length, elements.size()));
        }
        
        // Add new test coverage (last row)
        int newTestIndex = observedTests.size() - 1;
        for (int j = 0; j < elements.size(); j++) {
            String element = elements.get(j);
            newMatrix[newTestIndex][j] = newTest.getActualTrace().contains(element);
        }
        
        return newMatrix;
    }
    
    /**
     * Format component names for display
     */
    private String formatComponents(Set<String> components) {
        if (components.size() <= 3) {
            return components.toString();
        } else {
            List<String> sorted = components.stream().sorted().collect(Collectors.toList());
            return String.format("[%s, %s, ... +%d more]", 
                sorted.get(0), sorted.get(1), components.size() - 2);
        }
    }
    
    /**
     * Display final diagnosis result
     */
    private void displayFinalResult(Diagnosis finalDiagnosis) {
        System.out.println("🎯 FINAL DIAGNOSIS RESULT");
        System.out.println("===================================================================");
        System.out.printf("🔧 Faulty Components (Probability: %.1f%%):%n", 
            finalDiagnosis.getProbability() * 100);
        
        for (String component : finalDiagnosis.getComponents()) {
            System.out.println("  • " + component);
        }
        
        System.out.println("\n📊 Recommendation:");
        System.out.println("  Focus debugging efforts on the components listed above.");
        System.out.println("  These components are most likely responsible for the observed failures.");
    }
    
    /**
     * Convenience method to run TDP with default directory
     */
    public static void main(String[] args) throws Exception {
        Path gzoltarDir = Paths.get(args.length > 0 ? args[0] : ".gzoltar/sfl/txt");
        
        TDPAlgorithm tdp = new TDPAlgorithm();
        Diagnosis result = tdp.runTDP(gzoltarDir);
        
        if (result != null) {
            System.out.println("\n✅ TDP completed successfully!");
        } else {
            System.out.println("\n❌ TDP failed to find a diagnosis");
        }
    }
}