package com.github.gkane1234;
import java.math.BigInteger;
import java.util.Arrays;
/**
    Counts inequivalent expressions on n variables with the four arithmetic operations.
    This is the OEIS A140606 algorithm (https://oeis.org/A140606/a140606_2.pl.txt).
    ComSearch Appendix A (https://arxiv.org/pdf/2210.07017) uses that sequence:
    two outermost-operator families, a factor of 2^g-1 on each set of g subexpressions,
    and S = T - U, which is 2*sc - sc1 + 2*sa - sa1 here.
*/
public class Counter {
    
    private static BigInteger[] sc;
    private static BigInteger[] sa;
    private static BigInteger[] sc1;
    private static BigInteger[] sa1;
    private static BigInteger[] fact;
    private static BigInteger[] p2m1;
    private static int[] ind;
    private static int n;  

    
    public static BigInteger run(int num) {
        n = num;
        init(num);
        if (num==1) {
            return BigInteger.ONE;
        }
        for (int i = 2; i <= num; i++) {
            count(i);
        }
        return count(num);
    }

    private static BigInteger count(int n) {
        sc[n] = sa[n] = sc1[n] = sa1[n] = BigInteger.ZERO;

        for (int g = 2; g <= n; g++) {
            countGroup(n, g);
        }

        BigInteger sum = sc[n].multiply(BigInteger.valueOf(2))
                .subtract(sc1[n])
                .add(sa[n].multiply(BigInteger.valueOf(2)))
                .subtract(sa1[n]);

        return sum;
    }

    private static void init(int num) {
        n = num;
        sc = new BigInteger[n + 1];
        sa = new BigInteger[n + 1];
        sc1 = new BigInteger[n + 1];
        sa1 = new BigInteger[n + 1];
        fact = new BigInteger[n + 1];
        p2m1 = new BigInteger[n + 1];
        ind = new int[n + 1];

        Arrays.fill(sc, BigInteger.ZERO);
        Arrays.fill(sa, BigInteger.ZERO);
        Arrays.fill(sc1, BigInteger.ZERO);
        Arrays.fill(sa1, BigInteger.ZERO);
        Arrays.fill(fact, BigInteger.ONE);
        Arrays.fill(p2m1, BigInteger.ONE);

        sc[1] = sa[1] = sc1[1] = sa1[1] = fact[1] = p2m1[1] = BigInteger.ONE;  // Initialize index 1

        for (int i = 2; i <= n; i++) {
            fact[i] = fact[i - 1].multiply(BigInteger.valueOf(i));
            p2m1[i] = p2m1[i - 1].multiply(BigInteger.valueOf(2)).add(BigInteger.ONE);
        }
    }


    private static BigInteger getComb(int n, int g) {
        BigInteger comb = fact[n];
        int c = 0;

        while (c < g) {
            int i = c + 1;
            while (i < g && ind[i] == ind[c]) {
                i++;
            }
            int k = i - c;
            int t = ind[c];
            for (int j = 1; j <= k; j++) {
                comb = comb.divide(fact[t]);
            }
            comb = comb.divide(fact[k]);
            c = i;
        }

        return comb;
    }


    private static void accum(int n, int g) {
        BigInteger comb = getComb(n, g);

        // sc[n]
        BigInteger tmp = comb.multiply(p2m1[g]);
        for (int i = 0; i < g; i++) {
            tmp = tmp.multiply(sa[ind[i]]);
        }
        sc[n] = sc[n].add(tmp);

        // sc1[n]
        tmp = comb.multiply(p2m1[g]);
        for (int i = 0; i < g; i++) {
            tmp = tmp.multiply(sa1[ind[i]]);
        }
        sc1[n] = sc1[n].add(tmp);

        // sa[n]
        tmp = comb.multiply(p2m1[g - 1].add(BigInteger.ONE));
        for (int i = 0; i < g; i++) {
            tmp = tmp.multiply(sc[ind[i]]);
        }
        sa[n] = sa[n].add(tmp);

        // sa1[n]
        tmp = comb;
        for (int i = 0; i < g; i++) {
            tmp = tmp.multiply(sc1[ind[i]]);
        }
        sa1[n] = sa1[n].add(tmp);
    }

    private static void tryIndex(int n, int g, int cur_g, int left) {
        if (cur_g == 0) {
            ind[cur_g] = left;
            accum(n, g);
            return;
        }

        int maxVal = left - cur_g;
        if (cur_g < g - 1 && maxVal > ind[cur_g + 1]) {
            maxVal = ind[cur_g + 1];
        }

        int minVal = (left + cur_g) / (cur_g + 1);

        for (int i = minVal; i <= maxVal; i++) {
            ind[cur_g] = i;
            tryIndex(n, g, cur_g - 1, left - i);
        }
    }

    private static void countGroup(int n, int g) {
        tryIndex(n, g, g - 1, n);
    }

}
