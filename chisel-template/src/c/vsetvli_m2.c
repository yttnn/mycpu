#include <stdio.h>

int main(void) {
  unsigned int size = 10;
  unsigned int vl;
  while(size > 0){
    asm volatile("vsetvli %0, %1, e32, m2"
                  : "=r"(vl)
                  : "r"(size));
    size -= vl;
  }
  asm volatile("unimp");
  return 0;
}