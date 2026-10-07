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
 * <p>Snapshots admitted to the packed common path use specialized handling for
 * at most 64 items and nine candidates. Positive-only snapshots use monotone closure. Negative
 * snapshots first try a driver/sink certificate and then a constructive full-set
 * pass; only unresolved cases enter the 512-state exact subset search.</p>
 *
 * <p>Snapshots outside that domain, including numerically unsafe and dense-nine
 * cases, route to scalar hardened solvers. Builder work is limited to metadata
 * preparation; solve results are produced by {@code run()}.</p>
 */
@Information(name = "Cascade Sentinel", version = 4, authors = {"Luuk"})
public final class CascadeSentinelV4Algorithm implements IAlgorithm<CascadeSentinelV4Player> {
    private static final int FAST_BITS = 9;
    private static final int FAST_STATES = 1 << FAST_BITS;
    private static final long LANE_GUARDS = 0x0800_8008_0080_0800L;

    private final CascadeSentinelV4SmallHardenedSolver smallHardened = new CascadeSentinelV4SmallHardenedSolver();
    private final CascadeSentinelV4LargeHardenedSolver largeHardened = new CascadeSentinelV4LargeHardenedSolver();
    private final int[] reachedGeneration = new int[FAST_STATES];
    private final long[] stateTotal = new long[FAST_STATES];
    private final long[] stateThreshold = new long[FAST_STATES];
    private final int[] stateWeight = new int[FAST_STATES];
    private int generation;

    @Override
    public Result run(CascadeSentinelV4Player player) {
        final int k = player.candidateCount();
        if (player.itemCount > 64 || k > FAST_BITS) return largeHardened.run(player);
        // Within <=64 items and <=9 candidates, HardenedPlayer marks snapshots
        // that require scalar handling because packed range safety fails or the dense-nine guard triggers.
        if (player instanceof CascadeSentinelV4Player.HardenedPlayer) return smallHardened.run(player);

        if (k == 0) {
            player.setPackedTotal(player.basePacked);
            return new Result(player, Collections.emptyList());
        }

        final CascadeSentinelV4Player.Data d = player.data;
        final int fullMask = (1 << k) - 1;

        // Positive-only systems are monotone: the closure is the unique maximum
        // reachable set, so no subset search is needed.
        if (!player.anyNegative()) {
            int active = 0;
            int remaining = fullMask;
            long total = player.basePacked;
            boolean progress;
            do {
                progress = false;
                if ((remaining & 256) != 0 && meetsStaticReq(total, d.packedReqAdd[8])) {
                    active |= 256; remaining ^= 256;
                    total += d.packedBonus[8] - CascadeSentinelV4Player.PACK_BIAS_5;
                    progress = true;
                }
                if ((remaining & 128) != 0 && meetsStaticReq(total, d.packedReqAdd[7])) {
                    active |= 128; remaining ^= 128;
                    total += d.packedBonus[7] - CascadeSentinelV4Player.PACK_BIAS_5;
                    progress = true;
                }
                if ((remaining & 64) != 0 && meetsStaticReq(total, d.packedReqAdd[6])) {
                    active |= 64; remaining ^= 64;
                    total += d.packedBonus[6] - CascadeSentinelV4Player.PACK_BIAS_5;
                    progress = true;
                }
                if ((remaining & 32) != 0 && meetsStaticReq(total, d.packedReqAdd[5])) {
                    active |= 32; remaining ^= 32;
                    total += d.packedBonus[5] - CascadeSentinelV4Player.PACK_BIAS_5;
                    progress = true;
                }
                if ((remaining & 16) != 0 && meetsStaticReq(total, d.packedReqAdd[4])) {
                    active |= 16; remaining ^= 16;
                    total += d.packedBonus[4] - CascadeSentinelV4Player.PACK_BIAS_5;
                    progress = true;
                }
                if ((remaining & 8) != 0 && meetsStaticReq(total, d.packedReqAdd[3])) {
                    active |= 8; remaining ^= 8;
                    total += d.packedBonus[3] - CascadeSentinelV4Player.PACK_BIAS_5;
                    progress = true;
                }
                if ((remaining & 4) != 0 && meetsStaticReq(total, d.packedReqAdd[2])) {
                    active |= 4; remaining ^= 4;
                    total += d.packedBonus[2] - CascadeSentinelV4Player.PACK_BIAS_5;
                    progress = true;
                }
                if ((remaining & 2) != 0 && meetsStaticReq(total, d.packedReqAdd[1])) {
                    active |= 2; remaining ^= 2;
                    total += d.packedBonus[1] - CascadeSentinelV4Player.PACK_BIAS_5;
                    progress = true;
                }
                if ((remaining & 1) != 0 && meetsStaticReq(total, d.packedReqAdd[0])) {
                    active |= 1; remaining ^= 1;
                    total += d.packedBonus[0] - CascadeSentinelV4Player.PACK_BIAS_5;
                    progress = true;
                }
            } while (progress && remaining != 0);
            player.setPackedTotal(total);
            if (remaining == 0) return new Result(player, Collections.emptyList());
            return result(player, active);
        }

        // The driver/sink certificate can prove full feasibility or prove that no
        // candidate can start. Inconclusive cases fall through to constructive/exact solving.
        final long negativeBase = player.basePacked;
        int r2Certificate = CascadeSentinelV4NegativeCertificate.tryFull(player, d, fullMask, negativeBase);
        if (r2Certificate == 1) return new Result(player, Collections.emptyList());
        if (r2Certificate == 2) return result(player, 0);

        // Constructive certificate. If this reaches every candidate, optimality
        // is immediate and we avoid the exact state table entirely.
        int greedyMask = 0;
        int remaining = fullMask;
        long greedyTotal = negativeBase;
        long greedyThreshold = 0L;
        boolean progress;
        do {
            progress = false;
            for (int bits = remaining; bits != 0; bits &= bits - 1) {
                int i = Integer.numberOfTrailingZeros(bits);
                if (!meetsStaticReq(greedyTotal, d.packedReqAdd[i])) continue;
                long nextTotal = greedyTotal + d.packedBonus[i] - CascadeSentinelV4Player.PACK_BIAS_5;
                if (!ge5(nextTotal, greedyThreshold)) continue;
                int bit = 1 << i;
                greedyMask |= bit;
                remaining ^= bit;
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
            player.setPackedTotal(negativeBase);
            return result(player, 0);
        }

        int greedyWeight = 0;
        for (int bits = greedyMask; bits != 0; bits &= bits - 1) {
            greedyWeight += d.weight[Integer.numberOfTrailingZeros(bits)];
        }

        // Exact subset reachability for the small unresolved case.
        int stamp = ++generation;
        if (stamp == 0) {
            java.util.Arrays.fill(reachedGeneration, 0);
            stamp = ++generation;
        }
        reachedGeneration[0] = stamp;
        stateTotal[0] = negativeBase;
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
                if (!meetsStaticReq(total, d.packedReqAdd[i])) continue;
                long nextTotal = total + d.packedBonus[i] - CascadeSentinelV4Player.PACK_BIAS_5;
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
        // Generation-stamped arrays are scratch space only; no solve result is
        // retained for reuse across calls.
        smallHardened.clearCache();
        largeHardened.clearCache();
    }

    private static boolean meetsStaticReq(long values, long reqAdd) {
        return ((values + reqAdd) & LANE_GUARDS) == LANE_GUARDS;
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

    private static Result result(CascadeSentinelV4Player player, int candidateMask) {
        long selected = player.freeOriginalMask();
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
