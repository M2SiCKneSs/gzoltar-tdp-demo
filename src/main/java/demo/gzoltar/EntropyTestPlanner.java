package demo.gzoltar;

import java.util.*;
import java.util.stream.Collectors;
import demo.gzoltar.TDPDataStructures.*;

/** 
 * Implements entropy-based test planning for TDP
 * Selects tests that maximize information gain to reduce diagnosis uncertainty
 */
public class EntropyTestPlanner {
    
    /**
     * Select the best test to run next based on information gain
     * @param availableTests Tests that could be executed
     * @param currentDiagnoses Current set of diagnoses with probabilities
     * @return The test that maximizes expected information gain
     */
    public AvailableTest selectBestTest(List<AvailableTest> availableTests, 
                                      List<Diagnosis> currentDiagnoses) {
        
        if (availableTests.isEmpty()) {
            System.out.println("No available tests to select from");
            return null;
        }
        
        if (currentDiagnoses.isEmpty() || currentDiagnoses.size() == 1) {
            System.out.println("No need to select test - diagnosis already determined");
            return null;
        }
        
        System.out.println("Evaluating " + availableTests.size() + " available tests...");
        
        double currentEntropy = computeEntropy(currentDiagnoses);
        System.out.printf("Current entropy: %.4f%n", currentEntropy);
        
        AvailableTest bestTest = null;
        double maxInfoGain = -1.0;
        
        for (AvailableTest test : availableTests) {
            double infoGain = computeInformationGain(test, currentDiagnoses, currentEntropy);
            
            System.out.printf("Test %s: Info Gain = %.4f%n", test.getTestName(), infoGain);
            
            if (infoGain > maxInfoGain) {
                maxInfoGain = infoGain;
                bestTest = test;
            }
        }
        
        if (bestTest != null) {
            System.out.printf("Selected test: %s (Info Gain: %.4f)%n", 
                            bestTest.getTestName(), maxInfoGain);
        }
        
        return bestTest;
    }
    
    /**
     * Compute entropy of current diagnosis set: -∑ p(Δᵢ) × log(p(Δᵢ))
     */
    public double computeEntropy(List<Diagnosis> diagnoses) {
        double entropy = 0.0;
        
        for (Diagnosis d : diagnoses) {
            if (d.getProbability() > 0) {
                entropy -= d.getProbability() * Math.log(d.getProbability());
            }
        }
        
        return entropy;
    }
    
    /**
     * Compute information gain for a specific test
     * InfoGain(t) = Entropy(Ω) - [p(t,Ω) × Entropy(Ω⁺(t)) + (1-p(t,Ω)) × Entropy(Ω⁻(t))]
     */
    public double computeInformationGain(AvailableTest test, 
                                       List<Diagnosis> currentDiagnoses, 
                                       double currentEntropy) {
        
        // Estimate probability that test will pass
        double pPass = estimateTestPassProbability(test, currentDiagnoses);
        double pFail = 1.0 - pPass;
        
        // Compute expected diagnosis sets if test passes or fails
        List<Diagnosis> diagnosesIfPass = updateDiagnosesForTestOutcome(currentDiagnoses, test, true);
        List<Diagnosis> diagnosesIfFail = updateDiagnosesForTestOutcome(currentDiagnoses, test, false);
        
        // Compute entropies
        double entropyIfPass = computeEntropy(diagnosesIfPass);
        double entropyIfFail = computeEntropy(diagnosesIfFail);
        
        // Information gain = current entropy - expected entropy after test
        double expectedEntropy = pPass * entropyIfPass + pFail * entropyIfFail;
        double infoGain = currentEntropy - expectedEntropy;
        
        return Math.max(0, infoGain); // Ensure non-negative
    }
    
    /**
     * Estimate probability that a test will pass given current diagnoses
     * This uses the "goodness" concept from the TDP paper
     */
    private double estimateTestPassProbability(AvailableTest test, List<Diagnosis> diagnoses) {
        // For each diagnosis, estimate if this test would pass if that diagnosis is true
        double weightedPassProb = 0.0;
        
        for (Diagnosis diagnosis : diagnoses) {
            // If test trace intersects with faulty components, more likely to fail
            Set<String> intersection = new HashSet<>(test.getEstimatedTrace());
            intersection.retainAll(diagnosis.getComponents());
            
            double passGivenDiagnosis;
            if (intersection.isEmpty()) {
                // Test doesn't cover any faulty components - likely to pass
                passGivenDiagnosis = 0.9;
            } else {
                // Test covers faulty components - likely to fail
                // More faulty components covered = higher failure probability
                double faultyCoverage = (double) intersection.size() / diagnosis.getComponents().size();
                passGivenDiagnosis = Math.max(0.1, 0.8 - faultyCoverage);
            }
            
            weightedPassProb += diagnosis.getProbability() * passGivenDiagnosis;
        }
        
        return Math.max(0.1, Math.min(0.9, weightedPassProb));
    }
    
