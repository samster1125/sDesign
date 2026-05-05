#ifndef GETIMAGE_H
#define GETIMAGE_H

#ifdef __cplusplus
extern "C" {
#endif


#include <termios.h>
#include <stddef.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <stddef.h>
#include <limits.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/spi/spidev.h>
#include <sys/socket.h>
#include <stdbool.h>
#include <arpa/inet.h>
#include <errno.h>
#include <sys/param.h>
#include <netinet/in.h>

#define DEFAULT 0
#define COLUMNS 160
#define FULL_ROWS 120
#define PACKETS_PER_SEGMENT 60
#define NUM_SEGMENTS 4
#define VOSPI_FRAME_SIZE 164
#define PIXELS_PER_PACKET 80
#define DEFAULT 0

typedef uint8_t u8;
typedef uint16_t u16;
typedef unsigned int u32;

typedef struct SpiConfig
{
    const char *device;
    u8 mode;
    u8 bits;
    u32 speed;
    u16 delay_usecs;
} SpiConfig;


int captureOneSegment(int fd, u8 dest[PACKETS_PER_SEGMENT][VOSPI_FRAME_SIZE], int *segment_number, const SpiConfig* config);
int readPacket(int fd, u8 packet[VOSPI_FRAME_SIZE], const SpiConfig* config);
int buildImage(u32 img[FULL_ROWS][COLUMNS],u8 shelf[NUM_SEGMENTS][PACKETS_PER_SEGMENT][VOSPI_FRAME_SIZE]);
int checkSPI(SpiConfig* config);
void pabort(const char *s);
void convertToGray(u32 img[FULL_ROWS][COLUMNS],u8 gray[FULL_ROWS][COLUMNS]);
u16 getAverage(u32 img[FULL_ROWS][COLUMNS]);
u16 getMin(u32 img[FULL_ROWS][COLUMNS]);
u16 getMax(u32 img[FULL_ROWS][COLUMNS]);

#ifdef __cplusplus
} 
#endif

#endif
