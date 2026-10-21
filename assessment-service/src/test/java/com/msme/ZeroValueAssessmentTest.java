package com.msme;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.msme.calculator.AssessmentCalculator;
import com.msme.enums.*;
import static org.junit.jupiter.api.Assertions.*;
class ZeroValueAssessmentTest {
 private final AssessmentCalculator engine=new AssessmentCalculator();
 private AssessmentCalculator.Snapshot s(long id,String revenue,String total,String overdue) {
  return new AssessmentCalculator.Snapshot(id,1L,LocalDate.of(2026,(int)id+6,1),new BigDecimal(revenue),new BigDecimal(total),new BigDecimal(overdue),BigDecimal.ZERO,new BigDecimal("100"));
 }
 private com.msme.calculator.AssessmentCalculationResult calculate(String a,String b,String c,String total,String overdue){
  return engine.calculate(List.of(s(1,a,"100","0"),s(2,b,"100","0"),s(3,c,total,overdue)),new AssessmentCalculator.Thresholds(new BigDecimal("15"),new BigDecimal("30"),new BigDecimal("70")));
 }
 @Test void zeroReceivablesMeansNoOverdueExposure(){
  var result=calculate("100","100","100","0","0");assertEquals(Severity.HEALTHY,result.severity());
  var overdue=result.indicators().get(1);assertEquals(0,overdue.calculatedValue().signum());assertFalse(overdue.breached());
 }
 @Test void zeroRevenueOverridesSeverityEvenWithOnlyOneBreach(){
  var result=calculate("100","100","0","0","0");assertEquals(Severity.HIGH_PRIORITY,result.severity());assertEquals(1,result.breachedIndicatorCount());assertEquals(0,new BigDecimal("100").compareTo(result.indicators().getFirst().calculatedValue()));
 }
 @Test void continuedZeroRevenueHasNoInventedPercentage(){
  var result=calculate("100","0","0","0","0");assertEquals(Severity.HIGH_PRIORITY,result.severity());assertNull(result.indicators().getFirst().calculatedValue());assertTrue(result.indicators().getFirst().breached());
 }
 @Test void revenueResumingIsNotDeterioration(){
  var result=calculate("100","0","50","100","0");assertEquals(Severity.HEALTHY,result.severity());assertNull(result.indicators().getFirst().calculatedValue());assertFalse(result.indicators().getFirst().breached());
 }
 @Test void overdueAboveTotalIsRejected(){assertThrows(IllegalArgumentException.class,()->calculate("100","100","100","0","1"));}
}