    /**
     * Update diagnosis probabilities based on a hypothetical test outcome
     * This is used for information gain calculation
     */
    private List<Diagnosis> updateDiagnosesForTestOutcome(List<Diagnosis> currentDiagnoses,
                                                        AvailableTest test,
                                                        boolean testPassed) {
        List<Diagnosis> updatedDiagnoses = new ArrayList<>();
        
        for (Diagnosis diagnosis : currentDiagnoses) {
            // Calculate how likely this test outcome is given this diagnosis
            Set<String> intersection = new HashSet<>(test.getEstimatedTrace());
            intersection.retainAll(diagnosis.getComponents());
            
            double likelihoodGivenDiagnosis;
            if (testPassed) {
                // Test passed - more likely if it doesn't cover faulty components
                if (intersection.isEmpty()) {
                    likelihoodGivenDiagnosis = 0.9;
                } else {
                    double faultyCoverage = (double) intersection.size() / diagnosis.getComponents().size();
                    likelihoodGivenDiagnosis = Math.max(0.1, 0.8 - faultyCoverage);
                }
            } else {
                // Test failed - more likely if it covers faulty components
                if (intersection.isEmpty()) {
                    likelihoodGivenDiagnosis = 0.1;
                } else {
                    double faultyCoverage = (double) intersection.size() / diagnosis.getComponents().size();
                    likelihoodGivenDiagnosis = Math.min(0.9, 0.2 + faultyCoverage);
                }
            }
            
            // Update probability using Bayes' rule (simplified)
            double newProbability = diagnosis.getProbability() * likelihoodGivenDiagnosis;
            
            if (newProbability > 0.001) { // Filter out very unlikely diagnoses
                Diagnosis updatedDiagnosis = new Diagnosis(diagnosis.getComponents(), newProbability);
                updatedDiagnoses.add(updatedDiagnosis);
            }
        }
        
        // Renormalize probabilities
        double totalProb = updatedDiagnoses.stream().mapToDouble(Diagnosis::getProbability).sum();
        if (totalProb > 0) {
            for (Diagnosis d : updatedDiagnoses) {
                d.setProbability(d.getProbability() / totalProb);
            }
        }
        
        return updatedDiagnoses;
    }
    
    /**
     * Create available tests from method signatures
     * In practice, this would analyze the code or use existing test suite
     */
    public static List<AvailableTest> createAvailableTestsForCalculator() {
        List<AvailableTest> availableTests = new ArrayList<>();
        
        // Add potential tests that could be run to gather more information
        // Each test has an estimated trace (which methods it might call)
        
        availableTests.add(new AvailableTest("testAddNegative", 
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#add(int,int)"))));
            
        availableTests.add(new AvailableTest("testSubtractZero", 
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#subtract(int,int)"))));
            
        availableTests.add(new AvailableTest("testMultiplyZero", 
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#multiply(int,int)"))));
            
        availableTests.add(new AvailableTest("testDivideOne", 
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#divide(int,int)"))));
            
        availableTests.add(new AvailableTest("testPowerOne", 
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#power(int,int)"))));
            
        availableTests.add(new AvailableTest("testFactorialOne", 
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#factorial(int)"))));
            
        availableTests.add(new AvailableTest("testIsPrimeLarge", 
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#isPrime(int)"))));
            
        availableTests.add(new AvailableTest("testMultiplyNegativeZero", 
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#multiply(int,int)"))));
            
        availableTests.add(new AvailableTest("testDivideNegativeByNegative", 
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#divide(int,int)"))));
            
        availableTests.add(new AvailableTest("testPowerLargeExponent", 
            new HashSet<>(Arrays.asList("demo.gzoltar.Calculator#power(int,int)"))));
        
        return availableTests;
    }
    
    /**
     * Simulate running a test and getting its result
     * In practice, this would actually execute the test
     */
    public static TestResult simulateTestExecution(AvailableTest test) {
        Scanner scanner = new Scanner(System.in);
        System.out.printf("%n=== EXECUTE TEST: %s ===%n", test.getTestName());
        System.out.println("Estimated trace: " + test.getEstimatedTrace());
        System.out.print("Did this test PASS or FAIL? (p/f): ");
        
        String input = scanner.nextLine().trim().toLowerCase();
        boolean passed = input.startsWith("p");
        
        // For simulation, we'll use the estimated trace as actual trace
        // In real implementation, this would come from actual test execution
        return new TestResult(test.getTestName(), passed, test.getEstimatedTrace());
    }
    
    /**
     * Get comprehensive statistics about the current diagnosis state
     */
    public DiagnosisStatistics getDiagnosisStatistics(List<Diagnosis> diagnoses) {
        return new DiagnosisStatistics(diagnoses);
    }
}