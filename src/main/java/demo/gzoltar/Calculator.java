package demo.gzoltar;

/**
 * Simple Calculator class with intentional bugs for TDP demonstration.
 * This class has multiple methods that can fail independently.
 */
public class Calculator {
    
    /**
     * Basic addition - this method is correct
     */
    public int add(int a, int b) {
        return a + b;
    }
    
    /**
     * Basic subtraction - this method is correct  
     */
    public int subtract(int a, int b) {
        return a - b;
    }
    
    /**
     * Multiplication with a bug - returns wrong result for negative numbers
     */
    public int multiply(int a, int b) {
        // BUG: Wrong handling of negative numbers
        if (a < 0 || b < 0) {
            return Math.abs(a * b); // Should not take absolute value!
        }
        return a * b;
    }
    
    /**
     * Division with multiple bugs
     */
    public int divide(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("Division by zero");
        }
        
        // BUG: Wrong result for negative dividends
        if (a < 0) {
            return -(Math.abs(a) / b); // This introduces errors!
        }
        
        return a / b;
    }
    
    /**
     * Power calculation with a bug
     */
    public int power(int base, int exponent) {
        if (exponent == 0) {
            return 1;
        }
        
        // BUG: Wrong handling of negative exponents
        if (exponent < 0) {
            return 0; // Should handle negative exponents properly!
        }
        
        int result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= base;
        }
        return result;
    }
    
    /**
     * Factorial calculation - this method is correct
     */
    public int factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Factorial of negative number");
        }
        if (n == 0 || n == 1) {
            return 1;
        }
        
        int result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }
    
    /**
     * Check if number is prime - has a subtle bug
     */
    public boolean isPrime(int n) {
        if (n <= 1) {
            return false;
        }
        
        // BUG: Missing check for n == 2
        if (n == 2) {
            return false; // Should return true!
        }
        
        if (n % 2 == 0) {
            return false;
        }
        
        for (int i = 3; i * i <= n; i += 2) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }
}