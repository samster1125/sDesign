#include "getImage.h"


int checkAndSetSPI(SpiConfig* config)
{
    int fd = open(config->device, O_RDWR);
    if (fd < 0) {
        pabort("open SPI device");
    }

    if (ioctl(fd, SPI_IOC_WR_MODE, &config->mode) < 0 ||
        ioctl(fd, SPI_IOC_RD_MODE, &config->mode) < 0 ||
        ioctl(fd, SPI_IOC_WR_BITS_PER_WORD, &config->bits) < 0 ||
        ioctl(fd, SPI_IOC_RD_BITS_PER_WORD, &config->bits) < 0 ||
        ioctl(fd, SPI_IOC_WR_MAX_SPEED_HZ, &config->speed) < 0 ||
        ioctl(fd, SPI_IOC_RD_MAX_SPEED_HZ, &config->speed) < 0) {
        pabort("SPI configuration failed");
        return -1;
    }

    return fd;
}


  void pabort(const char *s)
{
    perror(s);
    abort();
}

 int findNextFileName(char *out, size_t size)
{
    static int counter = 0;
    for (int i = 0; i < 9999; i++) {
        snprintf(out, size, "thermal_full_%04d.ppm", counter++);
        if (access(out, F_OK) != 0) return 1;
    }
    return 0;
}

 int readPacket(int fd, u8 packet[VOSPI_FRAME_SIZE], const SpiConfig* config)
{
    u8 tx[VOSPI_FRAME_SIZE] = {0};

    struct spi_ioc_transfer tr = {
        .tx_buf        = (unsigned long)tx,
        .rx_buf        = (unsigned long)packet,
        .len           = VOSPI_FRAME_SIZE,
        .delay_usecs   = config->delay_usecs,
        .speed_hz      = config->speed,
        .bits_per_word = config->bits,
    };

    if (ioctl(fd, SPI_IOC_MESSAGE(1), &tr) < 1) {
        perror("SPI transfer failed");
        return -1;
    }

    if ((packet[0] & 0xF0) == 0xF0) {
        return 0;
    }

    return 1;
}

int captureOneSegment(int fd, u8 dest[PACKETS_PER_SEGMENT][VOSPI_FRAME_SIZE], int *segment_number, const SpiConfig* config)
{
    int resets = 0;
    *segment_number = -1;

    for (int j = 0; j < PACKETS_PER_SEGMENT; j++) {
        int rc = readPacket(fd, dest[j], config);

        if (rc != 1) {
            j = -1;
            resets++;
            usleep(1000);

            if (resets >= 750) {
                fprintf(stderr, "Too many resets while capturing segment\n");
                return -1;
            }
            continue;
        }

        int packetNumber = dest[j][1];

        if (packetNumber != j) {
          
            j = -1;
            resets++;
            usleep(1000);

            if (resets >= 750) {
                return -1;
            }
            continue;
        }

        if (j == 20) {
            *segment_number = (dest[j][0] >> 4) & 0x0F;

            if (*segment_number < 1 || *segment_number > 4) {
                return 0;
            }
        }
    }

    return 1;
}

  int buildImage(u32 img[FULL_ROWS][COLUMNS],u8 shelf[NUM_SEGMENTS][PACKETS_PER_SEGMENT][VOSPI_FRAME_SIZE])
{
  u32 badPixel = 0;
    for (int seg = 0; seg < NUM_SEGMENTS; seg++) {
        int row_offset = seg * 30;

        for (int pkt = 0; pkt < PACKETS_PER_SEGMENT; pkt++) {
            int row = pkt / 2;
            int half = pkt % 2;
            int col_offset = half * PIXELS_PER_PACKET;

            for (int i = 0; i < PIXELS_PER_PACKET; i++) {
                int idx = 4 + i * 2;

                u32 value =
                    ((u32)shelf[seg][pkt][idx] << 8) |
                    (u32)shelf[seg][pkt][idx + 1];

                img[row_offset + row][col_offset + i] = value;

                if(value == 0)
                {
                  badPixel++;
                }
            }
        }
    }
    if (badPixel > 10)
    {
      return -1;
    }
    return 1;
}

void convertToGray(u32 img[FULL_ROWS][COLUMNS], u8 gray[FULL_ROWS][COLUMNS])
{
    u32 minval = UINT_MAX;
    u32 maxval = 0;

    for (int r = 0; r < FULL_ROWS; r++) 
    {
        for (int c = 0; c < COLUMNS; c++) 
        {
            u32 v = img[r][c];
            if (v < minval) minval = v;
            if (v > maxval) maxval = v;
        }
    }

    if (maxval == minval) {
        maxval = minval + 1;
    }

    for (int r = 0; r < FULL_ROWS; r++) {
        for (int c = 0; c < COLUMNS; c++) {
            float norm = (float)(img[r][c] - minval) / (maxval - minval);
            gray[r][c] = (u8)(norm * 255.0f);
        }
    }
}

u16 getAverage(u32 img[FULL_ROWS][COLUMNS])
{
  u16 avg = 0;
  u32 total = 0;

  for (u32 i = 0; i < FULL_ROWS; i++)
  {
    for (u32 j = 0; j < COLUMNS; j++)
    {
      total += img[i][j];
    }
  }
    avg = (total)/(FULL_ROWS*COLUMNS);
    return avg;
}

u16 getMin(u32 img[FULL_ROWS][COLUMNS])
{
  u16 min = UINT16_MAX;

  for (int i = 0; i < FULL_ROWS; i++)
  {
    for (int j = 0; j < COLUMNS; j++)
      if (img[i][j] < min)
        min = img[i][j];
  }
  return min;
}

u16 getMax(u32 img[FULL_ROWS][COLUMNS])
{
  u16 max = 1;

  for (int i = 0; i < FULL_ROWS; i++)
  {
    for (int j = 0; j < COLUMNS; j++)
      if (img[i][j] > max)
        max = img[i][j];
  }
  return max;
}

