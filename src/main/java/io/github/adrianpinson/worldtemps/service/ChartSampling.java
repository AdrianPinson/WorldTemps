package io.github.adrianpinson.worldtemps.service;

import java.util.*;
import io.github.adrianpinson.worldtemps.service.TemperatureAnalysis.Point;

/** Ordered min/max envelope: retains endpoints and both average-value extremes per bucket. */
public final class ChartSampling {
    private ChartSampling() { }
    public static List<Point> sample(List<Point> points, int budget) {
        if (budget < 4) throw new IllegalArgumentException("Chart point budget must be at least four.");
        if (points.size() <= budget) return List.copyOf(points);
        int buckets = (budget - 2) / 2, interior = points.size() - 2;
        SortedSet<Integer> selected = new TreeSet<>(); selected.add(0); selected.add(points.size()-1);
        for (int bucket=0; bucket<buckets; bucket++) {
            int first = 1 + (int)((long) bucket * interior / buckets);
            int end = 1 + (int)((long)(bucket+1) * interior / buckets);
            int min = first, max = first;
            for (int i=first+1; i<end; i++) {
                if (points.get(i).average() < points.get(min).average()) min=i;
                if (points.get(i).average() > points.get(max).average()) max=i;
            }
            selected.add(min); selected.add(max);
        }
        return selected.stream().map(points::get).toList();
    }
}
