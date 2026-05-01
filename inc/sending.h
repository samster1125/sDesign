#ifndef SEND_H
#define SEND_H


#include "getImage.h"
#include <pthread.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/rfcomm.h>


#define ACK 0xFF
#define STATUS_NORMAL 0xA
#define STATUS_PICTURE 0xB
#define IMAGE_SIZE 19200
#define STATUS_NORMAL_SIZE 11 
#define DEVICE "/dev/serial0"

#pragma pack(push, 1)
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
#pragma pack(pop)

typedef struct Packet
{
    PacketHeader header;
    u8 img[FULL_ROWS][COLUMNS];   
    size_t       image_size;
} Packet;

typedef struct RS485
{
    const char *device;
    u8 mode;
    u8 bits;
    u32 speed;
    u16 delay_usecs;
} RS485;

extern pthread_mutex_t mutex;
extern Packet packet;

void writeImg(u8* buf, u8 img[FULL_ROWS][COLUMNS], size_t* off);
size_t getSize(const Packet* packet);
int sendStatus(int fd, const Packet* packet);
u8* buildStatus(const Packet* packet);
int writeAll(int sockfd, void* buff, size_t bufflen);
void setSocket(struct sockaddr_rc* sockaddy);
void check(int fd, const char* type);
void writeBytes(u8* buf, const void* src, size_t n, size_t* off);
void writeu8(u8* buf, u8 val, size_t* off);
void writeu16(u8* buf, u16 val, size_t* off);
int readAll(int fd, void* buff, size_t bufflen);
void setStatus(Packet* packet);
void setStatusFromImage(Packet* packet, u32 img[FULL_ROWS][COLUMNS]);
void setTermios(struct termios* tty, int fd);
void* btLoop(void* arg);
  
#endif
