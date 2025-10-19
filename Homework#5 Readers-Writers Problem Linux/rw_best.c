// rw_best.c — First Readers-Writers Problem Implementation
// 1000 threads: 10 randomly-selected writers, 990 readers
// Writers execute sequentially; readers execute in randomized batches
// Each critical section occupies exactly 1 second via busy-wait spinlock
//
// Build: gcc -O2 -pthread -o rw_best rw_best.c
// Run:   ./rw_best

#define _GNU_SOURCE
#include <pthread.h>
#include <semaphore.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

// Configuration
enum { TOTAL = 1000, WRITERS = 10, READERS = TOTAL - WRITERS };
enum { READER_BATCHES = 11, MIN_B = 60, MAX_B = 120 };

// Shared state
static atomic_int x = 0;
static atomic_int enter_seq = 0;

// Synchronization primitives
static sem_t gate_read;
static sem_t gate_write;
static sem_t batch_done;
static sem_t writer_done;

static atomic_int readers_in_batch = 0;
static int is_writer[TOTAL];
static int batch_sz[READER_BATCHES];

// Busy-wait for exactly 1 second using CLOCK_MONOTONIC
static inline void busy_1s(void) {
    struct timespec t0, now;
    clock_gettime(CLOCK_MONOTONIC, &t0);
    for (;;) {
        clock_gettime(CLOCK_MONOTONIC, &now);
        long sec  = now.tv_sec  - t0.tv_sec;
        long nsec = now.tv_nsec - t0.tv_nsec;
        if (nsec < 0) { sec--; nsec += 1000000000L; }
        if (sec > 1 || (sec==1 && nsec >= 0)) break;
        for (volatile int k=0; k<1000; ++k) {}
    }
}

// Randomly select 10 threads as writers using Fisher-Yates shuffle
static void pick_random_writers(void) {
    int idx[TOTAL];
    for (int i=0;i<TOTAL;i++) idx[i]=i;
    for (int i=TOTAL-1;i>0;i--) {
        int j = rand() % (i+1);
        int t = idx[i]; idx[i]=idx[j]; idx[j]=t;
    }
    for (int k=0;k<WRITERS;k++) is_writer[idx[k]] = 1;
}

// Generate random batch sizes in [MIN_B, MAX_B] that sum to READERS
static void make_random_batches(void) {
    for (int i=0;i<READER_BATCHES;i++) batch_sz[i]=90;
    
    for (int iter=0; iter<2000; ++iter) {
        int a = rand()%READER_BATCHES, b = rand()%READER_BATCHES;
        if (a==b) continue;
        int delta = (rand()%3)-1;
        if (delta==0) continue;
        int na = batch_sz[a] + delta;
        int nb = batch_sz[b] - delta;
        if (na>=MIN_B && na<=MAX_B && nb>=MIN_B && nb<=MAX_B) {
            batch_sz[a]=na; batch_sz[b]=nb;
        }
    }
    
    // Ensure sum equals READERS
    int sum=0; for(int i=0;i<READER_BATCHES;i++) sum+=batch_sz[i];
    int diff = READERS - sum;
    while (diff != 0) {
        int i = rand()%READER_BATCHES;
        int tryv = batch_sz[i] + (diff>0 ? 1 : -1);
        if (tryv>=MIN_B && tryv<=MAX_B) { 
            batch_sz[i]=tryv; 
            diff += (diff>0?-1:+1); 
        }
    }
}

static void* reader_fn(void* arg) {
    (void)arg;
    sem_wait(&gate_read);

    int my_no = atomic_fetch_add_explicit(&enter_seq, 1, memory_order_relaxed) + 1;
    (void)my_no;

    atomic_fetch_add_explicit(&readers_in_batch, 1, memory_order_relaxed);

    busy_1s();

    int left = atomic_fetch_sub_explicit(&readers_in_batch, 1, memory_order_relaxed) - 1;
    if (left == 0) sem_post(&batch_done);
    return NULL;
}

static void* writer_fn(void* arg) {
    (void)arg;
    sem_wait(&gate_write);

    int no = atomic_fetch_add_explicit(&enter_seq, 1, memory_order_relaxed) + 1;

    busy_1s();

    int newx = atomic_fetch_add_explicit(&x, 1, memory_order_relaxed) + 1;

    printf("no = %-4d x = %d\n", no, newx);
    fflush(stdout);

    sem_post(&writer_done);
    return NULL;
}

int main(void) {
    setvbuf(stdout, NULL, _IOLBF, 0);

    srand((unsigned)time(NULL));
    pick_random_writers();
    make_random_batches();

    sem_init(&gate_read,  0, 0);
    sem_init(&gate_write, 0, 0);
    sem_init(&batch_done, 0, 0);
    sem_init(&writer_done,0, 0);

    pthread_t th[TOTAL];
    for (int i=0;i<TOTAL;i++) {
        if (is_writer[i]) pthread_create(&th[i], NULL, writer_fn, (void*)(intptr_t)i);
        else              pthread_create(&th[i], NULL, reader_fn, (void*)(intptr_t)i);
    }

    struct timespec t0,t1;
    clock_gettime(CLOCK_MONOTONIC, &t0);

    // Orchestrate: interleave reader batches with writers
    for (int b=0; b<READER_BATCHES; ++b) {
        atomic_store_explicit(&readers_in_batch, 0, memory_order_relaxed);

        for (int r=0; r<batch_sz[b]; ++r) sem_post(&gate_read);
        sem_wait(&batch_done);

        if (b < WRITERS) {
            sem_post(&gate_write);
            sem_wait(&writer_done);
        }
    }

    for (int i=0;i<TOTAL;i++) pthread_join(th[i], NULL);

    clock_gettime(CLOCK_MONOTONIC, &t1);
    double elapsed = (t1.tv_sec - t0.tv_sec) + (t1.tv_nsec - t0.tv_nsec)/1e9;
    printf("Finished in %.2f seconds. Final x = %d\n", elapsed, atomic_load(&x));

    sem_destroy(&gate_read);
    sem_destroy(&gate_write);
    sem_destroy(&batch_done);
    sem_destroy(&writer_done);
    return 0;
}
