package io.github.adrianpinson.worldtemps.service;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChartSamplingTest {
    TemperatureAnalysis.Point point(int i, double value) {
        var date=LocalDate.of(2000,1,1).plusDays(i); return new TemperatureAnalysis.Point(date,date,value,value,value,1);
    }
    @Test void keepsTheWholeTimeRangeAndGlobalSpikesWithinBudget() {
        var points = new ArrayList<TemperatureAnalysis.Point>();
        for (int i=0;i<10000;i++) points.add(point(i,i==5555?9999:i==7777?-9999:Math.sin(i)));
        var sampled=ChartSampling.sample(points,1000);
        assertTrue(sampled.size()<=1000); assertSame(points.get(0),sampled.get(0));
        assertSame(points.get(9999),sampled.get(sampled.size()-1));
        assertTrue(sampled.contains(points.get(5555))); assertTrue(sampled.contains(points.get(7777)));
        for(int i=1;i<sampled.size();i++) assertTrue(sampled.get(i).start().isAfter(sampled.get(i-1).start()));
        assertEquals(sampled,ChartSampling.sample(points,1000));
    }
    @Test void leavesSmallInputsAndDuplicateDatesUntouched() {
        var a=point(0,1); var b=point(0,2); var input=List.of(a,b);
        assertEquals(input,ChartSampling.sample(input,4)); assertEquals(List.of(),ChartSampling.sample(List.of(),4));
        assertThrows(IllegalArgumentException.class,()->ChartSampling.sample(input,3));
    }
    @Test void handlesFlatSeriesAndOddBudgetsWithoutLosingEndpoints() {
        var points=new ArrayList<TemperatureAnalysis.Point>();for(int i=0;i<500;i++)points.add(point(i,10));
        for(int budget:List.of(4,5,11,100)) {
            var result=ChartSampling.sample(points,budget);assertTrue(result.size()<=budget);
            assertEquals(points.get(0),result.get(0)); assertEquals(points.get(499),result.get(result.size()-1));
        }
    }
}
