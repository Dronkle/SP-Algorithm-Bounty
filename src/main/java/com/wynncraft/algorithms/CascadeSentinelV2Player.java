package com.wynncraft.algorithms;

import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.IPlayer;
import com.wynncraft.core.interfaces.IPlayerBuilder;
import com.wynncraft.enums.SkillPoint;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;

/**
 * Preprocessed player representation for Cascade Sentinel.
 *
 * Immutable equipment metadata is extracted once by the builder. Built players
 * are snapshots of the current equipment prefix; later builder appends do not
 * mutate an existing player. This is preprocessing, not a cross-run result cache.
 */
public final class CascadeSentinelV2Player extends AbstractList<IEquipment> implements IPlayer {
    static final int NO_NEED = Integer.MIN_VALUE;
    static final int PACK_BIAS = 1024;
    static final long PACK_BIAS_5 = 0x0400_4004_0040_0400L;

    final Data data;
    final int itemCount;
    final int candidateCount;
    final HardenedSnapshot hardenedSnapshot;
    final long freeOriginalMask;
    final boolean anyNegative;
    final boolean packedSafe;
    final boolean denseNineRisk;
    final long basePacked;
    final int alloc0, alloc1, alloc2, alloc3, alloc4;

    private int bonus0, bonus1, bonus2, bonus3, bonus4;
    private int weight;

    private CascadeSentinelV2Player(
            Data data,
            int itemCount,
            int candidateCount,
            HardenedSnapshot hardenedSnapshot,
            long freeOriginalMask,
            boolean anyNegative, boolean packedSafe, boolean denseNineRisk, long basePacked,
            int a0, int a1, int a2, int a3, int a4) {
        this.data = data;
        this.itemCount = itemCount;
        this.candidateCount = candidateCount;
        this.hardenedSnapshot = hardenedSnapshot;
        this.freeOriginalMask = freeOriginalMask;
        this.anyNegative = anyNegative;
        this.packedSafe = packedSafe;
        this.denseNineRisk = denseNineRisk;
        this.basePacked = basePacked;
        this.alloc0 = a0; this.alloc1 = a1; this.alloc2 = a2; this.alloc3 = a3; this.alloc4 = a4;
    }

    @Override public IEquipment get(int index) {
        if (index < 0 || index >= itemCount) throw new IndexOutOfBoundsException(index);
        return data.items[index];
    }
    @Override public int size() { return itemCount; }
    @Override public List<IEquipment> equipment() { return this; }
    @Override public int weight() { return weight; }
    @Override public int total(SkillPoint skill) {
        return switch (skill) {
            case STRENGTH -> alloc0 + bonus0;
            case DEXTERITY -> alloc1 + bonus1;
            case INTELLIGENCE -> alloc2 + bonus2;
            case DEFENCE -> alloc3 + bonus3;
            case AGILITY -> alloc4 + bonus4;
        };
    }
    @Override public int allocated(SkillPoint skill) {
        return switch (skill) {
            case STRENGTH -> alloc0;
            case DEXTERITY -> alloc1;
            case INTELLIGENCE -> alloc2;
            case DEFENCE -> alloc3;
            case AGILITY -> alloc4;
        };
    }
    @Override public void modify(int[] sp, boolean sum) {
        int sign = sum ? 1 : -1;
        bonus0 += sign * sp[0]; bonus1 += sign * sp[1]; bonus2 += sign * sp[2]; bonus3 += sign * sp[3]; bonus4 += sign * sp[4];
        weight = bonus0 + bonus1 + bonus2 + bonus3 + bonus4;
    }
    @Override public void reset() { bonus0=bonus1=bonus2=bonus3=bonus4=weight=0; }
    void setBonus(int b0, int b1, int b2, int b3, int b4) {
        bonus0=b0; bonus1=b1; bonus2=b2; bonus3=b3; bonus4=b4;
        weight=b0+b1+b2+b3+b4;
    }
    void setPackedTotal(long packedTotal) {
        bonus0 = lane(packedTotal, 0) - alloc0;
        bonus1 = lane(packedTotal, 12) - alloc1;
        bonus2 = lane(packedTotal, 24) - alloc2;
        bonus3 = lane(packedTotal, 36) - alloc3;
        bonus4 = lane(packedTotal, 48) - alloc4;
        weight = bonus0 + bonus1 + bonus2 + bonus3 + bonus4;
    }
    static long pack5(int a,int b,int c,int d,int e) {
        return (long)(a + PACK_BIAS)
            | ((long)(b + PACK_BIAS) << 12)
            | ((long)(c + PACK_BIAS) << 24)
            | ((long)(d + PACK_BIAS) << 36)
            | ((long)(e + PACK_BIAS) << 48);
    }
    static int lane(long packed, int shift) {
        return (int)((packed >>> shift) & 0xFFFL) - PACK_BIAS;
    }

