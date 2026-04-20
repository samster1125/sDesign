#include "getImage.h"
#include "sending.h"


// --TODO modify code to match functionality for 'NEW' packet

void check(int fd, const char* type)
{
    if (fd < 0)
    {
        printf("%s\n", type);
        perror("failed\n");
    }
}
void setSocket(struct sockaddr_in* sockaddr)
{
    memset(sockaddr, 0, sizeof(*sockaddr));
    sockaddr->sin_family = AF_INET;
    sockaddr->sin_port = htons(PORT);
    sockaddr->sin_addr.s_addr = INADDR_ANY;
}

int writeAll(int fd, void* buf, size_t buflen)
{
    int total = 0;
    size_t remaining = buflen;
    
    while (remaining > 0)
    {
        int n = write(fd, (u8*)buf+total, remaining);

        if (n < 0)
        {
            return -1;
            perror("something happened w/ writing in writeAll()\n");
        }

        if (n == 0)
        {
          return 0;
        }

        total += n;
        remaining -= n;
    }
    return 1;
}

void writeBytes(u8* buf, const void* src, size_t n, size_t* off)
{
  if (!buf || !src)
  {
    perror("Something fucked up w/params in writeBytes\n");
  }

  memcpy(buf+ *off, src, n);
  *off += n;
}

void writeu8(u8* buf, u8 val, size_t* off)
{
  writeBytes(buf, &val, sizeof(val), off);
}

void writeImg(u8* buf, u8 img[FULL_ROWS][COLUMNS], size_t* off)
{
  writeBytes(buf, img, IMAGE_SIZE, off);
}

void writeu16(u8* buf, u16 val, size_t* off)
{
  u16 valNet = htons(val);
  writeBytes(buf, &valNet, sizeof(valNet), off);
}


size_t getSize(const Packet* packet)
{
  if (!packet)
  {
    return 0;
  }

  size_t base = sizeof(PacketHeader);

  if (!packet->header.flagImage)
  {
     return base;
  }

  else
  {
    return base + IMAGE_SIZE; 
  }
}

u8* buildStatus(const Packet* packet)
{
  if (!packet)
  {
    return NULL;
  }

  size_t buffSize = getSize(packet);

  u8* buf = (u8*)malloc(buffSize);

  if (!buf)
  {
    perror("Malloc fucked up in buildStatus()\n");
    return NULL;
  }

  size_t offset = 0;
  memset(buf, 0, buffSize);
  writeu8(buf, packet->header.ack, &offset);
  writeu16(buf, (u16)buffSize, &offset);
  writeu16(buf, packet->header.min, &offset);
  writeu16(buf, packet->header.max, &offset);
  writeu16(buf, packet->header.avg, &offset);

  if (packet->header.flagImage)
  {
    writeImg(buf, packet->img, &offset);
  }
   
  writeu8(buf, packet->header.stop, &offset);

  return buf;
}

int sendStatus(int fd, const Packet* packet)
{

    u8* status = buildStatus(packet);

    if (!status) 
    {
      perror("building packet fucked up in sendStatus()\n");
      return -1;
    }

    size_t size = getSize(packet);

    int retVal = writeAll(fd, status, size);
    free(status);

    if (retVal < 1)
    {
      perror("Packet failed to send: sendStatus()");
    }
    return 1;
}

int readAll(int fd, void* buff, size_t bufflen)
{
    int total = 0;
    int remaining = bufflen;

    while (remaining > 0)
    {
        int n = read(fd, (u8*)buff+total, remaining);

        if (n < 0)
        {
            return -1;
            perror("Somethng fucked up w/ read");
        }

        if (n == 0)
        {
            return 0;
        }

        total += n;
        remaining -= n;
    }
    return 1;
}

void setStatus(Packet* packet)
{
  if (!packet) return;

  packet->header.ack = 0xF;
  packet->header.stop = 0xA;
}

void setStatusFromImage(Packet* packet, u32 img[FULL_ROWS][COLUMNS])
{
  if (!packet || !img) return;

  packet->header.avg = getAverage(img);
  packet->header.min = getMin(img);
  packet->header.max = getMax(img);
}

