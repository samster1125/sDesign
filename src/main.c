#include "getImage.h"
#include "sending.h"
pthread_mutex_t mutex;
Packet packet;

  int main()
  {
      SpiConfig config = 
    {
      .device = "/dev/spidev0.0",
      .mode = SPI_MODE_3,
      .bits = 8,
      .speed = 12000000,
      .delay_usecs = 10
    }; 

      int fd = checkSPI(&config);   
      u32 fullImage[FULL_ROWS][COLUMNS] = {0};
      u8 res[PACKETS_PER_SEGMENT][VOSPI_FRAME_SIZE]  = {0};
      u8 shelf[NUM_SEGMENTS][PACKETS_PER_SEGMENT][VOSPI_FRAME_SIZE] = {0};
      printf("connecting to %s\n", DEVICE);

      int rs_fd = open(DEVICE, O_RDWR | O_NOCTTY);
      check(rs_fd, "rs485");
      struct termios tty;
      setTermios(&tty, rs_fd);
      pthread_mutex_init(&mutex, NULL);
      
      pthread_t bt;
      pthread_create(&bt, NULL, btLoop, NULL);

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

          printf("Building Packet... \n");
          u8 buff = 0;

         int ret = readAll(rs_fd, &buff, sizeof(buff));

         check(ret, "readAll func");
          Packet p;
          
         if (buff == 0xB)
         {
            u8 gray[FULL_ROWS][COLUMNS] = {0};
            p.header.flagImage = true;
            convertToGray(fullImage, gray);
            printf("Receieved 0xB from client... sending\n");
            setStatusFromImage(&p, fullImage);
            memcpy(p.img, gray, sizeof(gray));
            pthread_mutex_lock(&mutex);
            memcpy(&packet, &p, sizeof(p));
            pthread_mutex_unlock(&mutex);
            sendStatus(rs_fd, &p);
         }

         else if (buff == 0xA)
          {
            p.header.flagImage = false;
            printf("Receieved 0xA from client.... sending\n");
            setStatusFromImage(&p, fullImage);
            pthread_mutex_lock(&mutex);
            memcpy(&packet, &p, sizeof(p));
            pthread_mutex_unlock(&mutex);
            sendStatus(rs_fd, &p);
          }
      }
  }
  return 0;
}