    /**
     * Snapshot-local inputs used only after dispatch has selected a hardened solver.
     * Common packed snapshots leave this null; it is created by build(), never run().
     */
    static final class HardenedSnapshot {
        final int freeCount, freeScore;
        final int free0, free1, free2, free3, free4;

        HardenedSnapshot(int freeCount, int freeScore, int free0, int free1, int free2, int free3, int free4) {
            this.freeCount = freeCount;
            this.freeScore = freeScore;
            this.free0 = free0; this.free1 = free1; this.free2 = free2; this.free3 = free3; this.free4 = free4;
        }
    }

    static final class HardenedData {
        int[] r0, r1, r2, r3, r4;
        int[] b0, b1, b2, b3, b4;
        int[] p0, p1, p2, p3, p4;

        HardenedData(int capacity) {
            r0=new int[capacity];r1=new int[capacity];r2=new int[capacity];r3=new int[capacity];r4=new int[capacity];
            b0=new int[capacity];b1=new int[capacity];b2=new int[capacity];b3=new int[capacity];b4=new int[capacity];
            p0=new int[capacity];p1=new int[capacity];p2=new int[capacity];p3=new int[capacity];p4=new int[capacity];
        }

        void ensureCapacity(int n) {
            if (n <= r0.length) return;
            int c = Math.max(n, r0.length << 1);
            r0=Arrays.copyOf(r0,c);r1=Arrays.copyOf(r1,c);r2=Arrays.copyOf(r2,c);r3=Arrays.copyOf(r3,c);r4=Arrays.copyOf(r4,c);
            b0=Arrays.copyOf(b0,c);b1=Arrays.copyOf(b1,c);b2=Arrays.copyOf(b2,c);b3=Arrays.copyOf(b3,c);b4=Arrays.copyOf(b4,c);
            p0=Arrays.copyOf(p0,c);p1=Arrays.copyOf(p1,c);p2=Arrays.copyOf(p2,c);p3=Arrays.copyOf(p3,c);p4=Arrays.copyOf(p4,c);
        }

        void set(int i, int q0,int q1,int q2,int q3,int q4, int x0,int x1,int x2,int x3,int x4) {
            r0[i]=q0;r1[i]=q1;r2[i]=q2;r3[i]=q3;r4[i]=q4;
            b0[i]=x0;b1[i]=x1;b2[i]=x2;b3[i]=x3;b4[i]=x4;
            p0[i]=q0>0?q0+x0:NO_NEED;p1[i]=q1>0?q1+x1:NO_NEED;
            p2[i]=q2>0?q2+x2:NO_NEED;p3[i]=q3>0?q3+x3:NO_NEED;p4[i]=q4>0?q4+x4:NO_NEED;
        }
    }

    static final class Data {
        IEquipment[] items = new IEquipment[32];
        int[] originalIndex = new int[9];
        int[] weight = new int[9];
        long[] packedReq = new long[9], packedBonus = new long[9], packedNeed = new long[9];
        HardenedData hardened;

        void ensureItemCapacity(int n) {
            if (n <= items.length) return;
            items = Arrays.copyOf(items, Math.max(n, items.length << 1));
        }
        void ensureCandidateCapacity(int n) {
            if (n <= originalIndex.length) return;
            int c = Math.max(n, originalIndex.length << 1);
            originalIndex=Arrays.copyOf(originalIndex,c);
            weight=Arrays.copyOf(weight,c);
            packedReq=Arrays.copyOf(packedReq,c); packedBonus=Arrays.copyOf(packedBonus,c); packedNeed=Arrays.copyOf(packedNeed,c);
            if (hardened != null) hardened.ensureCapacity(c);
        }
        void ensureHardenedData(int candidateCount) {
            if (hardened != null) {
                hardened.ensureCapacity(candidateCount);
                return;
            }
            hardened = new HardenedData(originalIndex.length);
            for (int i = 0; i < candidateCount; i++) {
                IEquipment item = items[originalIndex[i]];
                int[] req = item.requirements();
                int[] bon = item.bonuses();
                hardened.set(i, req[0],req[1],req[2],req[3],req[4], bon[0],bon[1],bon[2],bon[3],bon[4]);
            }
        }
    }

