#include "client.h"
#include "validate.hpp"

bool has_packet = false;
Packet packet;
std::mutex mutex;


void readBytes(const void* src, void* dest, size_t n, size_t* off)
{
  if (!dest || !src)
  {
    perror("Something messed up w/params in readBytes\n");
  }

  memcpy(dest, (const u8*)src + *off, n);
  *off += n;
}

template <typename T>
void readB(const void* src, T* dest, size_t * off)
{
    if constexpr (sizeof(T) == 2)
    {
      T valHost{};  
      readBytes(src, &valHost, sizeof(valHost), off);
      *dest = ntohs(valHost);
      return;
    }
       readBytes(src, dest, sizeof(*dest), off);
}

float kelvinToF(u16 raw)
{
    float kelvin {raw/100.0f};

    return (kelvin-273.15) * 1.8 + 32;
}

void readImg(const u8* src, u8 img[FULL_ROWS][COLUMNS], size_t* off)
{
  readBytes(src, img, IMAGE_SIZE, off);
}

std::unique_ptr<Packet> parsePacket(const std::vector<u8>& buf)
{
  if (buf.size() < BASE)
  {
      std::cout << "buf size less than BASE\n";
      return nullptr;
  }
    
  auto packet = std::make_unique<Packet>();

  if (!packet) 
  {
      std::cout << "no packet...\n";
      return nullptr;
  }

  size_t offset = 0;
  packet->header.ack = buf[offset++];
  readB<u16>(buf.data(), &packet->header.len , &offset);
  readB<u16>(buf.data(), &packet->header.min, &offset);
  readB<u16>(buf.data(), &packet->header.max, &offset);
  readB<u16>(buf.data(), &packet->header.avg, &offset);
  readB<u8>(buf.data(), (u8*)&packet->header.flagImage, &offset);

  if (packet->header.flagImage)
  {
    readImg(buf.data(), packet->img, &offset);
  }

  readB<u8>(buf.data(), &packet->header.stop, &offset);
  return packet;
}

  void pabort(const char *s)
{
    perror(s);
    abort();
}

void heatColor(float norm, u8 *r8, u8 *g8, u8 *b8)
{
    if (norm < 0.42f) 
    {
        float t = norm / 0.42f;
        *r8 = (u8)(8 + t * 55);
        *g8 = 0;
        *b8 = (u8)(25 + t * 165);
    }
    else if (norm < 0.75f) 
    {
        float t = (norm - 0.42f) / 0.33f;
        *r8 = (u8)(63 + t * 192);
        *g8 = (u8)(t * 55);
        *b8 = (u8)(190 - t * 190);
    }
    else if (norm < 0.95f) 
    {
        float t = (norm - 0.75f) / 0.20f;
        *r8 = 255;
        *g8 = (u8)(55 + t * 200);
        *b8 = 0;
    }
    else 
    {
        float t = (norm - 0.95f) / 0.05f;
        *r8 = 255;
        *g8 = 255;
        *b8 = (u8)(t * 100);
    }
}


void savePPM(const char *filename, u8 img[FULL_ROWS][COLUMNS])
{
    FILE *f = fopen(filename, "wb");
    if (!f) {
        pabort("Cannot open ppm");
    }

    fprintf(f, "P6\n%d %d\n255\n", COLUMNS, FULL_ROWS);

    for (int r = 0; r < FULL_ROWS; r++) 
    {
        for (int c = 0; c < COLUMNS; c++) 
        {
            float norm = img[r][c] / 255.0f;

            if (norm < 0.0f) norm = 0.0f;
            if (norm > 1.0f) norm = 1.0f;

            u8 r8, g8, b8;
            heatColor(norm, &r8, &g8, &b8);

            fputc(r8, f);
            fputc(g8, f);
            fputc(b8, f);
        }
    }

    fclose(f);
    printf("Saved thermal image: %s\n", filename);
}

int searchForStart(u8* buf, int size)
{
    for (int i {0}; i < size; i++)
    {
        if (buf[i] == 0xFF) return i;
    }
    return -1;
}

int isValid(std::vector<u8>& buf, int fd)
{
    while (1)
    {
        size_t old_size = buf.size();
        buf.resize(old_size + 4096);
        
        ssize_t bytesRead = read(fd, buf.data() + old_size, 4096);

        if (bytesRead > 0) 
        {
            buf.resize(old_size + bytesRead);   
        } else if (bytesRead <= 0) 
        {
            return 0;   
        }

        while (buf.size() >= BASE)
        {
            int start = searchForStart(buf.data(), buf.size());

            if (start < 0) 
            {
                buf.clear();        
                break;
            }

            if (start > 0)
            {
                buf.erase(buf.begin(), buf.begin() + start);
            }

            if (buf.size() < 3) break;

            u16 packetSize = ((u16)buf[1] << 8) | buf[2];

            if (packetSize != BASE && packetSize != MAX_SIZE) 
            {
                buf.erase(buf.begin());
                continue;
            }

            if (buf.size() < packetSize) 
            {
                break;   
            }

            if (buf[packetSize - 1] == STOP_BYTE) 
            {
                return 1;   
            }
            buf.erase(buf.begin());
        }
    }
}


void collectorThread(int fd, std::mutex& mutex)
{
    std::vector<u8> buf(MAX_SIZE);

    while (true)
    {
        std::fill(buf.begin(), buf.end(), 0);

        bool mode = imageMode.load(std::memory_order_acquire);
        u8 start = mode ? 0x0B : 0x0A;

        if (writeAll(fd, &start, 1) != 1) {
            continue;
        }

        if (isValid(buf, fd) != 1) continue;

        auto p = parsePacket(buf);
        if (!p) continue;

        {
            std::lock_guard<std::mutex> lock(mutex);
            packet = *p;
            has_packet = true;
        }
    }
}
