package demo.gzoltar;

import java.util.*;
import java.util.stream.Collectors;
import com.gzoltar.sfl.formulas.Barinel;
import com.gzoltar.sfl.formulas.ISFLFormula;
import demo.gzoltar.TDPDataStructures.*;

/**
 * Computes hitting sets from conflicts and assigns probabilities using Barinel scores
 * This is the core of converting GZoltar suspicious elements into TDP diagnoses
 */
public class HittingSetComputer {
    
    private final ISFLFormula barinelFormula;
    
    public HittingSetComputer() {
        this.barinelFormula = new Barinel();
    }
    
    /**
     * Main method: Convert conflicts to diagnoses with probabilities
     * @param conflicts List of failed test traces (conflicts)
     * @param elementStats Statistics for each program element from GZoltar
     * @param maxDiagnoses Maximum number of diagnoses to return (to avoid explosion)
     * @return List of diagnoses with assigned probabilities
     */
    public List<Diagnosis> computeDiagnoses(List<Conflict> conflicts, 
                                          List<EmbeddedSFL.ElemStats> elementStats,
                                          int maxDiagnoses) {
        
        if (conflicts.isEmpty()) {
            System.out.println("No conflicts found - no failed tests");
            return new ArrayList<>();
        }
        
        System.out.println("Computing diagnoses from " + conflicts.size() + " conflicts...");
        
        // Step 1: Find all minimal hitting sets
        List<Set<String>> hittingSets = computeMinimalHittingSets(conflicts, maxDiagnoses);
        System.out.println("Found " + hittingSets.size() + " hitting sets");
        
        // Step 2: Convert to diagnoses
        List<Diagnosis> diagnoses = hittingSets.stream()
            .map(Diagnosis::new)
            .collect(Collectors.toList());
        
        // Step 3: Assign probabilities using Barinel scores
        assignProbabilities(diagnoses, elementStats);
        
        // Step 4: Sort by probability (highest first)
        diagnoses.sort((d1, d2) -> Double.compare(d2.getProbability(), d1.getProbability()));
        
        return diagnoses;
    }
    
    /**
     * Compute minimal hitting sets using a simplified greedy approach
     * In practice, this is computationally hard, so we use heuristics
     */
    private List<Set<String>> computeMinimalHittingSets(List<Conflict> conflicts, int maxResults) {
        List<Set<String>> allHittingSets = new ArrayList<>();
        
        // Get all unique components mentioned in conflicts
        Set<String> allComponents = conflicts.stream()
            .flatMap(c -> c.getComponents().stream())
            .collect(Collectors.toSet());
        
        System.out.println("Total components in conflicts: " + allComponents.size());
        
        // Start with single-component diagnoses
        for (String component : allComponents) {
            Set<String> singleComponentSet = Collections.singleton(component);
            if (isHittingSet(singleComponentSet, conflicts)) {
                allHittingSets.add(singleComponentSet);
                System.out.println("Found single-component diagnosis: " + component);
            }
        }
        
        // If no single-component hitting sets, try pairs
        if (allHittingSets.isEmpty()) {
            List<String> componentList = new ArrayList<>(allComponents);
            
            outerLoop:
            for (int i = 0; i < componentList.size() && allHittingSets.size() < maxResults; i++) {
                for (int j = i + 1; j < componentList.size() && allHittingSets.size() < maxResults; j++) {
                    Set<String> pair = new HashSet<>();
                    pair.add(componentList.get(i));
                    pair.add(componentList.get(j));
                    
                    if (isHittingSet(pair, conflicts)) {
                        allHittingSets.add(pair);
                        System.out.println("Found two-component diagnosis: " + pair);
                        
                        // Limit the number of pairs to avoid explosion
                        if (allHittingSets.size() >= 10) break outerLoop;
                    }
                }
            }
        }
        
        // If still no hitting sets, try larger combinations (up to size 3)
        if (allHittingSets.isEmpty()) {
            List<String> componentList = new ArrayList<>(allComponents);
            
            for (int i = 0; i < componentList.size() && allHittingSets.size() < 5; i++) {
                for (int j = i + 1; j < componentList.size() && allHittingSets.size() < 5; j++) {
                    for (int k = j + 1; k < componentList.size() && allHittingSets.size() < 5; k++) {
                        Set<String> triple = new HashSet<>();
                        triple.add(componentList.get(i));
                        triple.add(componentList.get(j));
                        triple.add(componentList.get(k));
                        
                        if (isHittingSet(triple, conflicts)) {
                            allHittingSets.add(triple);
                            System.out.println("Found three-component diagnosis: " + triple);
                        }
                    }
                }
            }
        }
        
        // Fallback: if no hitting sets found, create one with all components
        if (allHittingSets.isEmpty()) {
            System.out.println("No minimal hitting sets found, using all components as fallback");
            allHittingSets.add(allComponents);
        }
        
        return allHittingSets;
    }
    
