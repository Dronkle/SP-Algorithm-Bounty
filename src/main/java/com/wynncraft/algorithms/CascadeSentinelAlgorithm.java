package com.wynncraft.algorithms;

import com.wynncraft.core.interfaces.IAlgorithm;
import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.Information;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

/**
 * Exact adaptive skill-point solver.
 *
 * <p>Small pack-safe candidate sets use a compact subset-state engine. Larger,
 * unusual, or numerically unsafe inputs delegate to the internal hardened exact
 * search engine. A builder-precomputed dense-nine risk flag also
 * routes the rare case where all nine candidates are initially equipable but
 * the full set violates a final cascade threshold to the hardened path, because
 * that shape can saturate the packed 512-state search. The detector performs no
 * timing or search and is not a result cache.</p>
 */
@Information(name = "Cascade Sentinel", version = 1, authors = {"Luuk"})
public final class CascadeSentinelAlgorithm implements IAlgorithm<CascadeSentinelPlayer> {
    private static final int FAST_BITS = 9;
    private static final int FAST_STATES = 1 << FAST_BITS;
    private static final long LANE_GUARDS = 0x0800_8008_0080_0800L;

    private final CascadeSentinelHardenedSolver hardened = new CascadeSentinelHardenedSolver();
    private final int[] reachedGeneration = new int[FAST_STATES];
    private final long[] stateTotal = new long[FAST_STATES];
    private final long[] stateThreshold = new long[FAST_STATES];
    private final int[] stateWeight = new int[FAST_STATES];
    private int generation;

    @Override
    public Result run(CascadeSentinelPlayer player) {
        final int k = player.candidateCount;
        if (player.itemCount > 64 || !player.packedSafe || k > FAST_BITS || player.denseNineRisk) {
            return hardened.run(player);
        }

        if (k == 0) {
            player.setPackedTotal(player.basePacked);
            return new Result(player, Collections.emptyList());
        }

        final CascadeSentinelPlayer.Data d = player.data;
        final int fullMask = (1 << k) - 1;

        // Positive-only systems are monotone: the closure is the unique maximum
        // reachable set, so no subset search is needed.
        if (!player.anyNegative) {
            int active = 0;
            int remaining = fullMask;
            long total = player.basePacked;
            boolean progress;
            do {
                progress = false;
                for (int bits = remaining; bits != 0; bits &= bits - 1) {
                    int i = Integer.numberOfTrailingZeros(bits);
                    if (!ge5(total, d.packedReq[i])) continue;
                    int bit = 1 << i;
                    active |= bit;
                    remaining ^= bit;
                    total += d.packedBonus[i] - CascadeSentinelPlayer.PACK_BIAS_5;
                    progress = true;
                }
            } while (progress && remaining != 0);
            player.setPackedTotal(total);
            return result(player, active);
        }

        // Constructive certificate. If this reaches every candidate, optimality
        // is immediate and we avoid the exact state table entirely.
        int greedyMask = 0;
        int remaining = fullMask;
        int greedyWeight = 0;
        long greedyTotal = player.basePacked;
        long greedyThreshold = 0L;
        boolean progress;
        do {
            progress = false;
            for (int bits = remaining; bits != 0; bits &= bits - 1) {
                int i = Integer.numberOfTrailingZeros(bits);
                if (!ge5(greedyTotal, d.packedReq[i])) continue;
                long nextTotal = greedyTotal + d.packedBonus[i] - CascadeSentinelPlayer.PACK_BIAS_5;
                if (!ge5(nextTotal, greedyThreshold)) continue;
                int bit = 1 << i;
                greedyMask |= bit;
                remaining ^= bit;
                greedyWeight += d.weight[i];
                greedyTotal = nextTotal;
                greedyThreshold = max5(greedyThreshold, d.packedNeed[i]);
                progress = true;
            }
        } while (progress && remaining != 0);

        if (remaining == 0) {
            player.setPackedTotal(greedyTotal);
            return new Result(player, Collections.emptyList());
        }
        if (greedyMask == 0) {
            // If no candidate can be the first legal transition, no legal equip
            // sequence can start. The unconditional free items are optimal.
            player.setPackedTotal(player.basePacked);
            return result(player, 0);
        }

        // Exact subset reachability for the small unresolved case.
        int stamp = ++generation;
        if (stamp == 0) {
            java.util.Arrays.fill(reachedGeneration, 0);
            stamp = ++generation;
        }
        reachedGeneration[0] = stamp;
        stateTotal[0] = player.basePacked;
        stateThreshold[0] = 0L;
        stateWeight[0] = 0;

        int bestMask = greedyMask;
        int bestCount = Integer.bitCount(greedyMask);
        int bestWeight = greedyWeight;
        long bestTotal = greedyTotal;

        for (int mask = 0; mask <= fullMask; mask++) {
            if (reachedGeneration[mask] != stamp) continue;
            long total = stateTotal[mask];
            long threshold = stateThreshold[mask];
            int weight = stateWeight[mask];
            int absent = fullMask & ~mask;
            for (int bits = absent; bits != 0; bits &= bits - 1) {
                int i = Integer.numberOfTrailingZeros(bits);
                if (!ge5(total, d.packedReq[i])) continue;
                long nextTotal = total + d.packedBonus[i] - CascadeSentinelPlayer.PACK_BIAS_5;
                if (!ge5(nextTotal, threshold)) continue;
                int nextMask = mask | (1 << i);
                if (reachedGeneration[nextMask] == stamp) continue;

                int nextWeight = weight + d.weight[i];
                reachedGeneration[nextMask] = stamp;
                stateTotal[nextMask] = nextTotal;
                stateThreshold[nextMask] = max5(threshold, d.packedNeed[i]);
                stateWeight[nextMask] = nextWeight;

                int nextCount = Integer.bitCount(nextMask);
                if (nextCount > bestCount || (nextCount == bestCount && nextWeight > bestWeight)) {
                    bestMask = nextMask;
                    bestCount = nextCount;
                    bestWeight = nextWeight;
                    bestTotal = nextTotal;
                    if (nextMask == fullMask) {
                        player.setPackedTotal(nextTotal);
                        return new Result(player, Collections.emptyList());
                    }
                }
            }
        }

        player.setPackedTotal(bestTotal);
        return result(player, bestMask);
    }