    public static final class Builder implements IPlayerBuilder<CascadeSentinelV2Player> {
        private final Data data = new Data();
        private int itemCount, candidateCount, freeCount, freeScore;
        private int free0, free1, free2, free3, free4;
        private long freeMask;
        private boolean anyNegative;
        private int a0,a1,a2,a3,a4;
        private int neg0,neg1,neg2,neg3,neg4,pos0,pos1,pos2,pos3,pos4;
        private boolean packedValuesSafe = true;

        @Override public IPlayerBuilder<CascadeSentinelV2Player> equipment(IEquipment... additions) {
            for (IEquipment item : additions) {
                data.ensureItemCapacity(itemCount + 1);
                data.items[itemCount] = item;
                int[] req = item.requirements();
                int[] bon = item.bonuses();
                int q0=req[0],q1=req[1],q2=req[2],q3=req[3],q4=req[4];
                int x0=bon[0],x1=bon[1],x2=bon[2],x3=bon[3],x4=bon[4];
                int w=x0+x1+x2+x3+x4;
                boolean hasReq=q0>0||q1>0||q2>0||q3>0||q4>0;
                boolean hasNeg=x0<0||x1<0||x2<0||x3<0||x4<0;
                if (!hasReq && !hasNeg) {
                    freeCount++; freeScore += w;
                    free0+=x0;free1+=x1;free2+=x2;free3+=x3;free4+=x4;
                    if (itemCount < 64) freeMask |= 1L << itemCount;
                } else {
                    data.ensureCandidateCapacity(candidateCount + 1);
                    int j=candidateCount++;
                    data.originalIndex[j]=itemCount;
                    data.weight[j]=w;
                    data.packedReq[j]=packRequirements(q0,q1,q2,q3,q4);
                    data.packedBonus[j]=pack5(x0,x1,x2,x3,x4);
                    data.packedNeed[j]=packNeeds(q0,q1,q2,q3,q4,x0,x1,x2,x3,x4);
                    boolean candidatePackable = inPackRange(x0)&&inPackRange(x1)&&inPackRange(x2)&&inPackRange(x3)&&inPackRange(x4)
                            && needPackable(q0,x0)&&needPackable(q1,x1)&&needPackable(q2,x2)&&needPackable(q3,x3)&&needPackable(q4,x4);
                    packedValuesSafe &= candidatePackable;
                    if (data.hardened != null) {
                        data.hardened.set(j,q0,q1,q2,q3,q4,x0,x1,x2,x3,x4);
                    } else if (!packedValuesSafe || candidateCount > 9) {
                        // Prepare the exact scalar sidecar during builder work, never lazily in run().
                        data.ensureHardenedData(candidateCount);
                    }
                    neg0+=Math.min(0,x0);neg1+=Math.min(0,x1);neg2+=Math.min(0,x2);neg3+=Math.min(0,x3);neg4+=Math.min(0,x4);
                    pos0+=Math.max(0,x0);pos1+=Math.max(0,x1);pos2+=Math.max(0,x2);pos3+=Math.max(0,x3);pos4+=Math.max(0,x4);
                    anyNegative |= hasNeg;
                }
                itemCount++;
            }
            return this;
        }
        @Override public IPlayerBuilder<CascadeSentinelV2Player> allocate(SkillPoint point, int amount) {
            switch (point) {
                case STRENGTH -> a0=amount; case DEXTERITY -> a1=amount; case INTELLIGENCE -> a2=amount;
                case DEFENCE -> a3=amount; case AGILITY -> a4=amount;
            }
            return this;
        }
        @Override public CascadeSentinelV2Player build() {
            int base0=a0+free0,base1=a1+free1,base2=a2+free2,base3=a3+free3,base4=a4+free4;
            boolean safe=packedValuesSafe
                && rangeSafe(base0,neg0,pos0)&&rangeSafe(base1,neg1,pos1)&&rangeSafe(base2,neg2,pos2)
                && rangeSafe(base3,neg3,pos3)&&rangeSafe(base4,neg4,pos4);
            if (!safe) {
                // The algorithm will route this snapshot to the hardened solver.
                // Materialize its scalar inputs here, while build() is already doing preprocessing.
                data.ensureHardenedData(candidateCount);
            }
            long packedBase = safe ? pack5(base0,base1,base2,base3,base4) : 0L;
            boolean denseNineRisk = false;
            if (candidateCount == 9 && anyNegative) {
                boolean allInitial = true;
                int sum0=0,sum1=0,sum2=0,sum3=0,sum4=0;
                int need0=NO_NEED,need1=NO_NEED,need2=NO_NEED,need3=NO_NEED,need4=NO_NEED;
                HardenedData h = data.hardened;
                for(int i=0;i<9;i++){
                    int q0,q1,q2,q3,q4,x0,x1,x2,x3,x4,p0,p1,p2,p3,p4;
                    if (h != null) {
                        q0=h.r0[i];q1=h.r1[i];q2=h.r2[i];q3=h.r3[i];q4=h.r4[i];
                        x0=h.b0[i];x1=h.b1[i];x2=h.b2[i];x3=h.b3[i];x4=h.b4[i];
                        p0=h.p0[i];p1=h.p1[i];p2=h.p2[i];p3=h.p3[i];p4=h.p4[i];
                    } else {
                        IEquipment item = data.items[data.originalIndex[i]];
                        int[] req = item.requirements(); int[] bon = item.bonuses();
                        q0=req[0];q1=req[1];q2=req[2];q3=req[3];q4=req[4];
                        x0=bon[0];x1=bon[1];x2=bon[2];x3=bon[3];x4=bon[4];
                        p0=q0>0?q0+x0:NO_NEED;p1=q1>0?q1+x1:NO_NEED;p2=q2>0?q2+x2:NO_NEED;
                        p3=q3>0?q3+x3:NO_NEED;p4=q4>0?q4+x4:NO_NEED;
                    }
                    if((q0>0&&base0<q0)||(q1>0&&base1<q1)||(q2>0&&base2<q2)||(q3>0&&base3<q3)||(q4>0&&base4<q4)) allInitial=false;
                    sum0+=x0;sum1+=x1;sum2+=x2;sum3+=x3;sum4+=x4;
                    if(p0>need0)need0=p0;if(p1>need1)need1=p1;if(p2>need2)need2=p2;if(p3>need3)need3=p3;if(p4>need4)need4=p4;
                }
                if(allInitial){
                    int final0=base0+sum0,final1=base1+sum1,final2=base2+sum2,final3=base3+sum3,final4=base4+sum4;
                    denseNineRisk=(need0!=NO_NEED&&final0<need0)||(need1!=NO_NEED&&final1<need1)||(need2!=NO_NEED&&final2<need2)
                            ||(need3!=NO_NEED&&final3<need3)||(need4!=NO_NEED&&final4<need4);
                }
                if (denseNineRisk) data.ensureHardenedData(candidateCount);
            }
            HardenedSnapshot snapshotHardened = null;
            if (itemCount <= 64 && (candidateCount > 9 || !safe || denseNineRisk)) {
                snapshotHardened = new HardenedSnapshot(
                        freeCount, freeScore, free0, free1, free2, free3, free4);
            }
            return new CascadeSentinelV2Player(data,itemCount,candidateCount,snapshotHardened,
                    freeMask,anyNegative,safe,denseNineRisk,packedBase,a0,a1,a2,a3,a4);
        }
        private static boolean inPackRange(int v){return v>=-PACK_BIAS && v<PACK_BIAS;}
        private static boolean needPackable(int r,int b){return r<=0 || (inPackRange(r)&&inPackRange(r+b));}
        private static boolean rangeSafe(int base,int neg,int pos){return inPackRange(base+neg)&&inPackRange(base+pos);}
        private static long laneReq(int r){return r>0 ? r+PACK_BIAS : 0L;}
        private static long laneNeed(int r,int b){return r>0 ? r+b+PACK_BIAS : 0L;}
        private static long packRequirements(int r0,int r1,int r2,int r3,int r4){
            return laneReq(r0)|(laneReq(r1)<<12)|(laneReq(r2)<<24)|(laneReq(r3)<<36)|(laneReq(r4)<<48);
        }
        private static long packNeeds(int r0,int r1,int r2,int r3,int r4,int b0,int b1,int b2,int b3,int b4){
            return laneNeed(r0,b0)|(laneNeed(r1,b1)<<12)|(laneNeed(r2,b2)<<24)|(laneNeed(r3,b3)<<36)|(laneNeed(r4,b4)<<48);
        }
    }
}
