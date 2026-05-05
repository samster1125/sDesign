#ifndef CLIENT_H
#define CLIENT_H

#ifdef __cplusplus
extern "C" {
#endif

#include <termios.h>
#include <stdio.h>
#include <sys/socket.h>
#include <stdbool.h>
#include <arpa/inet.h>
#include <errno.h>
#include <sys/param.h>
#include <string.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <unistd.h>
#include <limits.h>
#include <stdint.h>

#define DEVICE "/dev/ttyUSB0"
#define STOP_BYTE 0xAA
#define FULL_ROWS 120
#define COLUMNS 160
#define DEFAULT 0
#define IMAGE_SIZE 19200
#define BASE 11
#define MAX_SIZE (IMAGE_SIZE+BASE)
typedef uint8_t u8;
typedef unsigned int u32;
typedef uint16_t u16;

typedef struct Packetheader
{
  u8 ack;
  u16 len;
  u16 min;
  u16 max;
  u16 avg;
  bool flagImage;
  u8 stop;
}PacketHeader;

typedef struct Packet
{
    PacketHeader header;
    u8 img[FULL_ROWS][COLUMNS];
    size_t       image_size;
} Packet;


int writeAll(int fd, void* buf, size_t buflen);
int readAll(int fd, void* buff, size_t bufflen);
void check(const char* s, int fd);
void setSocket(struct sockaddr_in* sockaddr);
void setTermios(struct termios* tty, int fd);

#ifdef __cplusplus
}
#endif

#endif
