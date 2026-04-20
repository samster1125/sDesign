#include "getImage.h"
#include "sending.h"

  int main()
  {
      u32 fullImage[FULL_ROWS][COLUMNS] = {0};
      u8 res[PACKETS_PER_SEGMENT][VOSPI_FRAME_SIZE]  = {0};
      u8 shelf[NUM_SEGMENTS][PACKETS_PER_SEGMENT][VOSPI_FRAME_SIZE] = {0};

      SpiConfig config = 
    {
      .device = "/dev/spidev0.0",
      .mode = SPI_MODE_3,
      .bits = 8,
      .speed = 12000000,
      .delay_usecs = 10
    }; 

     int fd = checkAndSetSPI(&config);   struct sockaddr_in serveradd;
     socklen_t sockLen = sizeof(serveradd);
     int sockfd = socket(AF_INET, SOCK_STREAM, DEFAULT);
     setSocket(&serveradd);
     int opt = 1;
     setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
     check(sockfd, "socket");
     int ret = bind(sockfd, (struct sockaddr*)&serveradd, sizeof(serveradd));
     check(ret, "binding");
     listen(sockfd, 4);

     while (1)
  {
      int segmentNumber = -1;

      int ok = captureOneSegment(fd, res, &segmentNumber, &config);

      if (ok < 0) break;

      if (ok == 0) continue;

      memcpy(shelf[segmentNumber - 1], res, sizeof(res));

      if (segmentNumber == 4)
      {

        if (buildImage(fullImage, shelf) < 0)
        {
          continue;
        }

        Packet packet;

        u8 buff = 0;

        int clientfd = accept(sockfd, (struct sockaddr*)&serveradd, &sockLen);
        readAll(clientfd, &buff, sizeof(buff));
        setStatus(&packet);

        if (buff == 0xB)
        {
        u8 gray[FULL_ROWS][COLUMNS] = {0};


        packet.header.flagImage = true;

        convertToGray(fullImage, gray);

        printf("Receieved 0xA from client... sending\n");
        setStatusFromImage(&packet, fullImage);

        memcpy(packet.img, gray, sizeof(gray));

        sendStatus(clientfd, &packet);

        close(clientfd);
        }
      }
  }

  close(sockfd);
  return 0;
}


