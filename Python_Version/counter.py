'''
Python port of the OEIS A140606 counting program:
https://oeis.org/A140606/a140606_2.pl.txt

This is the same algorithm ComSearch Appendix A relies on
(https://arxiv.org/pdf/2210.07017): two outermost-operator families,
a factor of 2^g-1, and S = T - U via 2*sc - sc1 + 2*sa - sa1.
'''


import sys
import math

# Set default n if no argument is passed
n = 10

# Global arrays
sc = [0] * (n + 1)
sa = [0] * (n + 1)
sc1 = [0] * (n + 1)
sa1 = [0] * (n + 1)
fact = [0] * (n + 1)
p2m1 = [0] * (n + 1)
ind = [0] * (n + 1)


def init():
    sc[1] = sa[1] = sc1[1] = sa1[1] = fact[1] = p2m1[1] = 1
    for i in range(2, n + 1):
        fact[i] = fact[i - 1] * i
        p2m1[i] = p2m1[i - 1] * 2 + 1


def get_comb(n, g):
    # Initialize combination with factorial of n
    comb = fact[n]
    c = 0
    while c < g:
        i = c + 1
        # Find the range of identical values in ind[]
        while i < g and ind[i] == ind[c]:
            i += 1 
        k = i - c  # Length of the range of identical values
        t = ind[c]  # The value that's repeated
        # Divide comb by factorial of t, k times
        for _ in range(1, k + 1):
            comb //= fact[t]
        # Divide comb by factorial of k
        comb //= fact[k]
        c = i  # Move to the next distinct value
    return comb


def accum(n, g):
    # Get the combination value for n and g
    comb = get_comb(n, g)
    
    # Calculate and accumulate sc[n]
    tmp = comb * p2m1[g]
    for i in range(g):
        tmp *= sa[ind[i]]
    sc[n] += tmp

    # Calculate and accumulate sc1[n]
    tmp = comb * p2m1[g]
    for i in range(g):
        tmp *= sa1[ind[i]]
    sc1[n] += tmp
    
    # Calculate and accumulate sa[n]
    tmp = comb * (p2m1[g - 1] + 1)
    for i in range(g):
        tmp *= sc[ind[i]]
    sa[n] += tmp

    # Calculate and accumulate sa1[n]
    tmp = comb
    for i in range(g):
        tmp *= sc1[ind[i]]
    sa1[n] += tmp


def try_index(n, g, cur_g, left):
    if cur_g == 0:
        ind[cur_g] = left
        accum(n, g)
        return
    
    max_val = left - cur_g
    if cur_g < g - 1 and max_val > ind[cur_g + 1]:
        max_val = ind[cur_g + 1]
    
    min_val = (left + cur_g) // (cur_g + 1)
    
    for i in range(min_val, max_val + 1):
        ind[cur_g] = i
        try_index(n, g, cur_g - 1, left - i)


def count_group(n, g):
    try_index(n, g, g - 1, n)


def count(n):
    sc[n] = sa[n] = sc1[n] = sa1[n] = 0
    for g in range(2, n + 1):
        count_group(n, g)
    sum_val = 2 * sc[n] - sc1[n] + 2 * sa[n] - sa1[n]
    return sum_val

# Main execution function
def run():
    print('h')
    print(1)
    for i in range(2, n + 1):
        print(count(i),sc[i], sc1[i], sa[i], sa1[i],ind[0:i+1])
    print()

# Run the program
if __name__ == "__main__":
    init()
    run()
