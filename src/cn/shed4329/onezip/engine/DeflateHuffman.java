package cn.shed4329.onezip.engine;

import java.util.Arrays;

/** Reusable canonical Huffman builder; rescale over-deep trees instead of truncating codes. */
final class DeflateHuffman {
    final int[] lengths, codes;
    private final int[] work, weight, parent, heap, counts = new int[16], next = new int[16];
    private int heapSize;

    DeflateHuffman(int symbols) {
        lengths = new int[symbols];
        codes = new int[symbols];
        work = new int[symbols];
        weight = new int[symbols * 2];
        parent = new int[symbols * 2];
        heap = new int[symbols * 2];
    }

    private boolean less(int a, int b) {
        return weight[a] < weight[b] || (weight[a] == weight[b] && a < b);
    }

    private void push(int node) {
        int index = ++heapSize;
        while (index > 1 && less(node, heap[index >>> 1])) {
            heap[index] = heap[index >>> 1];
            index >>>= 1;
        }
        heap[index] = node;
    }

    private int pop() {
        int result = heap[1], last = heap[heapSize--], index = 1;
        while (index * 2 <= heapSize) {
            int child = index * 2;
            if (child < heapSize && less(heap[child + 1], heap[child])) child++;
            if (!less(heap[child], last)) break;
            heap[index] = heap[child];
            index = child;
        }
        heap[index] = last;
        return result;
    }

    void build(int[] frequency, int maximum) {
        System.arraycopy(frequency, 0, work, 0, work.length);
        int active = 0;
        for (int f : work) if (f != 0) active++;
        for (int i = 0; active < 2; i++)
            if (work[i] == 0) {
                work[i] = 1;
                active++;
            }
        for (; ; ) {
            heapSize = 0;
            Arrays.fill(lengths, 0);
            int nodes = work.length;
            for (int i = 0; i < work.length; i++)
                if (work[i] != 0) {
                    weight[i] = work[i];
                    parent[i] = -1;
                    push(i);
                }
            while (heapSize > 1) {
                int a = pop(), b = pop();
                weight[nodes] = weight[a] + weight[b];
                parent[a] = parent[b] = nodes;
                parent[nodes] = -1;
                push(nodes++);
            }
            int deepest = 0;
            for (int i = 0; i < work.length; i++)
                if (work[i] != 0) {
                    int bits = 0;
                    for (int node = i; parent[node] >= 0; node = parent[node]) bits++;
                    lengths[i] = bits;
                    deepest = Math.max(deepest, bits);
                }
            if (deepest <= maximum) break;
            for (int i = 0; i < work.length; i++) if (work[i] != 0) work[i] = (work[i] + 1) >>> 1;
        }
        Arrays.fill(counts, 0);
        for (int length : lengths) if (length != 0) counts[length]++;
        int code = 0;
        for (int length = 1; length < 16; length++) {
            code = (code + counts[length - 1]) << 1;
            next[length] = code;
        }
        for (int i = 0; i < lengths.length; i++) {
            int length = lengths[i];
            codes[i] =
                    length == 0
                            ? 0
                            : ((Integer.reverse(next[length]++) >>> (32 - length)) << 4) | length;
        }
    }
}
