#include <sys/types.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/wait.h>
#include <stdlib.h>


int main() {
	pid_t pids[10];

	for (size_t i = 0; i < 10; i++) {
		if ((pids[i] = fork()) < 0) {
			printf("Fork failed\n");
			exit(1);
		} else if (pids[i] == 0) {
			printf("I'm the child number %zu (pid %d)\n", i, getpid());
			exit(0);
		}
	}

	for (size_t i = 0; i < 10; i++) {
		wait(NULL);
	}
	printf("Parent terminated (pid %d)\n", getpid());

	return 0;
}
