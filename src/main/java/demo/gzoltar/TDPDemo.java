package demo.gzoltar;

import java.nio.file.*;
import java.util.*;
import demo.gzoltar.TDPDataStructures.*;

/**
 * Complete TDP demonstration
 * This shows how all components work together
 */
public class TDPDemo {
    
    public static void main(String[] args) throws Exception {
        System.out.println("🎯 TDP ALGORITHM DEMONSTRATION");
        System.out.println("====================================================================");
        System.out.println("This demo shows the complete Test, Diagnose, and Plan workflow.");
        System.out.println("Make sure you have run GZoltar on the Calculator tests first!\n");
        
        // Step 1: Check if GZoltar data exists
        Path gzoltarDir = Paths.get(".gzoltar/sfl/txt");
        if (!Files.exists(gzoltarDir)) {
            System.out.println("❌ GZoltar data not found at: " + gzoltarDir.toAbsolutePath());
            System.out.println("Please run the following steps first:");
            System.out.println("1. Compile: mvn compile test-compile");
            System.out.println("2. Run GZoltar with VS Code extension or command line");
            System.out.println("3. Ensure failed tests exist in the test suite");
            return;
        }
        
        // Step 2: Verify GZoltar files
        if (!Files.exists(gzoltarDir.resolve("spectra.csv")) ||
            !Files.exists(gzoltarDir.resolve("tests.csv")) ||
            !Files.exists(gzoltarDir.resolve("matrix.txt"))) {
            
            System.out.println("❌ Missing GZoltar output files in: " + gzoltarDir.toAbsolutePath());
            System.out.println("Expected files: spectra.csv, tests.csv, matrix.txt");
            return;
        }
        
        // Step 3: Run traditional GZoltar analysis first (for comparison)
        System.out.println("📊 Step 1: Traditional GZoltar Analysis");
        System.out.println("-----------------------------------------------");
        runTraditionalGZoltarAnalysis(gzoltarDir);
        
        // Step 4: Run TDP algorithm
        System.out.println("\n🚀 Step 2: TDP Algorithm");
        System.out.println("-----------------------------------------------");
        runTDPAlgorithm(gzoltarDir);
        
        System.out.println("\n🎉 Demo completed!");
    }
    
    /**
     * Run your existing EmbeddedSFL analysis for comparison
     */
    private static void runTraditionalGZoltarAnalysis(Path gzoltarDir) throws Exception {
        System.out.println("Running traditional Barinel analysis...");
        
        // Use your existing EmbeddedSFL code to show traditional rankings
        String[] args = {gzoltarDir.toString()};
        EmbeddedSFL.main(args);
        
        System.out.println("\n💡 Note: Traditional approach gives you ranked suspicious elements,");
        System.out.println("   but you still need to manually debug to find the actual bugs.");
    }
    
    /**
     * Run the complete TDP algorithm
     */
    private static void runTDPAlgorithm(Path gzoltarDir) throws Exception {
        System.out.println("Running TDP algorithm with human-in-the-loop test planning...");
        
        TDPAlgorithm tdp = new TDPAlgorithm();
        Diagnosis result = tdp.runTDP(gzoltarDir);
        
        if (result != null) {
            System.out.println("\n🎯 TDP SUCCESS!");
            System.out.println("Traditional debugging: You get ranked suspicious lines");
            System.out.println("TDP debugging: You get specific faulty components with high confidence");
            
            // Show comparison
            System.out.println("\n📊 COMPARISON:");
            System.out.println("Traditional approach:");
            System.out.println("  ❓ 'Line X has suspiciousness 0.85' - but is it really the bug?");
            System.out.println("  ❓ 'You need to manually check multiple suspicious lines'");
            System.out.println("  ❓ 'Uncertainty about which combination of components is faulty'");
            
            System.out.println("\nTDP approach:");
            System.out.printf("  ✅ 'These specific components are faulty with %.1f%% confidence'%n", 
                            result.getProbability() * 100);
            System.out.println("  ✅ 'Additional tests systematically narrowed down the possibilities'");
            System.out.println("  ✅ 'High confidence diagnosis delivered to developer'");
            
        } else {
            System.out.println("\n❌ TDP did not converge to a single diagnosis");
            System.out.println("This could happen if:");
            System.out.println("  • Not enough distinguishing tests available");
            System.out.println("  • Test traces are not precise enough"); 
            System.out.println("  • Multiple independent bugs exist");
        }
    }
    
    /**
     * Helper method to create a simple test scenario for demonstration
     * This creates some mock data if GZoltar data is not available
     */
    public static void createMockScenario() {
        System.out.println("🔧 Creating mock scenario for demonstration...");
        
        // Create mock diagnoses to show TDP concepts
        Set<String> diagnosis1 = new HashSet<>(Arrays.asList("Calculator#multiply(int,int)"));
        Set<String> diagnosis2 = new HashSet<>(Arrays.asList("Calculator#divide(int,int)"));  
        Set<String> diagnosis3 = new HashSet<>(Arrays.asList("Calculator#multiply(int,int)", "Calculator#isPrime(int)"));
        
        List<Diagnosis> mockDiagnoses = Arrays.asList(
            new Diagnosis(diagnosis1, 0.6),
            new Diagnosis(diagnosis2, 0.3), 
            new Diagnosis(diagnosis3, 0.1)
        );
        
        // Show entropy calculation
        EntropyTestPlanner planner = new EntropyTestPlanner();
        double entropy = planner.computeEntropy(mockDiagnoses);
        
        System.out.println("Mock diagnosis set:");
        for (Diagnosis d : mockDiagnoses) {
            System.out.println("  " + d);
        }
        System.out.printf("Entropy: %.4f (higher = more uncertainty)%n", entropy);
        
        // Show how test planning would work
        List<AvailableTest> mockTests = EntropyTestPlanner.createAvailableTestsForCalculator();
        System.out.println("\nAvailable tests for planning:");
        for (AvailableTest test : mockTests.subList(0, Math.min(5, mockTests.size()))) {
            System.out.println("  " + test.getTestName());
        }
        
        System.out.println("\n💡 In real TDP, these tests would be evaluated for information gain");
        System.out.println("   and the most informative test would be selected for execution.");
    }
}