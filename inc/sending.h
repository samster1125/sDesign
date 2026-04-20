#ifndef SEND_H
#define SEND_H


#include "getImage.h"
#define ACK 0xF
#define STATUS_NORMAL 0xA
#define STATUS_PICTURE 0xB
#define IMAGE_SIZE 19200
#define STATUS_NORMAL_SIZE 10 

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

void writeImg(u8* buf, u8 img[FULL_ROWS][COLUMNS], size_t* off);
size_t getSize(const Packet* packet);
int sendStatus(int fd, const Packet* packet);
u8* buildStatus(const Packet* packet);
int writeAll(int sockfd, void* buff, size_t bufflen);
void setSocket(struct sockaddr_in* sockaddr);
void check(int fd, const char* type);
void writeBytes(u8* buf, const void* src, size_t n, size_t* off);
void writeu8(u8* buf, u8 val, size_t* off);
void writeu16(u8* buf, u16 val, size_t* off);
int readAll(int fd, void* buff, size_t bufflen);
// --TODO look at bottom functions for new Packet functionality
void setStatus(Packet* packet);
void setStatusFromImage(Packet* packet, u32 img[FULL_ROWS][COLUMNS]);

#endif