    @Override
    public void clearCache() {
        // No cross-run result cache. Generation-stamped work arrays only avoid
        // clearing temporary memory and contain no reusable solver answer.
        hardened.clearCache();
    }

    private static boolean ge5(long values, long limits) {
        return (((values | LANE_GUARDS) - limits) & LANE_GUARDS) == LANE_GUARDS;
    }

    private static long max5(long a, long b) {
        long greater = ((a | LANE_GUARDS) - b) & LANE_GUARDS;
        long laneBits = greater >>> 11;
        long select = greater | (greater - laneBits);
        return (a & select) | (b & ~select);
    }

    private static Result result(CascadeSentinelPlayer player, int candidateMask) {
        long selected = player.freeOriginalMask;
        for (int bits = candidateMask; bits != 0; bits &= bits - 1) {
            int candidate = Integer.numberOfTrailingZeros(bits);
            selected |= 1L << player.data.originalIndex[candidate];
        }
        int count = Long.bitCount(selected);
        if (count == player.itemCount) return new Result(player, Collections.emptyList());
        if (count == 0) return new Result(Collections.emptyList(), player);
        long all = player.itemCount == 64 ? -1L : (1L << player.itemCount) - 1L;
        return new Result(
            new MaskList(player.data.items, selected, count),
            new MaskList(player.data.items, all & ~selected, player.itemCount - count)
        );
    }

    private static final class MaskList extends AbstractList<IEquipment> {
        private final IEquipment[] items;
        private final long mask;
        private final int size;
        MaskList(IEquipment[] items, long mask, int size) {
            this.items = items; this.mask = mask; this.size = size;
        }
        @Override public IEquipment get(int index) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
            long bits = mask;
            for (int i = 0; i < index; i++) bits &= bits - 1;
            return items[Long.numberOfTrailingZeros(bits)];
        }
        @Override public int size() { return size; }
    }
}
