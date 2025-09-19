package demo.gzoltar;

import org.junit.Test;
import org.junit.Assert;
import static org.junit.Assert.*;

/**
 * Test class for Calculator - includes both passing and failing tests
 * to demonstrate TDP algorithm
 */
public class CalculatorTest {
    
    private Calculator calc = new Calculator();
    
    // === PASSING TESTS ===
    
    @Test
    public void testAddPositive() {
        assertEquals(5, calc.add(2, 3));
    }
    
    @Test
    public void testAddZero() {
        assertEquals(2, calc.add(2, 0));
    }
    
    @Test
    public void testSubtractPositive() {
        assertEquals(2, calc.subtract(5, 3));
    }
    
    @Test
    public void testSubtractNegativeResult() {
        assertEquals(-2, calc.subtract(3, 5));
    }
    
    @Test
    public void testMultiplyPositive() {
        assertEquals(6, calc.multiply(2, 3));
    }
    
    @Test
    public void testDividePositive() {
        assertEquals(3, calc.divide(9, 3));
    }
    
    @Test
    public void testFactorialZero() {
        assertEquals(1, calc.factorial(0));
    }
    
    @Test
    public void testFactorialPositive() {
        assertEquals(24, calc.factorial(4));
    }
    
    @Test
    public void testPowerZero() {
        assertEquals(1, calc.power(5, 0));
    }
    
    @Test
    public void testPowerPositive() {
        assertEquals(8, calc.power(2, 3));
    }
    
    // === FAILING TESTS (due to bugs) ===
    
    @Test
    public void testMultiplyWithNegative() {
        // This will FAIL due to bug in multiply method
        assertEquals(-6, calc.multiply(-2, 3));  // Expected: -6, Got: 6
    }
    
    @Test
    public void testMultiplyBothNegative() {
        // This will FAIL due to bug in multiply method  
        assertEquals(6, calc.multiply(-2, -3));  // Expected: 6, Got: 6 (accidentally correct)
    }
    
    @Test
    public void testDivideNegativeDividend() {
        // This will FAIL due to bug in divide method
        assertEquals(-3, calc.divide(-9, 3));    // Expected: -3, Got: 3
    }
    
    @Test
    public void testPowerNegativeExponent() {
        // This will FAIL due to bug in power method
        // Note: In real math, 2^(-2) = 0.25, but we expect integer result
        // For this test, let's say we expect an exception or special handling
        try {
            int result = calc.power(2, -2);
            fail("Should handle negative exponents properly, got: " + result);
        } catch (Exception e) {
            // This is what we expect, but our buggy implementation returns 0
        }
    }
    
    @Test
    public void testIsPrimeTwo() {
        // This will FAIL due to bug in isPrime method
        assertTrue("2 should be prime", calc.isPrime(2));  // Expected: true, Got: false
    }
    
    @Test
    public void testIsPrimeThree() {
        assertTrue("3 should be prime", calc.isPrime(3));  // This should pass
    }
    
    @Test
    public void testIsPrimeFive() {
        assertTrue("5 should be prime", calc.isPrime(5));  // This should pass
    }
    
    @Test
    public void testIsNotPrimeFour() {
        assertFalse("4 should not be prime", calc.isPrime(4));  // This should pass
    }
    
    // === EXCEPTION TESTS ===
    
    @Test(expected = ArithmeticException.class)
    public void testDivideByZero() {
        calc.divide(5, 0);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testFactorialNegative() {
        calc.factorial(-1);
    }
}