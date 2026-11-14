package data.scripts.patrolfleet.utilis;

import java.util.*;


public final class BalancedFleetAllocator {
    public static Map<String, String> allocate(Collection<String> availableBases,
                                                Map<String, String> previousAssignments) {
        List<String> bases = new ArrayList<>(new TreeSet<>(availableBases));
        List<String> fleets = new ArrayList<>(previousAssignments.keySet());
        Collections.sort(fleets);
        Map<String, String> result = new LinkedHashMap<>();
        if (bases.isEmpty()) {
            for (String fleet : fleets) result.put(fleet, null);
            return result;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String base : bases) counts.put(base, 0);
        for (String base : previousAssignments.values()) {
            if (counts.containsKey(base)) counts.put(base, counts.get(base) + 1);
        }
        int minimum = fleets.size() / bases.size();
        int remainder = fleets.size() % bases.size();
        // Give the extra slots to bases already holding the most fleets.
        List<String> quotaOrder = new ArrayList<>(bases);
        quotaOrder.sort(Comparator.<String>comparingInt(counts::get).reversed()
                .thenComparing(Comparator.naturalOrder()));
        Map<String, Integer> remaining = new HashMap<>();
        for (int i = 0; i < quotaOrder.size(); i++) {
            remaining.put(quotaOrder.get(i), minimum + (i < remainder ? 1 : 0));
        }
        for (String fleet : fleets) {
            String previous = previousAssignments.get(fleet);
            if (remaining.getOrDefault(previous, 0) > 0) {
                result.put(fleet, previous);
                remaining.put(previous, remaining.get(previous) - 1);
            }
        }
        for (String fleet : fleets) {
            if (result.containsKey(fleet)) continue;
            for (String base : bases) {
                if (remaining.get(base) > 0) {
                    result.put(fleet, base);
                    remaining.put(base, remaining.get(base) - 1);
                    break;
                }
            }
        }
        return result;
    }
}