    /**
     * Check if a set of components is a hitting set for the given conflicts
     * A hitting set must contain at least one component from each conflict
     */
    private boolean isHittingSet(Set<String> components, List<Conflict> conflicts) {
        for (Conflict conflict : conflicts) {
            boolean hitsConflict = false;
            for (String component : components) {
                if (conflict.getComponents().contains(component)) {
                    hitsConflict = true;
                    break;
                }
            }
            if (!hitsConflict) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Assign probabilities to diagnoses based on Barinel scores of their components
     */
    private void assignProbabilities(List<Diagnosis> diagnoses, List<EmbeddedSFL.ElemStats> elementStats) {
        // Create a map from component ID to its Barinel score
        Map<String, Double> barinelScores = new HashMap<>();
        
        for (EmbeddedSFL.ElemStats stat : elementStats) {
            double score = barinelFormula.compute(stat.np, stat.nf, stat.ep, stat.ef);
            barinelScores.put(stat.id, score);
        }
        
        // Assign probability to each diagnosis based on its components' scores
        for (Diagnosis diagnosis : diagnoses) {
            double diagnosisScore = computeDiagnosisScore(diagnosis, barinelScores);
            diagnosis.setProbability(diagnosisScore);
        }
        
        // Normalize probabilities so they sum to 1
        double totalScore = diagnoses.stream().mapToDouble(Diagnosis::getProbability).sum();
        if (totalScore > 0) {
            for (Diagnosis diagnosis : diagnoses) {
                diagnosis.setProbability(diagnosis.getProbability() / totalScore);
            }
        } else {
            // If all scores are 0, assign uniform probabilities
            double uniformProb = 1.0 / diagnoses.size();
            for (Diagnosis diagnosis : diagnoses) {
                diagnosis.setProbability(uniformProb);
            }
        }
        
        System.out.println("Assigned probabilities to " + diagnoses.size() + " diagnoses");
    }
    
    /**
     * Compute a score for a diagnosis based on its components' Barinel scores
     * This is a simplified approach - more sophisticated methods exist
     */
    private double computeDiagnosisScore(Diagnosis diagnosis, Map<String, Double> barinelScores) {
        double score = 0.0;
        int componentCount = 0;
        
        for (String component : diagnosis.getComponents()) {
            Double componentScore = barinelScores.get(component);
            if (componentScore != null) {
                score += componentScore;
                componentCount++;
            }
        }
        
        // Average score, with penalty for larger diagnoses (Occam's razor)
        if (componentCount > 0) {
            double avgScore = score / componentCount;
            double sizePenalty = Math.pow(0.8, diagnosis.size() - 1); // Prefer smaller diagnoses
            return avgScore * sizePenalty;
        } else {
            return 0.0;
        }
    }
    
    /**
     * Extract conflicts from failed tests and coverage information
     */
    public static List<Conflict> extractConflicts(List<EmbeddedSFL.TestCase> testCases,
                                                boolean[][] coverage,
                                                List<String> elements) {
        List<Conflict> conflicts = new ArrayList<>();
        
        for (int i = 0; i < testCases.size(); i++) {
            EmbeddedSFL.TestCase testCase = testCases.get(i);
            if (testCase.failed) {
                Set<String> conflictComponents = new HashSet<>();
                
                // Add all components covered by this failed test
                for (int j = 0; j < elements.size() && j < coverage[i].length; j++) {
                    if (coverage[i][j]) {
                        conflictComponents.add(elements.get(j));
                    }
                }
                
                if (!conflictComponents.isEmpty()) {
                    conflicts.add(new Conflict(conflictComponents, testCase.name));
                }
            }
        }
        
        System.out.println("Extracted " + conflicts.size() + " conflicts from failed tests");
        return conflicts;
    }
}